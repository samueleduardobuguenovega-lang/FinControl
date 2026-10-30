package com.fincontrol.app.gastos;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Gasto;
import com.fincontrol.app.model.Plantilla;
import com.fincontrol.app.plantillas.PlantillaDao;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lista los movimientos del usuario y abre el formulario de gasto.
 *
 * <p>Cuatro mejoras sobre la lista simple:</p>
 * <ul>
 *     <li>S23: pestañas GASTOS / INGRESOS. Las dos comparten la lista, el encabezado y el total;
 *     solo cambian el {@code tipo} de la consulta y el estado vacío.</li>
 *     <li>S24: períodos Día / Semana / Mes / Año. La etiqueta, la lista y el total siguen al
 *     período elegido, y las flechas lo mueven un día, una semana, un mes o un año.</li>
 *     <li>S17: plantillas frecuentes. Una fila de chips agrega un gasto completo de un toque y
 *     anota el uso para que el orden aprenda; con pulsación larga se edita o se borra.</li>
 *     <li>S19: swipe para borrar, con vibración de confirmación y snackbar de deshacer.</li>
 * </ul>
 */
public class GastosActivity extends AppCompatActivity {

    private static final DateTimeFormatter FORMATO_MES_NOMBRE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CL"));
    /** Etiqueta de semana del encabezado del período: "26 oct". */
    private static final DateTimeFormatter FORMATO_DIA_MES =
            DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"));
    /**
     * Borde de semana del locale español de Chile: el lunes es el primer día y la etiqueta se arma
     * desde ese lunes, así un gasto del sábado cae en la semana que empezó el lunes.
     */
    private static final WeekFields SEMANA_LOCAL = WeekFields.of(Locale.forLanguageTag("es-CL"));

    /** Período que muestran el encabezado y la lista (S24). */
    private enum Periodo { DIA, SEMANA, MES, ANO }

    private GastoDao gastoDao;
    private PlantillaDao plantillaDao;
    private SessionManager session;
    private GastoAdapter adaptador;
    private PlantillaAdaptador adaptadorPlantillas;
    private RecyclerView lista;
    private TextView sinDatos;
    private TextView textoMes;
    private TextView totalMes;
    private TextView plantillasTitulo;
    private TextView plantillasVacias;
    private RecyclerView listaPlantillas;
    private View raiz;

    /** Tipo de registro que lista la pantalla: {@link Gasto#TIPO_GASTO} o
     * {@link Gasto#TIPO_INGRESO}. */
    private String tipoActual = Gasto.TIPO_GASTO;
    /** Período elegido en el segundo toggle group; por defecto queda el mes (S24). */
    private Periodo periodoActual = Periodo.MES;
    /** Fecha de referencia del período elegido; parte en hoy. */
    private LocalDate fechaSeleccionada = LocalDate.now();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja la toolbar, la lista y el FAB fuera de las barras del sistema.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_gastos);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.gastos_titulo);
        }

        gastoDao = new GastoDao(this);
        plantillaDao = new PlantillaDao(this);
        session = new SessionManager(this);

        raiz = findViewById(R.id.raiz_gastos);
        lista = findViewById(R.id.rv_gastos);
        sinDatos = findViewById(R.id.txt_sin_gastos);
        textoMes = findViewById(R.id.txt_mes);
        totalMes = findViewById(R.id.txt_mes_total);
        listaPlantillas = findViewById(R.id.rv_plantillas);
        plantillasTitulo = findViewById(R.id.txt_plantillas_titulo);
        plantillasVacias = findViewById(R.id.txt_plantillas_vacias);

        adaptador = new GastoAdapter();
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        configurarAccionesDeFila();
        configurarSwipe();

        adaptadorPlantillas = new PlantillaAdaptador();
        listaPlantillas.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        listaPlantillas.setAdapter(adaptadorPlantillas);

        MaterialButton mesAnterior = findViewById(R.id.btn_mes_anterior);
        MaterialButton mesSiguiente = findViewById(R.id.btn_mes_siguiente);
        mesAnterior.setOnClickListener(v -> cambiarPeriodo(-1));
        mesSiguiente.setOnClickListener(v -> cambiarPeriodo(1));

        configurarSelectorTipo();
        configurarSelectorPeriodo();

        ExtendedFloatingActionButton botonNuevo = findViewById(R.id.fab_nuevo_gasto);
        botonNuevo.setOnClickListener(v -> {
            // El formulario tiene que crear el registro de la pestaña activa, gasto o ingreso.
            Intent nuevo = new Intent(GastosActivity.this, GastoFormActivity.class);
            nuevo.putExtra(GastoFormActivity.EXTRA_TIPO, tipoActual);
            startActivity(nuevo);
        });
    }

    /** Selector segmentado GASTOS / INGRESOS (S23). */
    private void configurarSelectorTipo() {
        MaterialButtonToggleGroup grupoTipo = findViewById(R.id.grupo_tipo);
        grupoTipo.addOnButtonCheckedListener((grupo, botonId, chequeado) -> {
            // El grupo también avisa del botón que se desmarca: solo importa el que queda marcado.
            if (!chequeado) {
                return;
            }
            cambiarTipo(botonId == R.id.btn_tab_ingresos
                    ? Gasto.TIPO_INGRESO : Gasto.TIPO_GASTO);
        });
    }

    /** Selector segmentado Día / Semana / Mes / Año (S24). */
    private void configurarSelectorPeriodo() {
        MaterialButtonToggleGroup grupoPeriodo = findViewById(R.id.grupo_periodo);
        grupoPeriodo.addOnButtonCheckedListener((grupo, botonId, chequeado) -> {
            if (!chequeado) {
                return;
            }
            Periodo nuevo = periodoDe(botonId);
            if (nuevo == periodoActual) {
                return;
            }
            periodoActual = nuevo;
            cargarGastos();
        });
    }

    private static Periodo periodoDe(int botonId) {
        if (botonId == R.id.btn_periodo_dia) {
            return Periodo.DIA;
        }
        if (botonId == R.id.btn_periodo_semana) {
            return Periodo.SEMANA;
        }
        if (botonId == R.id.btn_periodo_ano) {
            return Periodo.ANO;
        }
        return Periodo.MES;
    }

    private void cambiarTipo(String tipo) {
        if (tipoActual.equals(tipo)) {
            return;
        }
        tipoActual = tipo;
        cargarTodo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session.haySesion()) {
            finish();
            return;
        }
        cargarTodo();
    }

    /** Mueve el período elegido una unidad hacia adelante o atrás (S24). */
    private void cambiarPeriodo(int pasos) {
        fechaSeleccionada = desplazar(fechaSeleccionada, pasos);
        cargarGastos();
    }

    /** Desplazamiento puro de la fecha de referencia: un día, una semana, un mes o un año. */
    private LocalDate desplazar(LocalDate referencia, int pasos) {
        switch (periodoActual) {
            case DIA:
                return referencia.plusDays(pasos);
            case SEMANA:
                return referencia.plusWeeks(pasos);
            case ANO:
                return referencia.plusYears(pasos);
            case MES:
            default:
                return referencia.plusMonths(pasos);
        }
    }

    private void cargarTodo() {
        cargarGastos();
        cargarPlantillas();
    }

    /** Recarga juntos el período elegido, su encabezado y el estado vacío (S23/S24). */
    private void cargarGastos() {
        if (!session.haySesion()) {
            finish();
            return;
        }
        long usuarioId = session.getUserId();
        String[] rango = rangoDe(fechaSeleccionada);

        List<Gasto> movimientos =
                gastoDao.listarPorRango(usuarioId, tipoActual, rango[0], rango[1]);
        if (movimientos == null) {
            movimientos = new ArrayList<>();
        }
        adaptador.actualizar(movimientos);

        textoMes.setText(getString(R.string.mes_total, etiquetaPeriodo(fechaSeleccionada)));
        totalMes.setText(String.format(Locale.US, "$%.2f",
                gastoDao.totalDelRango(usuarioId, tipoActual, rango[0], rango[1])));

        // Accesibilidad: cada flecha se anuncia con el período al que lleva, sin string extra.
        findViewById(R.id.btn_mes_anterior).setContentDescription(
                etiquetaPeriodo(desplazar(fechaSeleccionada, -1)));
        findViewById(R.id.btn_mes_siguiente).setContentDescription(
                etiquetaPeriodo(desplazar(fechaSeleccionada, 1)));

        actualizarEstadoVacio();
    }

    /**
     * Extremos ISO (AAAA-MM-DD) del período elegido, ambos incluidos, tal como se guarda
     * {@code fecha}.
     *
     * <p>Comparar las fechas con ceros a la izquierda en orden lexicográfico da el mismo orden
     * cronológico, y en eso se apoya el {@code BETWEEN} de {@link GastoDao#listarPorRango}.</p>
     */
    private String[] rangoDe(LocalDate referencia) {
        switch (periodoActual) {
            case DIA:
                return new String[]{referencia.toString(), referencia.toString()};
            case SEMANA: {
                LocalDate inicio = inicioDeSemana(referencia);
                return new String[]{inicio.toString(), inicio.plusDays(6).toString()};
            }
            case ANO:
                return new String[]{
                        LocalDate.of(referencia.getYear(), 1, 1).toString(),
                        LocalDate.of(referencia.getYear(), 12, 31).toString()};
            case MES:
            default: {
                YearMonth mes = YearMonth.from(referencia);
                return new String[]{mes.atDay(1).toString(), mes.atEndOfMonth().toString()};
            }
        }
    }

    /** Primer día de la semana de la fecha de referencia (lunes en el locale es-CL). */
    private static LocalDate inicioDeSemana(LocalDate referencia) {
        return referencia.with(SEMANA_LOCAL.dayOfWeek(), 1);
    }

    /** Etiqueta del encabezado según el período: 2026-10-26, "26 oct – 1 nov", "Octubre 2026" o
     * "2026". */
    private String etiquetaPeriodo(LocalDate referencia) {
        switch (periodoActual) {
            case DIA:
                return referencia.toString();
            case SEMANA: {
                LocalDate inicio = inicioDeSemana(referencia);
                return inicio.format(FORMATO_DIA_MES) + " – "
                        + inicio.plusDays(6).format(FORMATO_DIA_MES);
            }
            case ANO:
                return String.valueOf(referencia.getYear());
            case MES:
            default:
                return etiquetaMes(YearMonth.from(referencia));
        }
    }

    /**
     * Deja el estado vacío al día después de insertar, editar, borrar o deshacer. Arregla lo que
     * faltaba en la T15.
     */
    private void actualizarEstadoVacio() {
        boolean vacio = adaptador.getItemCount() == 0;
        // Mismo TextView para las dos pestañas: el mensaje sigue al tipo elegido (S23).
        sinDatos.setText(Gasto.TIPO_INGRESO.equals(tipoActual)
                ? R.string.sin_ingresos : R.string.sin_gastos);
        sinDatos.setVisibility(vacio ? View.VISIBLE : View.GONE);
        lista.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    private void cargarPlantillas() {
        // Las plantillas son gastos guardados (S17), así que solo van en la pestaña GASTOS.
        boolean esGasto = Gasto.TIPO_GASTO.equals(tipoActual);
        plantillasTitulo.setVisibility(esGasto ? View.VISIBLE : View.GONE);
        if (!esGasto) {
            listaPlantillas.setVisibility(View.GONE);
            plantillasVacias.setVisibility(View.GONE);
            return;
        }
        if (!session.haySesion()) {
            return;
        }
        List<Plantilla> plantillas = plantillaDao.listarPorUsuario(session.getUserId());
        if (plantillas == null) {
            plantillas = new ArrayList<>();
        }
        adaptadorPlantillas.actualizar(plantillas);
        boolean vacio = plantillas.isEmpty();
        listaPlantillas.setVisibility(vacio ? View.GONE : View.VISIBLE);
        plantillasVacias.setVisibility(vacio ? View.VISIBLE : View.GONE);
    }

    /** Nombre del mes en español y el año, con mayúscula inicial: "Enero 2025". */
    private String etiquetaMes(YearMonth mes) {
        String texto = mes.atDay(1).format(FORMATO_MES_NOMBRE);
        if (texto.isEmpty()) {
            return texto;
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    // ---------------------------------------------------------------- plantillas (S17)

    /** Agrega la plantilla como gasto de hoy, anota el uso y recarga la pantalla. */
    private void agregarDesdePlantilla(Plantilla plantilla) {
        if (!session.haySesion()) {
            return;
        }
        String hoy = LocalDate.now().toString();
        long id = gastoDao.insertar(new Gasto(0L, plantilla.getMonto(), plantilla.getCategoria(),
                hoy, plantilla.getNombre(), null, null, session.getUserId(), Gasto.TIPO_GASTO));
        if (id <= 0L) {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
            return;
        }
        // El uso es lo que reordena los chips: primero los más usados, después los más recientes.
        plantillaDao.marcarUso(plantilla.getId());
        cargarTodo();
        Snackbar.make(raiz, R.string.plantilla_agregada, Snackbar.LENGTH_SHORT)
                .setAnchorView(findViewById(R.id.fab_nuevo_gasto))
                .show();
    }

    /** Pulsación larga: editar o borrar la plantilla, o cancelar. */
    private void mostrarOpcionesPlantilla(Plantilla plantilla) {
        String[] opciones = {
                getString(R.string.accion_editar),
                getString(R.string.accion_eliminar),
                getString(R.string.btn_cancelar)
        };
        new AlertDialog.Builder(this)
                .setTitle(plantilla.getNombre())
                .setItems(opciones, (dialogo, cual) -> {
                    if (cual == 0) {
                        mostrarEdicionPlantilla(plantilla);
                    } else if (cual == 1) {
                        confirmarEliminarPlantilla(plantilla);
                    }
                })
                .show();
    }

    /** Diálogo de nombre y monto, mismo formato que el de renombrar categoría. */
    private void mostrarEdicionPlantilla(Plantilla plantilla) {
        int densidad = Math.round(getResources().getDisplayMetrics().density);

        TextInputLayout campoNombre = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        campoNombre.setHint(R.string.hint_descripcion);
        TextInputEditText entradaNombre = new TextInputEditText(this);
        entradaNombre.setSingleLine(true);
        entradaNombre.setMaxLines(1);
        entradaNombre.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entradaNombre.setText(plantilla.getNombre());
        entradaNombre.setSelection(entradaNombre.getText() == null
                ? 0 : entradaNombre.getText().length());
        campoNombre.addView(entradaNombre);

        TextInputLayout campoMonto = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        campoMonto.setHint(R.string.hint_monto);
        campoMonto.setPrefixText("$");
        TextInputEditText entradaMonto = new TextInputEditText(this);
        entradaMonto.setSingleLine(true);
        entradaMonto.setMaxLines(1);
        entradaMonto.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        entradaMonto.setText(String.format(Locale.US, "%.2f", plantilla.getMonto()));
        campoMonto.addView(entradaMonto);

        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(densidad * 24, densidad * 8, densidad * 24, 0);
        contenedor.addView(campoNombre, new LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams paramsMonto = new LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        paramsMonto.topMargin = densidad * 12;
        contenedor.addView(campoMonto, paramsMonto);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(R.string.accion_editar)
                .setView(contenedor)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.accion_editar, null)
                .create();

        dialogo.setOnShowListener(mostrado -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String nombre = entradaNombre.getText() == null
                            ? "" : entradaNombre.getText().toString().trim();
                    if (nombre.isEmpty()) {
                        campoNombre.setError(getString(R.string.error_campo_vacio));
                        return;
                    }
                    double monto;
                    try {
                        monto = Double.parseDouble(entradaMonto.getText() == null
                                ? "" : entradaMonto.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        monto = 0d;
                    }
                    if (monto <= 0d || Double.isNaN(monto) || Double.isInfinite(monto)) {
                        campoMonto.setError(getString(R.string.error_monto));
                        return;
                    }
                    if (!plantillaDao.actualizar(plantilla.getId(), nombre, monto)) {
                        // El DAO rechaza un nombre que ya tiene otra plantilla del usuario.
                        campoNombre.setError(getString(R.string.error_inesperado));
                        return;
                    }
                    campoNombre.setError(null);
                    dialogo.dismiss();
                    cargarPlantillas();
                }));

        dialogo.show();
        entradaNombre.requestFocus();
    }

    private void confirmarEliminarPlantilla(Plantilla plantilla) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.plantilla_confirmar_eliminar, plantilla.getNombre()))
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar, (dialogo, cual) -> {
                    if (plantillaDao.eliminar(plantilla.getId())) {
                        cargarPlantillas();
                        Toast.makeText(this, R.string.plantilla_eliminada, Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- filas (S15/S19)

    /**
     * El toque en la fila mantiene el diálogo de opciones de la T12, y la pantalla decide qué
     * hace cada opción.
     */
    private void configurarAccionesDeFila() {
        adaptador.setOnGastoAcciones(new GastoAdapter.OnGastoAcciones() {
            @Override
            public void onEditar(Gasto gasto) {
                Intent intent = new Intent(GastosActivity.this, GastoFormActivity.class);
                intent.putExtra(GastoFormActivity.EXTRA_GASTO_ID, gasto.getId());
                intent.putExtra(GastoFormActivity.EXTRA_TIPO, gasto.getTipo());
                startActivity(intent);
            }

            @Override
            public void onEliminar(Gasto gasto) {
                eliminarConDeshacer(gasto);
            }
        });
    }

    /** Swipe a cualquier lado borra la fila, con vibración de confirmación y deshacer (S19). */
    private void configurarSwipe() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int posicion = viewHolder.getBindingAdapterPosition();
                Gasto gasto = adaptador.quitar(posicion);
                if (gasto == null) {
                    return;
                }
                eliminarConDeshacer(gasto);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(lista);
    }

    /**
     * Borra el gasto, lo confirma con vibración y ofrece deshacer.
     *
     * <p>El deshacer reinserta una copia con id nuevo a propósito: el id borrado se fue con la
     * fila y ninguna otra tabla apunta a un id de gasto, así que un id nuevo mantiene coherentes
     * el contador y las claves foráneas sin resucitar una clave primaria.</p>
     */
    private void eliminarConDeshacer(Gasto gasto) {
        gastoDao.eliminar(gasto.getId());
        // Los dos caminos llegan acá: el swipe ya sacó la fila, el borrado del diálogo no.
        adaptador.quitarPorId(gasto.getId());
        lista.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        actualizarEstadoVacio();
        Snackbar.make(raiz, R.string.undo_eliminar, Snackbar.LENGTH_LONG)
                .setAnchorView(findViewById(R.id.fab_nuevo_gasto))
                .setAction(R.string.undo_deshacer, v -> {
                    // La copia guarda su propio tipo, así deshacer un ingreso devuelve un ingreso.
                    gastoDao.insertar(new Gasto(0L, gasto.getMonto(), gasto.getCategoria(),
                            gasto.getFecha(), gasto.getDescripcion(), gasto.getLatitud(),
                            gasto.getLongitud(), gasto.getUsuarioId(), gasto.getTipo()));
                    cargarGastos();
                })
                .show();
    }

    // ---------------------------------------------------------------- chips de plantillas

    /** Chips horizontales de plantillas frecuentes. */
    private final class PlantillaAdaptador
            extends RecyclerView.Adapter<PlantillaAdaptador.PlantillaViewHolder> {

        private final List<Plantilla> plantillas = new ArrayList<>();

        void actualizar(List<Plantilla> nuevas) {
            plantillas.clear();
            if (nuevas != null) {
                plantillas.addAll(nuevas);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlantillaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View vista = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_plantilla, parent, false);
            return new PlantillaViewHolder(vista);
        }

        @Override
        public void onBindViewHolder(@NonNull PlantillaViewHolder holder, int position) {
            Plantilla plantilla = plantillas.get(position);
            holder.nombre.setText(plantilla.getNombre());
            holder.monto.setText(String.format(Locale.US, "$%.2f", plantilla.getMonto()));
            holder.itemView.setOnClickListener(v -> agregarDesdePlantilla(plantilla));
            holder.itemView.setOnLongClickListener(v -> {
                mostrarOpcionesPlantilla(plantilla);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return plantillas.size();
        }

        final class PlantillaViewHolder extends RecyclerView.ViewHolder {
            private final TextView nombre;
            private final TextView monto;

            PlantillaViewHolder(@NonNull View itemView) {
                super(itemView);
                nombre = itemView.findViewById(R.id.txt_plantilla_nombre);
                monto = itemView.findViewById(R.id.txt_plantilla_monto);
            }
        }
    }
}

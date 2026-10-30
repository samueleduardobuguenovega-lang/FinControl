package com.fincontrol.app.gastos;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.categorias.CategoriaDao;
import com.fincontrol.app.categorias.CategoriasActivity;
import com.fincontrol.app.categorias.DialogoCategoria;
import com.fincontrol.app.categorias.EstiloCategoria;
import com.fincontrol.app.model.Categoria;
import com.fincontrol.app.model.Gasto;
import com.fincontrol.app.plantillas.PlantillaDao;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.fincontrol.app.util.LocationHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formulario para registrar o editar un gasto: monto, grilla de categorías, fecha, descripción y
 * una ubicación actual opcional (S15).
 *
 * <p>El modo edición lo decide el extra {@code gasto_id}: si viene, los campos se llenan con el
 * gasto guardado y al guardar se llama a {@link GastoDao#actualizar(Gasto)} en vez de insertar. El
 * extra {@code tipo} es opcional y elige el tipo de registro: gasto por defecto, ingreso cuando la
 * pestaña de ingresos de la lista abre el formulario. De paso define qué catálogo de categorías se
 * ofrece.</p>
 *
 * <p>Desde la v4 el monto se escribe con el teclado de la app (S21) y el selector de categoría es
 * visual (S22): cada celda es un círculo de color con el icono de la categoría, la celda del final
 * agrega una categoría nueva ahí mismo vía {@link DialogoCategoria}, y la grilla mantiene una sola
 * selección.</p>
 *
 * <p>La captura de plantillas (S17) solo existe al crear un gasto: el switch
 * {@code sw_plantilla} guarda el gasto como plantilla frecuente, y si el patrón se repite (tres
 * gastos parecidos en los últimos 30 días) la sugerencia inteligente aparece una vez guardado el
 * gasto.</p>
 */
public class GastoFormActivity extends AppCompatActivity {

    /** Extra que convierte el formulario en la pantalla de edición de un gasto existente. */
    public static final String EXTRA_GASTO_ID = "gasto_id";
    /** Extra opcional con el tipo de registro ('gasto' o 'ingreso'); por defecto es gasto. */
    public static final String EXTRA_TIPO = "tipo";

    private static final String FORMATO_FECHA = "yyyy-MM-dd";
    private static final Pattern PATRON_FECHA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int COLUMNAS_CATEGORIAS = 3;
    /** Mismo tope que el android:maxLength del campo de monto (12 dígitos o separadores). */
    private static final int MAX_MONTO = 12;
    /** Si un patrón se repite estas veces dentro de la ventana, vale una plantilla (S17). */
    private static final int USOS_PARA_SUGERIR = 3;
    /** Ventana de la sugerencia inteligente, en días. */
    private static final int DIAS_VENTANA_SUGERENCIA = 30;

    private EditText campoMonto;
    private EditText campoFecha;
    private EditText campoDescripcion;
    private TextView textoUbicacion;
    private Button botonUbicacion;
    private SelectorCategoriaAdapter adaptadorCategorias;
    private MaterialSwitch interruptorPlantilla;

    private GastoDao gastoDao;
    private PlantillaDao plantillaDao;
    private CategoriaDao categoriaDao;
    private SessionManager session;

    private long gastoId = -1L;
    private boolean modoEdicion;
    private String categoriaOriginal;
    private String tipoRegistro = Gasto.TIPO_GASTO;

    private Double latitud;
    private Double longitud;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja el formulario fuera de las barras de estado y navegación.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_gasto_form);

        gastoId = getIntent().getLongExtra(EXTRA_GASTO_ID, -1L);
        modoEdicion = gastoId > 0L;
        tipoRegistro = tipoDe(getIntent().getStringExtra(EXTRA_TIPO));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(modoEdicion ? R.string.gasto_editar_titulo : R.string.gasto_nuevo);
        }

        gastoDao = new GastoDao(this);
        plantillaDao = new PlantillaDao(this);
        categoriaDao = new CategoriaDao(this);
        session = new SessionManager(this);

        campoMonto = findViewById(R.id.et_monto);
        campoFecha = findViewById(R.id.et_fecha);
        campoDescripcion = findViewById(R.id.et_descripcion);
        textoUbicacion = findViewById(R.id.txt_ubicacion);
        botonUbicacion = findViewById(R.id.btn_ubicacion);
        interruptorPlantilla = findViewById(R.id.sw_plantilla);

        configurarCampoMonto();
        configurarTeclado();

        int densidad = Math.round(getResources().getDisplayMetrics().density);
        adaptadorCategorias = new SelectorCategoriaAdapter(densidad);
        adaptadorCategorias.setAlAgregar(this::agregarCategoriaInline);
        RecyclerView selector = findViewById(R.id.rv_categorias_selector);
        selector.setLayoutManager(new GridLayoutManager(this, COLUMNAS_CATEGORIAS));
        selector.setAdapter(adaptadorCategorias);
        selector.setNestedScrollingEnabled(false);

        findViewById(R.id.btn_gestionar_categorias).setOnClickListener(v ->
                startActivity(new Intent(GastoFormActivity.this, CategoriasActivity.class)));

        if (!modoEdicion) {
            campoFecha.setText(fechaDeHoy());
        }

        botonUbicacion.setOnClickListener(v -> obtenerUbicacion());
        findViewById(R.id.btn_guardar_gasto).setOnClickListener(v -> guardar());

        if (modoEdicion) {
            precargarGasto();
        }
        aplicarTipoRegistro();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver de la pantalla de categorías (o de una primera creación) el catálogo puede
        // haber cambiado: se recarga y se mantiene la selección actual si todavía existe.
        recargarCategorias();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LocationHelper.onRequestPermissionsResult(requestCode);
    }

    private String fechaDeHoy() {
        return new SimpleDateFormat(FORMATO_FECHA, Locale.US).format(new Date());
    }

    /** Normaliza el tipo de registro que viene en un intent: solo 'ingreso' es distinto. */
    private static String tipoDe(@Nullable String tipo) {
        return tipo != null && Gasto.TIPO_INGRESO.equalsIgnoreCase(tipo.trim())
                ? Gasto.TIPO_INGRESO
                : Gasto.TIPO_GASTO;
    }

    private boolean esIngreso() {
        return Gasto.TIPO_INGRESO.equals(tipoRegistro);
    }

    /**
     * Esconde los switch que solo tienen sentido en un gasto: un ingreso no lleva ubicación ni
     * plantilla de gasto frecuente (las plantillas siempre insertan gastos).
     */
    private void aplicarTipoRegistro() {
        boolean plantillaVisible = !modoEdicion && !esIngreso();
        interruptorPlantilla.setVisibility(plantillaVisible ? View.VISIBLE : View.GONE);
        botonUbicacion.setVisibility(esIngreso() ? View.GONE : View.VISIBLE);
        if (esIngreso()) {
            textoUbicacion.setVisibility(View.GONE);
        }
    }

    /** Precarga todos los campos del gasto guardado (modo edición de la S15). */
    private void precargarGasto() {
        if (!session.haySesion()) {
            finish();
            return;
        }
        Gasto gasto = gastoDao.buscar(gastoId, session.getUserId());
        if (gasto == null) {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tipoRegistro = tipoDe(gasto.getTipo());
        campoMonto.setText(String.format(Locale.US, "%.2f", gasto.getMonto()));
        campoFecha.setText(gasto.getFecha());
        campoDescripcion.setText(gasto.getDescripcion());
        categoriaOriginal = gasto.getCategoria();
        adaptadorCategorias.setSeleccionada(categoriaOriginal);
        latitud = gasto.getLatitud();
        longitud = gasto.getLongitud();
        if (gasto.tieneUbicacion()) {
            textoUbicacion.setText(String.format(Locale.US, "%.6f, %.6f", latitud, longitud));
            textoUbicacion.setVisibility(View.VISIBLE);
        }
        aplicarTipoRegistro();
    }

    /**
     * Carga el catálogo del tipo actual con el orden inteligente: primero las fijadas, después
     * por uso.
     */
    private void recargarCategorias() {
        adaptadorCategorias.actualizar(categoriaDao.listarOrdenadas(tipoRegistro));
    }

    /**
     * El campo de monto nunca abre el teclado del sistema (S21): tocarlo no hace nada y los
     * dígitos salen solo del {@link TecladoNumerico} que va bajo el formulario.
     */
    private void configurarCampoMonto() {
        campoMonto.setShowSoftInputOnFocus(false);
        campoMonto.setFocusable(false);
        campoMonto.setFocusableInTouchMode(false);
        campoMonto.setCursorVisible(false);
        campoMonto.setLongClickable(false);
    }

    private void configurarTeclado() {
        TecladoNumerico teclado = findViewById(R.id.teclado_monto);
        teclado.setListener(new TecladoNumerico.Listener() {
            @Override
            public void onDigito(String digito) {
                agregarAlMonto(digito);
            }

            @Override
            public void onBorrar() {
                borrarUltimoDelMonto();
            }

            @Override
            public void onConfirm() {
                guardar();
            }
        });
    }

    /**
     * Agrega una tecla al buffer del monto. Se aceptan los dos separadores y se normalizan a ".";
     * cabe un solo separador y el buffer nunca pasa de {@value #MAX_MONTO} caracteres.
     */
    private void agregarAlMonto(String tecla) {
        if (tecla == null || tecla.isEmpty()) {
            return;
        }
        StringBuilder buffer = new StringBuilder(campoMonto.getText() == null
                ? "" : campoMonto.getText().toString());
        String digito = tecla;
        if (",".equals(tecla) || ".".equals(tecla)) {
            if (buffer.indexOf(".") >= 0) {
                return;
            }
            // Un separador al principio se parsearía como monto inválido: se antepone el cero.
            if (buffer.length() == 0) {
                buffer.append('0');
            }
            digito = ".";
        }
        if (buffer.length() + digito.length() > MAX_MONTO) {
            return;
        }
        campoMonto.setError(null);
        String nuevo = buffer.append(digito).toString();
        campoMonto.setText(nuevo);
        campoMonto.setSelection(nuevo.length());
    }

    /** Saca el último carácter del buffer del monto. */
    private void borrarUltimoDelMonto() {
        String actual = campoMonto.getText() == null ? "" : campoMonto.getText().toString();
        if (actual.isEmpty()) {
            return;
        }
        campoMonto.setError(null);
        String nuevo = actual.substring(0, actual.length() - 1);
        campoMonto.setText(nuevo);
        campoMonto.setSelection(nuevo.length());
    }

    /** Celda de agregar de la grilla (S22): crea la categoría y la deja seleccionada al tiro. */
    private void agregarCategoriaInline() {
        DialogoCategoria.mostrar(this, null, false, (nombre, color, icono, esIngreso) -> {
            long id = categoriaDao.insertar(nombre, color, icono, tipoRegistro);
            if (id == -1L) {
                return false;
            }
            adaptadorCategorias.setSeleccionada(nombre);
            recargarCategorias();
            return true;
        });
    }

    private void obtenerUbicacion() {
        botonUbicacion.setEnabled(false);
        LocationHelper.fetchCurrentLocation(this, (lat, lon) -> {
            botonUbicacion.setEnabled(true);
            if (lat == null || lon == null) {
                latitud = null;
                longitud = null;
                Toast.makeText(this, R.string.permiso_ubicacion_denegado, Toast.LENGTH_LONG).show();
                textoUbicacion.setVisibility(View.GONE);
                return;
            }
            latitud = lat;
            longitud = lon;
            textoUbicacion.setText(String.format(Locale.US, "%.6f, %.6f", lat, lon));
            textoUbicacion.setVisibility(View.VISIBLE);
        });
    }

    private void guardar() {
        if (!session.haySesion()) {
            finish();
            return;
        }

        double monto = 0d;
        boolean montoValido = true;
        String montoTexto = campoMonto.getText().toString().trim();
        try {
            monto = Double.parseDouble(montoTexto);
            montoValido = monto > 0d && !Double.isNaN(monto) && !Double.isInfinite(monto);
        } catch (NumberFormatException e) {
            montoValido = false;
        }
        if (!montoValido) {
            campoMonto.setError(getString(R.string.error_monto));
        }

        String fecha = campoFecha.getText().toString().trim();
        boolean fechaValida = PATRON_FECHA.matcher(fecha).matches();
        if (!fechaValida) {
            campoFecha.setError(getString(R.string.error_fecha));
        }

        if (!montoValido || !fechaValida) {
            return;
        }

        // El selector deja marcada la primera categoría, así siempre hay una a menos que el
        // usuario haya dejado vacío el catálogo.
        String categoria = adaptadorCategorias.getSeleccionada();
        if (categoria == null || categoria.trim().isEmpty()) {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
            return;
        }
        String descripcion = campoDescripcion.getText().toString().trim();

        if (modoEdicion) {
            Gasto gasto = new Gasto(gastoId, monto, categoria, fecha, descripcion,
                    latitud, longitud, session.getUserId(), tipoRegistro);
            if (gastoDao.actualizar(gasto) > 0) {
                if (!categoria.equals(categoriaOriginal)) {
                    categoriaDao.marcarUso(categoria);
                }
                Toast.makeText(this, R.string.gasto_actualizado, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Gasto gasto = new Gasto(0L, monto, categoria, fecha, descripcion,
                latitud, longitud, session.getUserId(), tipoRegistro);
        if (gastoDao.insertar(gasto) > 0) {
            // Señal de aprendizaje para el orden inteligente (S13).
            categoriaDao.marcarUso(categoria);
            Toast.makeText(this, "Gasto guardado", Toast.LENGTH_SHORT).show();
            if (esIngreso()) {
                // Las plantillas son cosa de gastos (S17), así que un ingreso cierra acá.
                finish();
                return;
            }
            capturarPlantilla(monto, categoria, descripcion);
        } else {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Captura de plantilla del flujo de creación (S17).
     *
     * <p>Guarda la plantilla si el usuario dejó el switch activado y después ofrece la sugerencia
     * inteligente cuando la misma descripción y categoría ya aparece tres veces en los últimos 30
     * días y todavía no hay una plantilla con ese nombre. La pantalla se cierra al descartar el
     * diálogo, así la sugerencia nunca se corta porque la activity se fue.</p>
     */
    private void capturarPlantilla(double monto, String categoria, String descripcion) {
        // La descripción ya viene recortada del guardado; si queda vacía se usa la categoría, que
        // siempre existe, así la plantilla nunca queda con nombre en blanco.
        String nombre = descripcion.isEmpty() ? categoria : descripcion;
        long usuarioId = session.getUserId();

        if (interruptorPlantilla.isChecked()) {
            plantillaDao.insertar(nombre, monto, categoria, usuarioId);
        }

        boolean yaExiste = plantillaDao.existeNombre(usuarioId, nombre);
        // El conteo incluye el gasto recién guardado: tres filas del mismo patrón son la señal.
        int similares = yaExiste ? 0
                : gastoDao.contarSimilares(usuarioId, descripcion, categoria, fechaHace30Dias());
        if (similares < USOS_PARA_SUGERIR) {
            finish();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.sugerencia_guardar, nombre))
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.sugerencia_si, (dialogo, cual) ->
                        plantillaDao.insertar(nombre, monto, categoria, usuarioId))
                .setOnDismissListener(dialogo -> finish())
                .show();
    }

    /** Borde inferior (incluido) de la ventana de sugerencia, en el AAAA-MM-DD que se guarda. */
    private String fechaHace30Dias() {
        return LocalDate.now().minusDays(DIAS_VENTANA_SUGERENCIA).toString();
    }

    /**
     * Selector visual de categorías en una grilla de 3 columnas (S22), armado en código para que
     * el formulario mantenga un solo layout: cada celda es un círculo de color de 48dp con el
     * icono de la categoría y una etiqueta de una línea en 10sp, y la seleccionada queda con borde
     * primario y un poco más grande. La última celda es la de agregar. Solo hay una categoría
     * seleccionada a la vez.
     */
    private static final class SelectorCategoriaAdapter
            extends RecyclerView.Adapter<SelectorCategoriaAdapter.CeldaViewHolder> {

        private final List<Categoria> categorias = new ArrayList<>();
        private final int densidad;
        private String seleccionada;
        private Runnable alAgregar;

        SelectorCategoriaAdapter(int densidad) {
            this.densidad = densidad;
        }

        /** Registra la celda de agregar; si el callback viene null, se esconde. */
        void setAlAgregar(Runnable alAgregar) {
            this.alAgregar = alAgregar;
        }

        void actualizar(List<Categoria> nuevas) {
            categorias.clear();
            if (nuevas != null) {
                categorias.addAll(nuevas);
            }
            if (seleccionada == null || !contiene(seleccionada)) {
                seleccionada = categorias.isEmpty() ? null : categorias.get(0).getNombre();
            }
            notifyDataSetChanged();
        }

        String getSeleccionada() {
            return seleccionada;
        }

        void setSeleccionada(String nombre) {
            this.seleccionada = nombre;
        }

        private boolean contiene(String nombre) {
            for (Categoria categoria : categorias) {
                if (categoria.getNombre().equals(nombre)) {
                    return true;
                }
            }
            return false;
        }

        private int dp(int valor) {
            return Math.round(valor * densidad);
        }

        @NonNull
        @Override
        public CeldaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context contexto = parent.getContext();

            LinearLayout celda = new LinearLayout(contexto);
            celda.setOrientation(LinearLayout.VERTICAL);
            celda.setGravity(Gravity.CENTER_HORIZONTAL);
            RecyclerView.LayoutParams paramsCelda = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            paramsCelda.setMargins(dp(2), dp(6), dp(2), dp(2));
            celda.setLayoutParams(paramsCelda);

            MaterialCardView tarjeta = new MaterialCardView(contexto);
            tarjeta.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            tarjeta.setRadius(dp(24));
            tarjeta.setCardElevation(0f);
            tarjeta.setClickable(true);
            tarjeta.setFocusable(true);

            ImageView icono = new ImageView(contexto);
            icono.setLayoutParams(new FrameLayout.LayoutParams(
                    dp(24), dp(24), Gravity.CENTER));
            icono.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tarjeta.addView(icono);

            TextView etiqueta = new TextView(contexto);
            LinearLayout.LayoutParams paramsEtiqueta = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            paramsEtiqueta.topMargin = dp(4);
            etiqueta.setLayoutParams(paramsEtiqueta);
            etiqueta.setGravity(Gravity.CENTER);
            etiqueta.setMaxLines(1);
            etiqueta.setEllipsize(TextUtils.TruncateAt.END);
            etiqueta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            etiqueta.setTextColor(MaterialColors.getColor(etiqueta,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));

            celda.addView(tarjeta);
            celda.addView(etiqueta);
            return new CeldaViewHolder(celda, tarjeta, icono, etiqueta);
        }

        @Override
        public void onBindViewHolder(@NonNull CeldaViewHolder holder, int position) {
            if (position == categorias.size()) {
                enlazarAgregar(holder);
                return;
            }
            Categoria categoria = categorias.get(position);
            boolean activa = categoria.getNombre().equals(seleccionada);

            holder.etiqueta.setText(categoria.getNombre());
            EstiloCategoria.aplicarCirculo(holder.itemView, holder.tarjeta, holder.icono,
                    categoria.getColor(), categoria.getIcono());

            int primario = MaterialColors.getColor(holder.tarjeta,
                    androidx.appcompat.R.attr.colorPrimary);
            holder.tarjeta.setStrokeWidth(activa ? dp(2) : 0);
            holder.tarjeta.setStrokeColor(activa ? primario : Color.TRANSPARENT);
            holder.tarjeta.setScaleX(activa ? 1.06f : 1f);
            holder.tarjeta.setScaleY(activa ? 1.06f : 1f);

            String nombre = categoria.getNombre();
            holder.tarjeta.setContentDescription(nombre);
            holder.tarjeta.setOnClickListener(v -> {
                seleccionada = nombre;
                notifyDataSetChanged();
            });
        }

        /** Celda de agregar: círculo neutro con el más, del mismo tamaño que las categorías. */
        private void enlazarAgregar(CeldaViewHolder holder) {
            holder.etiqueta.setText("");
            holder.tarjeta.setCardBackgroundColor(MaterialColors.getColor(holder.tarjeta,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh));
            holder.tarjeta.setStrokeWidth(dp(1));
            holder.tarjeta.setStrokeColor(MaterialColors.getColor(holder.tarjeta,
                    com.google.android.material.R.attr.colorOutlineVariant));
            holder.tarjeta.setScaleX(1f);
            holder.tarjeta.setScaleY(1f);
            holder.icono.setImageResource(R.drawable.ic_add);
            holder.icono.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(
                    holder.tarjeta, androidx.appcompat.R.attr.colorPrimary)));
            holder.tarjeta.setContentDescription(
                    holder.itemView.getContext().getString(R.string.cat_nueva));
            holder.tarjeta.setOnClickListener(v -> {
                if (alAgregar != null) {
                    alAgregar.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return categorias.size() + (alAgregar == null ? 0 : 1);
        }

        static final class CeldaViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView tarjeta;
            private final ImageView icono;
            private final TextView etiqueta;

            CeldaViewHolder(@NonNull LinearLayout celda, @NonNull MaterialCardView tarjeta,
                            @NonNull ImageView icono, @NonNull TextView etiqueta) {
                super(celda);
                this.tarjeta = tarjeta;
                this.icono = icono;
                this.etiqueta = etiqueta;
            }
        }
    }
}

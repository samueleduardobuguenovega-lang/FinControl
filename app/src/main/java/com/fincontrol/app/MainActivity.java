package com.fincontrol.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.gastos.GastoDao;
import com.fincontrol.app.model.Gasto;
import com.fincontrol.app.model.Suscripcion;
import com.fincontrol.app.perfil.CuentaActivity;
import com.fincontrol.app.resumen.ResumenCategoria;
import com.fincontrol.app.resumen.ResumenDao;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.suscripciones.SuscripcionDao;
import com.fincontrol.app.util.InsetsHelper;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Panel principal: resumen del mes, distribución por categoría, próximos cobros
 * de suscripciones y acceso a las pantallas de gastos, suscripciones y mapa.
 */
public class MainActivity extends AppCompatActivity {

    /** Clave de mes que se guarda en la tabla gastos, por ejemplo 2025-10. */
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US);

    /** Tope de las barras: cada una es un porcentaje de la categoría mayor. */
    private static final int BARRA_MAXIMA = 100;

    /** Padding interno del placeholder ic_person dentro del chip de avatar de 40dp. */
    private static final int PADDING_PLACEHOLDER_DP = 8;

    private SessionManager sesion;
    private GastoDao gastoDao;
    private SuscripcionDao suscripcionDao;
    private ResumenDao resumenDao;

    private TextView tvHola;
    private ImageView ivAvatar;
    private TextView tvTotalGastos;
    private TextView tvTotalSubs;
    private TextView tvSaldo;
    private TextView tvSinProximas;
    private LinearLayout llResumen;
    private TextView tvResumenVacio;
    private RecyclerView rvProximos;
    private ProximosAdapter adaptador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja el panel libre de las barras de estado y navegación.
        InsetsHelper.aplicar(this);

        sesion = new SessionManager(this);
        if (!sesion.haySesion()) {
            irALogin();
            return;
        }

        setContentView(R.layout.activity_main);
        gastoDao = new GastoDao(this);
        suscripcionDao = new SuscripcionDao(this);
        resumenDao = new ResumenDao(this);

        tvHola = findViewById(R.id.tv_hola);
        ivAvatar = findViewById(R.id.iv_avatar);
        tvTotalGastos = findViewById(R.id.tv_total_gastos);
        tvTotalSubs = findViewById(R.id.tv_total_subs);
        tvSaldo = findViewById(R.id.tv_saldo);
        tvSinProximas = findViewById(R.id.tv_sin_proximas);
        llResumen = findViewById(R.id.ll_resumen);
        tvResumenVacio = findViewById(R.id.tv_resumen_vacio);
        rvProximos = findViewById(R.id.rv_proximos);

        rvProximos.setLayoutManager(new LinearLayoutManager(this));
        adaptador = new ProximosAdapter();
        rvProximos.setAdapter(adaptador);

        findViewById(R.id.btn_gastos).setOnClickListener(v ->
                startActivity(new Intent(this, com.fincontrol.app.gastos.GastosActivity.class)));

        findViewById(R.id.btn_suscripciones).setOnClickListener(v ->
                startActivity(new Intent(this, com.fincontrol.app.suscripciones.SuscripcionesActivity.class)));

        findViewById(R.id.btn_mapa).setOnClickListener(v ->
                startActivity(new Intent(this, com.fincontrol.app.mapa.MapaActivity.class)));

        // El chip del avatar y el saludo son la entrada a la pantalla de cuenta (S16).
        findViewById(R.id.header_perfil).setOnClickListener(v ->
                startActivity(new Intent(this, CuentaActivity.class)));

        findViewById(R.id.btn_cerrar_sesion).setOnClickListener(v -> {
            sesion.cerrarSesion();
            irALogin();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresca los totales al volver al panel, por si hay gastos o suscripciones nuevas.
        if (sesion != null && sesion.haySesion()) {
            refrescar();
        }
    }

    private void refrescar() {
        long usuarioId = sesion.getUserId();
        tvHola.setText(getString(R.string.dashboard_hola, sesion.getNombre()));
        mostrarAvatar(usuarioId);

        try {
            String mes = LocalDate.now().format(FORMATO_MES);
            // La v4 separa la tabla por tipo: el número grande es solo de gastos (S23).
            double gastado = gastoDao.totalDelTipo(usuarioId, Gasto.TIPO_GASTO, mes);
            double ingresado = gastoDao.totalDelTipo(usuarioId, Gasto.TIPO_INGRESO, mes);
            double suscripciones = suscripcionDao.totalActivas(usuarioId);
            tvTotalGastos.setText(formatoMonto(gastado));
            tvTotalSubs.setText(formatoMonto(suscripciones));
            tvSaldo.setText(formatoSaldo(ingresado - gastado));

            List<Suscripcion> proximas = suscripcionDao.proximosEn7Dias(usuarioId);
            if (proximas == null) {
                proximas = new ArrayList<>();
            }
            adaptador.actualizar(proximas);

            boolean vacia = proximas.isEmpty();
            tvSinProximas.setVisibility(vacia ? View.VISIBLE : View.GONE);
            rvProximos.setVisibility(vacia ? View.GONE : View.VISIBLE);

            mostrarTopCategorias(usuarioId, mes);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
        }
    }

    private static String formatoMonto(double valor) {
        return String.format(Locale.getDefault(), "$%.2f", valor);
    }

    /**
     * Saldo del mes: ingresos menos gastos (S23).
     *
     * <p>Simplificación de la v1: un saldo negativo mantiene el mismo color del número
     * grande en vez de pasar al rol de error. El número va con colorOnPrimary sobre un
     * contenedor primary, donde el rojo rompería el contraste de la tarjeta, y el signo
     * menos ya dice el signo.</p>
     */
    private static String formatoSaldo(double valor) {
        return String.format(Locale.US, "$%.2f", valor);
    }

    /**
     * Llena la tarjeta "top categorías" (S20): una barra por categoría del mes, la
     * primera siempre llena y el resto en proporción a ella. El contenedor se limpia
     * antes para que un refresh (al volver del formulario de gasto) no duplique filas.
     */
    private void mostrarTopCategorias(long usuarioId, String mes) {
        List<ResumenCategoria> top = resumenDao.topCategorias(usuarioId, mes);
        llResumen.removeAllViews();

        if (top.isEmpty()) {
            tvResumenVacio.setVisibility(View.VISIBLE);
            llResumen.setVisibility(View.GONE);
            return;
        }
        tvResumenVacio.setVisibility(View.GONE);
        llResumen.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        // La lista viene ordenada por SUM(monto) DESC, así que la primera es la mayor del mes.
        double mayor = top.get(0).getTotal();
        for (ResumenCategoria categoria : top) {
            View fila = inflater.inflate(R.layout.item_resumen_categ, llResumen, false);
            TextView nombre = fila.findViewById(R.id.tv_categoria);
            TextView monto = fila.findViewById(R.id.tv_monto);
            ProgressBar barra = fila.findViewById(R.id.pb_categoria);

            nombre.setText(categoria.getCategoria());
            monto.setText(formatoResumen(categoria.getTotal()));
            barra.setMax(BARRA_MAXIMA);
            barra.setProgress(porcentajeDelTop(categoria.getTotal(), mayor));
            // S22: la barra toma el color guardado para esa categoría; si viene NULL queda el
            // tint del tema de item_resumen_categ.xml, así un catálogo sin colores se ve igual.
            Integer color = obtenerColorCategoria(categoria.getCategoria());
            if (color != null) {
                barra.setProgressTintList(ColorStateList.valueOf(color));
            }

            llResumen.addView(fila);
        }
    }

    /** Locale US fijo en los montos del resumen: el gráfico de barras no muestra unidad. */
    private static String formatoResumen(double valor) {
        return String.format(Locale.US, "$%.2f", valor);
    }

    /**
     * Color guardado para una categoría, o null si no tiene o el nombre no está en el
     * catálogo.
     *
     * <p>Va inline a propósito: los colores de categoría son del módulo de categorías,
     * pero el panel solo necesita esta lectura, así que queda como una consulta
     * parametrizada de una columna en vez de meterse en un DAO de otra tarea. El nombre
     * viaja enlazado (S3/S4).</p>
     */
    private Integer obtenerColorCategoria(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = DatabaseHelper.obtener(this).getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.T_CATEGORIAS,
                new String[]{DatabaseHelper.C_COLOR},
                DatabaseHelper.C_NOMBRE + " = ? COLLATE NOCASE",
                new String[]{nombre},
                null, null, null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0);
            }
        }
        return null;
    }

    /** Porcentaje de la categoría mayor: ella misma siempre marca 100, aun con el mes en cero. */
    private static int porcentajeDelTop(double total, double mayor) {
        if (mayor <= 0d) {
            return BARRA_MAXIMA;
        }
        return (int) Math.round(total / mayor * BARRA_MAXIMA);
    }

    /**
     * Muestra el avatar guardado desde la pantalla de cuenta, o el placeholder ic_person.
     * La ruta del archivo sale de {@link CuentaActivity#archivoAvatar} para que las dos
     * pantallas coincidan.
     */
    private void mostrarAvatar(long usuarioId) {
        if (ivAvatar == null) {
            return;
        }
        File archivo = CuentaActivity.archivoAvatar(this, usuarioId);
        Bitmap foto = archivo.isFile() ? decodificarAvatar(archivo) : null;
        if (foto == null) {
            // Placeholder: el layout ya trae ic_person, su tint y el padding de 8dp.
            int padding = Math.round(PADDING_PLACEHOLDER_DP * getResources().getDisplayMetrics().density);
            ivAvatar.setImageTintList(ContextCompat.getColorStateList(this, R.color.on_primary_container));
            ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivAvatar.setPadding(padding, padding, padding, padding);
            ivAvatar.setImageResource(R.drawable.ic_person);
            return;
        }
        // El MaterialCardView recorta al radio de 20dp, así que la foto se ve como círculo.
        ivAvatar.setImageTintList(null);
        ivAvatar.setPadding(0, 0, 0, 0);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setImageBitmap(foto);
    }

    /** Decode acotado: el archivo ya mide <= 512 px y con la mitad llena el chip de 40dp. */
    private static Bitmap decodificarAvatar(File archivo) {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = 2;
        return BitmapFactory.decodeFile(archivo.getAbsolutePath(), opciones);
    }

    private void irALogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    /** Adapter de la lista de próximos cobros (item_proximo.xml). */
    private static class ProximosAdapter extends RecyclerView.Adapter<ProximosAdapter.Titular> {

        private final List<Suscripcion> items = new ArrayList<>();

        void actualizar(List<Suscripcion> nuevas) {
            items.clear();
            items.addAll(nuevas);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Titular onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View vista = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_proximo, parent, false);
            return new Titular(vista);
        }

        @Override
        public void onBindViewHolder(@NonNull Titular titular, int posicion) {
            Suscripcion suscripcion = items.get(posicion);
            titular.nombre.setText(suscripcion.getNombre());
            // Etiqueta corta del día de cobro; strings.xml no tiene un recurso para esto.
            titular.dia.setText(String.format(Locale.getDefault(), "Día %d", suscripcion.getDiaCobro()));
            titular.monto.setText(formatoMonto(suscripcion.getMonto()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Titular extends RecyclerView.ViewHolder {
            final TextView nombre;
            final TextView dia;
            final TextView monto;

            Titular(@NonNull View itemView) {
                super(itemView);
                nombre = itemView.findViewById(R.id.tv_nombre);
                dia = itemView.findViewById(R.id.tv_dia);
                monto = itemView.findViewById(R.id.tv_monto);
            }
        }
    }
}

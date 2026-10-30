package com.fincontrol.app.suscripciones;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Suscripcion;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * Formulario de una suscripción: nombre, monto y día de cobro.
 * La misma pantalla crea y edita: el extra suscripcion_id la pasa a modo edición, que
 * precarga los valores actuales y actualiza la fila existente en vez de insertar otra.
 * La validación replica los rangos que asumen la base de datos y el recordatorio.
 */
public class SuscripcionFormActivity extends AppCompatActivity {

    /** Id de la suscripción en edición; si falta o viene -1, se crea una nueva. */
    public static final String EXTRA_SUSCRIPCION_ID = "suscripcion_id";

    private static final long SIN_ID = -1L;
    private static final int DIA_MINIMO = 1;
    private static final int DIA_MAXIMO = 31;

    private SuscripcionDao dao;
    private SessionManager sesion;

    /** Suscripción cargada en modo edición; null mientras se crea. */
    private Suscripcion original;
    private long suscripcionId = SIN_ID;

    private TextInputLayout tilNombre;
    private TextInputLayout tilMonto;
    private TextInputLayout tilDia;
    private TextInputEditText etNombre;
    private TextInputEditText etMonto;
    private TextInputEditText etDia;

    @Override
    protected void onCreate(Bundle estadoGuardado) {
        super.onCreate(estadoGuardado);
        // Edge-to-edge: la toolbar y el formulario no quedan bajo las barras del sistema.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_suscripcion_form);

        dao = new SuscripcionDao(this);
        sesion = new SessionManager(this);
        suscripcionId = getIntent().getLongExtra(EXTRA_SUSCRIPCION_ID, SIN_ID);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_form_sub);
        setSupportActionBar(toolbar);
        ActionBar barra = getSupportActionBar();
        if (barra != null) {
            barra.setTitle(suscripcionId == SIN_ID ? R.string.sub_nueva : R.string.accion_editar);
            barra.setDisplayHomeAsUpEnabled(true);
        }

        tilNombre = findViewById(R.id.til_nombre_sub);
        tilMonto = findViewById(R.id.til_monto_sub);
        tilDia = findViewById(R.id.til_dia_cobro_sub);
        etNombre = findViewById(R.id.et_nombre_sub);
        etMonto = findViewById(R.id.et_monto_sub);
        etDia = findViewById(R.id.et_dia_cobro_sub);

        if (suscripcionId != SIN_ID) {
            precargar();
        }

        MaterialButton guardar = findViewById(R.id.btn_guardar_sub);
        guardar.setOnClickListener(v -> guardar());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /** true cuando el formulario edita una fila existente (el id se resolvió en onCreate). */
    private boolean esEdicion() {
        return original != null;
    }

    /**
     * Carga la suscripción que indica el extra del intent y llena los campos.
     * Si la fila ya no está (por ejemplo la borraron mientras tanto) no hay nada que
     * editar, así que la pantalla se cierra en vez de insertar un duplicado.
     */
    private void precargar() {
        original = dao.buscarPorId(suscripcionId);
        if (original == null) {
            finish();
            return;
        }
        etNombre.setText(original.getNombre());
        etMonto.setText(String.format(Locale.US, "%.2f", original.getMonto()));
        etDia.setText(String.valueOf(original.getDiaCobro()));
    }

    private void guardar() {
        limpiarErrores();

        String nombre = texto(etNombre);
        String montoTexto = texto(etMonto);
        String diaTexto = texto(etDia);

        if (nombre.isEmpty() || montoTexto.isEmpty() || diaTexto.isEmpty()) {
            tilNombre.setError(getString(R.string.error_campo_vacio));
            return;
        }

        double monto;
        try {
            monto = Double.parseDouble(montoTexto);
        } catch (NumberFormatException e) {
            tilMonto.setError(getString(R.string.error_monto));
            return;
        }
        if (Double.isNaN(monto) || Double.isInfinite(monto) || monto <= 0d) {
            tilMonto.setError(getString(R.string.error_monto));
            return;
        }

        int dia;
        try {
            dia = Integer.parseInt(diaTexto);
        } catch (NumberFormatException e) {
            tilDia.setError(getString(R.string.error_dia_cobro));
            return;
        }
        if (dia < DIA_MINIMO || dia > DIA_MAXIMO) {
            tilDia.setError(getString(R.string.error_dia_cobro));
            return;
        }

        long usuarioId = sesion.getUserId();
        if (usuarioId <= 0) {
            finish();
            return;
        }

        if (esEdicion()) {
            // Mantiene el flag de activa y el dueño cargados: el update no toca esas columnas.
            dao.actualizar(new Suscripcion(original.getId(), nombre, monto, dia,
                    original.isActiva(), original.getUsuarioId()));
            Toast.makeText(this, R.string.sub_editada, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dao.insertar(new Suscripcion(0L, nombre, monto, dia, true, usuarioId));
        finish();
    }

    private void limpiarErrores() {
        tilNombre.setError(null);
        tilMonto.setError(null);
        tilDia.setError(null);
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}

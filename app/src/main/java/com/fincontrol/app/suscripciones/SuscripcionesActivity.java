package com.fincontrol.app.suscripciones;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Suscripcion;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Lista de suscripciones: muestra las del usuario de la sesión, permite activarlas o
 * pausarlas y abre el menú de editar / eliminar al tocar una fila.
 */
public class SuscripcionesActivity extends AppCompatActivity
        implements SuscripcionAdapter.OnActivaCambiada, SuscripcionAdapter.OnSuscripcionSeleccionada {

    /** Request code del permiso de notificaciones en runtime (Android 13+). */
    private static final int RC_NOTIFICACIONES = 101;

    private SuscripcionDao dao;
    private SessionManager sesion;
    private SuscripcionAdapter adaptador;
    private RecyclerView lista;
    private View vacio;

    @Override
    protected void onCreate(Bundle estadoGuardado) {
        super.onCreate(estadoGuardado);
        // Edge-to-edge: la toolbar, la lista y el FAB no quedan bajo las barras del sistema.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_suscripciones);

        dao = new SuscripcionDao(this);
        sesion = new SessionManager(this);

        lista = findViewById(R.id.rv_subs);
        vacio = findViewById(R.id.tv_sin_subs);
        adaptador = new SuscripcionAdapter(this, this);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);

        findViewById(R.id.fab_nueva_sub).setOnClickListener(v -> {
            startActivity(new Intent(this, SuscripcionFormActivity.class));
        });

        pedirPermisoNotificaciones();
        // Recordatorio diario: alarma inexacta repetitiva, no pide permiso de alarma exacta.
        ReminderReceiver.programarDiario(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sesion.getUserId() <= 0) {
            finish();
            return;
        }
        cargar();
    }

    @Override
    public void onActivaCambiada(int posicion, Suscripcion suscripcion, boolean activa) {
        dao.setActiva(suscripcion.getId(), activa);
        adaptador.aplicarActiva(posicion, activa);
    }

    /** Tap en la fila: pregunta si quiere editar o eliminar esa suscripción. */
    @Override
    public void onSuscripcionSeleccionada(Suscripcion suscripcion) {
        CharSequence[] acciones = {
                getString(R.string.accion_editar),
                getString(R.string.accion_eliminar)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(suscripcion.getNombre())
                .setItems(acciones, (dialogo, opcion) -> {
                    if (opcion == 0) {
                        editar(suscripcion);
                    } else {
                        confirmarEliminar(suscripcion);
                    }
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    /** Abre el formulario en modo edición con el extra suscripcion_id. */
    private void editar(Suscripcion suscripcion) {
        Intent intent = new Intent(this, SuscripcionFormActivity.class);
        intent.putExtra(SuscripcionFormActivity.EXTRA_SUSCRIPCION_ID, suscripcion.getId());
        startActivity(intent);
    }

    /** Borrar no se deshace, por eso confirma una vez con el nombre antes de eliminar. */
    private void confirmarEliminar(Suscripcion suscripcion) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.sub_confirmar_eliminar, suscripcion.getNombre()))
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar, (dialogo, opcion) -> {
                    dao.eliminar(suscripcion.getId());
                    Toast.makeText(this, R.string.sub_eliminada, Toast.LENGTH_SHORT).show();
                    cargar();
                })
                .show();
    }

    private void cargar() {
        List<Suscripcion> suscripciones = dao.listarPorUsuario(sesion.getUserId());
        adaptador.actualizar(suscripciones);
        boolean hayDatos = !suscripciones.isEmpty();
        lista.setVisibility(hayDatos ? View.VISIBLE : View.GONE);
        vacio.setVisibility(hayDatos ? View.GONE : View.VISIBLE);
    }

    /** Android 13+ exige permiso explícito en runtime antes de publicar notificaciones. */
    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIFICACIONES);
        }
    }

    @Override
    public void onRequestPermissionsResult(int codigo, @NonNull String[] permisos, @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigo, permisos, resultados);
        // Nada más que hacer: ReminderReceiver revisa el permiso antes de notificar.
    }
}

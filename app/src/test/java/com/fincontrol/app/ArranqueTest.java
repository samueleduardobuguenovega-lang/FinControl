package com.fincontrol.app;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import com.fincontrol.app.session.SessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/**
 * Prueba de regresión del "la app se cae al abrirla en el emulador": la
 * activity de entrada y el panel tienen que poder correr onCreate sin lanzar
 * excepción, incluso en un equipo donde el almacén de sesión no se puede abrir.
 */
@RunWith(RobolectricTestRunner.class)
public class ArranqueTest {

    private Context contexto;

    @Before
    public void preparar() {
        contexto = RuntimeEnvironment.getApplication();
        new SessionManager(contexto).cerrarSesion();
    }

    @Test
    public void laPantallaDeLoginSeAbreSinSesion() {
        ActivityController<LoginActivity> control =
                Robolectric.buildActivity(LoginActivity.class).setup();

        LoginActivity actividad = control.get();
        assertNotNull(actividad);
        assertNotNull("el formulario debe existir",
                actividad.findViewById(R.id.et_usuario));
        assertNotNull(actividad.findViewById(R.id.et_password));
    }

    @Test
    public void elPanelSeAbreConSesionIniciada() {
        new SessionManager(contexto).iniciarSesion(1L, "Nek");

        ActivityController<MainActivity> control =
                Robolectric.buildActivity(MainActivity.class).setup();

        MainActivity actividad = control.get();
        assertNotNull(actividad);
    }
}

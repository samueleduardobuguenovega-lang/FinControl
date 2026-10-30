package com.fincontrol.app.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * El almacén de sesión no puede botar la app al arrancar. En un emulador puede
 * pasar que el Keystore no esté disponible; antes del fallback el constructor
 * lanzaba IllegalStateException y el proceso moría antes del primer frame.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionManagerTest {

    private Context contexto;

    @Before
    public void preparar() {
        contexto = RuntimeEnvironment.getApplication();
        new SessionManager(contexto).cerrarSesion();
    }

    @Test
    public void abrirElAlmacenNuncaLanzaExcepcion() {
        SessionManager sesion = new SessionManager(contexto);
        assertFalse("una instalacion nueva no tiene sesion", sesion.haySesion());
    }

    @Test
    public void laSesionSeGuardaYSeCierra() {
        SessionManager sesion = new SessionManager(contexto);
        sesion.iniciarSesion(7L, "Nek");

        assertTrue(sesion.haySesion());
        assertEquals(7L, sesion.getUserId());
        assertEquals("Nek", sesion.getNombre());

        sesion.cerrarSesion();
        assertFalse(sesion.haySesion());
        assertEquals(-1L, sesion.getUserId());
    }

    @Test
    public void unaSegundaInstanciaVeLaMismaSesion() {
        new SessionManager(contexto).iniciarSesion(42L, "Compañero");

        SessionManager otra = new SessionManager(contexto);
        assertTrue("la sesion debe sobrevivir a la recreacion de la actividad", otra.haySesion());
        assertEquals(42L, otra.getUserId());
    }

    /**
     * Camino degradado: si el almacén cifrado no se puede abrir, el campo queda
     * en null y todo tiene que seguir funcionando en memoria (ese era el crash
     * que aparecía en el emulador antes del fallback).
     */
    @Test
    public void sinAlmacenCifradoLaSesionViveEnMemoria() throws Exception {
        SessionManager sesion = new SessionManager(contexto);
        java.lang.reflect.Field campo = SessionManager.class.getDeclaredField("prefs");
        campo.setAccessible(true);
        campo.set(sesion, null);

        assertFalse("no debe persistir sin almacen", sesion.persiste());
        sesion.iniciarSesion(99L, "Emulador");

        assertTrue(sesion.haySesion());
        assertEquals(99L, sesion.getUserId());
        assertEquals("Emulador", sesion.getNombre());

        sesion.cerrarSesion();
        assertFalse(sesion.haySesion());
    }
}

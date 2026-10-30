package com.fincontrol.app.perfil;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Preferencia de tema (S16): "según el sistema", "claro" u "oscuro".
 *
 * <p>Se guarda en SharedPreferences sin cifrar a propósito: el tema es cosmético y no es un
 * dato sensible, así que no necesita el almacén con Keystore. Lo que sí es dato del usuario
 * (id, nombre, credenciales) sigue en {@link com.fincontrol.app.session.SessionManager}, que
 * se mantiene sobre EncryptedSharedPreferences.</p>
 */
public final class TemaPreferencias {

    /** Sigue el ajuste día/noche del sistema (por defecto). */
    public static final String SISTEMA = "sistema";
    /** Fuerza el tema claro. */
    public static final String CLARO = "claro";
    /** Fuerza el tema oscuro. */
    public static final String OSCURO = "oscuro";

    private static final String PREFS = "fincontrol_prefs";
    private static final String K_TEMA = "tema";

    private TemaPreferencias() {
    }

    /** @return el tema guardado, o {@link #SISTEMA} cuando no hay nada válido. */
    public static String obtener(Context context) {
        String valor = prefs(context).getString(K_TEMA, SISTEMA);
        return esValido(valor) ? valor : SISTEMA;
    }

    /** Guarda el tema; si el valor no es conocido, queda en {@link #SISTEMA}. */
    public static void guardar(Context context, String valor) {
        prefs(context).edit().putString(K_TEMA, esValido(valor) ? valor : SISTEMA).apply();
    }

    /** Aplica el tema guardado vía AppCompatDelegate (recrea la activity actual). */
    public static void aplicarTemaGuardado(Context context) {
        switch (obtener(context)) {
            case CLARO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case OSCURO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case SISTEMA:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private static boolean esValido(String valor) {
        return SISTEMA.equals(valor) || CLARO.equals(valor) || OSCURO.equals(valor);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

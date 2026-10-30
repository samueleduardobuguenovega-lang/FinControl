package com.fincontrol.app.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Guarda la sesión del usuario logueado. Se apoya en EncryptedSharedPreferences:
 * los valores y las claves van cifrados con AES-256-GCM y una master key que vive
 * en el Keystore de Android, así el id y el nombre del usuario no se pueden leer
 * de un dump del dispositivo.
 * Historial: la v1 usaba SharedPreferences en texto plano (ver best_practices.md).
 *
 * <p>Cuando el almacén cifrado no se puede abrir (un emulador sin un Keystore sano,
 * un Keystore borrado por un factory reset o un restore, un archivo de prefs
 * corrupto), la sesión queda solo en memoria: el usuario sigue adentro durante la
 * corrida actual y nunca se escribe nada en texto plano. Antes de este fallback el
 * constructor lanzaba {@link IllegalStateException} y la app moría antes de dibujar
 * la primera pantalla.</p>
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREFS = "fincontrol_session";
    private static final String K_USER_ID = "user_id";
    private static final String K_NOMBRE = "nombre";

    /** Sesión en memoria, usada cuando {@link #prefs} es null (sin persistencia ni texto plano). */
    private static long memoriaUserId = -1;
    private static String memoriaNombre = "";

    /** Almacén cifrado, o null cuando no se pudo abrir (fallback a memoria). */
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = crearPrefs(context.getApplicationContext());
    }

    private static SharedPreferences crearPrefs(Context ctx) {
        try {
            MasterKey clave = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx, PREFS, clave,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            // Nunca fallar con un crash: la pantalla importa más que
            // persistir un id de sesión que no es sensible. Loguear y seguir.
            Log.w(TAG, "No se pudo abrir el almacen cifrado de sesion; "
                    + "la sesion vivira solo en memoria", e);
            return null;
        }
    }

    public void iniciarSesion(long userId, String nombre) {
        memoriaUserId = userId;
        memoriaNombre = nombre == null ? "" : nombre;
        if (prefs != null) {
            prefs.edit().putLong(K_USER_ID, userId).putString(K_NOMBRE, memoriaNombre).apply();
        }
    }

    public boolean haySesion() {
        if (memoriaUserId != -1) {
            return true;
        }
        return prefs != null && prefs.contains(K_USER_ID);
    }

    public long getUserId() {
        if (memoriaUserId != -1) {
            return memoriaUserId;
        }
        return prefs == null ? -1 : prefs.getLong(K_USER_ID, -1);
    }

    public String getNombre() {
        if (!memoriaNombre.isEmpty()) {
            return memoriaNombre;
        }
        return prefs == null ? "" : prefs.getString(K_NOMBRE, "");
    }

    public void cerrarSesion() {
        memoriaUserId = -1;
        memoriaNombre = "";
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }

    /** true cuando la sesión quedó persistida en el almacén cifrado (no solo en memoria). */
    public boolean persiste() {
        return prefs != null;
    }
}

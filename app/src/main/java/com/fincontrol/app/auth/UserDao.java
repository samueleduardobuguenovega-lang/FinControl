package com.fincontrol.app.auth;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Acceso a datos de la tabla usuarios.
 * Las contraseñas se guardan como hash PBKDF2-HMAC-SHA256 con un salt aleatorio
 * por usuario (150.000 iteraciones). Todas las consultas van parametrizadas.
 * En la v1 las contraseñas quedaban en texto plano y el SQL se armaba por
 * concatenación (ver security_tips.md).
 */
public class UserDao {

    private static final int ITERACIONES_PBKDF2 = 150_000;
    private static final int LONGITUD_HASH_BITS = 256;
    private static final int LONGITUD_SALT_BYTES = 16;

    private final DatabaseHelper helper;

    public UserDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /**
     * Inserta un usuario.
     *
     * @return false si el nombre de usuario ya existe o si el insert falló.
     */
    public boolean insertarUsuario(String usuario, String password, String nombre) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (existeUsuario(db, usuario)) {
            return false;
        }

        ContentValues valores = DatabaseHelper.valores();
        valores.put(DatabaseHelper.U_USUARIO, usuario);
        // Corregido en la v2: hash PBKDF2 con salt en vez de texto plano.
        valores.put(DatabaseHelper.U_PASSWORD, hashear(password, generarSalt()));
        valores.put(DatabaseHelper.U_NOMBRE, nombre);

        long fila = db.insert(DatabaseHelper.T_USUARIOS, null, valores);
        return fila != -1;
    }

    /**
     * Busca al usuario por nombre de usuario y verifica la contraseña contra el
     * hash PBKDF2 guardado. Ni la búsqueda ni la verificación interpolan datos en
     * el SQL.
     *
     * @return el usuario, o null si no hay fila.
     */
    public User autenticar(String usuario, String password) {
        SQLiteDatabase db = helper.getReadableDatabase();
        // Corregido en la v2: consulta parametrizada, la entrada ya no altera el SQL.
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.T_USUARIOS,
                    new String[]{DatabaseHelper.U_ID, DatabaseHelper.U_USUARIO,
                            DatabaseHelper.U_PASSWORD, DatabaseHelper.U_NOMBRE},
                    DatabaseHelper.U_USUARIO + " = ?",
                    new String[]{usuario},
                    null, null, null, "1");
            if (cursor.moveToFirst()) {
                String hashAlmacenado = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_PASSWORD));
                if (!verificarHash(password, hashAlmacenado)) {
                    return null;
                }
                return new User(
                        cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.U_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_USUARIO)),
                        hashAlmacenado,
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_NOMBRE)));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Busca un usuario por id (S16: la pantalla de cuenta lee el nombre actual).
     * El id viaja como argumento enlazado, nunca como texto del SQL.
     *
     * @return el usuario, o null si no hay fila.
     */
    public User obtener(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.T_USUARIOS,
                    new String[]{DatabaseHelper.U_ID, DatabaseHelper.U_USUARIO,
                            DatabaseHelper.U_PASSWORD, DatabaseHelper.U_NOMBRE},
                    DatabaseHelper.U_ID + " = ?",
                    new String[]{String.valueOf(id)},
                    null, null, null, "1");
            if (cursor.moveToFirst()) {
                return new User(
                        cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.U_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_USUARIO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_PASSWORD)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.U_NOMBRE)));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Cambia el nombre visible de un usuario (S16). UPDATE parametrizado: solo cambia
     * la columna nombre de la fila con ese id, el usuario y el hash quedan intactos.
     *
     * @return true si se actualizó alguna fila.
     */
    public boolean actualizarNombre(long id, String nombre) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(DatabaseHelper.U_NOMBRE, nombre);
        int filas = db.update(DatabaseHelper.T_USUARIOS, valores,
                DatabaseHelper.U_ID + " = ?",
                new String[]{String.valueOf(id)});
        return filas > 0;
    }

    /** 16 bytes aleatorios en hex, generados por usuario con SecureRandom. */
    private static String generarSalt() {
        byte[] salt = new byte[LONGITUD_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return aHex(salt);
    }

    /** Devuelve "hex(salt):hex(pbkdf2)", que es lo que se guarda en la columna. */
    private static String hashear(String password, String saltHex) {
        byte[] hash = pbkdf2(password, aBytes(saltHex));
        return saltHex + ":" + aHex(hash);
    }

    /** Recalcula el hash PBKDF2 con el salt guardado y compara en tiempo constante. */
    private static boolean verificarHash(String password, String almacenado) {
        int separador = almacenado.indexOf(':');
        if (separador <= 0) {
            return false;
        }
        byte[] salt = aBytes(almacenado.substring(0, separador));
        byte[] esperado = aBytes(almacenado.substring(separador + 1));
        byte[] calculado = pbkdf2(password, salt);
        return MessageDigest.isEqual(esperado, calculado);
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                    ITERACIONES_PBKDF2, LONGITUD_HASH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return hash;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el hash PBKDF2", e);
        }
    }

    private static String aHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    private static byte[] aBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** Revisa si el nombre de usuario ya está tomado. */
    private boolean existeUsuario(SQLiteDatabase db, String usuario) {
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.T_USUARIOS,
                    new String[]{DatabaseHelper.U_ID},
                    DatabaseHelper.U_USUARIO + " = ?",
                    new String[]{usuario},
                    null, null, null, "1");
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}

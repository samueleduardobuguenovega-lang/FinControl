package com.fincontrol.app.plantillas;

import static com.fincontrol.app.db.DatabaseHelper.P_CATEGORIA;
import static com.fincontrol.app.db.DatabaseHelper.P_ID;
import static com.fincontrol.app.db.DatabaseHelper.P_MONTO;
import static com.fincontrol.app.db.DatabaseHelper.P_NOMBRE;
import static com.fincontrol.app.db.DatabaseHelper.P_ULTIMA_VEZ;
import static com.fincontrol.app.db.DatabaseHelper.P_USOS;
import static com.fincontrol.app.db.DatabaseHelper.P_USUARIO_ID;
import static com.fincontrol.app.db.DatabaseHelper.T_PLANTILLAS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.model.Plantilla;

import java.util.ArrayList;
import java.util.List;

/**
 * Data access object de la tabla "plantillas" (S17).
 *
 * <p>Seguridad: todas las sentencias van parametrizadas. Los nombres, ids y montos que
 * escribe el usuario viajan solo por {@code ContentValues} o {@code selectionArgs}, y la
 * actualización del contador ata sus argumentos en vez de armar el SQL como texto.</p>
 *
 * <p>Orden: {@code usos DESC} (la plantilla más usada primero), después {@code ultima_vez
 * DESC} (la usada más reciente) y el nombre como desempate estable, para que los chips no
 * salten entre recargas.</p>
 */
public class PlantillaDao {

    private static final String[] COLUMNAS = {
            P_ID,
            P_USUARIO_ID,
            P_NOMBRE,
            P_MONTO,
            P_CATEGORIA,
            P_USOS,
            P_ULTIMA_VEZ
    };

    private final DatabaseHelper helper;

    public PlantillaDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /** Plantillas de un usuario: primero las más usadas, después las más recientes. */
    public List<Plantilla> listarPorUsuario(long usuarioId) {
        List<Plantilla> plantillas = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                T_PLANTILLAS,
                COLUMNAS,
                P_USUARIO_ID + " = ?",
                new String[]{String.valueOf(usuarioId)},
                null,
                null,
                P_USOS + " DESC, " + P_ULTIMA_VEZ + " DESC, " + P_NOMBRE + " ASC")) {
            while (cursor.moveToNext()) {
                plantillas.add(leerPlantilla(cursor));
            }
        }
        return plantillas;
    }

    /**
     * Inserta una plantilla para el usuario indicado.
     *
     * @return el id de la fila nueva, o -1 cuando el nombre viene vacío o el usuario ya tiene
     *         una plantilla con ese nombre (comparación sin mayúsculas), así queda un chip
     *         por nombre.
     */
    public long insertar(String nombre, double monto, String categoria, long usuarioId) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || existeNombre(usuarioId, limpio, -1L)) {
            return -1L;
        }
        ContentValues valores = DatabaseHelper.valores();
        valores.put(P_USUARIO_ID, usuarioId);
        valores.put(P_NOMBRE, limpio);
        valores.put(P_MONTO, monto);
        valores.put(P_CATEGORIA, categoria);
        valores.put(P_USOS, 0);
        valores.put(P_ULTIMA_VEZ, System.currentTimeMillis());
        return helper.getWritableDatabase().insert(T_PLANTILLAS, null, valores);
    }

    /**
     * Edita el nombre y el monto de una plantilla (long press sobre un chip).
     *
     * @return false cuando la fila no existe, el nombre nuevo viene vacío o el usuario ya
     *         tiene otra plantilla con ese nombre.
     */
    public boolean actualizar(long id, String nombre, double monto) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return false;
        }
        Long usuarioId = usuarioDe(id);
        if (usuarioId == null || existeNombre(usuarioId, limpio, id)) {
            return false;
        }
        ContentValues valores = DatabaseHelper.valores();
        valores.put(P_NOMBRE, limpio);
        valores.put(P_MONTO, monto);
        return helper.getWritableDatabase().update(T_PLANTILLAS, valores,
                P_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /** Borra una plantilla. */
    public boolean eliminar(long id) {
        return helper.getWritableDatabase().delete(T_PLANTILLAS,
                P_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Registra un uso de la plantilla: sube el contador de usos y marca el momento, que es
     * lo que mueve el chip al frente de la fila.
     */
    public void marcarUso(long id) {
        helper.getWritableDatabase().execSQL(
                "UPDATE " + T_PLANTILLAS
                        + " SET " + P_USOS + " = " + P_USOS + " + 1, " + P_ULTIMA_VEZ + " = ?"
                        + " WHERE " + P_ID + " = ?",
                new Object[]{System.currentTimeMillis(), id});
    }

    /** true si el usuario ya tiene una plantilla con ese nombre (lo usa la sugerencia). */
    public boolean existeNombre(long usuarioId, String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return false;
        }
        return existeNombre(usuarioId, limpio, -1L);
    }

    /** true si otra plantilla del usuario, distinta de {@code idExcluido}, ya tiene ese nombre. */
    private boolean existeNombre(long usuarioId, String nombre, long idExcluido) {
        String seleccion = P_USUARIO_ID + " = ? AND " + P_NOMBRE + " = ? COLLATE NOCASE";
        String[] argumentos;
        if (idExcluido > 0L) {
            seleccion += " AND " + P_ID + " <> ?";
            argumentos = new String[]{String.valueOf(usuarioId), nombre,
                    String.valueOf(idExcluido)};
        } else {
            argumentos = new String[]{String.valueOf(usuarioId), nombre};
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_PLANTILLAS, new String[]{P_ID}, seleccion, argumentos,
                null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    /** Dueño de la plantilla, o null cuando la fila no existe. */
    private Long usuarioDe(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_PLANTILLAS, new String[]{P_USUARIO_ID},
                P_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private Plantilla leerPlantilla(Cursor cursor) {
        return new Plantilla(
                cursor.getLong(cursor.getColumnIndexOrThrow(P_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(P_USUARIO_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(P_NOMBRE)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(P_MONTO)),
                cursor.getString(cursor.getColumnIndexOrThrow(P_CATEGORIA)),
                cursor.getInt(cursor.getColumnIndexOrThrow(P_USOS)),
                cursor.getLong(cursor.getColumnIndexOrThrow(P_ULTIMA_VEZ)));
    }
}

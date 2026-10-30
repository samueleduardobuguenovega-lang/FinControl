package com.fincontrol.app.suscripciones;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.model.Suscripcion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Data access object de la tabla "suscripciones".
 *
 * Seguridad del módulo: todas las consultas van parametrizadas, así los valores que
 * escribe el usuario (ids, día de cobro) viajan solo por selectionArgs y nunca quedan
 * dentro del texto SQL. Solo GastoDao mantiene a propósito el patrón vulnerable con
 * concatenación, que sirve para demostrar la corrección.
 */
public class SuscripcionDao {

    /** Proyección de columnas compartida, en el orden que espera leerFila(). */
    private static final String[] COLUMNAS = {
            DatabaseHelper.S_ID,
            DatabaseHelper.S_NOMBRE,
            DatabaseHelper.S_MONTO,
            DatabaseHelper.S_DIA_COBRO,
            DatabaseHelper.S_ACTIVA,
            DatabaseHelper.S_USUARIO_ID
    };

    /** Tamaño de la ventana de "próximos días" que usa proximosEn7Dias(). */
    private static final int VENTANA_DIAS = 7;

    private final DatabaseHelper helper;

    public SuscripcionDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /** Inserta una suscripción y devuelve el id nuevo, o -1 si el insert falló. */
    public long insertar(Suscripcion suscripcion) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(DatabaseHelper.S_NOMBRE, suscripcion.getNombre());
        valores.put(DatabaseHelper.S_MONTO, suscripcion.getMonto());
        valores.put(DatabaseHelper.S_DIA_COBRO, suscripcion.getDiaCobro());
        valores.put(DatabaseHelper.S_ACTIVA, suscripcion.isActiva() ? 1 : 0);
        valores.put(DatabaseHelper.S_USUARIO_ID, suscripcion.getUsuarioId());
        return db.insert(DatabaseHelper.T_SUSCRIPCIONES, null, valores);
    }

    /** Todas las suscripciones de un usuario, ordenadas por día de cobro. */
    public List<Suscripcion> listarPorUsuario(long usuarioId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String seleccion = DatabaseHelper.S_USUARIO_ID + " = ?";
        String[] argumentos = {String.valueOf(usuarioId)};
        Cursor cursor = db.query(DatabaseHelper.T_SUSCRIPCIONES, COLUMNAS, seleccion, argumentos,
                null, null, DatabaseHelper.S_DIA_COBRO + " ASC");
        try {
            return leerTodas(cursor);
        } finally {
            cursor.close();
        }
    }

    /** Una suscripción por id, o null cuando la fila ya no existe. */
    public Suscripcion buscarPorId(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String seleccion = DatabaseHelper.S_ID + " = ?";
        String[] argumentos = {String.valueOf(id)};
        Cursor cursor = db.query(DatabaseHelper.T_SUSCRIPCIONES, COLUMNAS, seleccion, argumentos,
                null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return leerFila(cursor);
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    /** Activa o pausa una suscripción. */
    public void setActiva(long id, boolean activa) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(DatabaseHelper.S_ACTIVA, activa ? 1 : 0);
        String seleccion = DatabaseHelper.S_ID + " = ?";
        String[] argumentos = {String.valueOf(id)};
        db.update(DatabaseHelper.T_SUSCRIPCIONES, valores, seleccion, argumentos);
    }

    /**
     * Actualiza nombre, monto y día de cobro de una suscripción existente y devuelve cuántas
     * filas cambió (0 si el id no existe). El flag de activa y el dueño quedan intactos a
     * propósito: esos los manejan setActiva() y la sesión.
     */
    public int actualizar(Suscripcion suscripcion) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(DatabaseHelper.S_NOMBRE, suscripcion.getNombre());
        valores.put(DatabaseHelper.S_MONTO, suscripcion.getMonto());
        valores.put(DatabaseHelper.S_DIA_COBRO, suscripcion.getDiaCobro());
        String seleccion = DatabaseHelper.S_ID + " = ?";
        String[] argumentos = {String.valueOf(suscripcion.getId())};
        return db.update(DatabaseHelper.T_SUSCRIPCIONES, valores, seleccion, argumentos);
    }

    /** Borra una suscripción por id; si la fila no está, no hace nada. */
    public void eliminar(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        String seleccion = DatabaseHelper.S_ID + " = ?";
        String[] argumentos = {String.valueOf(id)};
        db.delete(DatabaseHelper.T_SUSCRIPCIONES, seleccion, argumentos);
    }

    /** Suma de los montos activos de un usuario (0 cuando no tiene). */
    public double totalActivas(long usuarioId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT SUM(" + DatabaseHelper.S_MONTO + ") FROM " + DatabaseHelper.T_SUSCRIPCIONES
                + " WHERE " + DatabaseHelper.S_USUARIO_ID + " = ? AND " + DatabaseHelper.S_ACTIVA + " = ?";
        String[] argumentos = {String.valueOf(usuarioId), "1"};
        Cursor cursor = db.rawQuery(sql, argumentos);
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getDouble(0);
            }
            return 0d;
        } finally {
            cursor.close();
        }
    }

    /**
     * Suscripciones activas de un usuario cuyo día de cobro cae dentro de los próximos
     * siete días (hoy incluido). El resultado va ordenado por cercanía del cobro, lo que
     * importa cuando la ventana se cruza al mes siguiente.
     */
    public List<Suscripcion> proximosEn7Dias(long usuarioId) {
        List<Integer> dias = diasDeLaVentana();
        StringBuilder marcas = new StringBuilder();
        String[] argumentos = new String[dias.size() + 2];
        argumentos[0] = String.valueOf(usuarioId);
        argumentos[1] = "1";
        for (int i = 0; i < dias.size(); i++) {
            if (i > 0) {
                marcas.append(", ");
            }
            marcas.append('?');
            argumentos[i + 2] = String.valueOf(dias.get(i));
        }
        String seleccion = DatabaseHelper.S_USUARIO_ID + " = ? AND " + DatabaseHelper.S_ACTIVA + " = ? AND "
                + DatabaseHelper.S_DIA_COBRO + " IN (" + marcas + ")";

        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.T_SUSCRIPCIONES, COLUMNAS, seleccion, argumentos,
                null, null, null);
        List<Suscripcion> resultado;
        try {
            resultado = leerTodas(cursor);
        } finally {
            cursor.close();
        }
        Collections.sort(resultado, (a, b) -> Integer.compare(
                dias.indexOf(a.getDiaCobro()), dias.indexOf(b.getDiaCobro())));
        return resultado;
    }

    /**
     * Suscripciones activas de todos los usuarios que cobran el día indicado.
     * El receiver de recordatorios no tiene sesión, así que es la única consulta que no
     * filtra por usuario_id: aun así el día va atado por selectionArgs.
     */
    public List<Suscripcion> listarPorDiaCobro(int diaCobro) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String seleccion = DatabaseHelper.S_ACTIVA + " = ? AND " + DatabaseHelper.S_DIA_COBRO + " = ?";
        String[] argumentos = {"1", String.valueOf(diaCobro)};
        Cursor cursor = db.query(DatabaseHelper.T_SUSCRIPCIONES, COLUMNAS, seleccion, argumentos,
                null, null, DatabaseHelper.S_USUARIO_ID + " ASC");
        try {
            return leerTodas(cursor);
        } finally {
            cursor.close();
        }
    }

    /** Días del mes distintos que cubre la ventana de siete días, en orden de calendario. */
    private static List<Integer> diasDeLaVentana() {
        LocalDate hoy = LocalDate.now();
        LinkedHashSet<Integer> dias = new LinkedHashSet<>();
        for (int i = 0; i < VENTANA_DIAS; i++) {
            dias.add(hoy.plusDays(i).getDayOfMonth());
        }
        return new ArrayList<>(dias);
    }

    private static List<Suscripcion> leerTodas(Cursor cursor) {
        List<Suscripcion> filas = new ArrayList<>();
        while (cursor.moveToNext()) {
            filas.add(leerFila(cursor));
        }
        return filas;
    }

    private static Suscripcion leerFila(Cursor cursor) {
        return new Suscripcion(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getDouble(2),
                cursor.getInt(3),
                cursor.getInt(4) == 1,
                cursor.getLong(5));
    }
}

package com.fincontrol.app.categorias;

import static com.fincontrol.app.db.DatabaseHelper.C_COLOR;
import static com.fincontrol.app.db.DatabaseHelper.C_CONTADOR_USO;
import static com.fincontrol.app.db.DatabaseHelper.C_FIJADA;
import static com.fincontrol.app.db.DatabaseHelper.C_ICONO;
import static com.fincontrol.app.db.DatabaseHelper.C_ID;
import static com.fincontrol.app.db.DatabaseHelper.C_NOMBRE;
import static com.fincontrol.app.db.DatabaseHelper.C_ORDEN_MANUAL;
import static com.fincontrol.app.db.DatabaseHelper.C_TIPO;
import static com.fincontrol.app.db.DatabaseHelper.G_CATEGORIA;
import static com.fincontrol.app.db.DatabaseHelper.T_CATEGORIAS;
import static com.fincontrol.app.db.DatabaseHelper.T_GASTOS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.model.Categoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla "categorias".
 *
 * <p>Seguridad: todas las consultas van parametrizadas. Lo que escribe el usuario (nombres de
 * categoría, ids) viaja solo por selectionArgs / argumentos enlazados, así que nunca puede
 * cambiar el SQL.</p>
 *
 * <p>Orden (S13/S14): primero las fijadas, en el orden en que el usuario las arrastró
 * ({@code orden_manual ASC}); después las demás por uso ({@code contador_uso DESC}) y al final
 * alfabético.</p>
 *
 * <p>v4 (S22/S23): la identidad visual de la categoría va en cada fila ({@code color},
 * {@code icono}) y el catálogo se separa por {@code tipo}, así el selector solo ofrece las
 * categorías del registro que se está creando.</p>
 */
public class CategoriaDao {

    private static final String[] COLUMNAS = {
            C_ID,
            C_NOMBRE,
            C_CONTADOR_USO,
            C_FIJADA,
            C_ORDEN_MANUAL,
            C_COLOR,
            C_ICONO,
            C_TIPO
    };

    private final DatabaseHelper helper;

    public CategoriaDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /**
     * Todas las categorías: primero las fijadas (orden manual), después el resto por uso y nombre.
     */
    public List<Categoria> listarOrdenadas() {
        return listarOrdenadas(null);
    }

    /**
     * Primero las fijadas (orden manual), después el resto por uso y nombre. Opcionalmente se
     * puede limitar a un tipo de registro.
     *
     * @param tipo {@code "gasto"} o {@code "ingreso"} para listar solo ese catálogo, o null para
     *             listar ambos (la pantalla de administración muestra el catálogo completo).
     */
    public List<Categoria> listarOrdenadas(@Nullable String tipo) {
        boolean filtrar = tipo != null && !tipo.trim().isEmpty();
        String filtroTipo = filtrar ? " AND " + C_TIPO + " = ?" : "";
        List<Categoria> categorias = new ArrayList<>();
        categorias.addAll(consultar(C_FIJADA + " = ?" + filtroTipo,
                argumentosFijada(1, tipo, filtrar),
                C_ORDEN_MANUAL + " ASC, " + C_NOMBRE + " ASC"));
        categorias.addAll(consultar(C_FIJADA + " = ?" + filtroTipo,
                argumentosFijada(0, tipo, filtrar),
                C_CONTADOR_USO + " DESC, " + C_NOMBRE + " ASC"));
        return categorias;
    }

    /**
     * Inserta una categoría de gasto sin color ni icono. Se mantiene para los llamados al DAO
     * anterior a la v4.
     *
     * @return el id de la fila nueva, o -1 si el nombre viene vacío o ya existe (comparación
     *         recortada y sin distinguir mayúsculas).
     */
    public long insertar(String nombre) {
        return insertar(nombre, null, null, Categoria.TIPO_GASTO);
    }

    /**
     * Inserta una categoría con su identidad visual y su tipo de registro (S22/S23).
     *
     * @param color ARGB del color, o null para dejar el del tema.
     * @param icono clave de icono dentro de {@link EstiloCategoria#ICONOS}, o null sin icono.
     * @param tipo  {@code "gasto"} o {@code "ingreso"}; cualquier otra cosa cae en "gasto".
     * @return el id de la fila nueva, o -1 si el nombre viene vacío o ya existe.
     */
    public long insertar(String nombre, @Nullable Integer color, @Nullable String icono,
                         @Nullable String tipo) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || existeNombre(limpio, -1L)) {
            return -1L;
        }
        ContentValues valores = DatabaseHelper.valores();
        valores.put(C_NOMBRE, limpio);
        valores.put(C_CONTADOR_USO, 0);
        valores.put(C_FIJADA, 0);
        valores.put(C_ORDEN_MANUAL, 0);
        valores.put(C_TIPO, tipoDe(tipo));
        if (color == null) {
            valores.putNull(C_COLOR);
        } else {
            valores.put(C_COLOR, color);
        }
        if (icono == null) {
            valores.putNull(C_ICONO);
        } else {
            valores.put(C_ICONO, icono);
        }
        return helper.getWritableDatabase().insert(T_CATEGORIAS, null, valores);
    }

    /**
     * Cambia el nombre de una categoría.
     *
     * @return false si el nombre nuevo viene vacío o ya lo usa otra fila.
     */
    public boolean renombrar(long id, String nuevoNombre) {
        String limpio = nuevoNombre == null ? "" : nuevoNombre.trim();
        if (limpio.isEmpty() || existeNombre(limpio, id)) {
            return false;
        }
        ContentValues valores = DatabaseHelper.valores();
        valores.put(C_NOMBRE, limpio);
        return helper.getWritableDatabase().update(T_CATEGORIAS, valores,
                C_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Guarda la identidad visual de una categoría (S22): el color de la paleta y la clave del
     * icono.
     *
     * @param color ARGB del color, o null para volver al color del tema.
     * @param icono clave de icono, o null para dejar la categoría sin icono propio.
     * @return cantidad de filas actualizadas (0 si el id no existe).
     */
    public int actualizarEstilo(long id, @Nullable Integer color, @Nullable String icono) {
        ContentValues valores = DatabaseHelper.valores();
        if (color == null) {
            valores.putNull(C_COLOR);
        } else {
            valores.put(C_COLOR, color);
        }
        if (icono == null) {
            valores.putNull(C_ICONO);
        } else {
            valores.put(C_ICONO, icono);
        }
        return helper.getWritableDatabase().update(T_CATEGORIAS, valores,
                C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /**
     * Mueve una categoría a otro tipo de registro (S23).
     *
     * @param tipo {@code "gasto"} o {@code "ingreso"}; cualquier otra cosa cae en "gasto".
     * @return cantidad de filas actualizadas (0 si el id no existe).
     */
    public int actualizarTipo(long id, @Nullable String tipo) {
        ContentValues valores = DatabaseHelper.valores();
        valores.put(C_TIPO, tipoDe(tipo));
        return helper.getWritableDatabase().update(T_CATEGORIAS, valores,
                C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /**
     * Color propio de la categoría con ese nombre (sin distinguir mayúsculas), o null si la
     * categoría no existe o no tiene color propio.
     */
    @Nullable
    public Integer colorPorNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_CATEGORIAS, new String[]{C_COLOR},
                C_NOMBRE + " = ? COLLATE NOCASE", new String[]{limpio}, null, null, null)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getInt(0);
        }
    }

    /**
     * Clave de icono de la categoría con ese nombre (sin distinguir mayúsculas), o null si la
     * categoría no existe o no tiene icono.
     */
    @Nullable
    public String iconoPorNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_CATEGORIAS, new String[]{C_ICONO},
                C_NOMBRE + " = ? COLLATE NOCASE", new String[]{limpio}, null, null, null)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        }
    }

    /**
     * Elimina una categoría, pero solo mientras ningún gasto la use.
     *
     * @return false si la categoría no existe o si la tabla de gastos todavía usa su nombre.
     */
    public boolean eliminar(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        String nombre = nombrePorId(db, id);
        if (nombre == null || contarGastosConCategoria(db, nombre) > 0) {
            return false;
        }
        return db.delete(T_CATEGORIAS, C_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Suma un uso a la categoría con ese nombre: la inserta si falta (un gasto antiguo puede tener
     * una categoría que no está en el catálogo) y después sube su contador.
     */
    public void marcarUso(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO " + T_CATEGORIAS
                        + " (" + C_NOMBRE + ", " + C_CONTADOR_USO + ", " + C_FIJADA + ", "
                        + C_ORDEN_MANUAL + ")"
                        + " SELECT ?, 0, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM " + T_CATEGORIAS
                        + " WHERE " + C_NOMBRE + " = ? COLLATE NOCASE)",
                new Object[]{limpio, limpio});
        db.execSQL("UPDATE " + T_CATEGORIAS
                        + " SET " + C_CONTADOR_USO + " = " + C_CONTADOR_USO + " + 1"
                        + " WHERE " + C_NOMBRE + " = ? COLLATE NOCASE",
                new Object[]{limpio});
    }

    /**
     * Fija o desfija una categoría.
     *
     * <p>Si la categoría no estaba fijada, al fijarla se agrega al final del bloque de fijadas
     * ({@code max(orden_manual) + 1} entre las filas fijadas). Al desfijar se conserva el
     * {@code orden_manual} guardado, así volver a fijarla no obliga a reordenar.</p>
     */
    public void fijar(long id, boolean fijada) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(C_FIJADA, fijada ? 1 : 0);
        if (fijada && !estaFijada(db, id)) {
            valores.put(C_ORDEN_MANUAL, siguienteOrdenFijada(db));
        }
        db.update(T_CATEGORIAS, valores, C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /**
     * Guarda el orden de las fijadas: {@code orden_manual = index} para cada id de la lista.
     */
    public void reordenar(List<Long> idsEnOrden) {
        if (idsEnOrden == null || idsEnOrden.isEmpty()) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int indice = 0; indice < idsEnOrden.size(); indice++) {
                ContentValues valores = DatabaseHelper.valores();
                valores.put(C_ORDEN_MANUAL, indice);
                db.update(T_CATEGORIAS, valores, C_ID + " = ?",
                        new String[]{String.valueOf(idsEnOrden.get(indice))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<Categoria> consultar(String seleccion, String[] argumentos, String orden) {
        List<Categoria> categorias = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_CATEGORIAS, COLUMNAS, seleccion, argumentos,
                null, null, orden)) {
            while (cursor.moveToNext()) {
                categorias.add(leerCategoria(cursor));
            }
        }
        return categorias;
    }

    /** Argumentos de un bloque de fijadas, con el filtro de tipo cuando corresponde. */
    private static String[] argumentosFijada(int fijada, @Nullable String tipo, boolean filtrar) {
        String valor = String.valueOf(fijada);
        return filtrar ? new String[]{valor, tipoDe(tipo)} : new String[]{valor};
    }

    /**
     * Devuelve true cuando otra fila (cualquier id distinto de {@code idExcluido}) ya tiene ese
     * nombre.
     */
    private boolean existeNombre(String nombre, long idExcluido) {
        String seleccion = C_NOMBRE + " = ? COLLATE NOCASE";
        String[] argumentos;
        if (idExcluido > 0) {
            seleccion += " AND " + C_ID + " <> ?";
            argumentos = new String[]{nombre, String.valueOf(idExcluido)};
        } else {
            argumentos = new String[]{nombre};
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_CATEGORIAS, new String[]{C_ID}, seleccion, argumentos,
                null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    private String nombrePorId(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.query(T_CATEGORIAS, new String[]{C_NOMBRE},
                C_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private int contarGastosConCategoria(SQLiteDatabase db, String nombre) {
        try (Cursor cursor = db.query(T_GASTOS, new String[]{"COUNT(*)"},
                G_CATEGORIA + " = ? COLLATE NOCASE", new String[]{nombre},
                null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private boolean estaFijada(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.query(T_CATEGORIAS, new String[]{C_FIJADA},
                C_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    /** Siguiente posición libre al final del bloque de fijadas (0 para la primera fijada). */
    private int siguienteOrdenFijada(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(" + C_ORDEN_MANUAL + "), -1) + 1"
                + " FROM " + T_CATEGORIAS + " WHERE " + C_FIJADA + " = 1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * Normaliza el tipo de registro: solo 'ingreso' se aparta del 'gasto' histórico por defecto.
     */
    private static String tipoDe(@Nullable String tipo) {
        return tipo != null && Categoria.TIPO_INGRESO.equalsIgnoreCase(tipo.trim())
                ? Categoria.TIPO_INGRESO
                : Categoria.TIPO_GASTO;
    }

    private Categoria leerCategoria(Cursor cursor) {
        int idxId = cursor.getColumnIndexOrThrow(C_ID);
        int idxNombre = cursor.getColumnIndexOrThrow(C_NOMBRE);
        int idxContador = cursor.getColumnIndexOrThrow(C_CONTADOR_USO);
        int idxFijada = cursor.getColumnIndexOrThrow(C_FIJADA);
        int idxOrden = cursor.getColumnIndexOrThrow(C_ORDEN_MANUAL);
        int idxColor = cursor.getColumnIndexOrThrow(C_COLOR);
        int idxIcono = cursor.getColumnIndexOrThrow(C_ICONO);
        int idxTipo = cursor.getColumnIndexOrThrow(C_TIPO);
        return new Categoria(
                cursor.getLong(idxId),
                cursor.getString(idxNombre),
                cursor.getInt(idxContador),
                cursor.getInt(idxFijada) == 1,
                cursor.getInt(idxOrden),
                cursor.isNull(idxColor) ? null : cursor.getInt(idxColor),
                cursor.isNull(idxIcono) ? null : cursor.getString(idxIcono),
                cursor.getString(idxTipo));
    }
}

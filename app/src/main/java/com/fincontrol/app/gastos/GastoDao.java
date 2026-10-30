package com.fincontrol.app.gastos;

import static com.fincontrol.app.db.DatabaseHelper.G_CATEGORIA;
import static com.fincontrol.app.db.DatabaseHelper.G_DESCRIPCION;
import static com.fincontrol.app.db.DatabaseHelper.G_FECHA;
import static com.fincontrol.app.db.DatabaseHelper.G_ID;
import static com.fincontrol.app.db.DatabaseHelper.G_LATITUD;
import static com.fincontrol.app.db.DatabaseHelper.G_LONGITUD;
import static com.fincontrol.app.db.DatabaseHelper.G_MONTO;
import static com.fincontrol.app.db.DatabaseHelper.G_TIPO;
import static com.fincontrol.app.db.DatabaseHelper.G_USUARIO_ID;
import static com.fincontrol.app.db.DatabaseHelper.T_GASTOS;
import static com.fincontrol.app.db.DatabaseHelper.TIPO_GASTO;
import static com.fincontrol.app.db.DatabaseHelper.TIPO_INGRESO;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fincontrol.app.db.DatabaseHelper;
import com.fincontrol.app.model.Gasto;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO de la tabla "gastos".
 * Toda consulta va filtrada por el id del usuario que llega como parámetro.
 */
public class GastoDao {

    private final DatabaseHelper helper;

    public GastoDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /** Inserta un gasto y devuelve el id de la fila nueva (-1 si falla). */
    public long insertar(Gasto gasto) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(G_MONTO, gasto.getMonto());
        valores.put(G_CATEGORIA, gasto.getCategoria());
        valores.put(G_FECHA, gasto.getFecha());
        valores.put(G_DESCRIPCION, gasto.getDescripcion());
        if (gasto.getLatitud() == null) {
            valores.putNull(G_LATITUD);
        } else {
            valores.put(G_LATITUD, gasto.getLatitud());
        }
        if (gasto.getLongitud() == null) {
            valores.putNull(G_LONGITUD);
        } else {
            valores.put(G_LONGITUD, gasto.getLongitud());
        }
        valores.put(G_USUARIO_ID, gasto.getUsuarioId());
        valores.put(G_TIPO, tipoDe(gasto));
        return db.insert(T_GASTOS, null, valores);
    }

    /** Actualiza los campos editables de un gasto existente (S15). Devuelve las filas afectadas. */
    public int actualizar(Gasto gasto) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues valores = DatabaseHelper.valores();
        valores.put(G_MONTO, gasto.getMonto());
        valores.put(G_CATEGORIA, gasto.getCategoria());
        valores.put(G_FECHA, gasto.getFecha());
        valores.put(G_DESCRIPCION, gasto.getDescripcion());
        if (gasto.getLatitud() == null) {
            valores.putNull(G_LATITUD);
        } else {
            valores.put(G_LATITUD, gasto.getLatitud());
        }
        if (gasto.getLongitud() == null) {
            valores.putNull(G_LONGITUD);
        } else {
            valores.put(G_LONGITUD, gasto.getLongitud());
        }
        valores.put(G_TIPO, tipoDe(gasto));
        return db.update(T_GASTOS, valores, G_ID + " = ?",
                new String[]{String.valueOf(gasto.getId())});
    }

    /** Borra un gasto por id (S15). */
    public void eliminar(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(T_GASTOS, G_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /** Devuelve un gasto del usuario, o null si no existe. Lo usa el formulario de edición. */
    public Gasto buscar(long id, long usuarioId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_GASTOS, null,
                G_ID + " = ? AND " + G_USUARIO_ID + " = ?",
                new String[]{String.valueOf(id), String.valueOf(usuarioId)},
                null, null, null)) {
            return cursor.moveToFirst() ? leerGasto(cursor) : null;
        }
    }

    /**
     * Lista todos los gastos del usuario, del más nuevo al más antiguo.
     *
     * <p>Cambio de la v4 (S23): los ingresos viven en la misma tabla, así que ahora el método
     * filtra {@code tipo = 'gasto'} de forma explícita. Con eso ningún llamador existente se
     * rompe y no hay que tocar su contrato: el mapa solo dibuja gastos con ubicación y los chips
     * de plantillas solo insertan gastos, o sea que ninguna de esas pantallas quería una fila de
     * ingreso. Para ingresos están {@link #listarPorTipo(long, String)} y
     * {@link #listarPorRango(long, String, String, String)}.</p>
     */
    public List<Gasto> listarPorUsuario(long usuarioId) {
        return listarPorTipo(usuarioId, TIPO_GASTO);
    }

    /**
     * Lista las filas de un tipo ('gasto' o 'ingreso') del usuario, más nuevas primero (S23).
     */
    public List<Gasto> listarPorTipo(long usuarioId, String tipo) {
        return consultarLista(
                G_USUARIO_ID + " = ? AND " + G_TIPO + " = ?",
                new String[]{String.valueOf(usuarioId), tipoDe(tipo)});
    }

    /**
     * Lista las filas de un tipo cuya fecha cae entre {@code desde} y {@code hasta}, ambos
     * incluidos, más nuevas primero (S24: períodos día / semana / mes / año).
     *
     * <p>La fecha se guarda como texto ISO AAAA-MM-DD, así que el {@code BETWEEN} lexicográfico
     * compara en orden cronológico y los extremos siguen viajando como argumentos, nunca como
     * texto SQL.</p>
     */
    public List<Gasto> listarPorRango(long usuarioId, String tipo, String desde, String hasta) {
        return consultarLista(
                G_USUARIO_ID + " = ? AND " + G_TIPO + " = ? AND " + G_FECHA + " BETWEEN ? AND ?",
                new String[]{String.valueOf(usuarioId), tipoDe(tipo), desde, hasta});
    }

    /**
     * Cuerpo común de las consultas de lista: mismas columnas, mismo orden y selección
     * parametrizada.
     */
    private List<Gasto> consultarLista(String seleccion, String[] argumentos) {
        List<Gasto> gastos = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_GASTOS, null, seleccion, argumentos,
                null, null, G_FECHA + " DESC, " + G_ID + " DESC")) {
            while (cursor.moveToNext()) {
                gastos.add(leerGasto(cursor));
            }
        }
        return gastos;
    }

    /**
     * Suma todas las filas del usuario, sin mirar el tipo. Queda para el código anterior a la v4;
     * el dashboard y la lista usan {@link #totalDelTipo} / {@link #totalDelRango}, porque meter
     * los ingresos acá inflaría el total de gastos (S23).
     */
    public double totalDelMes(long usuarioId, String mesAAAAMM) {
        SQLiteDatabase db = helper.getReadableDatabase();
        // Corregido en la v1 -> v2: selectionArgs parametrizado en vez de concatenar.
        try (Cursor cursor = db.query(T_GASTOS,
                new String[]{"SUM(" + G_MONTO + ")"},
                G_USUARIO_ID + " = ? AND " + G_FECHA + " LIKE ? || '%'",
                new String[]{String.valueOf(usuarioId), mesAAAAMM},
                null, null, null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getDouble(0);
            }
        }
        return 0d;
    }

    /**
     * Suma las filas de un tipo cuya fecha parte con el mes indicado (AAAA-MM). Sirve, por
     * ejemplo, para comparar los gastos del mes con los ingresos del mes en el balance (S23).
     */
    public double totalDelTipo(long usuarioId, String tipo, String mesAAAAMM) {
        return consultarTotal(
                G_USUARIO_ID + " = ? AND " + G_TIPO + " = ? AND " + G_FECHA + " LIKE ? || '%'",
                new String[]{String.valueOf(usuarioId), tipoDe(tipo), mesAAAAMM});
    }

    /**
     * Suma las filas de un tipo cuya fecha cae entre {@code desde} y {@code hasta}, ambos
     * incluidos. El total sigue al período elegido: día, semana, mes o año (S24).
     */
    public double totalDelRango(long usuarioId, String tipo, String desde, String hasta) {
        return consultarTotal(
                G_USUARIO_ID + " = ? AND " + G_TIPO + " = ? AND " + G_FECHA + " BETWEEN ? AND ?",
                new String[]{String.valueOf(usuarioId), tipoDe(tipo), desde, hasta});
    }

    /** Cuerpo común de las consultas de SUM: mismo agregado, selección parametrizada. */
    private double consultarTotal(String seleccion, String[] argumentos) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_GASTOS,
                new String[]{"SUM(" + G_MONTO + ")"},
                seleccion,
                argumentos,
                null, null, null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getDouble(0);
            }
        }
        return 0d;
    }

    /**
     * Lista los gastos de un mes (AAAA-MM), más nuevos primero.
     *
     * <p>Se mantiene por compatibilidad con la pantalla de lista de la T15. El selector de
     * período de la S24 usa {@link #listarPorRango(long, String, String, String)}, que cubre día,
     * semana, mes y año con una sola consulta parametrizada. El mes solo viaja como argumento del
     * patrón {@code LIKE}, nunca como texto SQL.</p>
     */
    public List<Gasto> listarPorMes(long usuarioId, String mesAAAAMM) {
        List<Gasto> gastos = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                T_GASTOS,
                null,
                G_USUARIO_ID + " = ? AND " + G_FECHA + " LIKE ?",
                new String[]{String.valueOf(usuarioId), mesAAAAMM + "%"},
                null,
                null,
                G_FECHA + " DESC, " + G_ID + " DESC")) {
            while (cursor.moveToNext()) {
                gastos.add(leerGasto(cursor));
            }
        }
        return gastos;
    }

    /**
     * Cuenta las filas del usuario que repiten la misma descripción y categoría desde
     * {@code fechaLimite} en adelante, incluido. Es la señal que aprende la sugerencia de
     * plantillas (S17): si un patrón aparece tres veces dentro de la ventana, vale una plantilla
     * de un toque.
     *
     * <p>El conteo corre sobre los dos tipos a propósito: los ingresos tienen sus propias
     * categorías, así que una fila de ingreso nunca va a calzar con un patrón de gasto.</p>
     */
    public int contarSimilares(long usuarioId, String descripcion, String categoria,
                               String fechaLimite) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(T_GASTOS,
                new String[]{"COUNT(*)"},
                G_USUARIO_ID + " = ? AND " + G_DESCRIPCION + " = ? AND "
                        + G_CATEGORIA + " = ? AND " + G_FECHA + " >= ?",
                new String[]{String.valueOf(usuarioId),
                        descripcion == null ? "" : descripcion,
                        categoria == null ? "" : categoria,
                        fechaLimite},
                null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private Gasto leerGasto(Cursor cursor) {
        int idxId = cursor.getColumnIndexOrThrow(G_ID);
        int idxMonto = cursor.getColumnIndexOrThrow(G_MONTO);
        int idxCategoria = cursor.getColumnIndexOrThrow(G_CATEGORIA);
        int idxFecha = cursor.getColumnIndexOrThrow(G_FECHA);
        int idxDescripcion = cursor.getColumnIndexOrThrow(G_DESCRIPCION);
        int idxLatitud = cursor.getColumnIndexOrThrow(G_LATITUD);
        int idxLongitud = cursor.getColumnIndexOrThrow(G_LONGITUD);
        int idxUsuario = cursor.getColumnIndexOrThrow(G_USUARIO_ID);
        // La columna existe en toda base v4, pero leerla con resguardo deja válido un cursor viejo.
        int idxTipo = cursor.getColumnIndex(G_TIPO);

        Double latitud = cursor.isNull(idxLatitud) ? null : cursor.getDouble(idxLatitud);
        Double longitud = cursor.isNull(idxLongitud) ? null : cursor.getDouble(idxLongitud);
        String descripcion = cursor.isNull(idxDescripcion) ? "" : cursor.getString(idxDescripcion);
        String tipo = (idxTipo < 0 || cursor.isNull(idxTipo))
                ? TIPO_GASTO : cursor.getString(idxTipo);

        return new Gasto(
                cursor.getLong(idxId),
                cursor.getDouble(idxMonto),
                cursor.getString(idxCategoria),
                cursor.getString(idxFecha),
                descripcion,
                latitud,
                longitud,
                cursor.getLong(idxUsuario),
                tipo);
    }

    /** Normaliza el tipo de un argumento o de un Gasto a uno de los dos valores válidos. */
    private static String tipoDe(String tipo) {
        return TIPO_INGRESO.equals(tipo) ? TIPO_INGRESO : TIPO_GASTO;
    }

    private static String tipoDe(Gasto gasto) {
        return tipoDe(gasto.getTipo());
    }
}

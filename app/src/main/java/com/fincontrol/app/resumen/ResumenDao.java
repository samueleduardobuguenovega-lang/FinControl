package com.fincontrol.app.resumen;

import static com.fincontrol.app.db.DatabaseHelper.G_CATEGORIA;
import static com.fincontrol.app.db.DatabaseHelper.G_FECHA;
import static com.fincontrol.app.db.DatabaseHelper.G_MONTO;
import static com.fincontrol.app.db.DatabaseHelper.G_TIPO;
import static com.fincontrol.app.db.DatabaseHelper.G_USUARIO_ID;
import static com.fincontrol.app.db.DatabaseHelper.T_GASTOS;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fincontrol.app.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Agregaciones de solo lectura sobre la tabla "gastos" para el resumen del dashboard (S20).
 * Todas las consultas van acotadas al id del usuario que recibe el método.
 */
public class ResumenDao {

    /** SUM(monto) por categoría; el alias sirve solo para leer la columna agregada. */
    private static final String COLUMNA_TOTAL = "total";

    /** Los reportes tipo Monefy muestran las cinco categorías donde se concentra el gasto. */
    private static final int LIMITE_CATEGORIAS = 5;

    private final DatabaseHelper helper;

    public ResumenDao(Context context) {
        this.helper = DatabaseHelper.obtener(context);
    }

    /**
     * Devuelve las {@value #LIMITE_CATEGORIAS} categorías de mayor gasto de un mes (AAAA-MM),
     * de mayor a menor. El mes viaja solo como argumento atado del patrón {@code LIKE},
     * nunca dentro del SQL (S3/S20).
     */
    public List<ResumenCategoria> topCategorias(long usuarioId, String mesAAAAMM) {
        List<ResumenCategoria> resumen = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String suma = "SUM(" + G_MONTO + ")";

        try (Cursor cursor = db.query(
                T_GASTOS,
                new String[]{G_CATEGORIA, suma + " AS " + COLUMNA_TOTAL},
                G_USUARIO_ID + " = ? AND " + G_FECHA + " LIKE ? || '%' AND " + G_TIPO + " = ?",
                new String[]{String.valueOf(usuarioId), mesAAAAMM, DatabaseHelper.TIPO_GASTO},
                G_CATEGORIA,
                null,
                suma + " DESC",
                String.valueOf(LIMITE_CATEGORIAS))) {
            int idxCategoria = cursor.getColumnIndexOrThrow(G_CATEGORIA);
            int idxTotal = cursor.getColumnIndexOrThrow(COLUMNA_TOTAL);
            while (cursor.moveToNext()) {
                resumen.add(new ResumenCategoria(cursor.getString(idxCategoria), cursor.getDouble(idxTotal)));
            }
        }
        return resumen;
    }
}

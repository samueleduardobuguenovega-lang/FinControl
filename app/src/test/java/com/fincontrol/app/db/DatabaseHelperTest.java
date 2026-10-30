package com.fincontrol.app.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Una instalación nueva (un emulador siempre lo es) crea la base desde
 * onCreate, así que el esquema y los catálogos sembrados tienen que quedar
 * completos por esa vía y no solo por la ruta de upgrade.
 */
@RunWith(RobolectricTestRunner.class)
public class DatabaseHelperTest {

    private Context contexto() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void laBaseNuevaQuedaEnLaVersionActual() {
        SQLiteDatabase db = DatabaseHelper.obtener(contexto()).getWritableDatabase();
        assertEquals(DatabaseHelper.DB_VERSION, db.getVersion());
    }

    @Test
    public void siembraLasCategoriasDeGastoYDeIngreso() {
        SQLiteDatabase db = DatabaseHelper.obtener(contexto()).getWritableDatabase();

        assertEquals(8, contar(db, DatabaseHelper.TIPO_GASTO));
        assertEquals("Sueldo y Extra son el catalogo de ingresos",
                2, contar(db, DatabaseHelper.TIPO_INGRESO));
    }

    @Test
    public void lasTablasDelEsquemaExisten() {
        SQLiteDatabase db = DatabaseHelper.obtener(contexto()).getWritableDatabase();
        for (String tabla : new String[]{
                DatabaseHelper.T_USUARIOS,
                DatabaseHelper.T_GASTOS,
                DatabaseHelper.T_SUSCRIPCIONES,
                DatabaseHelper.T_CATEGORIAS,
                DatabaseHelper.T_PLANTILLAS}) {
            assertTrue("falta la tabla " + tabla, tablaExiste(db, tabla));
        }
    }

    private static int contar(SQLiteDatabase db, String tipo) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.T_CATEGORIAS
                        + " WHERE " + DatabaseHelper.C_TIPO + " = ?",
                new String[]{tipo});
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private static boolean tablaExiste(SQLiteDatabase db, String tabla) {
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tabla});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }
}

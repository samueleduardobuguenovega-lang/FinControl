package com.fincontrol.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Base de datos local de FinControl.
 * Esquema compartido por todos los módulos (auth, gastos, suscripciones, categorías).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "fincontrol.db";
    /**
     * v1: usuarios/gastos/suscripciones. v2: agrega el catálogo sembrado de categorías.
     * v3: agrega las plantillas de gastos frecuentes (S17).
     * v4: separa ingresos de gastos (tipo en gastos y categorías) y le da color/icono a la
     *     categoría (S23/S22).
     */
    public static final int DB_VERSION = 4;

    /** Valor de {@code tipo} para un gasto; la única alternativa es {@link #TIPO_INGRESO}. */
    public static final String TIPO_GASTO = "gasto";
    /** Valor de {@code tipo} para una fila de ingreso (S23). */
    public static final String TIPO_INGRESO = "ingreso";

    // Tabla usuarios
    public static final String T_USUARIOS = "usuarios";
    public static final String U_ID = "id";
    public static final String U_USUARIO = "usuario";
    public static final String U_PASSWORD = "password";
    public static final String U_NOMBRE = "nombre";

    // Tabla gastos
    public static final String T_GASTOS = "gastos";
    public static final String G_ID = "id";
    public static final String G_MONTO = "monto";
    public static final String G_CATEGORIA = "categoria";
    public static final String G_FECHA = "fecha";
    public static final String G_DESCRIPCION = "descripcion";
    public static final String G_LATITUD = "latitud";
    public static final String G_LONGITUD = "longitud";
    public static final String G_USUARIO_ID = "usuario_id";
    /** v4: 'gasto' | 'ingreso'. Las filas viejas quedan 'gasto' en la migración (S23). */
    public static final String G_TIPO = "tipo";

    // Tabla suscripciones
    public static final String T_SUSCRIPCIONES = "suscripciones";
    public static final String S_ID = "id";
    public static final String S_NOMBRE = "nombre";
    public static final String S_MONTO = "monto";
    public static final String S_DIA_COBRO = "dia_cobro";
    public static final String S_ACTIVA = "activa";
    public static final String S_USUARIO_ID = "usuario_id";

    // Tabla categorias (v2)
    public static final String T_CATEGORIAS = "categorias";
    public static final String C_ID = "id";
    public static final String C_NOMBRE = "nombre";
    public static final String C_CONTADOR_USO = "contador_uso";
    public static final String C_FIJADA = "fijada";
    public static final String C_ORDEN_MANUAL = "orden_manual";
    /** v4: 'gasto' | 'ingreso', para que el picker ofrezca el catálogo que corresponde (S23). */
    public static final String C_TIPO = "tipo";
    /** v4: color ARGB de la categoría; NULL significa usar el color del tema (S22). */
    public static final String C_COLOR = "color";
    /** v4: clave de icono de la categoría; NULL significa sin icono (S22). */
    public static final String C_ICONO = "icono";

    // Tabla plantillas (v3) - gastos frecuentes de un usuario (S17).
    public static final String T_PLANTILLAS = "plantillas";
    public static final String P_ID = "id";
    public static final String P_USUARIO_ID = "usuario_id";
    public static final String P_NOMBRE = "nombre";
    public static final String P_MONTO = "monto";
    public static final String P_CATEGORIA = "categoria";
    public static final String P_USOS = "usos";
    public static final String P_ULTIMA_VEZ = "ultima_vez";

    /** Catálogo inicial sembrado en onCreate y en la migración 1 -> 2 (S12). */
    private static final String[] CATEGORIAS_INICIALES = {
            "Comida",
            "Transporte",
            "Hogar",
            "Salud",
            "Entretención",
            "Educación",
            "Compras",
            "Otros"
    };

    /** Catálogo de ingresos sembrado en onCreate y en la migración 3 -> 4 (S23). */
    private static final String[] CATEGORIAS_INGRESO_INICIALES = {
            "Sueldo",
            "Extra"
    };

    private static DatabaseHelper instancia;

    public static synchronized DatabaseHelper obtener(Context context) {
        if (instancia == null) {
            instancia = new DatabaseHelper(context.getApplicationContext());
        }
        return instancia;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_USUARIOS + " ("
                + U_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + U_USUARIO + " TEXT NOT NULL UNIQUE, "
                + U_PASSWORD + " TEXT NOT NULL, "
                + U_NOMBRE + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + T_GASTOS + " ("
                + G_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + G_MONTO + " REAL NOT NULL, "
                + G_CATEGORIA + " TEXT NOT NULL, "
                + G_FECHA + " TEXT NOT NULL, "
                + G_DESCRIPCION + " TEXT, "
                + G_LATITUD + " REAL, "
                + G_LONGITUD + " REAL, "
                + G_USUARIO_ID + " INTEGER NOT NULL, "
                + G_TIPO + " TEXT NOT NULL DEFAULT '" + TIPO_GASTO + "', "
                + "FOREIGN KEY(" + G_USUARIO_ID + ") REFERENCES " + T_USUARIOS + "(" + U_ID + "))");

        db.execSQL("CREATE TABLE " + T_SUSCRIPCIONES + " ("
                + S_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + S_NOMBRE + " TEXT NOT NULL, "
                + S_MONTO + " REAL NOT NULL, "
                + S_DIA_COBRO + " INTEGER NOT NULL, "
                + S_ACTIVA + " INTEGER NOT NULL DEFAULT 1, "
                + S_USUARIO_ID + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + S_USUARIO_ID + ") REFERENCES " + T_USUARIOS + "(" + U_ID + "))");

        crearTablaCategorias(db);
        sembrarCategorias(db);
        sembrarCategoriasIngreso(db);
        crearTablaPlantillas(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int vieja, int nueva) {
        if (vieja < 2) {
            // v1 -> v2 es aditiva: las filas de usuarios, gastos y suscripciones de la app
            // instalada se mantienen, solo se agrega el catálogo sembrado de categorías.
            crearTablaCategorias(db);
            sembrarCategorias(db);
        }
        if (vieja < 3) {
            // v2 -> v3 también es aditiva: solo se crea la tabla plantillas, no se dropea ni se
            // reescribe ninguna tabla del usuario, así que quien actualiza conserva sus gastos,
            // sus suscripciones y sus categorías.
            crearTablaPlantillas(db);
        }
        if (vieja < 4) {
            // v3 -> v4 también es aditiva (S23/S22): ALTER TABLE ... ADD COLUMN, nunca un DROP ni
            // un rebuild de tabla, así cada gasto, suscripción, plantilla y categoría guardada
            // conserva sus filas. El DEFAULT 'gasto' clasifica las filas viejas como gasto sin
            // tocarlas, y el catálogo de ingresos se agrega solo si el nombre sigue libre.
            db.execSQL("ALTER TABLE " + T_GASTOS + " ADD COLUMN " + G_TIPO
                    + " TEXT NOT NULL DEFAULT '" + TIPO_GASTO + "'");
            db.execSQL("ALTER TABLE " + T_CATEGORIAS + " ADD COLUMN " + C_TIPO
                    + " TEXT NOT NULL DEFAULT '" + TIPO_GASTO + "'");
            // color/icono en NULL dejan el default del tema: no hay que hacer backfill en las
            // categorías que vienen de v3.
            db.execSQL("ALTER TABLE " + T_CATEGORIAS + " ADD COLUMN " + C_COLOR + " INTEGER");
            db.execSQL("ALTER TABLE " + T_CATEGORIAS + " ADD COLUMN " + C_ICONO + " TEXT");
            sembrarCategoriasIngreso(db);
        }
    }

    /** Crea la tabla de plantillas de gastos frecuentes (usos / ultima_vez ordenan los chips). */
    private void crearTablaPlantillas(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_PLANTILLAS + " ("
                + P_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + P_USUARIO_ID + " INTEGER NOT NULL, "
                + P_NOMBRE + " TEXT NOT NULL, "
                + P_MONTO + " REAL NOT NULL, "
                + P_CATEGORIA + " TEXT NOT NULL, "
                + P_USOS + " INTEGER NOT NULL DEFAULT 0, "
                + P_ULTIMA_VEZ + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + P_USUARIO_ID + ") REFERENCES " + T_USUARIOS + "(" + U_ID + "))");
    }

    /**
     * Crea la tabla del catálogo de categorías: nombre único, contador de uso, estado de fijada,
     * orden dentro de las fijadas y las columnas de v4 {@code tipo} (gasto/ingreso), {@code color}
     * (ARGB o NULL) y {@code icono} (clave de icono o NULL).
     */
    private void crearTablaCategorias(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_CATEGORIAS + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_NOMBRE + " TEXT NOT NULL UNIQUE, "
                + C_CONTADOR_USO + " INTEGER NOT NULL DEFAULT 0, "
                + C_FIJADA + " INTEGER NOT NULL DEFAULT 0, "
                + C_ORDEN_MANUAL + " INTEGER NOT NULL DEFAULT 0, "
                + C_TIPO + " TEXT NOT NULL DEFAULT '" + TIPO_GASTO + "', "
                + C_COLOR + " INTEGER, "
                + C_ICONO + " TEXT)");
    }

    /**
     * Siembra el catálogo por defecto. Los nombres viajan como argumentos enlazados, nunca como
     * texto SQL, y el INSERT OR IGNORE deja intacta una segunda corrida (o una categoría del
     * usuario con el mismo nombre).
     */
    private void sembrarCategorias(SQLiteDatabase db) {
        for (String nombre : CATEGORIAS_INICIALES) {
            db.execSQL("INSERT OR IGNORE INTO " + T_CATEGORIAS
                    + " (" + C_NOMBRE + ") VALUES (?)", new Object[]{nombre});
        }
    }

    /**
     * Siembra el catálogo de ingresos (Sueldo, Extra) cuando el nombre todavía está libre.
     *
     * <p>{@code INSERT OR IGNORE} solo reacciona al índice UNIQUE sobre nombre, que distingue
     * mayúsculas, así que la existencia se revisa con un {@code NOT EXISTS ... COLLATE NOCASE}
     * explícito: repetir la siembra, o una categoría del usuario llamada "sueldo", nunca genera
     * un duplicado. Los dos nombres viajan como argumentos enlazados, nunca como texto SQL
     * (S3/S4).</p>
     */
    private void sembrarCategoriasIngreso(SQLiteDatabase db) {
        for (String nombre : CATEGORIAS_INGRESO_INICIALES) {
            db.execSQL("INSERT INTO " + T_CATEGORIAS
                            + " (" + C_NOMBRE + ", " + C_TIPO + ")"
                            + " SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM " + T_CATEGORIAS
                            + " WHERE " + C_NOMBRE + " = ? COLLATE NOCASE)",
                    new Object[]{nombre, TIPO_INGRESO, nombre});
        }
    }

    /** Utilidad para armar ContentValues sin exponer la clase interna de cada DAO. */
    public static ContentValues valores() {
        return new ContentValues();
    }
}

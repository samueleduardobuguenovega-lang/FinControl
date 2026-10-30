package com.fincontrol.app.model;

/**
 * Registro de movimiento: gasto o ingreso (v4).
 *
 * <p>{@code tipo} separa los gastos ({@code "gasto"}, el significado histórico de esta tabla) de
 * los ingresos ({@code "ingreso"}, S23). {@code latitud}/{@code longitud} vienen null cuando la
 * fila no tiene ubicación; las filas de ingreso nunca la llevan.</p>
 */
public class Gasto {

    /** Valor de {@code tipo} para un gasto (el default de toda fila anterior a la v4). */
    public static final String TIPO_GASTO = "gasto";
    /** Valor de {@code tipo} para una fila de ingreso (S23). */
    public static final String TIPO_INGRESO = "ingreso";

    private final long id;
    private final double monto;
    private final String categoria;
    private final String fecha; // AAAA-MM-DD
    private final String descripcion;
    private final Double latitud;
    private final Double longitud;
    private final long usuarioId;
    private final String tipo;

    /**
     * Constructor completo (v4). El tipo va explícito para que las consultas del listado puedan
     * separar gastos de ingresos.
     */
    public Gasto(long id, double monto, String categoria, String fecha, String descripcion,
                 Double latitud, Double longitud, long usuarioId, String tipo) {
        this.id = id;
        this.monto = monto;
        this.categoria = categoria;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.usuarioId = usuarioId;
        this.tipo = (tipo == null || tipo.trim().isEmpty()) ? TIPO_GASTO : tipo;
    }

    /**
     * Constructor histórico que se mantiene para el código escrito antes de la v4 (el formulario,
     * las plantillas, el deshacer). Crea un gasto, que es lo que esos flujos hacían.
     */
    public Gasto(long id, double monto, String categoria, String fecha, String descripcion,
                 Double latitud, Double longitud, long usuarioId) {
        this(id, monto, categoria, fecha, descripcion, latitud, longitud, usuarioId, TIPO_GASTO);
    }

    public long getId() { return id; }
    public double getMonto() { return monto; }
    public String getCategoria() { return categoria; }
    public String getFecha() { return fecha; }
    public String getDescripcion() { return descripcion; }
    public Double getLatitud() { return latitud; }
    public Double getLongitud() { return longitud; }
    public long getUsuarioId() { return usuarioId; }
    public String getTipo() { return tipo; }

    /** true cuando esta fila cuenta como ingreso del período elegido (S23). */
    public boolean esIngreso() {
        return TIPO_INGRESO.equals(tipo);
    }

    public boolean tieneUbicacion() {
        return latitud != null && longitud != null;
    }
}

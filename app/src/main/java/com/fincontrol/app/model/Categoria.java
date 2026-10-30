package com.fincontrol.app.model;

/**
 * Categoría del catálogo sembrado y editable (S12-S14), con su identidad visual (S22).
 *
 * <p>{@code contadorUso} alimenta el orden "más usadas primero", {@code fijada} marca la categoría
 * anclada arriba del picker y {@code ordenManual} guarda la posición que el usuario le dio dentro
 * del bloque de fijadas, arrastrando y soltando.</p>
 *
 * <p>La v4 agrega {@code color} (un ARGB que puede venir null; null significa "usar el color del
 * tema"), {@code icono} (una clave de icono que también puede venir null y que resuelve
 * {@link com.fincontrol.app.categorias.EstiloCategoria}) y {@code tipo}, que separa las categorías
 * de gasto de las de ingreso (S23).</p>
 */
public class Categoria {

    /** Valor de {@code tipo} para una categoría de gasto, igual al default de la base (S23). */
    public static final String TIPO_GASTO = "gasto";
    /** Valor de {@code tipo} para una categoría de ingreso (S23). */
    public static final String TIPO_INGRESO = "ingreso";

    private final long id;
    private final String nombre;
    private final int contadorUso;
    private final boolean fijada;
    private final int ordenManual;
    private final Integer color;
    private final String icono;
    private final String tipo;

    /** Constructor completo (v4): todas las columnas de la tabla categorias. */
    public Categoria(long id, String nombre, int contadorUso, boolean fijada, int ordenManual,
                     Integer color, String icono, String tipo) {
        this.id = id;
        this.nombre = nombre;
        this.contadorUso = contadorUso;
        this.fijada = fijada;
        this.ordenManual = ordenManual;
        this.color = color;
        this.icono = icono;
        this.tipo = (tipo == null || tipo.trim().isEmpty()) ? TIPO_GASTO : tipo;
    }

    /**
     * Constructor histórico que se mantiene para el código escrito antes de la v4: describe una
     * categoría de gasto sin color ni icono propio, que es justo lo que ese código esperaba.
     */
    public Categoria(long id, String nombre, int contadorUso, boolean fijada, int ordenManual) {
        this(id, nombre, contadorUso, fijada, ordenManual, null, null, TIPO_GASTO);
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public int getContadorUso() { return contadorUso; }
    public boolean isFijada() { return fijada; }
    public int getOrdenManual() { return ordenManual; }

    /** Color ARGB propio de la categoría, o null cuando usa el del tema (S22). */
    public Integer getColor() { return color; }

    /** Clave de icono de la categoría (por ejemplo "comida"), o null cuando no tiene (S22). */
    public String getIcono() { return icono; }

    /** 'gasto' o 'ingreso' (S23). */
    public String getTipo() { return tipo; }

    /** true cuando esta categoría es de ingresos y no de gastos (S23). */
    public boolean esIngreso() {
        return TIPO_INGRESO.equals(tipo);
    }
}

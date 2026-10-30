package com.fincontrol.app.model;

/**
 * Plantilla de gasto frecuente (S17): un monto con nombre y una categoría que el usuario agrega
 * en un toque.
 *
 * <p>{@code usos} y {@code ultimaVez} son las señales que ordenan los chips: primero la plantilla
 * más usada, después la usada más recientemente.</p>
 */
public class Plantilla {
    private final long id;
    private final long usuarioId;
    private final String nombre;
    private final double monto;
    private final String categoria;
    private final int usos;
    private final long ultimaVez;

    public Plantilla(long id, long usuarioId, String nombre, double monto, String categoria,
                     int usos, long ultimaVez) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.monto = monto;
        this.categoria = categoria;
        this.usos = usos;
        this.ultimaVez = ultimaVez;
    }

    public long getId() { return id; }
    public long getUsuarioId() { return usuarioId; }
    public String getNombre() { return nombre; }
    public double getMonto() { return monto; }
    public String getCategoria() { return categoria; }
    public int getUsos() { return usos; }
    public long getUltimaVez() { return ultimaVez; }
}

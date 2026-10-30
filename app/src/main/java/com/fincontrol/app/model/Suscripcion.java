package com.fincontrol.app.model;

/** Modelo de suscripción o pago mensual. */
public class Suscripcion {
    private final long id;
    private final String nombre;
    private final double monto;
    private final int diaCobro; // 1-31
    private final boolean activa;
    private final long usuarioId;

    public Suscripcion(long id, String nombre, double monto, int diaCobro, boolean activa, long usuarioId) {
        this.id = id;
        this.nombre = nombre;
        this.monto = monto;
        this.diaCobro = diaCobro;
        this.activa = activa;
        this.usuarioId = usuarioId;
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public double getMonto() { return monto; }
    public int getDiaCobro() { return diaCobro; }
    public boolean isActiva() { return activa; }
    public long getUsuarioId() { return usuarioId; }
}

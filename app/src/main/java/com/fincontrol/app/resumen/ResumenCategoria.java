package com.fincontrol.app.resumen;

/**
 * Una fila de la distribución mensual de gastos del dashboard (S20): el nombre de la
 * categoría y el total gastado en ella durante el mes.
 */
public class ResumenCategoria {

    private final String categoria;
    private final double total;

    public ResumenCategoria(String categoria, double total) {
        this.categoria = categoria == null ? "" : categoria;
        this.total = total;
    }

    public String getCategoria() {
        return categoria;
    }

    public double getTotal() {
        return total;
    }
}

package com.fincontrol.app.categorias;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

import com.fincontrol.app.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * Identidad visual de las categorías (S22): la paleta de colores, las claves de icono guardadas en
 * {@code categorias.icono} y la resolución de clave a drawable que comparten los adapters.
 *
 * <p>{@link #PALETA} e {@link #ICONOS} están alineados índice a índice, así elegir el icono de la
 * posición N sugiere el color de la posición N. La paleta es a propósito fría (azul, teal, verde,
 * índigo, cian, morado): confianza y calma para una app de finanzas, y deja el rojo y el naranja
 * solo para errores y advertencias.</p>
 */
public final class EstiloCategoria {

    /**
     * Doce colores ARGB pensados para finanzas, dominio frío. Nunca hardcodeados en un layout.
     */
    public static final int[] PALETA = {
            0xFF1565C0, // azul
            0xFF00897B, // teal
            0xFF2E7D32, // verde
            0xFF00838F, // cian oscuro
            0xFF3F51B5, // índigo
            0xFF5E35B1, // morado profundo
            0xFF0277BD, // azul medio
            0xFF00695C, // teal oscuro
            0xFF283593, // índigo oscuro
            0xFF7B1FA2, // morado
            0xFF006064, // cian más oscuro
            0xFF1B5E20  // verde oscuro
    };

    /** Claves de icono guardadas en la base, alineadas con {@link #PALETA}. */
    public static final String[] ICONOS = {
            "comida",
            "casa",
            "transporte",
            "salud",
            "entretencion",
            "cafe",
            "educacion",
            "regalos",
            "compras",
            "otros",
            "sueldo",
            "extra"
    };

    /** Drawable de cada clave de {@link #ICONOS}, en el mismo orden. */
    private static final int[] DIBUJOS = {
            R.drawable.ic_cat_comida,
            R.drawable.ic_cat_casa,
            R.drawable.ic_cat_transporte,
            R.drawable.ic_cat_salud,
            R.drawable.ic_cat_entretencion,
            R.drawable.ic_cat_cafe,
            R.drawable.ic_cat_educacion,
            R.drawable.ic_cat_regalos,
            R.drawable.ic_cat_compras,
            R.drawable.ic_cat_otros,
            R.drawable.ic_cat_sueldo,
            R.drawable.ic_cat_extra
    };

    private EstiloCategoria() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Color de la paleta para una posición. Un índice fuera de rango da la vuelta en vez de lanzar
     * una excepción.
     */
    public static int color(int indice) {
        return PALETA[Math.floorMod(indice, PALETA.length)];
    }

    /**
     * Posición de una clave de icono dentro de {@link #ICONOS} (sin distinguir mayúsculas).
     *
     * @return la posición, o -1 si la clave es null o no existe.
     */
    public static int indiceDe(String iconoKey) {
        if (iconoKey == null) {
            return -1;
        }
        for (int indice = 0; indice < ICONOS.length; indice++) {
            if (ICONOS[indice].equalsIgnoreCase(iconoKey.trim())) {
                return indice;
            }
        }
        return -1;
    }

    /**
     * Posición de un color ARGB guardado dentro de {@link #PALETA}. Sirve para preseleccionar el
     * punto de color del editor.
     *
     * @return la posición, o 0 (el color por defecto) si el color es null o es uno propio.
     */
    public static int indiceDeColor(Integer color) {
        if (color == null) {
            return 0;
        }
        for (int indice = 0; indice < PALETA.length; indice++) {
            if (PALETA[indice] == color) {
                return indice;
            }
        }
        return 0;
    }

    /**
     * Drawable de una clave de icono. Las claves desconocidas, faltantes o null caen en
     * {@code ic_cat_otros}, así una categoría editada fuera de la app nunca rompe la lista.
     */
    public static int drawableFor(String iconoKey) {
        int indice = indiceDe(iconoKey);
        return indice < 0 ? R.drawable.ic_cat_otros : DIBUJOS[indice];
    }

    /**
     * Pinta el chip redondo de categoría que usan el selector, la fila de gasto y la fila de
     * categoría: el color propio (o el contenedor del tema cuando la categoría no tiene) y el
     * icono en blanco sobre color propio, o en {@code colorOnPrimaryContainer} sobre el contenedor
     * del tema.
     */
    public static void aplicarCirculo(View vista, MaterialCardView circulo, ImageView icono,
                                      Integer color, String iconoKey) {
        circulo.setCardBackgroundColor(color != null
                ? color
                : MaterialColors.getColor(vista,
                        com.google.android.material.R.attr.colorPrimaryContainer));
        icono.setImageResource(drawableFor(iconoKey));
        icono.setImageTintList(ColorStateList.valueOf(color != null
                ? Color.WHITE
                : MaterialColors.getColor(vista,
                        com.google.android.material.R.attr.colorOnPrimaryContainer)));
    }
}

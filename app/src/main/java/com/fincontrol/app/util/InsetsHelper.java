package com.fincontrol.app.util;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Soporte edge-to-edge.
 *
 * <p>Desde targetSdk 35 la ventana se dibuja detrás de las barras de estado y navegación, así que
 * una pantalla que ignora los insets termina con su contenido bajo los íconos de notificación, el
 * reloj y la barra de gestos. Este helper rellena la raíz del contenido de la ventana con los
 * insets de las barras del sistema más el cutout de pantalla, y así cada pantalla queda dentro del
 * área segura visible.</p>
 */
public final class InsetsHelper {

    private InsetsHelper() {
    }

    /**
     * Aplica los insets de las barras del sistema y del cutout como padding en la raíz del
     * contenido ({@code android.R.id.content}), una vez por activity.
     *
     * <p>La raíz conserva su propio padding de layout para los hijos; solo la raíz recibe el
     * padding de los insets, y los insets se consumen para que las vistas anidadas no los apliquen
     * una segunda vez.</p>
     */
    public static void aplicar(Activity activity) {
        if (activity == null) {
            return;
        }
        View contenido = activity.findViewById(android.R.id.content);
        if (contenido == null) {
            return;
        }
        // Registrar el listener en la raíz una sola vez: el decor view ya existe en este punto,
        // incluso cuando setContentView todavía no corrió, y el listener se evalúa al attach.
        ViewCompat.setOnApplyWindowInsetsListener(contenido, (vista, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            // Consumir solo los insets de barras y cutout: los del IME siguen pasando, así los
            // formularios con adjustResize siguen reaccionando al teclado en Android 15.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .build();
        });
    }
}

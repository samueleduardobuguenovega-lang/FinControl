package com.fincontrol.app.gastos;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fincontrol.app.R;
import com.google.android.material.color.MaterialColors;

/**
 * Teclado numérico propio para el campo de monto (S21), al estilo de My Finance: nunca aparece el
 * teclado del sistema, solo dígitos, un separador decimal, borrar y confirmar.
 *
 * <p>La vista es un {@link LinearLayout} vertical que infla {@code res/layout/teclado_numerico}
 * como {@code merge}, así las cuatro filas de teclas quedan como hijos suyos. No tiene lógica de
 * monto: solo le pasa las teclas al {@link Listener}, que mantiene el buffer en la pantalla.</p>
 */
public class TecladoNumerico extends LinearLayout {

    /** Recibe las teclas; la pantalla decide cómo cambia el texto del monto. */
    public interface Listener {
        /** Se apretó un dígito o el separador decimal. */
        void onDigito(String digito);

        /** Se apretó borrar. */
        void onBorrar();

        /** Se apretó confirmar: hace lo mismo que el botón de guardar. */
        void onConfirm();
    }

    private Listener listener;

    public TecladoNumerico(Context context) {
        this(context, null);
    }

    public TecladoNumerico(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TecladoNumerico(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inicializar();
    }

    /** Registra la pantalla que consume las teclas. */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    private void inicializar() {
        setOrientation(VERTICAL);
        // Superficie tonal para que el teclado se lea como bloque aparte sobre la barra de abajo.
        setBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
        LayoutInflater.from(getContext()).inflate(R.layout.teclado_numerico, this, true);

        enlazarDigito(R.id.btn_tecla_0, "0");
        enlazarDigito(R.id.btn_tecla_1, "1");
        enlazarDigito(R.id.btn_tecla_2, "2");
        enlazarDigito(R.id.btn_tecla_3, "3");
        enlazarDigito(R.id.btn_tecla_4, "4");
        enlazarDigito(R.id.btn_tecla_5, "5");
        enlazarDigito(R.id.btn_tecla_6, "6");
        enlazarDigito(R.id.btn_tecla_7, "7");
        enlazarDigito(R.id.btn_tecla_8, "8");
        enlazarDigito(R.id.btn_tecla_9, "9");
        // Se aceptan los dos separadores; la pantalla los normaliza a ".".
        enlazarDigito(R.id.btn_tecla_coma, ",");
        enlazarDigito(R.id.btn_tecla_punto, ".");

        findViewById(R.id.btn_tecla_borrar).setOnClickListener(v -> {
            if (listener != null) {
                listener.onBorrar();
            }
        });
        findViewById(R.id.btn_tecla_confirmar).setOnClickListener(v -> {
            if (listener != null) {
                listener.onConfirm();
            }
        });
    }

    private void enlazarDigito(int id, String digito) {
        View tecla = findViewById(id);
        tecla.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDigito(digito);
            }
        });
    }
}

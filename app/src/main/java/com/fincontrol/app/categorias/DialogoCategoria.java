package com.fincontrol.app.categorias;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.TextViewCompat;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Categoria;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor de categoría reutilizable (S22): nombre, identidad visual (puntos de color y círculos de
 * icono) y, cuando quien lo llama lo permite, el interruptor de tipo de registro.
 *
 * <p>Un solo diálogo sirve a los dos puntos de entrada de la app: el FAB de "nueva categoría" de
 * {@link CategoriasActivity} (con el interruptor de tipo a la vista) y el tile de "agregar" del
 * formulario de gasto (ahí el tipo viene del formulario, así que el interruptor queda oculto). El
 * layout se arma en código a propósito: así el editor tiene una sola fuente de verdad y no hace
 * falta un segundo archivo de layout que repita la paleta y la fila de iconos.</p>
 *
 * <p>La validación de nombre repetido vive dentro del diálogo: el callback devuelve false y el
 * campo muestra {@code cat_ya_existe} en vez de cerrarse.</p>
 */
public final class DialogoCategoria {

    /** Largo máximo del nombre, igual al android:maxLength="30" del campo del formulario. */
    private static final int MAX_NOMBRE = 30;
    private static final int TAMANO_PUNTO = 40;
    private static final int TAMANO_CIRCULO = 48;
    private static final int TAMANO_ICONO = 24;

    /**
     * Recibe la categoría editada; si devuelve false el diálogo sigue abierto con el error inline.
     */
    public interface Callback {
        /**
         * @param nombre    nombre recortado y no vacío (el diálogo ya lo validó).
         * @param color     color ARGB elegido de la paleta.
         * @param icono     clave del icono elegido dentro de {@link EstiloCategoria#ICONOS}.
         * @param esIngreso true cuando el interruptor de tipo está encendido (solo al crear).
         * @return false si el nombre ya lo usa otra categoría.
         */
        boolean onGuardar(String nombre, Integer color, String icono, boolean esIngreso);
    }

    private final Context contexto;
    private final Callback callback;
    private final boolean esRenombrar;
    private final TextInputLayout campo;
    private final TextInputEditText entrada;
    private final List<MaterialCardView> puntos = new ArrayList<>();
    private final List<MaterialCardView> circulos = new ArrayList<>();
    private final SwitchCompat interruptor;

    private int indiceColor;
    private int indiceIcono;

    private DialogoCategoria(Context contexto, @Nullable Categoria categoria, boolean permitirTipo,
                             Callback callback) {
        this.contexto = contexto;
        this.callback = callback;
        this.esRenombrar = categoria != null;
        this.indiceColor = EstiloCategoria.indiceDeColor(
                categoria == null ? null : categoria.getColor());
        this.indiceIcono = Math.max(0,
                EstiloCategoria.indiceDe(categoria == null ? null : categoria.getIcono()));

        campo = new TextInputLayout(contexto, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        campo.setHint(R.string.cat_hint_nombre);
        entrada = new TextInputEditText(contexto);
        entrada.setSingleLine(true);
        entrada.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entrada.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NOMBRE)});
        if (categoria != null) {
            entrada.setText(categoria.getNombre());
            entrada.setSelection(entrada.getText() == null ? 0 : entrada.getText().length());
        }
        campo.addView(entrada);

        interruptor = permitirTipo ? interruptorTipo(categoria) : null;
    }

    /**
     * Abre el editor.
     *
     * @param categoria   categoría existente a editar, o null para crear una nueva.
     * @param permitirTipo true para mostrar el interruptor "es un ingreso" (al crear desde la
     *                     pantalla de categorías); false para heredar el tipo de quien llama
     *                     (agregar inline).
     */
    public static void mostrar(Context contexto, @Nullable Categoria categoria,
                               boolean permitirTipo, Callback callback) {
        new DialogoCategoria(contexto, categoria, permitirTipo, callback).mostrar();
    }

    private void mostrar() {
        AlertDialog dialogo = new MaterialAlertDialogBuilder(contexto)
                .setTitle(esRenombrar ? R.string.cat_editar_nombre : R.string.cat_nueva)
                .setView(construirVista())
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.btn_ingresar, null)
                .create();

        // El botón positivo se sobrescribe para avisar el nombre repetido inline, sin cerrar.
        dialogo.setOnShowListener(mostrado -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> confirmar(dialogo)));
        dialogo.show();
        entrada.requestFocus();
    }

    private void confirmar(AlertDialog dialogo) {
        campo.setError(null);
        String nombre = entrada.getText() == null ? "" : entrada.getText().toString().trim();
        if (nombre.isEmpty()) {
            campo.setError(contexto.getString(R.string.error_campo_vacio));
            return;
        }
        boolean esIngreso = interruptor != null && interruptor.isChecked();
        boolean guardado = callback.onGuardar(nombre, EstiloCategoria.color(indiceColor),
                EstiloCategoria.ICONOS[indiceIcono], esIngreso);
        if (!guardado) {
            campo.setError(contexto.getString(R.string.cat_ya_existe));
            return;
        }
        dialogo.dismiss();
    }

    private View construirVista() {
        LinearLayout contenido = new LinearLayout(contexto);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(dp(24), dp(8), dp(24), 0);

        contenido.addView(campo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contenido.addView(etiqueta(R.string.cat_color));
        contenido.addView(filaDesplazable(filaColores()));
        contenido.addView(etiqueta(R.string.cat_icono));
        contenido.addView(filaDesplazable(filaIconos()));

        if (interruptor != null) {
            contenido.addView(interruptor, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        pintarColores();
        pintarIconos();

        ScrollView scroll = new ScrollView(contexto);
        scroll.addView(contenido);
        return scroll;
    }

    private TextView etiqueta(int texto) {
        TextView etiqueta = new TextView(contexto);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        etiqueta.setLayoutParams(params);
        etiqueta.setText(texto);
        TextViewCompat.setTextAppearance(etiqueta,
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        return etiqueta;
    }

    private View filaDesplazable(View fila) {
        HorizontalScrollView scroll = new HorizontalScrollView(contexto);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(fila, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** Un punto redondo por color de la paleta; el elegido mantiene un borde visible. */
    private View filaColores() {
        LinearLayout fila = new LinearLayout(contexto);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        for (int indice = 0; indice < EstiloCategoria.PALETA.length; indice++) {
            MaterialCardView punto = new MaterialCardView(contexto);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(TAMANO_PUNTO), dp(TAMANO_PUNTO));
            params.setMargins(dp(4), dp(8), dp(4), dp(8));
            punto.setLayoutParams(params);
            punto.setRadius(dp(TAMANO_PUNTO / 2));
            punto.setCardElevation(0f);
            punto.setCardBackgroundColor(EstiloCategoria.PALETA[indice]);
            punto.setClickable(true);
            punto.setFocusable(true);
            punto.setContentDescription(contexto.getString(R.string.cat_color) + " " + (indice + 1));
            final int elegido = indice;
            punto.setOnClickListener(v -> {
                indiceColor = elegido;
                pintarColores();
                pintarIconos();
            });
            puntos.add(punto);
            fila.addView(punto);
        }
        return fila;
    }

    /** Un círculo por clave de icono, pintado con el color elegido y el glifo en blanco. */
    private View filaIconos() {
        LinearLayout fila = new LinearLayout(contexto);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        for (int indice = 0; indice < EstiloCategoria.ICONOS.length; indice++) {
            MaterialCardView circulo = new MaterialCardView(contexto);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(TAMANO_CIRCULO), dp(TAMANO_CIRCULO));
            params.setMargins(dp(4), dp(8), dp(4), dp(8));
            circulo.setLayoutParams(params);
            circulo.setRadius(dp(TAMANO_CIRCULO / 2));
            circulo.setCardElevation(0f);
            circulo.setClickable(true);
            circulo.setFocusable(true);
            circulo.setContentDescription(contexto.getString(R.string.cat_icono) + " "
                    + EstiloCategoria.ICONOS[indice]);

            ImageView icono = new ImageView(contexto);
            icono.setLayoutParams(new FrameLayout.LayoutParams(
                    dp(TAMANO_ICONO), dp(TAMANO_ICONO), Gravity.CENTER));
            icono.setImageResource(EstiloCategoria.drawableFor(EstiloCategoria.ICONOS[indice]));
            icono.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            circulo.addView(icono);

            final int elegido = indice;
            circulo.setOnClickListener(v -> {
                indiceIcono = elegido;
                pintarIconos();
            });
            circulos.add(circulo);
            fila.addView(circulo);
        }
        return fila;
    }

    private void pintarColores() {
        int seleccionado = MaterialColors.getColor(contexto,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        for (int indice = 0; indice < puntos.size(); indice++) {
            MaterialCardView punto = puntos.get(indice);
            boolean activo = indice == indiceColor;
            punto.setStrokeWidth(activo ? dp(3) : 0);
            punto.setStrokeColor(seleccionado);
        }
    }

    private void pintarIconos() {
        int color = EstiloCategoria.color(indiceColor);
        int seleccionado = MaterialColors.getColor(contexto,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        for (int indice = 0; indice < circulos.size(); indice++) {
            MaterialCardView circulo = circulos.get(indice);
            boolean activo = indice == indiceIcono;
            circulo.setCardBackgroundColor(color);
            circulo.setStrokeWidth(activo ? dp(3) : 0);
            circulo.setStrokeColor(seleccionado);
        }
    }

    private SwitchCompat interruptorTipo(@Nullable Categoria categoria) {
        SwitchCompat tipo = new SwitchCompat(contexto);
        tipo.setText(R.string.cat_tipo_ingreso);
        tipo.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        tipo.setChecked(categoria != null && categoria.esIngreso());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        tipo.setLayoutParams(params);
        tipo.setMinHeight(dp(48));
        return tipo;
    }

    private int dp(int valor) {
        return Math.round(valor * contexto.getResources().getDisplayMetrics().density);
    }
}

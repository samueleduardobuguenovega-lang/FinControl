package com.fincontrol.app.categorias;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Categoria;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter de las filas de la pantalla de categorías (S13/S14).
 *
 * <p>El mismo adapter dibuja las dos secciones: la lista de fijadas (arrastrable, con el handle de
 * arrastre visible) y la lista de "las demás por uso" (sin handle). La pantalla es dueña de las
 * acciones, así cada cambio pasa por una sola llamada al DAO y una sola recarga.</p>
 */
public class CategoriaAdapter extends RecyclerView.Adapter<CategoriaAdapter.CategoriaViewHolder> {

    /** Acciones de la fila que implementa la pantalla. */
    public interface Acciones {
        void onFijar(Categoria categoria);

        void onRenombrar(Categoria categoria);

        void onEliminar(Categoria categoria);

        void onIniciarArrastre(RecyclerView.ViewHolder holder);
    }

    private final List<Categoria> categorias = new ArrayList<>();
    private Acciones acciones;
    private boolean arrastrable;

    public void setAcciones(Acciones acciones) {
        this.acciones = acciones;
    }

    /** Solo la sección de fijadas se puede reordenar, por eso solo ahí se muestra el handle. */
    public void setArrastrable(boolean arrastrable) {
        this.arrastrable = arrastrable;
    }

    /** Reemplaza el dataset completo y refresca la lista. */
    public void actualizar(List<Categoria> nuevas) {
        categorias.clear();
        if (nuevas != null) {
            categorias.addAll(nuevas);
        }
        notifyDataSetChanged();
    }

    /** Mueve una fila mientras dura el arrastre (el orden lo guarda el handler del drop). */
    public void mover(int desde, int hasta) {
        if (desde < 0 || hasta < 0 || desde >= categorias.size() || hasta >= categorias.size()) {
            return;
        }
        Categoria movida = categorias.remove(desde);
        categorias.add(hasta, movida);
        notifyItemMoved(desde, hasta);
    }

    /** Ids de las filas, en el orden en que las ve el usuario. */
    public List<Long> idsEnOrden() {
        List<Long> ids = new ArrayList<>();
        for (Categoria categoria : categorias) {
            ids.add(categoria.getId());
        }
        return ids;
    }

    @NonNull
    @Override
    public CategoriaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_categoria, parent, false);
        return new CategoriaViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoriaViewHolder holder, int position) {
        holder.bind(categorias.get(position));
    }

    @Override
    public int getItemCount() {
        return categorias.size();
    }

    final class CategoriaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgArrastrar;
        private final MaterialCardView cardCategoria;
        private final ImageView imgCategoria;
        private final TextView txtNombre;
        private final TextView txtUso;
        private final MaterialButton btnFijar;
        private final MaterialButton btnEliminar;

        CategoriaViewHolder(@NonNull View itemView) {
            super(itemView);
            imgArrastrar = itemView.findViewById(R.id.img_arrastrar);
            cardCategoria = itemView.findViewById(R.id.card_categoria);
            imgCategoria = itemView.findViewById(R.id.img_categoria);
            txtNombre = itemView.findViewById(R.id.txt_categoria_nombre);
            txtUso = itemView.findViewById(R.id.txt_categoria_uso);
            btnFijar = itemView.findViewById(R.id.btn_fijar);
            btnEliminar = itemView.findViewById(R.id.btn_eliminar);
        }

        void bind(Categoria categoria) {
            txtNombre.setText(categoria.getNombre());
            txtUso.setText(itemView.getContext().getString(R.string.cat_usada, categoria.getContadorUso()));

            // Identidad visual (S22): el chip de la fila lleva el color y el icono de la categoría.
            EstiloCategoria.aplicarCirculo(itemView, cardCategoria, imgCategoria,
                    categoria.getColor(), categoria.getIcono());

            boolean fijada = categoria.isFijada();
            btnFijar.setIconResource(fijada ? R.drawable.ic_star : R.drawable.ic_star_border);
            btnFijar.setContentDescription(itemView.getContext().getString(
                    fijada ? R.string.cat_quitar_fijada : R.string.cat_fijar));
            btnFijar.setIconTint(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(itemView, fijada
                            ? androidx.appcompat.R.attr.colorPrimary
                            : com.google.android.material.R.attr.colorOnSurfaceVariant)));
            btnFijar.setOnClickListener(v -> {
                if (acciones != null) {
                    acciones.onFijar(categoria);
                }
            });
            btnEliminar.setOnClickListener(v -> {
                if (acciones != null) {
                    acciones.onEliminar(categoria);
                }
            });

            // Tocar el nombre (o la fila) abre el diálogo para renombrar.
            View.OnClickListener renombrar = v -> {
                if (acciones != null) {
                    acciones.onRenombrar(categoria);
                }
            };
            txtNombre.setOnClickListener(renombrar);
            itemView.setOnClickListener(renombrar);

            imgArrastrar.setVisibility(arrastrable ? View.VISIBLE : View.GONE);
            if (arrastrable) {
                // Mantener presionado el handle, o cualquier parte de la fila, inicia el arrastre.
                View.OnLongClickListener iniciarArrastre = v -> {
                    if (acciones != null) {
                        acciones.onIniciarArrastre(this);
                    }
                    return true;
                };
                imgArrastrar.setOnLongClickListener(iniciarArrastre);
                itemView.setOnLongClickListener(iniciarArrastre);
            } else {
                imgArrastrar.setOnLongClickListener(null);
                itemView.setOnLongClickListener(null);
            }
        }
    }
}

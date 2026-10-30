package com.fincontrol.app.gastos;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.categorias.CategoriaDao;
import com.fincontrol.app.categorias.EstiloCategoria;
import com.fincontrol.app.model.Gasto;
import com.fincontrol.app.session.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter que dibuja los gastos del usuario en la lista.
 *
 * <p>Un toque en la fila abre las opciones de editar o borrar ese gasto (S15). La pantalla puede
 * quedarse con las dos acciones vía {@link #setOnGastoAcciones(OnGastoAcciones)}; mientras no lo
 * haga, el adapter resuelve el flujo por su cuenta: abre el formulario en modo edición, o borra
 * después de confirmar y recarga la lista desde la base.</p>
 *
 * <p>Desde la v4 (S22) el chip de la izquierda de cada fila lleva el color y el icono de la
 * categoría, que se buscan por nombre en el catálogo del DAO. Una categoría sin estilo propio
 * mantiene el círculo del tema.</p>
 */
public class GastoAdapter extends RecyclerView.Adapter<GastoAdapter.GastoViewHolder> {

    /** Hook opcional para que la pantalla se haga cargo de editar y borrar. */
    public interface OnGastoAcciones {
        void onEditar(Gasto gasto);

        void onEliminar(Gasto gasto);
    }

    private final List<Gasto> gastos = new ArrayList<>();
    private OnGastoAcciones acciones;
    private CategoriaDao categoriaDao;

    /**
     * Catálogo de categorías de las filas, creado una vez por adapter (el helper de la base es
     * singleton).
     */
    private CategoriaDao categoriaDao(Context contexto) {
        if (categoriaDao == null) {
            categoriaDao = new CategoriaDao(contexto);
        }
        return categoriaDao;
    }

    /** Registra las acciones de la pantalla; si no se setean, el adapter usa su flujo interno. */
    public void setOnGastoAcciones(OnGastoAcciones acciones) {
        this.acciones = acciones;
    }

    /** Reemplaza todos los datos y refresca la lista. */
    public void actualizar(List<Gasto> nuevos) {
        gastos.clear();
        if (nuevos != null) {
            gastos.addAll(nuevos);
        }
        notifyDataSetChanged();
    }

    /** Item de esa posición, o null si la posición queda fuera de rango. */
    public Gasto getItem(int posicion) {
        return posicion >= 0 && posicion < gastos.size() ? gastos.get(posicion) : null;
    }

    /**
     * Saca de la lista la fila que se está deslizando (S19) y la devuelve, así la pantalla la
     * borra y ofrece deshacer con la copia completa en vez de una referencia vieja.
     */
    public Gasto quitar(int posicion) {
        if (posicion < 0 || posicion >= gastos.size()) {
            return null;
        }
        Gasto gasto = gastos.remove(posicion);
        notifyItemRemoved(posicion);
        return gasto;
    }

    /**
     * Saca la fila de ese id si la lista todavía la tiene (el borrado desde el diálogo) y devuelve
     * null cuando el swipe ya la había sacado.
     */
    public Gasto quitarPorId(long id) {
        for (int posicion = 0; posicion < gastos.size(); posicion++) {
            if (gastos.get(posicion).getId() == id) {
                return quitar(posicion);
            }
        }
        return null;
    }

    @NonNull
    @Override
    public GastoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gasto, parent, false);
        return new GastoViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull GastoViewHolder holder, int position) {
        Gasto gasto = gastos.get(position);
        CategoriaDao dao = categoriaDao(holder.itemView.getContext());
        holder.bind(gasto, dao.colorPorNombre(gasto.getCategoria()),
                dao.iconoPorNombre(gasto.getCategoria()));
        holder.itemView.setOnClickListener(
                v -> mostrarOpciones(v.getContext(), gasto));
    }

    @Override
    public int getItemCount() {
        return gastos.size();
    }

    /** Opciones de la fila: editar, borrar o cancelar. */
    private void mostrarOpciones(Context contexto, Gasto gasto) {
        String[] opciones = {
                contexto.getString(R.string.accion_editar),
                contexto.getString(R.string.accion_eliminar),
                contexto.getString(R.string.btn_cancelar)
        };
        new AlertDialog.Builder(contexto)
                .setTitle(titulo(gasto))
                .setItems(opciones, (dialogo, cual) -> {
                    if (cual == 0) {
                        editar(contexto, gasto);
                    } else if (cual == 1) {
                        confirmarEliminar(contexto, gasto);
                    }
                })
                .show();
    }

    private void editar(Context contexto, Gasto gasto) {
        if (acciones != null) {
            acciones.onEditar(gasto);
            return;
        }
        Intent intent = new Intent(contexto, GastoFormActivity.class);
        intent.putExtra(GastoFormActivity.EXTRA_GASTO_ID, gasto.getId());
        contexto.startActivity(intent);
    }

    private void confirmarEliminar(Context contexto, Gasto gasto) {
        new AlertDialog.Builder(contexto)
                .setMessage(R.string.gasto_confirmar_eliminar)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar, (dialogo, cual) -> {
                    if (acciones != null) {
                        acciones.onEliminar(gasto);
                        return;
                    }
                    new GastoDao(contexto).eliminar(gasto.getId());
                    Toast.makeText(contexto, R.string.gasto_eliminado, Toast.LENGTH_SHORT).show();
                    recargarDesdeBase(contexto);
                })
                .show();
    }

    /** Relee la lista del usuario tras borrar, así la fila desaparece sin salir de la pantalla. */
    private void recargarDesdeBase(Context contexto) {
        SessionManager session = new SessionManager(contexto);
        if (!session.haySesion()) {
            return;
        }
        actualizar(new GastoDao(contexto).listarPorUsuario(session.getUserId()));
    }

    private String titulo(Gasto gasto) {
        String descripcion = gasto.getDescripcion();
        return descripcion == null || descripcion.trim().isEmpty()
                ? gasto.getCategoria()
                : descripcion;
    }

    static class GastoViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardCategoria;
        private final ImageView imgCategoria;
        private final TextView txtTitulo;
        private final TextView txtDetalle;
        private final TextView txtMonto;

        GastoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCategoria = itemView.findViewById(R.id.card_categoria);
            imgCategoria = itemView.findViewById(R.id.img_categoria);
            txtTitulo = itemView.findViewById(R.id.txt_gasto_titulo);
            txtDetalle = itemView.findViewById(R.id.txt_gasto_detalle);
            txtMonto = itemView.findViewById(R.id.txt_gasto_monto);
        }

        void bind(Gasto gasto, Integer color, String icono) {
            String descripcion = gasto.getDescripcion();
            boolean sinDescripcion = descripcion == null || descripcion.trim().isEmpty();
            txtTitulo.setText(sinDescripcion ? gasto.getCategoria() : descripcion);
            txtDetalle.setText(String.format(Locale.US, "%s - %s",
                    gasto.getCategoria(), gasto.getFecha()));
            txtMonto.setText("$" + String.format(Locale.US, "%.2f", gasto.getMonto()));
            // Color e icono de la categoría (S22); si el estilo es null queda el círculo del tema.
            EstiloCategoria.aplicarCirculo(itemView, cardCategoria, imgCategoria, color, icono);
        }
    }
}

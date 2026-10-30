package com.fincontrol.app.suscripciones;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Suscripcion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter de la lista de suscripciones. */
public class SuscripcionAdapter extends RecyclerView.Adapter<SuscripcionAdapter.SuscripcionVista> {

    /** Avisa que el usuario cambió el estado activo de una fila. */
    public interface OnActivaCambiada {
        void onActivaCambiada(int posicion, Suscripcion suscripcion, boolean activa);
    }

    /** Avisa que el usuario tocó la fila fuera del switch (menú editar / eliminar). */
    public interface OnSuscripcionSeleccionada {
        void onSuscripcionSeleccionada(Suscripcion suscripcion);
    }

    private final List<Suscripcion> items = new ArrayList<>();
    private final OnActivaCambiada escucha;
    private final OnSuscripcionSeleccionada escuchaSeleccion;

    public SuscripcionAdapter(OnActivaCambiada escucha, OnSuscripcionSeleccionada escuchaSeleccion) {
        this.escucha = escucha;
        this.escuchaSeleccion = escuchaSeleccion;
    }

    public void actualizar(List<Suscripcion> nuevas) {
        items.clear();
        items.addAll(nuevas);
        notifyDataSetChanged();
    }

    /**
     * Reemplaza la fila en memoria para que el texto del switch siga al estado nuevo sin
     * recargar toda la lista (el modelo es inmutable).
     */
    public void aplicarActiva(int posicion, boolean activa) {
        if (posicion < 0 || posicion >= items.size()) {
            return;
        }
        Suscripcion vieja = items.get(posicion);
        items.set(posicion, new Suscripcion(vieja.getId(), vieja.getNombre(), vieja.getMonto(),
                vieja.getDiaCobro(), activa, vieja.getUsuarioId()));
        notifyItemChanged(posicion);
    }

    @NonNull
    @Override
    public SuscripcionVista onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        View fila = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_suscripcion, padre, false);
        return new SuscripcionVista(fila);
    }

    @Override
    public void onBindViewHolder(@NonNull SuscripcionVista vista, int posicion) {
        Suscripcion suscripcion = items.get(posicion);
        vista.nombre.setText(suscripcion.getNombre());
        vista.dia.setText("D\u00eda de cobro: " + suscripcion.getDiaCobro());
        vista.monto.setText(String.format(Locale.US, "$%.2f", suscripcion.getMonto()));

        // El tap en la fila abre el menú editar/eliminar. El switch consume sus propios
        // toques, así cambiar el estado activo nunca abre el menú.
        vista.itemView.setOnClickListener(v -> {
            int actual = vista.getBindingAdapterPosition();
            if (actual == RecyclerView.NO_POSITION || escuchaSeleccion == null) {
                return;
            }
            escuchaSeleccion.onSuscripcionSeleccionada(items.get(actual));
        });

        // Suelta el listener mientras sincroniza el widget para no entrar en un loop.
        vista.activa.setOnCheckedChangeListener(null);
        vista.activa.setChecked(suscripcion.isActiva());
        vista.activa.setText(suscripcion.isActiva() ? R.string.sub_activa : R.string.sub_pausada);
        vista.activa.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton boton, boolean marcado) {
                int actual = vista.getBindingAdapterPosition();
                if (actual == RecyclerView.NO_POSITION || escucha == null) {
                    return;
                }
                escucha.onActivaCambiada(actual, items.get(actual), marcado);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SuscripcionVista extends RecyclerView.ViewHolder {
        final TextView nombre;
        final TextView dia;
        final TextView monto;
        final SwitchCompat activa;

        SuscripcionVista(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.tv_nombre_sub);
            dia = itemView.findViewById(R.id.tv_dia_sub);
            monto = itemView.findViewById(R.id.tv_monto_sub);
            activa = itemView.findViewById(R.id.sw_sub_activa);
        }
    }
}

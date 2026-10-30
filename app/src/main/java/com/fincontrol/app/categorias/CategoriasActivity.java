package com.fincontrol.app.categorias;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Categoria;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de administración de categorías (S12-S15): catálogo inicial, orden por uso, pin con
 * drag & drop y agregar / renombrar / eliminar con la protección de "todavía la usa un gasto".
 *
 * <p>Layout: una pantalla con scroll y dos secciones. {@code rv_categorias_fijadas} tiene las filas
 * fijadas en el orden manual y es la única lista con drag & drop ({@link ItemTouchHelper});
 * {@code rv_categorias_otras} tiene el resto de las categorías ordenadas por uso.</p>
 *
 * <p>Desde la v4 (S22) tanto crear como renombrar pasan por {@link DialogoCategoria}, el mismo
 * editor visual del formulario de gasto: nombre, puntos de color y círculos de icono, más el
 * interruptor de tipo cuando la categoría se está creando.</p>
 */
public class CategoriasActivity extends AppCompatActivity implements CategoriaAdapter.Acciones {

    private CategoriaDao categoriaDao;
    private CategoriaAdapter adaptadorFijadas;
    private CategoriaAdapter adaptadorOtras;
    private View contenedorFijadas;
    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja la toolbar, la lista y el FAB libres de las barras del sistema.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_categorias);

        categoriaDao = new CategoriaDao(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        contenedorFijadas = findViewById(R.id.contenedor_fijadas);

        adaptadorFijadas = new CategoriaAdapter();
        adaptadorFijadas.setAcciones(this);
        adaptadorFijadas.setArrastrable(true);

        adaptadorOtras = new CategoriaAdapter();
        adaptadorOtras.setAcciones(this);
        adaptadorOtras.setArrastrable(false);

        RecyclerView listaFijadas = findViewById(R.id.rv_categorias_fijadas);
        listaFijadas.setLayoutManager(new LinearLayoutManager(this));
        listaFijadas.setAdapter(adaptadorFijadas);

        RecyclerView listaOtras = findViewById(R.id.rv_categorias_otras);
        listaOtras.setLayoutManager(new LinearLayoutManager(this));
        listaOtras.setAdapter(adaptadorOtras);

        itemTouchHelper = new ItemTouchHelper(new ArrastreCallback());
        itemTouchHelper.attachToRecyclerView(listaFijadas);

        FloatingActionButton fabNueva = findViewById(R.id.fab_nueva_categoria);
        fabNueva.setOnClickListener(v -> nuevaCategoria());
    }

    @Override
    protected void onResume() {
        super.onResume();
        recargar();
    }

    /** Recarga las dos secciones: las fijadas primero en orden manual, después el resto por uso. */
    private void recargar() {
        List<Categoria> fijadas = new ArrayList<>();
        List<Categoria> otras = new ArrayList<>();
        for (Categoria categoria : categoriaDao.listarOrdenadas()) {
            if (categoria.isFijada()) {
                fijadas.add(categoria);
            } else {
                otras.add(categoria);
            }
        }
        adaptadorFijadas.actualizar(fijadas);
        adaptadorOtras.actualizar(otras);
        contenedorFijadas.setVisibility(fijadas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onFijar(Categoria categoria) {
        categoriaDao.fijar(categoria.getId(), !categoria.isFijada());
        recargar();
    }

    @Override
    public void onRenombrar(Categoria categoria) {
        editarCategoria(categoria);
    }

    @Override
    public void onEliminar(Categoria categoria) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.cat_confirmar_eliminar, categoria.getNombre()))
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar, (dialogo, cual) -> {
                    if (categoriaDao.eliminar(categoria.getId())) {
                        recargar();
                    } else {
                        // El nombre todavía lo referencia al menos un gasto.
                        Toast.makeText(this, R.string.cat_no_eliminable, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public void onIniciarArrastre(RecyclerView.ViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    /**
     * Crea una categoría con su identidad visual y su tipo. El interruptor de tipo se ofrece solo
     * acá, donde el catálogo no está amarrado a un tipo de registro.
     */
    private void nuevaCategoria() {
        DialogoCategoria.mostrar(this, null, true, (nombre, color, icono, esIngreso) -> {
            long id = categoriaDao.insertar(nombre, color, icono,
                    esIngreso ? Categoria.TIPO_INGRESO : Categoria.TIPO_GASTO);
            if (id == -1L) {
                return false;
            }
            Toast.makeText(this, R.string.cat_agregada, Toast.LENGTH_SHORT).show();
            recargar();
            return true;
        });
    }

    /**
     * Renombra una categoría y actualiza color e icono en el mismo guardado, con el mismo editor
     * visual pero sin el interruptor de tipo (mover una categoría en uso de un tipo a otro es una
     * decisión aparte que la pantalla de catálogo no ofrece).
     */
    private void editarCategoria(@Nullable Categoria categoria) {
        DialogoCategoria.mostrar(this, categoria, false, (nombre, color, icono, esIngreso) -> {
            if (!categoriaDao.renombrar(categoria.getId(), nombre)) {
                return false;
            }
            categoriaDao.actualizarEstilo(categoria.getId(), color, icono);
            Toast.makeText(this, R.string.cat_color_aplicado, Toast.LENGTH_SHORT).show();
            recargar();
            return true;
        });
    }

    /**
     * Drag & drop solo dentro de la sección de fijadas: las filas se mueven entre ellas y al soltar
     * se persiste {@code orden_manual} con {@code reordenar()}.
     */
    private final class ArrastreCallback extends ItemTouchHelper.SimpleCallback {

        ArrastreCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            // La fila y el handle inician el arrastre a propósito, así el mantener presionado el
            // nombre conserva su propio significado y el arrastre nunca parte por accidente.
            return false;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder arrastrado,
                              @NonNull RecyclerView.ViewHolder destino) {
            adaptadorFijadas.mover(arrastrado.getBindingAdapterPosition(),
                    destino.getBindingAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direccion) {
            // En esta pantalla no se usa swipe.
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            categoriaDao.reordenar(adaptadorFijadas.idsEnOrden());
        }
    }
}

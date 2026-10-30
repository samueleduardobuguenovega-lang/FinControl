package com.fincontrol.app.mapa;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fincontrol.app.LoginActivity;
import com.fincontrol.app.R;
import com.fincontrol.app.gastos.GastoDao;
import com.fincontrol.app.model.Gasto;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

/**
 * Pantalla del mapa: dibuja un marcador por cada gasto guardado con ubicación.
 * El SupportMapFragment va declarado en activity_mapa.xml con el id "map".
 *
 * <p>Motor: Google Maps SDK for Android. La clave se lee de local.properties al
 * compilar y se inyecta como recurso google_maps_key, así que nunca queda en el
 * repositorio (BP4). Si la clave falta o no está autorizada, el mapa no dibuja
 * teselas y Google Maps lo reporta en el log, pero la pantalla no se cae.</p>
 */
public class MapaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapaActivity";

    /** Centro por defecto de la cámara cuando ningún gasto tiene ubicación (Santiago). */
    private static final LatLng SANTIAGO = new LatLng(-33.4489, -70.6693);
    private static final float ZOOM_CIUDAD = 10f;
    private static final float ZOOM_MARCADOR = 13f;

    private SessionManager sesion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: el título y el mapa no quedan bajo las barras del sistema.
        InsetsHelper.aplicar(this);

        sesion = new SessionManager(this);
        if (!sesion.haySesion()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_mapa);
        setTitle(R.string.mapa_titulo);

        try {
            SupportMapFragment fragmento =
                    (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            if (fragmento == null) {
                Log.e(TAG, "No se encontró el SupportMapFragment con id map en el layout");
                Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
                return;
            }
            fragmento.getMapAsync(this);
        } catch (RuntimeException e) {
            // Una clave de Maps ausente o inválida no puede tumbar la pantalla.
            Log.e(TAG, "No se pudo iniciar el mapa", e);
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMapReady(GoogleMap mapa) {
        try {
            dibujarGastos(mapa);
        } catch (RuntimeException e) {
            Log.e(TAG, "No se pudieron dibujar los gastos en el mapa", e);
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
        }
    }

    private void dibujarGastos(GoogleMap mapa) {
        List<Gasto> gastos = new GastoDao(this).listarPorUsuario(sesion.getUserId());
        LatLng primerMarcador = null;

        if (gastos != null) {
            for (Gasto gasto : gastos) {
                if (!gasto.tieneUbicacion()) {
                    continue; // Los gastos sin ubicación no se dibujan.
                }
                LatLng posicion = new LatLng(gasto.getLatitud(), gasto.getLongitud());
                String titulo = gasto.getDescripcion();
                if (titulo == null || titulo.trim().isEmpty()) {
                    titulo = gasto.getCategoria();
                }
                String snippet = String.format(Locale.getDefault(), "$%.2f — %s",
                        gasto.getMonto(), gasto.getFecha());
                mapa.addMarker(new MarkerOptions()
                        .position(posicion)
                        .title(titulo)
                        .snippet(snippet));
                if (primerMarcador == null) {
                    primerMarcador = posicion;
                }
            }
        }

        if (primerMarcador != null) {
            mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(primerMarcador, ZOOM_MARCADOR));
        } else {
            Toast.makeText(this, R.string.mapa_sin_datos, Toast.LENGTH_LONG).show();
            mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(SANTIAGO, ZOOM_CIUDAD));
        }
    }
}

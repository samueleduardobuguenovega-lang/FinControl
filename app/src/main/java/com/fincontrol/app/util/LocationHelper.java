package com.fincontrol.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Envoltorio chico sobre FusedLocationProviderClient, lo usa el formulario de gastos.
 * Maneja el flujo del permiso en runtime y entrega una sola ubicación (o null cuando
 * no hay permiso o no hay ningún fix disponible).
 *
 * <p>Cuando el equipo no tiene Google Play services (imágenes AOSP de emulador, equipos
 * Huawei, algunas ROMs) el proveedor fused no está disponible y toda llamada a él falla.
 * En vez de dejar que eso rompa el formulario, {@link #playServicesDisponible} lo detecta
 * una vez y el helper cae al {@link LocationManager} de la plataforma.</p>
 */
public final class LocationHelper {

    /** Recibe la ubicación, o null cuando no se pudo obtener. */
    public interface LocationCallback {
        void onLocation(Double latitud, Double longitud);
    }

    public static final int REQ_PERMISO_UBICACION = 3001;

    private static final String TAG = "LocationHelper";
    private static final long INTERVALO_MS = 1000L;
    private static final long TIEMPO_MAXIMO_MS = 15000L;

    // Solo hay un pedido de permiso en vuelo a la vez (una sola pantalla de formulario).
    private static WeakReference<Activity> actividadPendiente;
    private static WeakReference<LocationCallback> callbackPendiente;

    private LocationHelper() {
    }

    /**
     * Pide la ubicación actual del equipo. Solicita el permiso en runtime cuando falta;
     * si el usuario lo niega (o no hay fix) el callback recibe (null, null).
     */
    public static void fetchCurrentLocation(Activity activity, LocationCallback callback) {
        if (activity == null || callback == null) {
            return;
        }
        if (!tienePermiso(activity)) {
            actividadPendiente = new WeakReference<>(activity);
            callbackPendiente = new WeakReference<>(callback);
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_PERMISO_UBICACION);
            return;
        }
        obtenerUbicacion(activity, callback);
    }

    /**
     * Se llama desde onRequestPermissionsResult de la activity.
     *
     * @return true cuando el resultado es de este helper.
     */
    public static boolean onRequestPermissionsResult(int requestCode) {
        if (requestCode != REQ_PERMISO_UBICACION) {
            return false;
        }
        Activity activity = actividadPendiente == null ? null : actividadPendiente.get();
        LocationCallback callback = callbackPendiente == null ? null : callbackPendiente.get();
        actividadPendiente = null;
        callbackPendiente = null;
        if (callback == null) {
            return true;
        }
        if (activity == null || !tienePermiso(activity)) {
            callback.onLocation(null, null);
            return true;
        }
        obtenerUbicacion(activity, callback);
        return true;
    }

    /** true cuando hay permiso de ubicación fina o gruesa. */
    public static boolean tienePermiso(Activity activity) {
        return tienePermiso(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                || tienePermiso(activity, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private static boolean tienePermiso(Activity activity, String permiso) {
        return ContextCompat.checkSelfPermission(activity, permiso)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** true cuando Google Play services está instalado y sirve en este equipo o emulador. */
    public static boolean playServicesDisponible(Context context) {
        try {
            return GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
        } catch (RuntimeException e) {
            // En algunas imágenes AOSP no existen las clases de GMS: se toma como no disponible.
            Log.w(TAG, "Google Play services no disponible", e);
            return false;
        }
    }

    private static LocationCallback unaSolaVez(LocationCallback callback) {
        final AtomicBoolean entregado = new AtomicBoolean(false);
        return (latitud, longitud) -> {
            if (entregado.compareAndSet(false, true)) {
                callback.onLocation(latitud, longitud);
            }
        };
    }

    @SuppressLint("MissingPermission")
    private static void obtenerUbicacion(Activity activity, LocationCallback callback) {
        final LocationCallback entregaUnica = unaSolaVez(callback);
        if (!playServicesDisponible(activity)) {
            // Emulador sin Play services: usar el proveedor del sistema.
            obtenerUbicacionPlataforma(activity, entregaUnica);
            return;
        }
        final FusedLocationProviderClient cliente =
                LocationServices.getFusedLocationProviderClient(activity);
        // Un equipo bloqueado puede tener un last known location reciente; se prueba primero
        // para responder rápido.
        try {
            cliente.getLastLocation().addOnSuccessListener(activity, ubicacion -> {
                if (ubicacion != null) {
                    entregaUnica.onLocation(ubicacion.getLatitude(), ubicacion.getLongitude());
                }
            });
        } catch (SecurityException e) {
            entregaUnica.onLocation(null, null);
            return;
        }

        int prioridad = tienePermiso(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        LocationRequest solicitud = new LocationRequest.Builder(prioridad, INTERVALO_MS)
                .setMaxUpdates(1)
                .setDurationMillis(TIEMPO_MAXIMO_MS)
                .build();

        try {
            // Con nombre completo: este LocationCallback anidado tapa el nombre de la clase de GMS.
            final com.google.android.gms.location.LocationCallback escucha =
                    new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(LocationResult resultado) {
                            Location ubicacion = resultado == null
                                    ? null
                                    : resultado.getLastLocation();
                            if (ubicacion == null) {
                                return;
                            }
                            cliente.removeLocationUpdates(this);
                            entregaUnica.onLocation(
                                    ubicacion.getLatitude(), ubicacion.getLongitude());
                        }
                    };
            cliente.requestLocationUpdates(solicitud, escucha, Looper.getMainLooper());
            // No dejar la UI esperando para siempre: si el fix se pasa del tiempo,
            // se avisa que no hay ubicación.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                cliente.removeLocationUpdates(escucha);
                entregaUnica.onLocation(null, null);
            }, TIEMPO_MAXIMO_MS + 1000L);
        } catch (SecurityException e) {
            entregaUnica.onLocation(null, null);
        }
    }

    /**
     * Fallback para equipos o emuladores sin Google Play services: el último fix conocido de los
     * proveedores de la plataforma, o una sola actualización, o (null, null) pasado el timeout.
     */
    @SuppressLint("MissingPermission")
    private static void obtenerUbicacionPlataforma(Activity activity, LocationCallback callback) {
        LocationManager gestor =
                (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (gestor == null) {
            callback.onLocation(null, null);
            return;
        }

        Location ultima = null;
        for (String proveedor : new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location candidata = gestor.getLastKnownLocation(proveedor);
                if (candidata != null && (ultima == null || candidata.getTime() > ultima.getTime())) {
                    ultima = candidata;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // Falta el proveedor o revocaron el permiso en el camino: se prueba el siguiente.
                Log.d(TAG, "Proveedor " + proveedor + " no disponible", e);
            }
        }
        if (ultima != null) {
            callback.onLocation(ultima.getLatitude(), ultima.getLongitude());
            return;
        }

        final LocationListener escucha = new LocationListener() {
            @Override
            public void onLocationChanged(Location ubicacion) {
                if (ubicacion != null) {
                    callback.onLocation(ubicacion.getLatitude(), ubicacion.getLongitude());
                }
            }

            @Override
            public void onProviderDisabled(String proveedor) {
                callback.onLocation(null, null);
            }
        };

        try {
            String proveedor = gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER
                    : LocationManager.GPS_PROVIDER;
            gestor.requestLocationUpdates(proveedor, 0L, 0f, escucha, Looper.getMainLooper());
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    gestor.removeUpdates(escucha);
                } catch (RuntimeException ignored) {
                    // El proveedor ya no está: no hay nada que sacar.
                }
                callback.onLocation(null, null);
            }, TIEMPO_MAXIMO_MS + 1000L);
        } catch (SecurityException | IllegalArgumentException e) {
            callback.onLocation(null, null);
        }
    }
}

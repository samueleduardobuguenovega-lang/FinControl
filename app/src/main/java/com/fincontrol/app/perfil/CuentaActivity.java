package com.fincontrol.app.perfil;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.fincontrol.app.LoginActivity;
import com.fincontrol.app.R;
import com.fincontrol.app.auth.UserDao;
import com.fincontrol.app.model.User;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pantalla de cuenta (S16): el usuario edita su nombre, cambia la foto de perfil y elige el
 * tema de la app. Antes de esta pantalla el avatar y el saludo del dashboard eran solo
 * decoración.
 *
 * <p>Foto: la imagen elegida se decodifica reducida (máximo 512 px por lado) y se vuelve a
 * comprimir como JPEG (calidad 85) dentro de {@code filesDir/avatares/}, que es privado de la
 * app, así no se pide permiso de almacenamiento y nada sale del sandbox. El dashboard lee el
 * mismo archivo con {@link #archivoAvatar(Context, long)}.</p>
 */
public class CuentaActivity extends AppCompatActivity {

    private static final String TAG = "CuentaActivity";

    /** Carpeta privada de la app (dentro de filesDir) donde quedan las fotos de perfil. */
    private static final String DIR_AVATARES = "avatares";
    private static final String PREFIJO_AVATAR = "avatar_";
    private static final String EXT_AVATAR = ".jpg";

    /** Lado máximo que se conserva al guardar y mostrar el avatar. */
    private static final int LADO_MAX_AVATAR_PX = 512;
    private static final int CALIDAD_JPEG = 85;
    /** Padding interno del placeholder ic_person dentro de la tarjeta de 96dp. */
    private static final int PADDING_PLACEHOLDER_DP = 24;

    private SessionManager sesion;
    private UserDao userDao;

    private TextInputLayout campoNombreLayout;
    private TextInputEditText campoNombre;
    private ImageView ivAvatar;
    private MaterialButtonToggleGroup grupoTema;

    /** Photo Picker: sin permiso READ_MEDIA_IMAGES y sin acceso amplio a la galería. */
    private final ActivityResultLauncher<PickVisualMediaRequest> selectorFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    this::onFotoElegida);

    /**
     * Archivo del avatar de un usuario: {@code filesDir/avatares/avatar_<userId>.jpg}.
     *
     * <p>Es público y static a propósito: el dashboard ({@code MainActivity}) lee exactamente
     * el mismo path, así el nombre del archivo vive en un solo lugar y no se repite por
     * pantalla.</p>
     */
    public static File archivoAvatar(Context context, long userId) {
        File carpeta = new File(context.getApplicationContext().getFilesDir(), DIR_AVATARES);
        return new File(carpeta, PREFIJO_AVATAR + userId + EXT_AVATAR);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: la cabecera y las tarjetas no quedan bajo las barras del sistema.
        InsetsHelper.aplicar(this);

        sesion = new SessionManager(this);
        if (!sesion.haySesion()) {
            irALogin();
            return;
        }

        setContentView(R.layout.activity_cuenta);
        userDao = new UserDao(this);

        campoNombreLayout = findViewById(R.id.campo_nombre);
        campoNombre = findViewById(R.id.et_nombre);
        ivAvatar = findViewById(R.id.iv_avatar);
        grupoTema = findViewById(R.id.grupo_tema);

        campoNombre.setText(nombreActual());

        findViewById(R.id.card_avatar).setOnClickListener(v -> elegirFoto());
        findViewById(R.id.btn_cambio_foto).setOnClickListener(v -> elegirFoto());
        findViewById(R.id.btn_guardar_cambios).setOnClickListener(v -> guardarNombre());
        findViewById(R.id.btn_salir).setOnClickListener(v -> confirmarSalida());

        configurarTema();
        mostrarAvatar();
    }

    /** Lee el nombre de la base de datos y, si no hay, usa el de la sesión. */
    private String nombreActual() {
        User usuario = userDao.obtener(sesion.getUserId());
        String nombre = usuario == null ? null : usuario.getNombre();
        if (nombre == null || nombre.trim().isEmpty()) {
            return sesion.getNombre();
        }
        return nombre;
    }

    // ------------------------------------------------------------------ foto

    private void elegirFoto() {
        selectorFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** @param origen la imagen elegida, o null si el usuario cerró el selector. */
    private void onFotoElegida(Uri origen) {
        if (origen == null) {
            return; // Canceló: se mantiene la foto actual.
        }
        if (guardarFoto(origen)) {
            mostrarAvatar();
        } else {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Decodifica la imagen elegida reducida y la guarda como JPEG en el archivo del avatar.
     *
     * @return true si el archivo quedó escrito.
     */
    private boolean guardarFoto(Uri origen) {
        Bitmap foto = decodificar(this, origen, LADO_MAX_AVATAR_PX);
        if (foto == null) {
            Log.e(TAG, "No se pudo decodificar la imagen elegida");
            return false;
        }
        try {
            File destino = archivoAvatar(this, sesion.getUserId());
            File carpeta = destino.getParentFile();
            if (carpeta != null && !carpeta.isDirectory() && !carpeta.mkdirs()) {
                Log.e(TAG, "No se pudo crear la carpeta de avatares");
                return false;
            }
            try (OutputStream salida = new FileOutputStream(destino)) {
                return foto.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "No se pudo guardar el avatar", e);
                return false;
            }
        } finally {
            // La pantalla vuelve a decodificar desde el archivo, esta copia ya no sirve.
            foto.recycle();
        }
    }

    /** Muestra el avatar guardado o el placeholder ic_person si todavía no hay foto. */
    private void mostrarAvatar() {
        File archivo = archivoAvatar(this, sesion.getUserId());
        Bitmap foto = archivo.isFile() ? decodificar(this, Uri.fromFile(archivo), LADO_MAX_AVATAR_PX) : null;
        if (foto == null) {
            // Placeholder: ic_person con tinte onPrimaryContainer sobre la tarjeta.
            int padding = Math.round(PADDING_PLACEHOLDER_DP * getResources().getDisplayMetrics().density);
            ivAvatar.setImageTintList(colorOnPrimaryContainer());
            ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivAvatar.setPadding(padding, padding, padding, padding);
            ivAvatar.setImageResource(R.drawable.ic_person);
            return;
        }
        // Foto real: llena la tarjeta circular, sin tinte, sin padding, recorte al centro.
        ivAvatar.setImageTintList(null);
        ivAvatar.setPadding(0, 0, 0, 0);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setImageBitmap(foto);
    }

    private ColorStateList colorOnPrimaryContainer() {
        return ContextCompat.getColorStateList(this, R.color.on_primary_container);
    }

    /**
     * Decodifica un URI de imagen acotando la memoria: primero lee los límites, después
     * decodifica con {@code inSampleSize} y escala para que el lado mayor no pase de
     * {@code ladoMax}.
     *
     * @return el bitmap, o null cuando no se puede leer la imagen.
     */
    private static Bitmap decodificar(Context contexto, Uri origen, int ladoMax) {
        ContentResolver resolutor = contexto.getContentResolver();

        BitmapFactory.Options limites = new BitmapFactory.Options();
        limites.inJustDecodeBounds = true;
        try (InputStream entrada = resolutor.openInputStream(origen)) {
            if (entrada == null) {
                return null;
            }
            BitmapFactory.decodeStream(entrada, null, limites);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "No se pudo leer la imagen", e);
            return null;
        }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = factorMuestreo(limites.outWidth, limites.outHeight, ladoMax);
        Bitmap decodificado;
        try (InputStream entrada = resolutor.openInputStream(origen)) {
            if (entrada == null) {
                return null;
            }
            decodificado = BitmapFactory.decodeStream(entrada, null, opciones);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "No se pudo decodificar la imagen", e);
            return null;
        }
        if (decodificado == null) {
            return null;
        }
        return escalar(decodificado, ladoMax);
    }

    private static int factorMuestreo(int ancho, int alto, int ladoMax) {
        int factor = 1;
        int mayor = Math.max(ancho, alto);
        while (mayor / (factor * 2) >= ladoMax) {
            factor *= 2;
        }
        return factor;
    }

    private static Bitmap escalar(Bitmap original, int ladoMax) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        int mayor = Math.max(ancho, alto);
        if (mayor <= ladoMax) {
            return original;
        }
        float proporcion = (float) ladoMax / mayor;
        Bitmap escalado = Bitmap.createScaledBitmap(original,
                Math.max(1, Math.round(ancho * proporcion)),
                Math.max(1, Math.round(alto * proporcion)), true);
        if (escalado != original) {
            original.recycle();
        }
        return escalado;
    }

    // ------------------------------------------------------------------ nombre

    private void guardarNombre() {
        String nombre = campoNombre.getText() == null ? "" : campoNombre.getText().toString().trim();
        if (nombre.isEmpty()) {
            campoNombreLayout.setError(getString(R.string.error_campo_vacio));
            return;
        }
        campoNombreLayout.setError(null);

        long usuarioId = sesion.getUserId();
        if (!userDao.actualizarNombre(usuarioId, nombre)) {
            Toast.makeText(this, R.string.error_inesperado, Toast.LENGTH_LONG).show();
            return;
        }
        // Actualiza la copia de la sesión con el mismo id para que el saludo cambie al volver.
        sesion.iniciarSesion(usuarioId, nombre);
        Toast.makeText(this, R.string.perfil_actualizado, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ tema

    private void configurarTema() {
        // Marca el valor guardado ANTES de escuchar, así abrir la pantalla no escribe nada.
        grupoTema.check(idDeTema(TemaPreferencias.obtener(this)));
        grupoTema.addOnButtonCheckedListener((grupo, marcadoId, marcado) -> {
            if (!marcado) {
                return; // singleSelection: solo importa el botón que se acaba de marcar.
            }
            String valor = temaDeId(marcadoId);
            if (valor == null) {
                return;
            }
            TemaPreferencias.guardar(this, valor);
            // Se aplica de inmediato: AppCompatDelegate recrea esta activity en el modo nuevo.
            TemaPreferencias.aplicarTemaGuardado(this);
        });
    }

    private static int idDeTema(String valor) {
        if (TemaPreferencias.CLARO.equals(valor)) {
            return R.id.btn_tema_claro;
        }
        if (TemaPreferencias.OSCURO.equals(valor)) {
            return R.id.btn_tema_oscuro;
        }
        return R.id.btn_tema_sistema;
    }

    private static String temaDeId(int id) {
        if (id == R.id.btn_tema_claro) {
            return TemaPreferencias.CLARO;
        }
        if (id == R.id.btn_tema_oscuro) {
            return TemaPreferencias.OSCURO;
        }
        if (id == R.id.btn_tema_sistema) {
            return TemaPreferencias.SISTEMA;
        }
        return null;
    }

    // ------------------------------------------------------------------ salida

    private void confirmarSalida() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cuenta_titulo)
                .setMessage(R.string.cuenta_salir_confirmar)
                // "Cerrar sesión" ya existe como btn_cerrar_sesion (label del dashboard),
                // así el diálogo reutiliza esa clave en vez de un literal escrito a mano.
                .setPositiveButton(R.string.btn_cerrar_sesion, (dialogo, cual) -> {
                    sesion.cerrarSesion();
                    irALogin();
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    private void irALogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}

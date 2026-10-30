package com.fincontrol.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fincontrol.app.auth.UserDao;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Pantalla de registro: valida el formulario y crea la cuenta. */
public class RegisterActivity extends AppCompatActivity {

    private static final int PASSWORD_MIN_LENGTH = 4;

    private TextInputLayout layoutNombre;
    private TextInputLayout layoutUsuario;
    private TextInputLayout layoutPassword;
    private TextInputEditText inputNombre;
    private TextInputEditText inputUsuario;
    private TextInputEditText inputPassword;

    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja la pantalla libre de las barras de estado y navegación.
        InsetsHelper.aplicar(this);
        setContentView(R.layout.activity_register);

        userDao = new UserDao(this);
        layoutNombre = findViewById(R.id.layout_nombre);
        layoutUsuario = findViewById(R.id.layout_usuario);
        layoutPassword = findViewById(R.id.layout_password);
        inputNombre = findViewById(R.id.et_nombre);
        inputUsuario = findViewById(R.id.et_usuario);
        inputPassword = findViewById(R.id.et_password);

        MaterialButton btnRegistrar = findViewById(R.id.btn_registrar);
        btnRegistrar.setOnClickListener(v -> registrar());

        findViewById(R.id.tv_link_login).setOnClickListener(v -> finish());
    }

    /** Valida el formulario y guarda el usuario nuevo. */
    private void registrar() {
        layoutNombre.setError(null);
        layoutUsuario.setError(null);
        layoutPassword.setError(null);

        String nombre = texto(inputNombre).trim();
        String usuario = texto(inputUsuario).trim();
        String password = texto(inputPassword);

        boolean valido = true;
        if (nombre.isEmpty()) {
            layoutNombre.setError(getString(R.string.error_campo_vacio));
            valido = false;
        }
        if (usuario.isEmpty()) {
            layoutUsuario.setError(getString(R.string.error_campo_vacio));
            valido = false;
        }
        if (password.isEmpty()) {
            layoutPassword.setError(getString(R.string.error_campo_vacio));
            valido = false;
        }
        if (valido && password.length() < PASSWORD_MIN_LENGTH) {
            layoutPassword.setError(getString(R.string.error_password_corta));
            valido = false;
        }
        if (!valido) {
            return;
        }

        if (!userDao.insertarUsuario(usuario, password, nombre)) {
            layoutUsuario.setError(getString(R.string.error_usuario_existe));
            return;
        }

        Toast.makeText(this, "Cuenta creada. Ahora inicia sesión.", Toast.LENGTH_LONG).show();
        finish();
    }

    private static String texto(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}

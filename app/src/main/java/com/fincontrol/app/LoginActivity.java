package com.fincontrol.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fincontrol.app.auth.UserDao;
import com.fincontrol.app.model.User;
import com.fincontrol.app.session.SessionManager;
import com.fincontrol.app.util.InsetsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Pantalla de login: valida el formulario, autentica y abre el panel. */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout layoutUsuario;
    private TextInputLayout layoutPassword;
    private TextInputEditText inputUsuario;
    private TextInputEditText inputPassword;

    private UserDao userDao;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-edge: deja la pantalla libre de las barras de estado y navegación.
        InsetsHelper.aplicar(this);

        session = new SessionManager(this);
        if (session.haySesion()) {
            // Si ya hay sesión no se muestra el login.
            irAInicio();
            return;
        }

        setContentView(R.layout.activity_login);

        userDao = new UserDao(this);
        layoutUsuario = findViewById(R.id.layout_usuario);
        layoutPassword = findViewById(R.id.layout_password);
        inputUsuario = findViewById(R.id.et_usuario);
        inputPassword = findViewById(R.id.et_password);

        MaterialButton btnIngresar = findViewById(R.id.btn_ingresar);
        btnIngresar.setOnClickListener(v -> ingresar());

        findViewById(R.id.tv_link_registro).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    /** Valida el formulario y autentica al usuario. */
    private void ingresar() {
        layoutUsuario.setError(null);
        layoutPassword.setError(null);

        String usuario = texto(inputUsuario).trim();
        String password = texto(inputPassword);

        boolean valido = true;
        if (usuario.isEmpty()) {
            layoutUsuario.setError(getString(R.string.error_campo_vacio));
            valido = false;
        }
        if (password.isEmpty()) {
            layoutPassword.setError(getString(R.string.error_campo_vacio));
            valido = false;
        }
        if (!valido) {
            return;
        }

        User user = userDao.autenticar(usuario, password);
        if (user == null) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.error_credenciales, Snackbar.LENGTH_LONG).show();
            return;
        }

        session.iniciarSesion(user.getId(), user.getNombre());
        irAInicio();
    }

    /** Abre el panel y cierra el login. */
    private void irAInicio() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static String texto(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}

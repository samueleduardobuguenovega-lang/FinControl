package com.fincontrol.app;

import android.app.Application;

import com.fincontrol.app.perfil.TemaPreferencias;

/**
 * Enganche de la Application: aplica el tema claro/oscuro guardado al arrancar
 * el proceso, para que la elección del usuario se respete desde la primera
 * pantalla y no solo al abrir la pantalla de cuenta.
 */
public class FinControlApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TemaPreferencias.aplicarTemaGuardado(this);
    }
}

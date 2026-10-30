package com.fincontrol.app.model;

/** Modelo de usuario. La contraseña se guarda tal cual en la v1 (ver security_tips.md). */
public class User {
    private final long id;
    private final String usuario;
    private final String password;
    private final String nombre;

    public User(long id, String usuario, String password, String nombre) {
        this.id = id;
        this.usuario = usuario;
        this.password = password;
        this.nombre = nombre;
    }

    public long getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getPassword() { return password; }
    public String getNombre() { return nombre; }
}

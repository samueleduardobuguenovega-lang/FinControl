# Security Tips implementados — FinControl

Documento requerido por la Actividad 2.5: detalle de los **Consejos de Seguridad** (Security Tips, alineados con OWASP MASVS y las recomendaciones de Google) implementados en la aplicación y cómo cada uno mejora su seguridad.

| # | Security Tip | Estado |
|---|--------------|--------|
| ST1 | Protección contra inyección SQL | ✅ Implementada |
| ST2 | Autenticación y autorización seguras | ✅ Implementada |
| ST3 | Protección contra ataques de red (MITM) | ✅ Implementada |
| ST4 | Datos por usuario aislados en cada consulta | ✅ Implementada |
| ST5 | Componentes exportados mínimos | ✅ Implementada |

---

## ST1 — Protección contra inyección SQL

**Qué se implementó**
- **Todas** las consultas del proyecto usan consultas parametrizadas (`selectionArgs` / `ContentValues`), nunca concatenación de cadenas:
  - `UserDao` (login, registro, existe usuario),
  - `SuscripcionDao` (listar, insertar, activar/pausar, total, próximos 7 días, por día de cobro),
  - `GastoDao` (insertar, listar por usuario, total del mes).
- En la versión v1 existían deliberadamente dos inyecciones (login y total del mes) como línea base de análisis; ver `vulnerabilities.md` V1/V2 y el commit de corrección `8ff0560`.

**Cómo mejora la seguridad**
- El motor de SQLite recibe la estructura de la consulta y los datos por canales separados: el input del usuario nunca puede cambiar la semántica de la consulta (neutraliza `' OR '1'='1`, comentarios `--`, `UNION`, etc.).

## ST2 — Autenticación y autorización seguras

**Qué se implementó**
- Contraseñas solo como **hash PBKDF2 salado** (ver BP1); nunca se compara texto plano y la comparación usa `MessageDigest.isEqual` (tiempo constante).
- La sesión se crea solo tras verificar el hash y vive cifrada en el dispositivo; el id del usuario se usa para **aislar datos** (ST4).
- Cierre de sesión que borra el estado cifrado, y arranque directo al dashboard solo si existe sesión válida (`LoginActivity` / `MainActivity`).
- Almacenamiento falla **cerrado en lo que importa**: si el almacén cifrado no puede abrirse, **no se escribe nada en texto plano**; la sesión vive en memoria mientras la app corre y se pierde al cerrarla (`SessionManager`, degradación añadida para que un emulador sin Keystore no tumbe el arranque).

**Cómo mejora la seguridad**
- El robo de la base de datos no revela credenciales; el robo de preferencias no suplanta la sesión; la verificación en tiempo constante evita ataques de temporización sobre la comparación de hashes.

## ST3 — Protección contra ataques de red (MITM)

**Qué se implementó**
- `networkSecurityConfig` con tráfico en claro **desactivado** globalmente (ver BP2).
- Comunicaciones remotas delegadas en Google Maps y Google Play Services (TLS obligatorio con los certificados del sistema).

**Cómo mejora la seguridad**
- Un atacante en la misma red no puede forzar downgrade a HTTP ni interceptar tráfico: el sistema rechaza la conexión en claro y TLS valida la identidad del servidor.

## ST4 — Aislamiento de datos por usuario

**Qué se implementó**
- Cada consulta de gastos y suscripciones filtra por `usuario_id` de la sesión activa; el receptor de recordatorios (sin sesión) consulta solo por día de cobro y muestra el nombre de la suscripción, nunca datos ajenos al dueño.
- El dashboard, la lista de gastos y el mapa solo muestran datos del usuario autenticado.

**Cómo mejora la seguridad**
- Mitiga *IDOR* (referencia directa a objetos): aunque se manipule el UI, no hay ruta de código que lea datos de otro usuario.

## ST5 — Componentes exportados mínimos

**Qué se implementó**
- Solo `LoginActivity` está `exported="true"` (es el punto de entrada); el resto de activities y el `ReminderReceiver` son `exported="false"` (el receiver recibe solo el broadcast protegido del sistema `BOOT_COMPLETED`).

**Cómo mejora la seguridad**
- Otras apps no pueden invocar pantallas internas ni disparar el receptor con intents forjados; la superficie de ataque de componentes queda en el mínimo.

---

## Verificación

- ST1/ST2: `git grep -n "rawQuery\|+ \" '" -- app/src/main/java` no devuelve concatenación de input en SQL en el HEAD; `git show 8ff0560` muestra la corrección completa.
- ST3: descompilar el manifiesto (`aapt dump xmltree app-debug.apk AndroidManifest.xml`) muestra `networkSecurityConfig` y `allowBackup=false`.
- ST4/ST5: revisión de `AndroidManifest.xml` y de los métodos `listarPorUsuario`/`totalDelMes`/`totalActivas`.

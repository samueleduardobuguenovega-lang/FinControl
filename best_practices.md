# Best Practices implementadas — FinControl

Documento requerido por la Actividad 2.5: detalle de las **Buenas Prácticas** implementadas en la aplicación y cómo cada una mejora su seguridad.

| # | Best Practice | Estado |
|---|---------------|--------|
| BP1 | Cifrado de datos sensibles (contraseñas y sesión) | ✅ Implementada |
| BP2 | Comunicación de red segura (HTTPS) | ✅ Implementada |
| BP3 | Validación y sanitización de todas las entradas del usuario | ✅ Implementada |
| BP4 | Claves fuera del código fuente (gestión de secretos) | ✅ Implementada |
| BP5 | Deshabilitar backups y extracción de datos | ✅ Implementada |

---

## BP1 — Cifrado de datos sensibles

**Qué se implementó**
- Contraseñas: **PBKDF2WithHmacSHA256** (150.000 iteraciones, sal aleatoria de 16 bytes por usuario, comparación en tiempo constante) — `app/src/main/java/com/fincontrol/app/auth/UserDao.java`.
- Sesión del usuario: **EncryptedSharedPreferences** con esquemas AES-256-SIV (claves) y AES-256-GCM (valores), llave maestra **AES256-GCM en Android Keystore** — `app/src/main/java/com/fincontrol/app/session/SessionManager.java`.

**Cómo mejora la seguridad**
- Un dump del archivo `fincontrol.db` ya no revela contraseñas: PBKDF2 hace computacionalmente inviable el crackeo a escala (la sal evita tablas rainbow y ataques por lotes).
- Las preferencias de sesión están cifradas en reposo: leer `/data/data/com.fincontrol.app/shared_prefs/` (root o extracción) devuelve bytes cifrados, no el id/estado de sesión.
- La llave maestra vive en el Keystore respaldado por hardware (cuando existe), no en el APK.

## BP2 — Comunicación de red segura (HTTPS)

**Qué se implementó**
- `res/xml/network_security_config.xml` con `cleartextTrafficPermitted="false"` en el `base-config`, referenciado en el manifiesto (`android:networkSecurityConfig`).
- Toda la comunicación remota (Google Maps SDK, Google Play Services) viaja por TLS 1.2+ obligatorio del stack de GMS.

**Cómo mejora la seguridad**
- Se bloquea a nivel de plataforma cualquier conexión `http://` (hoy o en el futuro): neutraliza ataques de **hombre en el medio (MITM)** en redes abiertas.
- Un hallazgo clásico de MobSF ("app permite tráfico en claro") desaparece del informe.

## BP3 — Validación y sanitización de entradas

**Qué se implementó**
- Todos los campos de formulario se normalizan (`trim()`) antes de usarse.
- Validación en capa de presentación con mensajes específicos (`strings.xml`): monto numérico > 0, fecha con formato `AAAA-MM-DD` (regex), día de cobro 1–31, contraseña ≥ 4 caracteres, campos no vacíos — `LoginActivity`, `RegisterActivity`, `GastoFormActivity`, `SuscripcionFormActivity`.
- El acceso a datos no interpola nunca entradas en SQL (ver `security_tips.md`).

**Cómo mejora la seguridad**
- Reduce la superficie de inyección y de datos corruptos: las entradas inválidas se rechazan antes de llegar a la capa de persistencia.
- Los mensajes de error no revelan detalles internos (no se exponen mensajes de SQLite al usuario).

## BP4 — Clave de API fuera del código y del repositorio

**Qué se implementó**
- La clave de Google Maps **no vive en el código ni en el manifiesto**: `app/build.gradle.kts` la lee de `local.properties` (archivo local, ignorado por Git) y la inyecta como recurso `google_maps_key` al compilar. El manifiesto solo referencia `@string/google_maps_key`.
  - **Actualización de entrega (30/10):** se añadió una clave de respaldo versionada en `app/build.gradle.kts`, limitada en Google Cloud a `com.fincontrol.app`, para que el proyecto descargado sin configuración muestre el mapa (la disciplina de inyección y restricción se mantiene; la clave de respaldo debe rotarse si el repo se difunde).
- La clave está restringida en Google Cloud por nombre de paquete (`com.fincontrol.app`) y por huella SHA-1 del certificado de firma, así que no funciona desde otra aplicación ni desde otro equipo.
- Historia: la v1 tenía la clave literal en el manifiesto y en el repositorio (V4); la v2 la movió a `local.properties` (commit `8ff0560`); la versión publicada mantiene esa misma disciplina con el SDK de Google Maps (commit `d666190`).

**Cómo mejora la seguridad**
- Clonar el repositorio no entrega ninguna credencial utilizable: el historial de Git no contiene la clave.
- Si una clave se filtra, se restringe o se rota en Google Cloud sin tocar el código ni volver a compilar.
- La clave sí viaja dentro del APK —es inevitable con Maps SDK—, pero restringida por paquete y por firma: extraerla no permite usarla desde otra aplicación.

---


## BP5 — Sin backups ni extracción de datos

**Qué se implementó**
- `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"` y `android:dataExtractionRules="@xml/data_extraction_rules"` (excluyen base de datos y preferencias de *cloud backup* y *device transfer*).

**Cómo mejora la seguridad**
- Los datos financieros y la sesión no pueden extraerse vía backup de Google, `adb backup` ni transferencia a otro dispositivo, cerrando el vector de suplantación de sesión documentado en `vulnerabilities.md` (V5).

---

## Cómo verificar cada práctica

- BP1: `git show 8ff0560 -- app/src/main/java/com/fincontrol/app/auth/UserDao.java` (hash PBKDF2) y `SessionManager.java` (EncryptedSharedPreferences).
- BP2/BP5: inspeccionar el `AndroidManifest.xml` y `res/xml/*.xml` del HEAD.
- BP3: intentar registrar un gasto con monto `0`, fecha `31/12/2026` o día de cobro `45`: la app lo rechaza con mensaje específico.
- BP4: `git grep -i AIza` no devuelve ninguna clave en el repositorio.

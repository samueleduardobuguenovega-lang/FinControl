# Vulnerabilidades identificadas — FinControl

Análisis de seguridad del proyecto **FinControl** (Actividad 2.5 — Manejo de elementos de seguridad).

> **Nota sobre los commits citados:** los hashes (`43e9135`, `8ff0560`, etc.) corresponden al historial de Git del clon local del equipo, donde se conserva el antes y el después completo (verificable con `git show`). El 30/10/2026 el repositorio público de GitHub se consolidó con un reset (commit `3ebc3e4`) para blindar la clave de Google Maps expuesta en la v1, por lo que la comparación antes/después verificable por quien consulta este repositorio se apoya en este documento y en [`best_practices.md`](best_practices.md), [`security_tips.md`](security_tips.md) y [`security_improvement_program.md`](security_improvement_program.md).

- **App:** FinControl — control de gastos y suscripciones (Android, Java, minSdk 30).
- **Método:** análisis **estático** de código (revisión manual asistida + verificación estructural automatizada del APK y de las fuentes) sobre la **versión inicial (v1, commit `43e9135` y anteriores)**, seguido de re-análisis tras la corrección (v2, commit `8ff0560`).
- **Reproducción con MobSF:** instrucciones al final. Cada vulnerabilidad indica el archivo, la línea de comportamiento y cómo se corrigió.

---

## Resumen

| ID | Vulnerabilidad | Severidad | Estado |
|----|----------------|-----------|--------|
| V1 | Inyección SQL en login (consulta concatenada) | **Alta** | ✅ Corregida |
| V2 | Inyección SQL en total del mes (consulta concatenada) | **Alta** | ✅ Corregida |
| V3 | Contraseñas almacenadas en texto plano | **Alta** | ✅ Corregida |
| V4 | Clave de Google Maps incrustada en el código | **Media** | ✅ Corregida |
| V5 | Backup habilitado + sesión sin cifrar (exposición de datos) | **Media** | ✅ Corregida |
| V6 | Tráfico en claro permitido (sin `networkSecurityConfig`) | **Media** | ✅ Corregida |

---

## V1 — Inyección SQL en el login (Alta)

- **Ubicación (v1):** `app/src/main/java/com/fincontrol/app/auth/UserDao.java`, método `autenticar()`.
- **Código vulnerable (v1):**
  ```java
  String sql = "SELECT ... WHERE usuario = '" + usuario + "' AND password = '" + password + "'";
  cursor = db.rawQuery(sql, null);
  ```
- **Prueba de concepto:** ingresando `pepe'--` como usuario, la consulta resultante era
  `... WHERE usuario = 'pepe'--' AND password = '...'` — el `--` comenta la validación de contraseña y el atacante entra con **cualquier valor** en el campo contraseña. Con `x' OR '1'='1` en el campo contraseña se listaba el primer usuario.
- **Impacto:** bypass de autenticación total; un atacante con acceso al dispositivo (o a una build redistribuida) entra como cualquier usuario. MobSF reporta este patrón como *SQL Injection* (CWE-89).
- **Corrección (v2):** consulta parametrizada con `selectionArgs` + verificación del hash: el input ya no puede alterar la estructura del SQL. Ver commit `8ff0560`.

## V2 — Inyección SQL en el total del mes (Alta)

- **Ubicación (v1):** `app/src/main/java/com/fincontrol/app/gastos/GastoDao.java`, método `totalDelMes()`.
- **Código vulnerable (v1):**
  ```java
  String sql = "SELECT SUM(monto) FROM gastos WHERE usuario_id = " + usuarioId
             + " AND fecha LIKE '" + mesAAAAMM + "%'";
  ```
- **Prueba de concepto:** el parámetro `mesAAAAMM` provenía de `java.time` en el dashboard (no de input del usuario), pero el patrón de concatenación es la misma debilidad de V1: cualquier refactor que conecte ese parámetro con datos de entrada reactiva la inyección. MobSF lo clasifica igual (CWE-89).
- **Corrección (v2):** `db.query(..., "usuario_id = ? AND fecha LIKE ? || '%'", new String[]{...})`. Ver commit `8ff0560`.

## V3 — Contraseñas en texto plano (Alta)

- **Ubicación (v1):** `UserDao.insertarUsuario()` — la columna `usuarios.password` guardaba la contraseña tal cual.
- **Impacto:** quien obtenga el archivo `fincontrol.db` (root, backup USB, APK con datos de prueba) conoce todas las contraseñas. Además, la verificación comparaba texto plano, amplificando V1.
- **Corrección (v2):** hash **PBKDF2WithHmacSHA256** con sal aleatoria de 16 bytes por usuario (`SecureRandom`), 150.000 iteraciones, comparación en tiempo constante (`MessageDigest.isEqual`). Ver commit `8ff0560`.

## V4 — Clave de Google Maps incrustada en el manifiesto (Media)

- **Ubicación (v1):** `app/src/main/AndroidManifest.xml`, `meta-data com.google.android.geo.API_KEY` con el valor literal en el repositorio.
- **Impacto:** la clave queda en el control de versiones y en el APK (extraíble con `aapt`/MobSF *Har*delivery de la API). Puede consumir cuota y facturación de Google Cloud.
- **Corrección (v2):** la clave se inyectó en tiempo de build desde `local.properties` (commit `8ff0560`) y se restringió en Google Cloud por paquete y huella SHA-1. La versión publicada conserva Google Maps con esa disciplina: el manifiesto referencia `@string/google_maps_key` y el valor se genera al compilar (commit `d666190`).
  - **Actualización de entrega (30/10):** se añadió en `app/build.gradle.kts` una clave de respaldo versionada, limitada en Google Cloud a `com.fincontrol.app`, para que el clon del docente compile y muestre el mapa sin configuración previa. La inyección por `local.properties` sigue disponible (prevalece si existe). La clave de respaldo debe rotarse si se difunde el repositorio más allá de esta entrega.

## V5 — Backup habilitado + sesión sin cifrar (Media)

- **Ubicación (v1):** `AndroidManifest.xml` con `android:allowBackup="true"` (por defecto) y `SessionManager` usando `SharedPreferences` en texto plano.
- **Impacto:** la base de datos con gastos, suscripciones y credenciales, más la preferencia de sesión, viajaban a la nube del dispositivo o a un `adb backup` y podían restaurarse en otro dispositivo, exponiendo datos financieros y suplantando la sesión.
- **Corrección (v2):** `allowBackup="false"` + reglas `backup_rules.xml` / `data_extraction_rules.xml` (excluyen base de datos y preferencias de cloud backup y device transfer) + sesión en `EncryptedSharedPreferences` (AES-256-GCM con llave maestra en Android Keystore). Ver commit `8ff0560`.

## V6 — Tráfico en claro permitido (Media)

- **Ubicación (v1):** ausencia de `android:networkSecurityConfig` en el manifiesto.
- **Impacto:** cualquier dependencia (o código futuro) podía abrir conexiones `http://`; un atacante en la misma red (MITM) interceptaría o alteraría los datos. MobSF marca la ausencia de esta configuración como hallazgo.
- **Corrección (v2):** `res/xml/network_security_config.xml` con `cleartextTrafficPermitted="false"` en el `base-config`: todo el tráfico (Google Play Services / Maps) es TLS obligatorio. Ver commit `8ff0560`.

---

## Reproducción del análisis con MobSF

1. Instalar MobSF: `pip install mobsfscan` (análisis estático) o `docker run -it --rm -p 8000:8000 opensecurity/mobile-security-framework-mobsf` (consola web completa).
2. **Estático:** generar el APK de la v1 (`git checkout 43e9135 && ./gradlew assembleDebug`) y subir `app/build/outputs/apk/debug/app-debug.apk` a `http://localhost:8000`. El informe señalará inyección SQL, `allowBackup=true`, ausencia de `networkSecurityConfig` y la clave de API en el manifiesto.
3. **Dinámico:** conectar un emulador/dispositivo, usar la pestaña *Dynamic Analysis* de MobSF, y repetir la prueba de concepto de V1 en la pantalla de login de la v1.
4. **Repetir con la v2** (HEAD): los seis hallazgos desaparecen (V4 permanece como aviso informativo de gestión de claves, que se resuelve con la clave propia en `local.properties`).

El reporte formal de las pruebas se encuentra en `vulnerability_report.pdf`.

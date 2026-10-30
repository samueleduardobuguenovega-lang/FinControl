# FinControl

Aplicación Android (Java) de **control de gastos y suscripciones** que implementa medidas de seguridad para proteger contra vulnerabilidades comunes. Desarrollada como entregable de la Actividad 2.5: *Manejo de elementos de seguridad* (Programación Android, IEI-093 U2 S10).

## Descripción

FinControl permite:

- **Registrar usuarios** con inicio de sesión (contraseñas cifradas con PBKDF2, sal por usuario).
- **Registrar gastos** con monto, categoría, fecha, descripción y **ubicación actual** (Google Play Services Location).
- **Gestionar suscripciones y pagos mensuales** con **recordatorio diario por notificación** (AlarmManager, 09:00) del día de cobro.
- **Dashboard** con total gastado del mes, total de suscripciones activas y próximos cobros (7 días).
- **Mapa de gastos** con un marcador por gasto que tenga ubicación, sobre **Google Maps** (Maps SDK for Android).

## Vulnerabilidades Identificadas

En la versión inicial (v1, commits `6f1700c`–`43e9135`) el análisis de seguridad identificó:

- Inyección SQL en el login (consulta concatenada)
- Inyección SQL en el total mensual (consulta concatenada)
- Contraseñas almacenadas en texto plano
- Clave de Google Maps incrustada en el código
- Backup habilitado + sesión sin cifrar
- Tráfico en claro permitido (sin `networkSecurityConfig`)

Detalle completo, pruebas de concepto y correcciones: [vulnerabilities.md](vulnerabilities.md). Reporte formal: [vulnerability_report.pdf](vulnerability_report.pdf).

## Mejoras Implementadas

- Cifrado de datos sensibles (PBKDF2 para contraseñas, EncryptedSharedPreferences + Android Keystore para la sesión)
- Comunicación segura (HTTPS obligatorio vía `networkSecurityConfig`)
- Validación y sanitización de todas las entradas del usuario
- Consultas SQL 100% parametrizadas (anti-inyección)
- Clave de Google Maps fuera del repositorio: se lee de `local.properties` y se inyecta como recurso al compilar
- Backups y extracción de datos deshabilitados
- Componentes exportados al mínimo (solo el launcher)

Detalle: [best_practices.md](best_practices.md) y [security_tips.md](security_tips.md). Programa de mejora continua: [security_improvement_program.md](security_improvement_program.md).

## Documentación

- [Vulnerabilidades](vulnerabilities.md)
- [Best Practices](best_practices.md)
- [Security Tips](security_tips.md)
- [Security Improvement Program](security_improvement_program.md)

## Cómo abrir y ejecutar en Android Studio

### Requisitos

- **Android Studio** (cualquier versión de los últimos ~18 meses). El proyecto fija **AGP 8.13.2** y **Gradle 8.14.6** en el wrapper: no hace falta instalar Gradle a mano, y no exige un IDE de última generación.
- **JDK 17**, el que trae Android Studio. Deja `Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle ▸ Gradle JDK` en *Embedded JDK*.
- **Android SDK Platform 36** (Android 16). Si falta, el IDE lo ofrece al hacer sync, o se instala desde `Tools ▸ SDK Manager`.
- **Emulador o dispositivo con API 30 o superior** (Android 11+). Con una API menor el install falla con `INSTALL_FAILED_OLDER_SDK`.

### Pasos

1. Clonar el repositorio y abrir **la carpeta raíz** (donde está `settings.gradle.kts`) con `File ▸ Open`.
2. Esperar el *Gradle sync*. La primera vez descarga AGP, Gradle 8.14.6 y las dependencias (unos minutos).
3. `Run ▶ app` sobre el emulador o el teléfono conectado.

Hay que agregar la clave de Google Maps al `local.properties` (archivo local, no versionado), con la línea `MAPS_API_KEY=AIza...`. Sin clave la aplicación funciona completa salvo el mapa, que queda en blanco. La clave se restringe en Google Cloud por paquete (`com.fincontrol.app`) y huella SHA-1 del certificado.

### Por línea de comandos

```bash
./gradlew assembleDebug          # APK de depuración
./gradlew testDebugUnitTest      # tests unitarios (JUnit + Robolectric)
./gradlew assembleRelease        # APK de release (R8 + proguard-rules.pro)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Si algo falla

| Síntoma | Causa y solución |
| --- | --- |
| `This project uses AndroidX dependencies, but the 'android.useAndroidX' property is not enabled` | Falta `android.useAndroidX=true` en `gradle.properties`. Ya está incluido; si el error aparece, el archivo se sobrescribió. |
| `Failed to find target with hash string 'android-36'` | Falta la plataforma: `Tools ▸ SDK Manager ▸ Android 16 (API 36)`. |
| `INSTALL_FAILED_OLDER_SDK` | El emulador es anterior a API 30: crear uno con API 30 o superior. |
| `Unsupported class file major version` o `invalid source release` | El *Gradle JDK* no es 17: cambiarlo en la configuración de Gradle. |
| La app crashea al abrir en el emulador | Corregido en esta versión: si el Keystore del emulador no puede abrir el almacén cifrado, la sesión pasa a memoria en vez de matar el proceso (`SessionManager`). |
| El mapa queda en blanco | Falta `MAPS_API_KEY` en `local.properties` o la clave no autoriza ese paquete y esa huella SHA-1. |
| "No hay ubicación" al guardar un gasto | En el emulador, fijar coordenadas en `Extended Controls ▸ Location`; sin Google Play services la app usa el `LocationManager` del sistema. |

### Dependencias declaradas

Todas las librerías que el código importa están fijadas en `gradle/libs.versions.toml` y declaradas en `app/build.gradle.kts` (nada se resuelve por transitividad, que es lo que hacía que el proyecto compilara distinto en otra máquina):

- AndroidX: `annotation`, `core`, `appcompat`, `activity-ktx`, `recyclerview`, `constraintlayout`
- Material Components (`com.google.android.material:material`)
- `androidx.security:security-crypto` (sesión cifrada)
- Google Play services: `play-services-location` y `play-services-base` (detección de GMS)
- `com.google.android.gms:play-services-maps` (mapa)
- Tests: JUnit 4, Robolectric, Espresso y `androidx.test.ext:junit`

La versión inicial con la clave incrustada en el código quedó conservada en el tag `legacy-google-maps`, solo como referencia histórica.


## Reporte de Vulnerabilidades

El reporte detallado de las pruebas de vulnerabilidad realizadas se encuentra en el archivo [`vulnerability_report.pdf`](vulnerability_report.pdf).

## Estructura del proyecto

```
app/src/main/java/com/fincontrol/app/
├── LoginActivity / RegisterActivity   # autenticación
├── MainActivity                       # dashboard
├── auth/UserDao.java                  # DAO de usuarios (hash + queries parametrizadas)
├── db/DatabaseHelper.java             # esquema SQLite
├── gastos/                            # CRUD de gastos + ubicación
├── suscripciones/                     # CRUD de suscripciones + recordatorios
├── mapa/MapaActivity.java             # mapa de gastos (Google Maps)
├── model/                             # User, Gasto, Suscripcion
├── session/SessionManager.java        # sesión cifrada (Keystore)
└── util/LocationHelper.java           # ubicación con permisos en runtime
```


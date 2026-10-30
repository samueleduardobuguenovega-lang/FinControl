# FinControl — abrir y ejecutar en Android Studio sin crashear

Rama: `main` · Entrega: force-push a `main` (repo privado de Nek) · Runner: Gradle + JUnit4/Robolectric

## Specs

- **S1** El repo `https://github.com/samueleduardobuguenovega-lang/FinControl` queda con la versión que funciona (historial local de 18 commits, HEAD `978aa8a`), no la copia vieja de un solo commit (`d358b26`, Google Maps + clave redactada) que hoy está en `origin/main`.
- **S2** Todas las dependencias que el código importa están declaradas explícitamente en `gradle/libs.versions.toml` y `app/build.gradle.kts` con versión fija: `androidx.core:core`, `androidx.recyclerview:recyclerview`, `androidx.annotation:annotation`, `com.google.android.gms:play-services-base` (además de las ya declaradas). Ninguna queda heredada por transitividad.
- **S3** El proyecto abre y compila en un Android Studio estándar sin editar nada: AGP 8.13.2 + Gradle 8.14.6 + JDK 17 + `compileSdk = 36` / `targetSdk = 36`, `android.useAndroidX=true` en `gradle.properties`.
- **S4** La app no crashea al arrancar cuando el almacén cifrado (Android Keystore) no se puede abrir: la sesión degrada a memoria en vez de lanzar `IllegalStateException` (hoy `SessionManager.crearPrefs` lanza y mata el proceso antes del primer frame).
- **S5** La app no falla cuando el emulador no tiene Google Play services: `LocationHelper` comprueba `GoogleApiAvailability` y degrada a `LocationManager` / sin ubicación en lugar de depender de GMS.
- **S6** `README.md` documenta cómo abrir, compilar y ejecutar en Android Studio: requisitos (JDK 17 que trae el IDE, SDK Platform 36, emulador API 30+), build por CLI y qué revisar si algo falla.
- **S7** Verificación ejecutada, no supuesta: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest` y `assembleRelease` corren y pasan; el APK debug se genera.

## Tasks

- **T1** Publicar la versión final: tag `legacy-google-maps` sobre `d358b26` y force-push de `main` al historial real. S1 — inline — hecho (`main` = historial real + 6 commits, tag `legacy-google-maps`)
- **T2** Fijar stack compatible con Android Studio estándar (AGP 8.13.2, Gradle 8.14.6, compileSdk/targetSdk 36, useAndroidX, DSL de release de AGP 8). S3 — inline — hecho `f727e34`
- **T3** Declarar explícitamente core, recyclerview, annotation y play-services-base con las versiones ya resueltas. S2 — inline — hecho `f727e34`
- **T4** `SessionManager` con degradación a memoria si el Keystore falla. S4 — inline — hecho `acc8c67`
- **T5** `LocationHelper` con chequeo de Google Play services y fallback a `LocationManager`. S5 — inline — hecho `97c7c06`
- **T6** `proguard-rules.pro` (osmdroid / GMS) para que el release minificado no rompa en runtime. S7 — inline — hecho `f727e34`
- **T7** Tests unitarios (Robolectric) de la degradación de sesión, del sembrado de la BD y del arranque de las pantallas. S4/S7 — inline — hecho `30dd782` + `acc8c67`
- **T8** README: sección Android Studio + troubleshooting. S6 — inline — hecho `367181c`
- **T9** Verificar build debug/test/release y publicar. S7 — inline — verificado; push en curso

## Log

- **L1** (pedido original, verbatim): "puedes actualizar mi proyecto fincontrol https://github.com/samueleduardobuguenovega-lang/FinControl    y poner todas las dependecias del programa para ejecutarlo en android studio , ya que un compañero quizo emular el programa en android studio, y al emular el proyecto le crashea, y yo se que el programa funciona perfecto, algo debe fallar con sus dependecias"
- **L2** (evidencia, 29-oct): `origin/main` está en `d358b26`, un commit único con la versión de Google Maps y `google_maps_key` = `AIzaSy...iNsc` (clave redactada). El historial local bueno (`fincontrol-backup`) termina en `978aa8a` con osmdroid y sin claves; `git fetch` mostró que `origin/main` fue forzado de `978aa8a` a `d358b26`. El compañero clonó una versión distinta a la que funciona.
- **L3** (evidencia): importaciones del código vs dependencias declaradas: se usan `androidx.core.*`, `androidx.recyclerview.widget.*` y `androidx.annotation.*` sin declararlas (llegan por transitividad de appcompat/material), y `GoogleApiAvailability` necesitará `play-services-base` explícito.
- **L4** (evidencia): build local con el stack original (AGP 9.3.0 / Gradle 9.6.0 / compileSdk 37 / JDK 17) da BUILD SUCCESSFUL, así que el fallo del compañero no es del código: es del entorno (SDK 37 + AGP 9.3 exigen un Android Studio muy reciente) y del arranque en emulador (Keystore/GMS).
- **L5** (decisión): bajar a AGP 8.13.2 / Gradle 8.14.6 / compileSdk 36 porque `core 1.18.0` y `activity 1.13.0` piden `minCompileSdk=36` y no más; cualquier Studio de los últimos ~18 meses los soporta, a diferencia de AGP 9.3.
- **L6** (verificación, 29-oct, JDK 17 + AGP 8.13.2 + Gradle 8.14.6): `assembleDebug` BUILD SUCCESSFUL (APK 18 MB), `testDebugUnitTest` BUILD SUCCESSFUL (9 tests, 0 fallos: 2 ArranqueTest, 3 DatabaseHelperTest, 4 SessionManagerTest), `assembleRelease` BUILD SUCCESSFUL con R8 (APK 3.8 MB), `lintDebug` 0 errores. Robolectric corre en el sandbox API 35 porque el de API 36 exige un runtime Java 21 (`app/src/test/resources/robolectric.properties`).
- **L7** (límite honesto): no hay `/dev/kvm` en esta máquina, así que no se pudo arrancar un emulador real. El arranque se verificó con Robolectric (JVM Android + SQLite real) y el APK con Gradle; la prueba definitiva la hace el compañero instalando `app-debug.apk` en su emulador API 30+.

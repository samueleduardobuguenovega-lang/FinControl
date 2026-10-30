import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// La clave de Google Maps se lee de local.properties (archivo local, no versionado)
// y se inyecta como recurso google_maps_key, asi no queda en el repositorio.
val clavesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) {
        archivo.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = clavesLocales.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.fincontrol.app"

    // compileSdk 36: es el máximo que piden androidx.core 1.18.0 y activity
    // 1.13.0 (su aar-metadata declara minCompileSdk=36). Dejarlo en 36 permite
    // compilar desde cualquier Android Studio reciente sin instalar una
    // plataforma nueva.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fincontrol.app"
        // minSdk 30, o sea Android 11: el emulador donde se corre la app tiene
        // que ser API 30 o superior, si no el install falla con
        // INSTALL_FAILED_OLDER_SDK.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps SDK: la clave viaja como recurso, nunca como literal en el codigo.
        resValue("string", "google_maps_key", mapsApiKey)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Robolectric necesita los recursos mergeados y el AndroidManifest para
    // levantar las activities en un test de JVM (lo usan los tests unitarios).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Cada artefacto que importa el módulo se declara acá con versión fija
// (ver gradle/libs.versions.toml). Nada depende de resolución transitiva, que
// era justo lo que hacía que el proyecto compilara distinto en otro equipo.
dependencies {
    implementation(libs.annotation)
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.recyclerview)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.security.crypto)
    implementation(libs.play.services.location)
    implementation(libs.play.services.base)
    implementation(libs.play.services.maps)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

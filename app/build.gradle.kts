plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// La clave fija de la app está en keystore/mis-canales.p12, cifrada con la contraseña que
// GitHub pasa en SIGNING_PASSWORD (un secreto del repositorio). Sin ella (en un PC, por
// ejemplo) se firma con la clave de depuración, y ese APK no actualiza al de GitHub.
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }

android {
    namespace = "es.miscanales"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "es.miscanales"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.6.8"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/mis-canales.p12")
            storeType = "pkcs12"
            storePassword = signingPassword
            keyAlias = "miscanales"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (signingPassword != null) "release" else "debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.1.0")
    // la interfaz del móvil
    implementation("androidx.compose.material3:material3")
    // para que el reproductor siga sonando con la pantalla apagada
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
}

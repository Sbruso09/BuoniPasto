plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mario.buonipasto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mario.buonipasto"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "3.9"
    }

    // Chiave di firma personale: NON e' nel repository. Se app/buonipasto.keystore esiste (in locale, o creata
    // dal workflow da un secret di GitHub) gli aggiornamenti si installano sopra l'app gia' presente;
    // altrimenti l'APK viene firmato con la chiave di debug.
    val chiave = file("buonipasto.keystore")
    val password = (project.findProperty("buonipastoPassword") as String?) ?: System.getenv("BUONIPASTO_KEY_PASSWORD")
    signingConfigs {
        if (chiave.exists() && password != null) {
            create("personale") {
                storeFile = chiave
                storePassword = password
                keyAlias = (project.findProperty("buonipastoAlias") as String?) ?: System.getenv("BUONIPASTO_KEY_ALIAS") ?: "buonipasto"
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("personale") ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfigs.findByName("personale")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
}

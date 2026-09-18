import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization") version "2.1.10"
}

// Leitura segura do keystore.properties (se existir na raiz ou módulo)
val keystorePropertiesFile = listOf(
    rootProject.file("keystore.properties"),
    project.file("keystore.properties")
).firstOrNull { it.exists() }
val keystoreProperties = Properties()
val hasKeystore = if (keystorePropertiesFile != null && keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    true
} else {
    false
}

android {
    namespace = "br.com.bragasaude"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.bragasaude"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.1"

        buildConfigField("String", "BASE_URL", "\"https://api.bragasaude.online\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystore) {
            val storeFilePath = keystoreProperties.getProperty("storeFile") ?: "release-keystore.jks"
            val resolvedStoreFile = listOf(
                rootProject.file(storeFilePath),
                project.file(storeFilePath),
                file(storeFilePath)
            ).firstOrNull { it.exists() } ?: rootProject.file(storeFilePath)

            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = resolvedStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            applicationIdSuffix = ""
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // A mesma personalidade é carregada pelo gateway Python.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("scripts/server/persona"))
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.maxHeapSize = "768m"
                it.jvmArgs("-XX:MaxMetaspaceSize=256m", "-Dfile.encoding=UTF-8")
            }
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Retrofit & Serialization Converter
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // Firebase Stack
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-messaging")

    // Criptografia de Banco Local (SQLCipher + Security Crypto para chaves de hardware)
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Autenticação Biométrica (Fingerprint / Face Unlock para áreas sensíveis)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Google Health Connect (Galaxy Watch & Wearables)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // Google Credential Manager & Identity
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.kotlinx.serialization.json)

    // WorkManager
    implementation(libs.androidx.work.runtime)
    implementation("androidx.hilt:hilt-work:1.3.0-alpha02")
    ksp("androidx.hilt:hilt-compiler:1.3.0-alpha02") 

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)
    implementation("com.google.android.material:material:1.12.0")

    // Extração de texto de PDF — pdfbox-android (Apache 2.0).
    implementation(libs.pdfbox.android)

    // OCR de imagem On-Device — Google ML Kit Text Recognition (D49 / Fase 1)
    implementation(libs.mlkit.text.recognition)

    // Movement & GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")
    // QR Code Scanning (Intents integrados)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation(libs.mockk)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

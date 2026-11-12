plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.rota.copiloto"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.rota.copiloto.pro"
        minSdk = 26
        targetSdk = 34
        versionCode = 42
        versionName = "3.15.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave fixa: todas as versões saem assinadas igual, então o Android atualiza por cima
    // mantendo configurações e permissões (sem precisar desinstalar).
    signingConfigs {
        getByName("debug") {
            storeFile = file("rota-calculadora.keystore")
            storePassword = "rotacalc2026"
            keyAlias = "rota"
            keyPassword = "rotacalc2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Leitura por imagem (OCR) 100% no aparelho, modelo embutido no app.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("junit:junit:4.13.2")
}

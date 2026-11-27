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
        versionCode = 111
        versionName = "3.41.15"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave de assinatura fora do repositório. No CI ela vem dos segredos do GitHub
    // (RC_KEYSTORE_PATH, RC_STORE_PASSWORD, RC_KEY_ALIAS, RC_KEY_PASSWORD).
    // Mesma chave em todas as versões: o Android atualiza por cima sem desinstalar.
    signingConfigs {
        create("release") {
            System.getenv("RC_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("RC_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("RC_KEY_ALIAS") ?: "rota"
            keyPassword = System.getenv("RC_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
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
    // Reconhecimento de voz offline (Vosk): lê o microfone direto, sem pedir foco de áudio, então não pausa o Spotify.
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("junit:junit:4.13.2")
}

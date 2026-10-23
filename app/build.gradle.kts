import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.matrixkey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matrixkey"
        minSdk = 29
        targetSdk = 34
        versionCode = 36
        versionName = "1.3"
        // Nothing phones (and nearly every phone since 2019) are 64-bit ARM.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Release signing comes from the environment, so the key never lives in the repo:
    //   KEYSTORE       path to the .jks file
    //   KEYSTORE_PASS  its password (keystore and key share it; key alias "matrixkey")
    // Official releases are signed by the Release workflow with the key held in
    // GitHub's encrypted secrets. Without these, builds use the debug key.
    val keystorePath = System.getenv("KEYSTORE")
    val keystorePass = System.getenv("KEYSTORE_PASS")
    val hasReleaseKey = !keystorePath.isNullOrEmpty() && !keystorePass.isNullOrEmpty() && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keystorePath!!)
            storePassword = keystorePass
            keyAlias = "matrixkey"
            keyPassword = keystorePass
        }
    }

    buildTypes {
        release {
            // R8 strips unused library code and resources from the release APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // On-device text recognition (Scan text), via Google Play services: the model is
    // shared with other apps instead of being bundled (saves ~40 MB). Works offline
    // once Play services has downloaded it.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

// ---------------------------------------------------------------------------
// Bundled assets that are downloaded at build time instead of being committed:
//  - Doto: open-source (SIL OFL) dot-matrix font, used when Nothing's NDot
//    system font isn't found on the device.
//  - English word frequency list (FrequencyWords, CC BY-SA 4.0) for
//    suggestions, autocorrect and glide typing.
// ---------------------------------------------------------------------------
val assetsDir = layout.projectDirectory.dir("src/main/assets")

val downloadAssets by tasks.registering {
    val fontFile = assetsDir.file("fonts/Doto.ttf").asFile
    val dictFile = assetsDir.file("dict/en_50k.txt").asFile
    outputs.files(fontFile, dictFile)
    doLast {
        fun fetch(url: String, dest: File) {
            if (dest.exists() && dest.length() > 0) return
            dest.parentFile.mkdirs()
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
        fetch(
            "https://github.com/google/fonts/raw/main/ofl/doto/Doto%5BROND,wght%5D.ttf",
            fontFile
        )
        fetch(
            "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt",
            dictFile
        )
    }
}

tasks.named("preBuild") { dependsOn(downloadAssets) }

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
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK installs directly.
            // Swap in your own keystore before publishing anywhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += listOf("txt")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
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

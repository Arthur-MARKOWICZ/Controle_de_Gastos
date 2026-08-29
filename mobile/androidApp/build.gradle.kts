import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val apiBaseUrl = providers.gradleProperty("API_BASE_URL")
    .orElse(localProperties.getProperty("API_BASE_URL") ?: "http://10.0.2.2:8080")
    .get()
    .also { require(it.matches(Regex("https?://[^\\s\\\"\\\\]+"))) { "API_BASE_URL deve ser uma URL HTTP(S) válida" } }

android {
    namespace = "br.com.controlegastos.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.controlegastos.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.12.0")
}

import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

fun propertiesOf(file: File): Properties = Properties().apply {
    if (file.isFile) file.inputStream().use(::load)
}

val localProperties = propertiesOf(rootProject.file("local.properties"))
val dotEnv = propertiesOf(rootProject.file(".env"))

// Ambiente da API: -PAPI_ENV vence o .env; sem nenhum dos dois, assume desenvolvimento local.
val apiEnv = (providers.gradleProperty("API_ENV").orNull ?: dotEnv.getProperty("API_ENV") ?: "local")
    .trim()
    .lowercase()
    .also { require(it == "local" || it == "prod") { "API_ENV deve ser 'local' ou 'prod', mas era '$it'" } }

// local.properties existe para o caminho do SDK. Ter a URL em dois arquivos
// deixaria a edição de um deles sem efeito e sem explicação, então recusamos.
require(localProperties.getProperty("API_BASE_URL") == null) {
    "API_BASE_URL saiu de local.properties e agora vive em .env. Remova a linha de " +
        "mobile/local.properties e defina API_ENV e API_BASE_URL_LOCAL/_PROD em mobile/.env " +
        "(copie mobile/.env.example)."
}

// Precedência: -PAPI_BASE_URL, .env do ambiente escolhido, emulador.
val apiBaseUrl = (
    providers.gradleProperty("API_BASE_URL").orNull
        ?: dotEnv.getProperty(if (apiEnv == "prod") "API_BASE_URL_PROD" else "API_BASE_URL_LOCAL")
        ?: "http://10.0.2.2:8080"
    ).trim()

require(apiBaseUrl.matches(Regex("https?://[^\\s\"\\\\]+"))) {
    "API_BASE_URL deve ser uma URL HTTP(S) válida, mas era '$apiBaseUrl'"
}

if (apiEnv == "prod") {
    // Mesmas regras aplicadas a PUBLIC_APP_URL no deploy: só a origem HTTPS.
    // O Nginx publica a API em 443 e mantém a 8080 em loopback (docs/deploy/https-producao.md).
    val uri = runCatching { URI(apiBaseUrl) }.getOrNull()
    requireNotNull(uri) { "API_BASE_URL de produção não é uma URI válida: '$apiBaseUrl'" }
    require(uri.scheme == "https") { "API_BASE_URL de produção deve usar https://, mas era '$apiBaseUrl'" }
    require(uri.port == -1) {
        "API_BASE_URL de produção não pode ter porta: '$apiBaseUrl'. " +
            "A API responde em 443 atrás do Nginx; a 8080 não é publicada."
    }
    require(uri.path.isNullOrEmpty()) {
        "API_BASE_URL de produção deve conter só a origem, sem caminho nem barra final: '$apiBaseUrl'"
    }
    require(uri.query == null && uri.fragment == null) {
        "API_BASE_URL de produção não pode ter query nem fragmento: '$apiBaseUrl'"
    }
}

// O build release não pode sair com a API local: cleartext é bloqueado fora do debug.
val verifyReleaseApiBaseUrl = tasks.register("verifyReleaseApiBaseUrl") {
    val url = apiBaseUrl
    doLast {
        check(url.startsWith("https://")) {
            "O build release exige uma API_BASE_URL HTTPS, mas era '$url'. Use API_ENV=prod."
        }
    }
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach { dependsOn(verifyReleaseApiBaseUrl) }

android {
    namespace = "br.com.controlegastos.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.controlegastos.app"
        // O App Link do login social vive no mesmo domínio da API, porque é o
        // backend que redireciona o navegador do sistema de volta (ADR-020).
        manifestPlaceholders["oauthCallbackHost"] = URI(apiBaseUrl).host
        manifestPlaceholders["oauthCallbackScheme"] = URI(apiBaseUrl).scheme
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
    implementation(libs.androidx.core)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.12.0")
}

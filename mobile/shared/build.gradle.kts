import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()

    android {
        namespace = "br.com.controlegastos.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }

        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            // Última publicação multiplataforma; API estável com Compose 1.12.
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.compose.ui:ui:1.12.0")
            // Botão voltar do sistema no código comum.
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.0")
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Testes de UI Compose na JVM (telas de recorrência, ADR-0031): o runtime
        // desktop do Compose desenha sem emulador nem dispositivo.
        jvmTest.dependencies {
            implementation("org.jetbrains.compose.ui:ui-test:1.12.0")
            implementation(compose.desktop.currentOs)
        }
    }
}

// Cobertura (ADR-0030) medida pelos testes JVM; limites na linha de base, só sobem.
// Funções @Composable só desenham estado: a regra testável fica nos controllers,
// gateways e validações, que é o que a cobertura mede.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                classes("*ComposableSingletons*")
            }
        }
        verify {
            rule {
                minBound(0, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                minBound(0, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}

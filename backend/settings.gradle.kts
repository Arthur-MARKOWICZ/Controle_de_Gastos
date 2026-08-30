pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "controle-gastos-backend"

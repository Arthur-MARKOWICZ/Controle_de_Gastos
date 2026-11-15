plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "br.com.controlegastos"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        content {
            includeGroupByRegex(".*")
        }
    }
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("dev.samstevens.totp:totp:1.7.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Testes unitários: tudo que roda sem Docker. Os `*IntegrationTest` sobem
// PostgreSQL via Testcontainers e ficam reservados para a suíte completa do CI.
tasks.register<Test>("unitTest") {
    group = "verification"
    description = "Executa os testes que não dependem de Docker/Testcontainers."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        excludeTestsMatching("*IntegrationTest")
    }
}

// Cobertura (ADR-0030): o relatório combina unitários e integração, porque
// vários services só são exercitados pela API. A verificação entra no `check`.
jacoco {
    toolVersion = "0.8.16"
}

// Ponto de entrada e configuração declarativa não têm regra própria.
val coverageExclusions = listOf(
    "br/com/controlegastos/ControleGastosApplication*",
    "**/config/**",
)

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
    )
}

// Limites em fração (0.80 = 80%). Começam na linha de base medida e só sobem
// (catraca descrita no ADR-0030).
val coverageMinimumLines = providers.gradleProperty("coverageMinimumLines").orElse("0.00")
val coverageMinimumBranches = providers.gradleProperty("coverageMinimumBranches").orElse("0.00")

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.map { it.classDirectories })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = coverageMinimumLines.get().toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = coverageMinimumBranches.get().toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

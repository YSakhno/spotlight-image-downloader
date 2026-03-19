import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinx.kover)

    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinter)

    application
    alias(libs.plugins.gradleup.shadow)
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.clikt)
    implementation(libs.apache.commons.csv)

    testImplementation(libs.bundles.kotest)
    testRuntimeOnly(libs.kotest.runner)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xexplicit-backing-fields")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
        jvmTarget = JvmTarget.fromTarget(libs.versions.javaLanguageCompatibility.get())
    }
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.javaLanguageCompatibility.get())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kover {
    reports {
        total {
            html { onCheck = true }
            xml { onCheck = true }
        }
    }
}

kotlinter {
    ktlintVersion = libs.versions.ktlint.get()
    ignoreFormatFailures = false
    ignoreLintFailures = false
    reporters = arrayOf("plain", "checkstyle", "html")
}

detekt {
    buildUponDefaultConfig = true
    config.from("${rootProject.projectDir}/detekt.yaml")
    ignoreFailures = false
    parallel = true
}

tasks.detekt.configure {
    val typeResolutionDetektTasks = tasks.withType<Detekt>()
        .filter { it.project == project }
        .filter { it.name != name }

    dependsOn(typeResolutionDetektTasks)
}

tasks.jar {
    archiveBaseName.set(rootProject.name)

    manifest {
        attributes(
            mapOf(
                "Built-By" to "Gradle ${gradle.gradleVersion}",
                "Created-By" to "Yurii Sakhno",
                "Implementation-Title" to "Spotlight Image Downloader",
                "Implementation-Version" to project.version,
                "Implementation-Vendor-Id" to project.group,
                "Implementation-Vendor" to "Yurii Sakhno",
            )
        )
    }
}

application {
    mainClass = "io.ysakhno.tools.spotlight.imagedownloader.AppKt"
}

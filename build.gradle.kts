plugins {
    kotlin("jvm") version "2.3.10"
    id("io.kotest") version "6.1.7"
    application
    id("com.gradleup.shadow") version "9.4.0"
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.apache.commons:commons-csv:1.14.1")

    testImplementation("io.kotest:kotest-framework-engine:6.1.7")
    testImplementation("io.kotest:kotest-extensions:6.1.7")
    testImplementation("io.kotest:kotest-property:6.1.7")
    testRuntimeOnly("io.kotest:kotest-runner-junit5:6.1.7")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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

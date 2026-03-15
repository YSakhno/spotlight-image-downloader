plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("com.gradleup.shadow") version "9.4.0"
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.apache.commons:commons-csv:1.14.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "io.ysakhno.tools.spotlight.imagedownloader.AppKt"
}

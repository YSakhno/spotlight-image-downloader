@Suppress("UnstableApiUsage") // repositories and repositoriesMode are 'incubating' in Gradle 9.4.0
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "spotlight-image-downloader"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Optional for environments where Google Maven is inaccessible; official repository remains the default.
        providers.gradleProperty("pakkitGoogleMavenMirror").orNull?.let { mirror ->
            maven(mirror) { content { includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.android.*") } }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "pakkit"
// Pakkit is distributed as a standalone watch app.
include(":shared", ":wear")
// Test-only APK used by instrumentation. Never bundled in either consumer artifact.
include(":qaFixture")
project(":qaFixture").projectDir = file("qa/fixture")

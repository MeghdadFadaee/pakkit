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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Optional for environments where Google Maven is inaccessible; official repository remains
        // the default.
        providers.gradleProperty("pakkitGoogleMavenMirror").orNull?.let { mirror ->
            maven(mirror) {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.google\\.android.*")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "pakkit"

include(":app", ":qaFixture")

project(":qaFixture").projectDir = file("qa/fixture")

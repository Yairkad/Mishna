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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Mishna"
include(":core")
// Set MISHNA_CORE_ONLY=1 to build and test the pure-Kotlin core without the Android SDK.
if (System.getenv("MISHNA_CORE_ONLY") == null) {
    include(":app")
} else {
    rootProject.buildFileName = "build-core.gradle.kts"
}

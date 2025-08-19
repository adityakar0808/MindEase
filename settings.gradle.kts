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
        google()
        mavenCentral()
        //
        // JCenter is sunset, so it should ideally be removed if you have it.
        // If you still have jcenter() here, try removing it or placing
        // mavenCentral() above it.
    }
}


rootProject.name = "MindEase"
include(":app")

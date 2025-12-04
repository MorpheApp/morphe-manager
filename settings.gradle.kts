pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io") {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        maven {
            // A repository must be specified for some reason. "registry" is a dummy.
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                val hardcodedUser = ""
                val hardcodedToken = ""
                val gprUser: String? = providers.gradleProperty("gpr.user").orNull
                val gprKey: String? = providers.gradleProperty("gpr.key").orNull

                username = (if (hardcodedUser.isNotBlank()) hardcodedUser else System.getenv("GITHUB_ACTOR") ?: gprUser)
                password = (if (hardcodedToken.isNotBlank()) hardcodedToken else System.getenv("GITHUB_TOKEN") ?: gprKey)
            }
        }
    }
}

rootProject.name = "morphe-manager"
include(":app", ":api")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
    signing
}

group = "app.revanced"

dependencies {
    implementation(libs.androidx.ktx)
    implementation(libs.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
}

android {
    namespace = "app.universal.revanced.manager.plugin.downloader"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        aidl = true
    }
}

apiValidation {
    nonPublicMarkers += "app.revanced.manager.plugin.downloader.PluginHostApi"
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            // Publish to the current GitHub repository packages by default.
            // Fallback keeps local publishing possible when the env var is absent.
            val ghRepo = System.getenv("GITHUB_REPOSITORY") ?: "Jman-Github/Universal-ReVanced-Manager"
            url = uri("https://maven.pkg.github.com/$ghRepo")
            credentials {
                // Priority: hardcoded fallback -> environment -> Gradle properties
                val hardcodedUser = ""
                val hardcodedToken = ""
                val gprUser = providers.gradleProperty("gpr.user").orNull
                val gprKey = providers.gradleProperty("gpr.key").orNull

                username = if (hardcodedUser.isNotBlank()) hardcodedUser
                else System.getenv("GITHUB_ACTOR") ?: gprUser

                password = if (hardcodedToken.isNotBlank()) hardcodedToken
                else System.getenv("GITHUB_TOKEN") ?: gprKey
            }
        }
    }

    publications {
        create<MavenPublication>("Api") {
            afterEvaluate {
                from(components["release"])
            }

            groupId = "app.revanced"
            artifactId = "universal-revanced-manager-api"
            version = project.version.toString()

            pom {
                name = "ReVanced Manager API"
                description = "API for ReVanced Manager."
                url = "https://revanced.app"

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                    }
                }
                developers {
                    developer {
                        id = "ReVanced"
                        name = "ReVanced"
                        email = "contact@revanced.app"
                    }
                }
                scm {
                    val ghRepo = System.getenv("GITHUB_REPOSITORY") ?: "Jman-Github/Universal-ReVanced-Manager"
                    connection = "scm:git:git://github.com/$ghRepo.git"
                    developerConnection = "scm:git:git@github.com:$ghRepo.git"
                    url = "https://github.com/$ghRepo"
                }
            }
        }
    }
}

signing {
    // Disable signing by default in CI environments unless explicitly enabled.
    // Enable by setting env SIGNING_REQUIRED=true or Gradle property signing.required=true
    val requiredFromEnv = System.getenv("SIGNING_REQUIRED")?.toBoolean()
    val requiredFromProp = (findProperty("signing.required") as String?)?.toBoolean()
    isRequired = (requiredFromEnv ?: requiredFromProp) ?: false

    if (isRequired) {
        useGpgCmd()
    }
    sign(publishing.publications["Api"])
}

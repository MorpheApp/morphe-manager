plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // The Android builds of the JVM come as .tar.xz, which Gradle cannot unpack on its own
    implementation(libs.xz)
}

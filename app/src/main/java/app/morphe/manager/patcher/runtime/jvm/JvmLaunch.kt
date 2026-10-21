/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

/**
 * How [EmbeddedJvm] starts the JVM.
 *
 * @param preload Libraries of the runtime that others of it link against, dependencies first.
 * @param mainClass The class whose main runs, in JNI form such as `a/b/MainKt`.
 * @param args What main is called with.
 */
data class JvmLaunch(
    val libjvm: String,
    val preload: List<String>,
    val options: List<String>,
    val mainClass: String,
    val args: List<String>
)

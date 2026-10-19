/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

/**
 * A HotSpot JVM started inside the calling process. HotSpot cannot be started twice in one
 * process, so the process is meant to end with the JVM.
 */
internal object EmbeddedJvm {
    init {
        System.loadLibrary("jvm_launcher")
    }

    /**
     * Starts the JVM [launch] describes and runs its main class with [args], returning once every
     * non-daemon thread of it ended.
     *
     * @return 0 when main returned normally, otherwise non-zero.
     */
    fun run(launch: JvmLaunch, args: List<String>) = launchJvm(
        launch.libjvm,
        launch.preload.toTypedArray(),
        launch.options.toTypedArray(),
        launch.mainClass,
        args.toTypedArray()
    )

    private external fun launchJvm(
        libjvm: String,
        preload: Array<String>,
        options: Array<String>,
        mainClass: String,
        args: Array<String>
    ): Int
}

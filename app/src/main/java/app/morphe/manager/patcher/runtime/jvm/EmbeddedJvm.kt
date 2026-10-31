/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

/**
 * A HotSpot JVM started inside the calling process. HotSpot cannot be started twice in one process,
 * so the JVM stays until the process ends.
 */
internal object EmbeddedJvm {
    init {
        System.loadLibrary("jvm_launcher")
    }

    /**
     * Starts the JVM [launch] describes and calls its main class with [args] on a thread of its
     * own, returning once main is called.
     *
     * @return 0, or why the JVM did not start: a JNI_CreateJavaVM error, 1 for a library of the
     *         runtime that did not load, 2 for a main method that is missing and 3 for memory tag
     *         checks that could not be turned off.
     */
    fun start(launch: JvmLaunch, options: List<String>, args: List<String>) = startJvm(
        launch.libjvm,
        launch.preload.toTypedArray(),
        options.toTypedArray(),
        launch.mainClass,
        args.toTypedArray()
    )

    /**
     * The largest block of address space up to [limitMb] that one reservation gets right now, in
     * megabytes, or 0. HotSpot reserves its heap in one piece.
     */
    external fun largestReservationMb(limitMb: Int): Int

    private external fun startJvm(
        libjvm: String,
        preload: Array<String>,
        options: Array<String>,
        mainClass: String,
        args: Array<String>
    ): Int
}

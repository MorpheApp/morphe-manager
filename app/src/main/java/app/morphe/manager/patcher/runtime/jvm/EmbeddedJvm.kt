/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

/**
 * Starts the HotSpot JVM that patches as a process of its own, as it is built for the GNU C library,
 * which Android's cannot load into the calling process.
 */
internal object EmbeddedJvm {
    init {
        System.loadLibrary("jvm_launcher")
    }

    /**
     * Starts [command] as a process of its own. It keeps [keepFds] open under the same numbers,
     * writes its standard output and error to [outputFd], and is killed once the calling thread
     * ends, so that thread waits for it with [waitProcess].
     *
     * @return Its pid, or a negative errno.
     */
    fun spawn(command: List<String>, keepFds: IntArray, outputFd: Int) =
        spawnProcess(command.toTypedArray(), keepFds, outputFd)

    /** Waits for the process [pid] that [spawn] started, returning its exit code, or 128 plus the signal that ended it. */
    external fun waitProcess(pid: Int): Int

    private external fun spawnProcess(command: Array<String>, keepFds: IntArray, outputFd: Int): Int
}

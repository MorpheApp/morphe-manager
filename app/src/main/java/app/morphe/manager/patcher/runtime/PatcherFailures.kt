/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

/**
 * The patcher JVM could not be brought up for the run, which patching on ART can still take.
 */
class JvmUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The run failed in the patcher JVM.
 *
 * @param originalStackTrace The stack trace of what it failed on there.
 */
class JvmFailureException(val originalStackTrace: String) : Exception()

/**
 * The patcher ran out of the heap it was given, which no other heap the device allows would fix.
 *
 * @param heapLimitMb The heap the run had, in megabytes.
 * @param originalStackTrace The stack trace of the [OutOfMemoryError].
 */
class HeapExhaustedException(val heapLimitMb: Int, val originalStackTrace: String) :
    Exception("Patcher exhausted its ${heapLimitMb}MB heap")

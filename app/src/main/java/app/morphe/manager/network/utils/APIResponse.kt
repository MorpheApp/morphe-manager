/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/network/utils/APIResponse.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

@file:Suppress("NOTHING_TO_INLINE")

package app.morphe.manager.network.utils

import io.ktor.http.HttpStatusCode

/**
 * @author Aliucord Authors, DiamondMiner88
 */

sealed interface APIResponse<T> {
    data class Success<T>(val data: T) : APIResponse<T>
    data class Error<T>(val error: APIError) : APIResponse<T>
    data class Failure<T>(val error: APIFailure) : APIResponse<T>
}

class APIError(val statusCode: HttpStatusCode, body: String?) : Exception("HTTP Code $statusCode, Body: $body")

class APIFailure(error: Throwable, body: String?) : Exception(body ?: error.message, error)

inline fun <T, R> APIResponse<T>.fold(
    success: (T) -> R,
    fail: (Exception) -> R,
): R {
    return when (this) {
        is APIResponse.Success -> success(data)
        is APIResponse.Error -> fail(error)
        is APIResponse.Failure -> fail(error)
    }
}

inline fun <T> APIResponse<T>.getOrThrow(): T {
    return fold(
        success = { it },
        fail = { throw it }
    )
}

inline fun <T> APIResponse<T>.getOrNull(): T? {
    return fold(
        success = { it },
        fail = { null }
    )
}

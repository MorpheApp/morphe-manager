package app.revanced.manager.patcher.runtime

import android.content.Context
import kotlin.math.max

// TODO: Refactor or delete this.
object MemoryLimitConfig {
    const val MIN_LIMIT_MB = 200
    private const val DEFAULT_FALLBACK_LIMIT_MB = 700

    fun recommendedLimitMb(context: Context): Int = DEFAULT_FALLBACK_LIMIT_MB

    fun clampLimitMb(context: Context, requestedMb: Int): Int {
        val upperBound = max(MIN_LIMIT_MB, recommendedLimitMb(context))
        return requestedMb.coerceIn(MIN_LIMIT_MB, upperBound)
    }
}

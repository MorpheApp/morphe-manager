package app.revanced.manager.ui.viewmodel

import android.content.Context
import app.morphe.manager.R
import kotlin.math.abs
import kotlin.random.Random

/**
 * Slightly informative and witty/fun messages to show the user.
 */
object HomeAndPatcherMessages {

    private var incrementedHomeMessage = false

    private lateinit var homeGreetingMessages: List<Int>

    private fun shuffleAllExceptFirst(shuffleSeed: Long, vararg messages: Int) =
        listOf(messages.first()) + messages.drop(1).shuffled(Random(shuffleSeed))

    /**
     * Witty greeting message.
     */
    fun getHomeMessage(context: Context, shuffleSeed: Long): Int {
        // First message is always shown as the first message for installations,
        // and all other strings are randomly shown.
        // Use different seed on each install, but keep the same seed across sessions
        if (!::homeGreetingMessages.isInitialized) {
            homeGreetingMessages = shuffleAllExceptFirst(
                shuffleSeed,
                R.string.morphe_home_greeting_1,
                R.string.morphe_home_greeting_2,
                R.string.morphe_home_greeting_3,
                R.string.morphe_home_greeting_4,
                R.string.morphe_home_greeting_5,
                R.string.morphe_home_greeting_6,
                R.string.morphe_home_greeting_7,
            )
        }

        val messageIndexKey = "patching_home_message_index"
        // Home screen immediately refreshes itself on launch.
        // Use default -1 so incremented it gives 0 index.
        var currentMessageIndex = UIPersistentValues.getInt(context, messageIndexKey, -1)

        if (!incrementedHomeMessage) {
            incrementedHomeMessage = true
            currentMessageIndex++ // Increment returned index
            UIPersistentValues.putInt(context, messageIndexKey, currentMessageIndex)
        }

        val safeIndex = abs(currentMessageIndex) % homeGreetingMessages.size
        return homeGreetingMessages[safeIndex]
    }

    private lateinit var patcherMessages: List<Int>

    /**
     * Witty patcher message.
     */
    fun getPatcherMessage(context: Context, shuffleSeed: Long): Int {
        if (!::patcherMessages.isInitialized) {
            patcherMessages = shuffleAllExceptFirst(
                shuffleSeed,
                R.string.morphe_patcher_message_1,
                R.string.morphe_patcher_message_2,
                R.string.morphe_patcher_message_3,
                R.string.morphe_patcher_message_4,
                R.string.morphe_patcher_message_5,
                R.string.morphe_patcher_message_6,
                R.string.morphe_patcher_message_7,
                R.string.morphe_patcher_message_8,
                R.string.morphe_patcher_message_9,
                R.string.morphe_patcher_message_10,
                R.string.morphe_patcher_message_11,
                R.string.morphe_patcher_message_12,
                R.string.morphe_patcher_message_13,
                R.string.morphe_patcher_message_14,
                R.string.morphe_patcher_message_15,
                R.string.morphe_patcher_message_16,
                R.string.morphe_patcher_message_17,
                R.string.morphe_patcher_message_18,
                R.string.morphe_patcher_message_19,
                R.string.morphe_patcher_message_20,
            )
        }

        val messageIndexKey = "patching_patcher_message_index"
        val currentMessageIndex = UIPersistentValues.getInt(context, messageIndexKey)

        UIPersistentValues.putInt(context, messageIndexKey, currentMessageIndex + 1)

        val safeIndex = abs(currentMessageIndex) % patcherMessages.size
        return patcherMessages[safeIndex]
    }
}

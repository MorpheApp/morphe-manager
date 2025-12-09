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

    /**
     * Witty greeting message.
     */
    fun getHomeMessage(context: Context, shuffleSeed: Long): Int {
        // First message is always shown as the first message for installations,
        // and all other strings are randomly shown.
        // Use different seed on each install, but keep the same seed across sessions
        if (!::homeGreetingMessages.isInitialized) {
            val messages = listOf(
                R.string.morphe_home_greeting_1,
                R.string.morphe_home_greeting_2,
                R.string.morphe_home_greeting_3,
                R.string.morphe_home_greeting_4,
                R.string.morphe_home_greeting_5,
                R.string.morphe_home_greeting_6,
                R.string.morphe_home_greeting_7,
            )

            // Home screen immediately refreshes itself on first launch.
            // Use index 1 for the new install message.
            val firstMessage = messages.first()
            val shuffledMessages = messages.drop(1).shuffled(Random(shuffleSeed)).toMutableList()
            shuffledMessages[1] = firstMessage

            homeGreetingMessages = shuffledMessages
        }

        val messageIndexKey = "patching_home_message_index"
        var currentMessageIndex = abs(
            UIPersistentValues.getInt(context, messageIndexKey)
        ) % homeGreetingMessages.size

        if (!incrementedHomeMessage) {
            incrementedHomeMessage = true
            currentMessageIndex++
            UIPersistentValues.putInt(context, messageIndexKey, currentMessageIndex)
        }

        return homeGreetingMessages[currentMessageIndex]
    }

    private lateinit var patcherMessages: List<Int>

    /**
     * Witty patcher message.
     */
    fun getPatcherMessage(context: Context, shuffleSeed: Long): Int {
        if (!::patcherMessages.isInitialized) {
            val messages = listOf(
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

            patcherMessages = listOf(messages.first()) + messages.drop(1).shuffled(Random(shuffleSeed))
        }

        val messageIndexKey = "patching_patcher_message_index"
        val currentMessageIndex = abs(
            UIPersistentValues.getInt(context, messageIndexKey)
        ) % patcherMessages.size

        UIPersistentValues.putInt(context, messageIndexKey, currentMessageIndex + 1)

        return patcherMessages[currentMessageIndex]
    }
}

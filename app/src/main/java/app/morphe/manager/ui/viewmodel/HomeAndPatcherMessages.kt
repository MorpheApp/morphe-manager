package app.morphe.manager.ui.viewmodel

import android.content.Context
import android.util.Log
import app.morphe.manager.R
import app.morphe.manager.util.tag
import kotlin.random.Random

/**
 * Slightly informative and witty/fun messages to show the user.
 */
object HomeAndPatcherMessages {

    /**
     * Greeting message on the home screen. Same message shown for each app session.
     */
    private var homeGreetingMessage: Int? = null
    private val homeGreetingMessageIndex = PersistentValue("patching_home_message_index", 0)
    private val homeGreetingMessageSeed = PersistentValue("patching_home_message_seed", 0L)

    private val patcherMessageIndex = PersistentValue("patching_patcher_message_index", 0)
    private val patcherMessageSeed = PersistentValue("patching_patcher_message_seed", 0L)

    private fun updateValues(
        context: Context,
        messageIndex: PersistentValue<Int>,
        messageSeed: PersistentValue<Long>,
        messages: List<Int>
    ): Int {
        var seed = messageSeed.get(context)
        var updateSeed = false

        if (seed == 0L) {
            // First run of clean install.
            updateSeed = true
        }

        var currentMessageIndex = messageIndex.get(context)
        if (currentMessageIndex > messages.lastIndex) {
            // All messages are exhausted. Reset the shuffle so the next batch is in random order.
            currentMessageIndex = 0
            updateSeed = true
        }

        if (updateSeed) {
            seed = Random.nextInt().toLong()
            messageSeed.save(context, seed)
            Log.d(tag, "Updated message seed: $messageSeed")
        }

        val shuffledMessages = listOf(messages.first()) + messages.drop(1).shuffled(Random(seed))
        val greeting = shuffledMessages[currentMessageIndex]

        messageIndex.save(currentMessageIndex + 1)

        return greeting
    }

    /**
     * Witty greeting message.
     */
    fun getHomeMessage(context: Context): Int {
        // First message is always shown as the first message for installations,
        // and all other strings are randomly shown.
        // Use different seed on each install, but keep the same seed across sessions
        var message = homeGreetingMessage

        if (message == null) {
            message = updateValues(
                context,
                homeGreetingMessageIndex,
                homeGreetingMessageSeed,
                listOf(
                    R.string.home_greeting_1,
                    R.string.home_greeting_2,
                    R.string.home_greeting_3,
                    R.string.home_greeting_4,
                    R.string.home_greeting_5,
                    R.string.home_greeting_6,
                    R.string.home_greeting_7,
                    R.string.home_greeting_8,
                    R.string.home_greeting_9,
                    R.string.home_greeting_10,
                )
            )
            homeGreetingMessage = message
        }

        return message
    }

    /**
     * Witty patcher message.
     */
    fun getPatcherMessage(context: Context): Int {
        // Message changes each time called.
        return updateValues(
            context,
            patcherMessageIndex,
            patcherMessageSeed,
            listOf(
                R.string.patcher_message_1,
                R.string.patcher_message_2,
                R.string.patcher_message_3,
                R.string.patcher_message_4,
                R.string.patcher_message_5,
                R.string.patcher_message_6,
                R.string.patcher_message_7,
                R.string.patcher_message_8,
                R.string.patcher_message_9,
                R.string.patcher_message_10,
                R.string.patcher_message_11,
                R.string.patcher_message_12,
                R.string.patcher_message_13,
                R.string.patcher_message_14,
                R.string.patcher_message_15,
                R.string.patcher_message_16,
                R.string.patcher_message_17,
                R.string.patcher_message_18,
                R.string.patcher_message_19,
                R.string.patcher_message_20,
            )
        )
    }
}

package app.revanced.manager.ui.viewmodel

import android.content.Context
import android.util.Log
import app.morphe.manager.R
import app.revanced.manager.util.tag
import kotlin.random.Random

/**
 * Slightly informative and witty/fun messages to show the user.
 */
object HomeAndPatcherMessages {

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
     * Greeting message on the home screen. Same message shown for each app session.
     */
    private var homeGreetingMessage: Int? = null

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
                PersistentValue(context, "patching_home_message_index", 0),
                PersistentValue(context, "patching_home_message_seed", 0L),
                listOf(
                    R.string.morphe_home_greeting_1,
                    R.string.morphe_home_greeting_2,
                    R.string.morphe_home_greeting_3,
                    R.string.morphe_home_greeting_4,
                    R.string.morphe_home_greeting_5,
                    R.string.morphe_home_greeting_6,
                    R.string.morphe_home_greeting_7
                )
            )
            homeGreetingMessage = message
        }

        return message
    }

    private val patcherMessageIndex = PersistentValue("patching_patcher_message_index", 0)
    private val patcherMessageSeed = PersistentValue("patching_patcher_message_seed", 0L)

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
        )
    }
}

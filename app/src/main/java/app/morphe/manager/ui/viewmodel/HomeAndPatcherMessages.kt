package app.morphe.manager.ui.viewmodel

import android.content.Context
import android.util.Log
import app.morphe.manager.R
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages.getHomeMessage
import app.morphe.manager.util.tag
import java.util.Calendar
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
            // All messages are exhausted. Reset the shuffle so the next batch is in random order
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
     * Resets the cached greeting so the next call to [getHomeMessage] picks a new one.
     * Called on pull-to-refresh.
     */
    fun resetHomeMessage() {
        homeGreetingMessage = null
    }

    /**
     * Witty greeting message. Picks from a time-of-day bucket so the tone matches
     * when the user opens the app.
     */
    fun getHomeMessage(context: Context): Int {
        return homeGreetingMessage ?: run {
            // home_greeting_1 is always shown first on a new installation.
            // All other strings in the active time bucket are randomly shown
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val messages = listOf(R.string.home_greeting_1) + when (hour) {
                in 5..11 -> listOf(
                    R.string.home_greeting_4,
                    R.string.home_greeting_7,
                    R.string.home_greeting_10,
                    R.string.home_greeting_morning_1,
                    R.string.home_greeting_morning_2,
                )
                in 12..16 -> listOf(
                    R.string.home_greeting_2,
                    R.string.home_greeting_8,
                    R.string.home_greeting_afternoon_1,
                    R.string.home_greeting_afternoon_2,
                    R.string.home_greeting_afternoon_3,
                )
                in 17..21 -> listOf(
                    R.string.home_greeting_3,
                    R.string.home_greeting_6,
                    R.string.home_greeting_evening_1,
                    R.string.home_greeting_evening_2,
                    R.string.home_greeting_evening_3,
                )
                in 22..23 -> listOf(
                    R.string.home_greeting_5,
                    R.string.home_greeting_9,
                    R.string.home_greeting_late_night_1,
                    R.string.home_greeting_late_night_2,
                    R.string.home_greeting_late_night_3,
                )
                else -> listOf( // 0..4
                    R.string.home_greeting_super_late_1,
                    R.string.home_greeting_super_late_2,
                    R.string.home_greeting_super_late_3,
                    R.string.home_greeting_super_late_4,
                    R.string.home_greeting_super_late_5,
                )
            }
            // Use different seed on each install, but keep the same seed across sessions
            updateValues(context, homeGreetingMessageIndex, homeGreetingMessageSeed, messages).also {
                homeGreetingMessage = it
            }
        }
    }

    /**
     * Witty patcher message.
     */
    fun getPatcherMessage(context: Context): Int {
        // Message changes each time called
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

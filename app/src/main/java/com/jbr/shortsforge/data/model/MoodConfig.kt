package com.jbr.shortsforge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The 6 mood categories. Each maps to its own folder set.
 * [dayOfWeek] controls which day of the week this mood is used for auto-upload.
 * Calendar.SUNDAY = 1, MONDAY = 2 … SATURDAY = 7.
 */
enum class VideoMood(
    val label: String,
    val emoji: String,
    val defaultDay: Int,            // Calendar.DAY_OF_WEEK constant
    val defaultQuotes: List<String>
) {
    HAPPY(
        label = "Happy",
        emoji = "😊",
        defaultDay = 2, // Monday
        defaultQuotes = listOf(
            "Happiness is a choice. Choose it every day.",
            "Smile, it's the key that fits the lock of everybody's heart.",
            "Joy is not in things; it is in us.",
            "The most wasted of all days is one without laughter.",
            "Happiness is not something ready-made. It comes from your own actions.",
            "Count your blessings, not your problems.",
            "A happy soul is the best shield for a cruel world.",
            "Be the reason someone smiles today.",
            "Let your joy be unconfined!"
        )
    ),
    MOTIVATION(
        label = "Motivation",
        emoji = "🔥",
        defaultDay = 3, // Tuesday
        defaultQuotes = listOf(
            "Push yourself, because no one else is going to do it for you.",
            "Success doesn't just find you. You have to go out and get it.",
            "Dream it. Believe it. Build it.",
            "Great things never come from comfort zones.",
            "The secret of getting ahead is getting started.",
            "Don't stop when you're tired. Stop when you're done.",
            "Wake up with determination. Go to bed with satisfaction.",
            "Do something today that your future self will thank you for.",
            "It always seems impossible until it's done.",
            "Believe you can and you're halfway there."
        )
    ),
    LOVE(
        label = "Love",
        emoji = "❤️",
        defaultDay = 4, // Wednesday
        defaultQuotes = listOf(
            "Love is not about how many days, months, or years you have been together.",
            "The best thing to hold onto in life is each other.",
            "Where there is love there is life.",
            "Love recognizes no barriers.",
            "Love is a language spoken by everyone but understood only by the heart.",
            "You are my today and all of my tomorrows.",
            "In all the world, there is no heart for me like yours.",
            "Love is the bridge between two hearts."
        )
    ),
    SAD(
        label = "Sad",
        emoji = "😢",
        defaultDay = 5, // Thursday
        defaultQuotes = listOf(
            "It's okay to not be okay.",
            "Tears come from the heart and not from the brain.",
            "The pain you feel today will be the strength you feel tomorrow.",
            "Even the darkest night will end and the sun will rise.",
            "You are allowed to be both a masterpiece and a work in progress.",
            "Healing is not linear.",
            "Give yourself the same compassion you would give a good friend.",
            "Every storm runs out of rain."
        )
    ),
    ANGRY(
        label = "Angry",
        emoji = "😤",
        defaultDay = 6, // Friday
        defaultQuotes = listOf(
            "Speak when you are angry and you will make the best speech you will ever regret.",
            "For every minute you remain angry, you give up sixty seconds of peace of mind.",
            "Anger is one letter short of danger.",
            "The best fighter is never angry.",
            "Holding on to anger is like drinking poison and expecting the other person to die.",
            "Take a deep breath. It's just a bad day, not a bad life.",
            "Respond, don't react.",
            "Channel your anger into fuel for change."
        )
    ),
    LIFE_ADVICE(
        label = "Life Advice",
        emoji = "🌟",
        defaultDay = 7, // Saturday
        defaultQuotes = listOf(
            "Life is 10% what happens to you and 90% how you react to it.",
            "Don't count the days, make the days count.",
            "The only way to do great work is to love what you do.",
            "In the end, it's not the years in your life that count. It's the life in your years.",
            "Life is short, make it sweet.",
            "You only live once, but if you do it right, once is enough.",
            "Don't regret the past, just learn from it.",
            "Be yourself; everyone else is already taken.",
            "The purpose of our lives is to be happy.",
            "Live in the sunshine, swim in the sea, drink the wild air."
        )
    );

    companion object {
        /** Returns the mood assigned to today's day-of-week, or MOTIVATION as fallback. */
        fun forToday(): VideoMood {
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            return values().firstOrNull { it.defaultDay == today } ?: MOTIVATION
        }
    }
}

/**
 * Stores the per-mood folder configuration in Room.
 * Each mood has:
 *  - imagesFolderUri  → folder containing images for that mood
 *  - videoFolderUri   → folder containing source videos for clip-based mood shorts
 *  - musicFolderUri   → folder containing background music for that mood
 *  - customQuotes     → pipe-separated list of user quotes (overrides defaults when non-empty)
 *  - dayOfWeek        → which day 1–7 this mood fires (can be customized by user)
 *  - enabled          → whether this mood is active in the rotation
 */
@Entity(tableName = "mood_configs")
data class MoodConfig(
    @PrimaryKey
    val mood: String,              // VideoMood.name

    val imagesFolderUri: String = "",
    val videoFolderUri: String  = "",
    val musicFolderUri: String  = "",
    val customQuotes: String    = "",   // pipe-separated: "quote1|quote2|..."

    val dayOfWeek: Int          = 2,    // Calendar.DAY_OF_WEEK (default Monday)
    val hour: Int               = 10,   // 24h format
    val minute: Int             = 0,
    val enabled: Boolean        = true
) {
    fun parsedQuotes(): List<String> =
        customQuotes.split("|").map { it.trim() }.filter { it.isNotBlank() }
}

package com.jbr.shortsforge.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jbr.shortsforge.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val IMAGES_PER_SHORT = intPreferencesKey("images_per_short")
        val VIDEO_DURATION = intPreferencesKey("video_duration")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val DEFAULT_TRANSITION = stringPreferencesKey("default_transition")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val OUTPUT_RESOLUTION = stringPreferencesKey("output_resolution")
        val AUTO_ADD_TEXT_OVERLAY = booleanPreferencesKey("auto_add_text_overlay")
        val DEFAULT_FILE_NAME = stringPreferencesKey("default_file_name")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val AUTO_UPLOAD_ENABLED = booleanPreferencesKey("auto_upload_enabled")
        val AUTO_UPLOAD_HOUR = intPreferencesKey("auto_upload_hour")
        val AUTO_UPLOAD_MINUTE = intPreferencesKey("auto_upload_minute")
        val AUTO_UPLOAD_TITLE = stringPreferencesKey("auto_upload_title")
        val HOURLY_UPLOAD_ENABLED = booleanPreferencesKey("hourly_upload_enabled")
        val YT_ACCOUNT_EMAIL = stringPreferencesKey("yt_account_email")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            imagesPerShort = prefs[Keys.IMAGES_PER_SHORT] ?: 5,
            videoDuration = prefs[Keys.VIDEO_DURATION] ?: 15,
            aspectRatio = prefs[Keys.ASPECT_RATIO] ?: "9:16",
            defaultTransition = prefs[Keys.DEFAULT_TRANSITION] ?: "Random",
            defaultFilter = prefs[Keys.DEFAULT_FILTER] ?: "Random",
            outputResolution = prefs[Keys.OUTPUT_RESOLUTION] ?: "1080p",
            autoAddTextOverlay = prefs[Keys.AUTO_ADD_TEXT_OVERLAY] ?: true,
            defaultFileName = prefs[Keys.DEFAULT_FILE_NAME] ?: "ShortsForge_Video",
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 9,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            autoUploadEnabled = prefs[Keys.AUTO_UPLOAD_ENABLED] ?: false,
            autoUploadHour = prefs[Keys.AUTO_UPLOAD_HOUR] ?: 10,
            autoUploadMinute = prefs[Keys.AUTO_UPLOAD_MINUTE] ?: 0,
            autoUploadTitle = prefs[Keys.AUTO_UPLOAD_TITLE] ?: "",
            hourlyUploadEnabled = prefs[Keys.HOURLY_UPLOAD_ENABLED] ?: false,
            ytAccountEmail = prefs[Keys.YT_ACCOUNT_EMAIL] ?: ""
        )
    }

    suspend fun updateImagesPerShort(value: Int) {
        dataStore.edit { it[Keys.IMAGES_PER_SHORT] = value }
    }

    suspend fun updateVideoDuration(value: Int) {
        dataStore.edit { it[Keys.VIDEO_DURATION] = value }
    }

    suspend fun updateAspectRatio(value: String) {
        dataStore.edit { it[Keys.ASPECT_RATIO] = value }
    }

    suspend fun updateDefaultTransition(value: String) {
        dataStore.edit { it[Keys.DEFAULT_TRANSITION] = value }
    }

    suspend fun updateDefaultFilter(value: String) {
        dataStore.edit { it[Keys.DEFAULT_FILTER] = value }
    }

    suspend fun updateOutputResolution(value: String) {
        dataStore.edit { it[Keys.OUTPUT_RESOLUTION] = value }
    }

    suspend fun updateAutoAddTextOverlay(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_ADD_TEXT_OVERLAY] = value }
    }

    suspend fun updateDefaultFileName(value: String) {
        dataStore.edit { it[Keys.DEFAULT_FILE_NAME] = value }
    }

    suspend fun updateReminderEnabled(value: Boolean) {
        dataStore.edit { it[Keys.REMINDER_ENABLED] = value }
    }

    suspend fun updateReminderTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun updateAutoUploadEnabled(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_UPLOAD_ENABLED] = value }
    }

    suspend fun updateAutoUploadTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[Keys.AUTO_UPLOAD_HOUR] = hour
            it[Keys.AUTO_UPLOAD_MINUTE] = minute
        }
    }

    suspend fun updateAutoUploadTitle(value: String) {
        dataStore.edit { it[Keys.AUTO_UPLOAD_TITLE] = value }
    }

    suspend fun updateHourlyUploadEnabled(value: Boolean) {
        dataStore.edit { it[Keys.HOURLY_UPLOAD_ENABLED] = value }
    }

    suspend fun updateYtAccountEmail(email: String) {
        dataStore.edit { it[Keys.YT_ACCOUNT_EMAIL] = email }
    }
}
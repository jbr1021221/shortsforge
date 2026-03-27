package com.jbr.shortsforge.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class ThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name  -> ThemeMode.DARK
            else                 -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }
}
package com.jbr.shortsforge.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.preferences.ThemeMode
import com.jbr.shortsforge.data.preferences.ThemePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePrefs: ThemePreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePrefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePrefs.setThemeMode(mode) }
    }

    /** Cycles: SYSTEM → DARK → LIGHT → SYSTEM */
    fun cycleTheme() {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK   -> ThemeMode.LIGHT
            ThemeMode.LIGHT  -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }
}
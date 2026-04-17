package com.jbr.shortsforge.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.AudioItem
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.preferences.FolderPreferencesRepository
import com.jbr.shortsforge.data.repository.TemplateRepository
import com.jbr.shortsforge.engine.MusicManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorUiState(
    val slides: List<SlideItem> = emptyList(),
    val selectedIndex: Int = 0,
    val musicSettings: MusicSettings = MusicSettings(),
    val availableMusic: List<AudioItem> = emptyList(),
    val isLoadingMusic: Boolean = false,
    val hasMusicFolder: Boolean = false,
    // Selected audio item full duration for trim slider
    val selectedAudioDurationMs: Long = 0L
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val musicManager: MusicManager,
    private val folderPrefs: FolderPreferencesRepository,
    private val settingsRepository: AppSettingsRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun setSlides(slides: List<SlideItem>) {
        if (_uiState.value.slides.isEmpty()) {
            viewModelScope.launch {
                // Auto-apply the default template (if one is set) to all slides
                val defaultTemplateId = settingsRepository.settingsFlow.first().defaultTemplateId
                val finalSlides = if (defaultTemplateId != null) {
                    val template = templateRepository.getById(defaultTemplateId)
                    template?.let { t ->
                        slides.map { slide ->
                            slide.copy(
                                filterName     = t.filterName,
                                transitionName = t.transitionName,
                                durationMs     = t.durationMs,
                                fontSize       = t.fontSize,
                                textColor      = t.textColor,
                                textPosition   = t.textPosition
                            )
                        }
                    } ?: slides
                } else slides

                _uiState.value = _uiState.value.copy(slides = finalSlides)
                checkMusicFolder()
            }
        }
    }

    private fun checkMusicFolder() {
        viewModelScope.launch {
            val folderUriString = folderPrefs.folderUriFlow.first() ?: return@launch
            val folderUri = Uri.parse(folderUriString)
            val hasMusic = withContext(Dispatchers.IO) {
                musicManager.hasMusicFolder(folderUri)
            }
            _uiState.value = _uiState.value.copy(hasMusicFolder = hasMusic)
        }
    }

    fun loadMusicFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMusic = true)
            val folderUriString = folderPrefs.folderUriFlow.first() ?: return@launch
            val folderUri = Uri.parse(folderUriString)
            val music = withContext(Dispatchers.IO) {
                musicManager.scanMusicFolder(folderUri)
            }
            _uiState.value = _uiState.value.copy(
                availableMusic = music,
                isLoadingMusic = false
            )
        }
    }

    fun selectMusic(audio: AudioItem) {
        // Auto-set trimEnd = video duration so music fits video exactly
        val videoDurationMs = _uiState.value.slides.sumOf { it.durationMs }.toLong()
        val autoTrimEnd = if (videoDurationMs > 0 && videoDurationMs < audio.durationMs)
            videoDurationMs else audio.durationMs

        _uiState.value = _uiState.value.copy(
            musicSettings = _uiState.value.musicSettings.copy(
                selectedMusicUri = audio.uri,
                selectedMusicName = audio.fileName,
                isMusicEnabled = true,
                trimStartMs = 0L,
                trimEndMs = autoTrimEnd
            ),
            selectedAudioDurationMs = audio.durationMs
        )
    }

    // Called when user drags the music window on the timeline
    fun updateMusicStartPosition(startMs: Long) {
        val current = _uiState.value.musicSettings
        val totalDuration = _uiState.value.selectedAudioDurationMs
        val clipLength = current.trimEndMs - current.trimStartMs
        val newStart = startMs.coerceIn(0L, totalDuration - clipLength)
        val newEnd = (newStart + clipLength).coerceAtMost(totalDuration)
        _uiState.value = _uiState.value.copy(
            musicSettings = current.copy(trimStartMs = newStart, trimEndMs = newEnd)
        )
    }

    // Returns total video duration in ms
    fun getVideoDurationMs(): Long = _uiState.value.slides.sumOf { it.durationMs }.toLong()

    fun removeMusic() {
        _uiState.value = _uiState.value.copy(
            musicSettings = MusicSettings(),
            selectedAudioDurationMs = 0L
        )
    }

    fun toggleMusic(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            musicSettings = _uiState.value.musicSettings.copy(isMusicEnabled = enabled)
        )
    }

    fun updateMusicVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(
            musicSettings = _uiState.value.musicSettings.copy(musicVolume = volume)
        )
    }

    // ── Trim functions ─────────────────────────────────────────────────────

    fun updateTrimStart(ms: Long) {
        val current = _uiState.value.musicSettings
        // Ensure start doesn't go past end - 1 second
        val safeStart = ms.coerceIn(0L, current.trimEndMs - 1000L)
        _uiState.value = _uiState.value.copy(
            musicSettings = current.copy(trimStartMs = safeStart)
        )
    }

    fun updateTrimEnd(ms: Long) {
        val current = _uiState.value.musicSettings
        val totalDuration = _uiState.value.selectedAudioDurationMs
        // Ensure end doesn't go before start + 1 second
        val safeEnd = ms.coerceIn(current.trimStartMs + 1000L, totalDuration)
        _uiState.value = _uiState.value.copy(
            musicSettings = current.copy(trimEndMs = safeEnd)
        )
    }

    // ── Slide functions ────────────────────────────────────────────────────

    fun selectSlide(index: Int) {
        _uiState.value = _uiState.value.copy(selectedIndex = index)
    }

    fun updateSelectedSlide(update: (SlideItem) -> SlideItem) {
        val currentSlides = _uiState.value.slides.toMutableList()
        val index = _uiState.value.selectedIndex
        if (index in currentSlides.indices) {
            currentSlides[index] = update(currentSlides[index])
            _uiState.value = _uiState.value.copy(slides = currentSlides)
        }
    }

    fun moveSlide(from: Int, to: Int) {
        val currentSlides = _uiState.value.slides.toMutableList()
        if (from in currentSlides.indices && to in currentSlides.indices) {
            val item = currentSlides.removeAt(from)
            currentSlides.add(to, item)
            val newSelectedIndex = when {
                _uiState.value.selectedIndex == from -> to
                _uiState.value.selectedIndex in
                        (minOf(from, to)..maxOf(from, to)) -> {
                    if (from < to) _uiState.value.selectedIndex - 1
                    else _uiState.value.selectedIndex + 1
                }
                else -> _uiState.value.selectedIndex
            }
            _uiState.value = _uiState.value.copy(
                slides = currentSlides,
                selectedIndex = newSelectedIndex
            )
        }
    }

    // ── Template apply ─────────────────────────────────────────────────────

    fun updateSlideAtIndex(index: Int, update: (SlideItem) -> SlideItem) {
        val current = _uiState.value.slides.toMutableList()
        if (index in current.indices) {
            current[index] = update(current[index])
            _uiState.value = _uiState.value.copy(slides = current)
        }
    }
}
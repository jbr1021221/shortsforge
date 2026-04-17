package com.jbr.shortsforge.ui.templates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.VideoTemplate
import com.jbr.shortsforge.data.preferences.AppSettingsRepository
import com.jbr.shortsforge.data.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplatesUiState(
    val templates: List<VideoTemplate> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = ""
) {
    val categories: List<String>
        get() = listOf("All") + templates.map { it.category }.distinct()

    val filtered: List<VideoTemplate>
        get() {
            var list = templates
            if (selectedCategory != "All") list = list.filter { it.category == selectedCategory }
            if (searchQuery.isNotBlank()) list = list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
            return list
        }
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
    private val settingsRepository: AppSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repository.seedBuiltInsIfNeeded() }
        viewModelScope.launch {
            repository.allTemplates.collect { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
        }
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun deleteTemplate(template: VideoTemplate) {
        viewModelScope.launch { repository.deleteTemplate(template) }
    }

    /** Saves this template as the default — applied automatically when opening the Editor. */
    fun setDefaultTemplate(id: Long) {
        viewModelScope.launch { settingsRepository.updateDefaultTemplateId(id) }
    }

    fun saveCurrentAsTemplate(
        name: String, emoji: String, description: String, category: String,
        filterName: String, transitionName: String, durationMs: Int,
        fontSize: Int, textColor: Int, textPosition: String,
        aspectRatio: String, outputResolution: String
    ) {
        viewModelScope.launch {
            repository.saveCustomTemplate(
                VideoTemplate(
                    name = name, emoji = emoji, description = description,
                    category = category, isBuiltIn = false,
                    filterName = filterName, transitionName = transitionName,
                    durationMs = durationMs, fontSize = fontSize,
                    textColor = textColor, textPosition = textPosition,
                    aspectRatio = aspectRatio, outputResolution = outputResolution
                )
            )
        }
    }
}

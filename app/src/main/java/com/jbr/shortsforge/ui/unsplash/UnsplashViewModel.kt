package com.jbr.shortsforge.ui.unsplash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.UnsplashPhoto
import com.jbr.shortsforge.data.repository.UnsplashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class UnsplashUiState(
    val query: String = "",
    val photos: List<UnsplashPhoto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UnsplashViewModel @Inject constructor(
    private val repository: UnsplashRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UnsplashUiState())
    val state: StateFlow<UnsplashUiState> = _state.asStateFlow()

    init { search("kaaba mecca") }

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = _state.value.copy(query = query, isLoading = true, error = null, selectedIds = emptySet())
        viewModelScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) { repository.search(query) }
                _state.value = _state.value.copy(
                    photos = photos,
                    isLoading = false,
                    error = if (photos.isEmpty()) "No photos found for \"$query\"" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Search failed. Check your Access Key.")
            }
        }
    }

    fun toggleSelect(id: String) {
        val current = _state.value.selectedIds.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _state.value = _state.value.copy(selectedIds = current)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    fun selectedPhotos(): List<UnsplashPhoto> =
        _state.value.photos.filter { it.id in _state.value.selectedIds }
}

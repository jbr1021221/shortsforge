package com.jbr.shortsforge.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.ImageItem
import com.jbr.shortsforge.data.model.ProfileEntity
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.repository.ProfileRepository
import com.jbr.shortsforge.data.repository.ProjectRepository
import com.jbr.shortsforge.data.repository.ImageRepository
import com.jbr.shortsforge.engine.AutoGenerateEngine
import com.jbr.shortsforge.data.model.SlideItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val folderUri: Uri? = null,
    val images: List<ImageItem> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedImageIds: LinkedHashSet<String> = LinkedHashSet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository,
    private val projectRepository: ProjectRepository,
    private val autoGenerateEngine: AutoGenerateEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<List<SlideItem>>()
    val navigationEvent: SharedFlow<List<SlideItem>> = _navigationEvent.asSharedFlow()

    init {
        // Load projects
        projectRepository.allProjects
            .onEach { projects ->
                _uiState.value = _uiState.value.copy(projects = projects)
            }
            .launchIn(viewModelScope)

        // ── React to active profile changes ───────────────────────────────
        // Whenever the active profile switches, reload the folder + images
        profileRepository.activeProfile
            .distinctUntilChanged { old, new ->
                old?.id == new?.id && old?.folderUri == new?.folderUri
            }
            .onEach { profile -> onActiveProfileChanged(profile) }
            .launchIn(viewModelScope)
    }

    private suspend fun onActiveProfileChanged(profile: ProfileEntity?) {
        if (profile == null || profile.folderUri.isBlank()) {
            _uiState.value = _uiState.value.copy(
                folderUri = null,
                images = emptyList(),
                isLoading = false,
                error = null,
                isSelectionMode = false,
                selectedImageIds = LinkedHashSet()
            )
            return
        }

        val uri = Uri.parse(profile.folderUri)
        _uiState.value = _uiState.value.copy(
            folderUri = uri,
            isLoading = true,
            error = null,
            isSelectionMode = false,
            selectedImageIds = LinkedHashSet()
        )
        loadImages(uri)
    }

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // Save folder to the active profile
            val activeProfile = profileRepository.activeProfile.first()
            if (activeProfile != null) {
                profileRepository.updateFolder(activeProfile.id, treeUri.toString())
            }

            _uiState.value = _uiState.value.copy(
                folderUri = treeUri,
                isLoading = true,
                error = null,
                isSelectionMode = false,
                selectedImageIds = LinkedHashSet()
            )
            loadImages(treeUri)
        }
    }

    fun refresh() {
        val currentUri = _uiState.value.folderUri ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch { loadImages(currentUri) }
    }

    private suspend fun loadImages(treeUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val images = imageRepository.scanFolder(treeUri)
                _uiState.value = _uiState.value.copy(
                    images = images,
                    isLoading = false,
                    error = if (images.isEmpty()) "No images found in this folder." else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to read folder: ${e.message}"
                )
            }
        }
    }

    // ── Selection Mode ─────────────────────────────────────────────────────

    fun toggleSelectionMode() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isSelectionMode = !current.isSelectionMode,
            selectedImageIds = LinkedHashSet()
        )
    }

    fun toggleImageSelection(imageId: String) {
        val current = _uiState.value
        val newSet = LinkedHashSet(current.selectedImageIds)
        if (newSet.contains(imageId)) newSet.remove(imageId) else newSet.add(imageId)
        _uiState.value = current.copy(selectedImageIds = newSet)
    }

    fun selectAll() {
        val allIds = LinkedHashSet(_uiState.value.images.map { it.id })
        _uiState.value = _uiState.value.copy(selectedImageIds = allIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedImageIds = LinkedHashSet())
    }

    // ── Create ─────────────────────────────────────────────────────────────

    fun onCreateShort() {
        val currentImages = _uiState.value.images
        if (currentImages.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val slides = autoGenerateEngine.generateShort(currentImages)
            _uiState.value = _uiState.value.copy(isLoading = false)
            _navigationEvent.emit(slides)
        }
    }

    fun onCreateFromSelected() {
        val current = _uiState.value
        if (current.selectedImageIds.size < 2) {
            Toast.makeText(context, "Select at least 2 images", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedImages = current.selectedImageIds
            .mapNotNull { id -> current.images.find { it.id == id } }

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true)
            val slides = autoGenerateEngine.generateShort(selectedImages)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSelectionMode = false,
                selectedImageIds = LinkedHashSet()
            )
            _navigationEvent.emit(slides)
        }
    }
}
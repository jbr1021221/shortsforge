package com.jbr.shortsforge.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbr.shortsforge.data.model.MusicSettings
import com.jbr.shortsforge.data.model.SlideItem
import com.jbr.shortsforge.engine.VideoExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val progress: Int = 0,
    val isExporting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val outputFile: File? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val videoExporter: VideoExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun startExport(slides: List<SlideItem>, musicSettings: MusicSettings = MusicSettings()) {
        _uiState.value = _uiState.value.copy(
            isExporting = true, progress = 0, error = null, isSuccess = false
        )

        videoExporter.exportVideo(
            slides = slides,
            musicSettings = musicSettings,
            callback = object : VideoExporter.ExportCallback {
                override fun onProgress(percentage: Int) {
                    _uiState.value = _uiState.value.copy(progress = percentage)
                }
                override fun onSuccess(outputFile: File) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false, isSuccess = true,
                        progress = 100, outputFile = outputFile
                    )
                }
                override fun onFailure(message: String) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false, isSuccess = false, error = message
                    )
                }
            }
        )
    }

    fun resetState() {
        _uiState.value = ExportUiState()
    }
}
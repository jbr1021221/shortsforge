package com.jbr.shortsforge.ui.photoeditor

import androidx.lifecycle.ViewModel
import com.jbr.shortsforge.data.model.KenBurnsConfig
import com.jbr.shortsforge.data.model.SlideItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PhotoEditorViewModel @Inject constructor() : ViewModel() {

    private val _slides = MutableStateFlow<List<SlideItem>>(emptyList())
    val slides: StateFlow<List<SlideItem>> = _slides.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private var originalSlides: List<SlideItem> = emptyList()

    fun loadSlides(initial: List<SlideItem>) {
        originalSlides = initial.toList()
        _slides.value = initial.toList()
    }

    fun selectSlide(index: Int) {
        _selectedIndex.value = index.coerceIn(0, (_slides.value.size - 1).coerceAtLeast(0))
    }

    fun moveSlide(from: Int, to: Int) {
        val list = _slides.value.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        _slides.value = list
    }

    fun updateSelectedSlide(updated: SlideItem) {
        val index = _selectedIndex.value
        val list = _slides.value.toMutableList()
        if (index < 0 || index >= list.size) return
        list[index] = updated
        _slides.value = list
    }

    fun resetSelectedSlide() {
        val index = _selectedIndex.value
        if (index < 0 || index >= originalSlides.size) return
        val list = _slides.value.toMutableList()
        list[index] = originalSlides[index]
        _slides.value = list
    }

    fun getFinalSlides(): List<SlideItem> = _slides.value

    // Ken Burns preset -> KenBurnsConfig mapping
    fun kenBurnsConfigForPreset(preset: String): KenBurnsConfig = when (preset) {
        "Slow Zoom In" -> KenBurnsConfig(
            startScale = 1.03f,
            endScale = 1.14f,
            startCenterX = 0.50f,
            startCenterY = 0.50f,
            endCenterX = 0.50f,
            endCenterY = 0.49f
        )
        "Slow Zoom Out" -> KenBurnsConfig(
            startScale = 1.15f,
            endScale = 1.04f,
            startCenterX = 0.50f,
            startCenterY = 0.49f,
            endCenterX = 0.50f,
            endCenterY = 0.50f
        )
        "Pan Left" -> KenBurnsConfig(
            startScale = 1.11f,
            endScale = 1.12f,
            startCenterX = 0.56f,
            startCenterY = 0.50f,
            endCenterX = 0.44f,
            endCenterY = 0.50f
        )
        "Pan Right" -> KenBurnsConfig(
            startScale = 1.11f,
            endScale = 1.12f,
            startCenterX = 0.44f,
            startCenterY = 0.50f,
            endCenterX = 0.56f,
            endCenterY = 0.50f
        )
        "Pan Up" -> KenBurnsConfig(
            startScale = 1.10f,
            endScale = 1.12f,
            startCenterX = 0.50f,
            startCenterY = 0.56f,
            endCenterX = 0.50f,
            endCenterY = 0.44f
        )
        "Pan Down" -> KenBurnsConfig(
            startScale = 1.10f,
            endScale = 1.12f,
            startCenterX = 0.50f,
            startCenterY = 0.44f,
            endCenterX = 0.50f,
            endCenterY = 0.56f
        )
        "Cinematic Reveal" -> KenBurnsConfig(
            startScale = 1.16f,
            endScale = 1.06f,
            startCenterX = 0.50f,
            startCenterY = 0.58f,
            endCenterX = 0.50f,
            endCenterY = 0.48f
        )
        else -> KenBurnsConfig(
            startScale = 1.03f,
            endScale = 1.14f,
            startCenterX = 0.50f,
            startCenterY = 0.50f,
            endCenterX = 0.50f,
            endCenterY = 0.49f
        )
    }
}

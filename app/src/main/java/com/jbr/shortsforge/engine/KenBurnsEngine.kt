package com.jbr.shortsforge.engine

import android.graphics.Matrix
import android.util.Log
import com.jbr.shortsforge.data.model.KenBurnsConfig
import com.jbr.shortsforge.data.model.SlideItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KenBurnsEngine @Inject constructor() {
    
    /**
     * Calculates the Transformation Matrix for a specific frame in the Ken Burns effect.
     */
    fun getFrameMatrix(
        config: KenBurnsConfig,
        frameIndex: Int,
        totalFrames: Int,
        videoWidth: Int,
        videoHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Matrix {
        val progress = frameIndex.toFloat() / (totalFrames - 1).coerceAtLeast(1)
        
        // Interpolate Scale
        val currentScale = config.startScale + (config.endScale - config.startScale) * progress
        
        // Interpolate Center Points
        val currentCenterX = config.startCenterX + (config.endCenterX - config.startCenterX) * progress
        val currentCenterY = config.startCenterY + (config.endCenterY - config.startCenterY) * progress
        
        val matrix = Matrix()
        
        // 1. Initial Scale to fit the video dimensions (Aspect Fill)
        val scaleX = videoWidth.toFloat() / bitmapWidth
        val scaleY = videoHeight.toFloat() / bitmapHeight
        val baseScale = maxOf(scaleX, scaleY)
        
        // Apply Ken Burns zoom on top of base scale
        val finalScale = baseScale * currentScale
        matrix.postScale(finalScale, finalScale)
        
        // 2. Translation to center the animated focus point
        val scaledWidth = bitmapWidth * finalScale
        val scaledHeight = bitmapHeight * finalScale
        
        val focusX = currentCenterX * scaledWidth
        val focusY = currentCenterY * scaledHeight
        
        val transX = (videoWidth / 2f) - focusX
        val transY = (videoHeight / 2f) - focusY
        
        matrix.postTranslate(transX, transY)
        
        return matrix
    }

    fun randomKenBurns(): KenBurnsConfig {
        val startScale = 1.0f + (Math.random().toFloat() * 0.1f) // 1.0 to 1.1
        val endScale = startScale + 0.1f + (Math.random().toFloat() * 0.2f) // +0.1 to +0.3
        
        // Randomly decide between Zoom In and Zoom Out
        val shouldZoomIn = Math.random() > 0.5
        
        val sScale = if (shouldZoomIn) startScale else endScale
        val eScale = if (shouldZoomIn) endScale else startScale
        
        // Subtle pan
        val sCX = 0.45f + (Math.random().toFloat() * 0.1f)
        val sCY = 0.45f + (Math.random().toFloat() * 0.1f)
        val eCX = 0.45f + (Math.random().toFloat() * 0.1f)
        val eCY = 0.45f + (Math.random().toFloat() * 0.1f)
        
        return KenBurnsConfig(
            startScale = sScale,
            endScale = eScale,
            startCenterX = sCX,
            startCenterY = sCY,
            endCenterX = eCX,
            endCenterY = eCY
        )
    }
}

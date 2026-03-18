package com.jbr.shortsforge.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class KenBurnsConfig(
    val startScale: Float,
    val endScale: Float,
    val startCenterX: Float,
    val startCenterY: Float,
    val endCenterX: Float,
    val endCenterY: Float
) : Parcelable {
    companion object {
        fun default() = KenBurnsConfig(
            startScale = 1.0f,
            endScale = 1.15f,
            startCenterX = 0.5f,
            startCenterY = 0.5f,
            endCenterX = 0.5f,
            endCenterY = 0.5f
        )
    }
}

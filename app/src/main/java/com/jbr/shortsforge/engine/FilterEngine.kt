package com.jbr.shortsforge.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterEngine @Inject constructor() {

    suspend fun applyFilter(context: Context, imageUri: Uri, filterName: String): Bitmap {
        return withContext(Dispatchers.IO) {
            val bitmap = loadBitmapFromUri(context, imageUri) ?: throw IllegalArgumentException("Could not load bitmap from URI")
            applyFilterToBitmap(context, bitmap, filterName)
        }
    }

    fun previewFilter(bitmap: Bitmap, filterName: String, context: Context): Bitmap {
        return applyFilterToBitmap(context, bitmap, filterName)
    }

    private fun applyFilterToBitmap(context: Context, bitmap: Bitmap, filterName: String): Bitmap {
        val gpuImage = GPUImage(context)
        gpuImage.setImage(bitmap)
        
        val filter = when (filterName.lowercase()) {
            "none" -> null
            "vintage" -> GPUImageSepiaToneFilter()
            "bw" -> GPUImageGrayscaleFilter()
            "warm" -> GPUImageWhiteBalanceFilter().apply {
                setTemperature(6500f)
            }
            "cool" -> GPUImageWhiteBalanceFilter().apply {
                setTemperature(4000f)
            }
            "fade" -> GPUImageFilterGroup(
                listOf(
                    GPUImageBrightnessFilter(0.1f),
                    GPUImageContrastFilter(0.7f)
                )
            )
            "vivid" -> GPUImageSaturationFilter(1.5f)
            else -> null
        }
        
        if (filter != null) {
            gpuImage.setFilter(filter)
            return gpuImage.bitmapWithFilterApplied
        }
        
        return bitmap
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

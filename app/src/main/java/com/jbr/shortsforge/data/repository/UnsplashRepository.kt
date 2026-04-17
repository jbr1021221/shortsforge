package com.jbr.shortsforge.data.repository

import android.content.Context
import com.jbr.shortsforge.data.model.UnsplashPhoto
import com.jbr.shortsforge.data.remote.UnsplashConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnsplashRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    fun search(query: String, page: Int = 1, perPage: Int = 30): List<UnsplashPhoto> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${UnsplashConfig.BASE_URL}/search/photos" +
                "?query=$encoded&per_page=$perPage&page=$page&orientation=portrait"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Client-ID ${UnsplashConfig.ACCESS_KEY}")
            .addHeader("Accept-Version", "v1")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()
        val json  = JSONObject(body)
        val results = json.getJSONArray("results")

        return (0 until results.length()).mapNotNull { i ->
            try {
                val item = results.getJSONObject(i)
                val urls = item.getJSONObject("urls")
                val user = item.optJSONObject("user")
                UnsplashPhoto(
                    id               = item.getString("id"),
                    thumbUrl         = urls.getString("thumb"),
                    regularUrl       = urls.getString("regular"),
                    description      = item.optString("alt_description", "Unsplash photo"),
                    photographerName = user?.optString("name", "Unsplash") ?: "Unsplash"
                )
            } catch (e: Exception) { null }
        }
    }

    fun downloadToCache(context: Context, photo: UnsplashPhoto): File? {
        return try {
            val dir  = File(context.cacheDir, "unsplash").also { it.mkdirs() }
            val file = File(dir, "${photo.id}.jpg")
            if (file.exists()) return file

            // Request a 1080-wide JPEG from Unsplash CDN
            val request = Request.Builder()
                .url("${photo.regularUrl}&w=1080&q=85&fm=jpg")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) { null }
    }
}

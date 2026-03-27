package com.jbr.shortsforge.engine

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.model.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

object YouTubeUploadManager {

    private const val TAG = "YouTubeUploadManager"

    suspend fun uploadVideo(
        context: Context,
        email: String,
        videoFile: File,
        title: String,
        description: String,
        privacyStatus: String,
        onProgress: (Int) -> Unit,
        onSuccess: (videoId: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) {
                    withContext(Dispatchers.Main) { onError("Export video first") }
                    return@withContext
                }

                if (email.isBlank()) {
                    withContext(Dispatchers.Main) { onError("No YouTube account email provided") }
                    return@withContext
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf("https://www.googleapis.com/auth/youtube.upload")
                ).apply {
                    selectedAccountName = email
                }

                val youtube = YouTube.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ShortsForge").build()

                val videoObject = Video()

                val snippet = VideoSnippet()
                snippet.title = title
                snippet.description = "$description\n\n#Shorts #ShortsForge"
                snippet.tags = listOf("Shorts", "YouTubeShorts", "ShortsForge")
                snippet.categoryId = "22" // People & Blogs

                val status = VideoStatus()
                status.privacyStatus = privacyStatus.lowercase()

                videoObject.snippet = snippet
                videoObject.status = status

                val mediaContent = FileContent("video/*", videoFile)

                val videoInsert = youtube.videos()
                    .insert("snippet,statistics,status", videoObject, mediaContent)

                val uploader = videoInsert.mediaHttpUploader
                uploader.isDirectUploadEnabled = false
                uploader.chunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE
                uploader.setProgressListener(object : MediaHttpUploaderProgressListener {
                    override fun progressChanged(uploader: MediaHttpUploader) {
                        when (uploader.uploadState) {
                            MediaHttpUploader.UploadState.INITIATION_STARTED -> {
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { onProgress(0) }
                            }
                            MediaHttpUploader.UploadState.INITIATION_COMPLETE -> {
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { onProgress(5) }
                            }
                            MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                                val progress = (uploader.progress * 100).toInt()
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { onProgress(progress) }
                            }
                            MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { onProgress(100) }
                            }
                            MediaHttpUploader.UploadState.NOT_STARTED -> {}
                            null -> {}
                        }
                    }
                })

                val returnedVideo = videoInsert.execute()
                withContext(Dispatchers.Main) {
                    onSuccess(returnedVideo.id)
                }

            } catch (e: UserRecoverableAuthIOException) {
                Log.e(TAG, "Auth Error", e)
                withContext(Dispatchers.Main) {
                    onError("auth_required") // specific trigger to re-auth
                }
            } catch (e: IOException) {
                Log.e(TAG, "IO Error", e)
                withContext(Dispatchers.Main) {
                    if (e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                        onError("Check connection and try again")
                    } else {
                        onError(e.message ?: "Upload failed due to connection error")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload Error", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Upload failed")
                }
            }
        }
    }
}

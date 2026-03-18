package com.jbr.shortsforge.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.jbr.shortsforge.data.model.AudioItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MusicManager"
        private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac")
    }

    /**
     * Scans for a "Music" or "Audio" subfolder inside the given parent folder URI
     * and returns all audio files found.
     */
    fun scanMusicFolder(parentFolderUri: Uri): List<AudioItem> {
        val results = mutableListOf<AudioItem>()
        try {
            val parentDocId = DocumentsContract.getTreeDocumentId(parentFolderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentFolderUri, parentDocId
            )

            // Find Music or Audio subfolder
            val musicFolderDocId = findMusicSubfolder(parentFolderUri, childrenUri)
                ?: return emptyList()

            // Scan the music subfolder
            val musicChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentFolderUri, musicFolderDocId
            )

            context.contentResolver.query(
                musicChildrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2) ?: continue

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                            parentFolderUri, docId
                        )
                        val duration = getAudioDuration(fileUri)
                        results.add(
                            AudioItem(
                                id = UUID.randomUUID().toString(),
                                uri = fileUri.toString(),
                                fileName = name,
                                durationMs = duration
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning music folder", e)
        }
        return results
    }

    private fun findMusicSubfolder(treeUri: Uri, childrenUri: Uri): String? {
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2) ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                        (name.equals("Music", ignoreCase = true) ||
                                name.equals("Audio", ignoreCase = true) ||
                                name.equals("audio", ignoreCase = true))
                    ) {
                        return docId
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding music subfolder", e)
        }
        return null
    }

    private fun getAudioDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (e: Exception) {
            0L
        }
    }

    fun hasMusicFolder(parentFolderUri: Uri): Boolean {
        return try {
            val parentDocId = DocumentsContract.getTreeDocumentId(parentFolderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentFolderUri, parentDocId
            )
            findMusicSubfolder(parentFolderUri, childrenUri) != null
        } catch (e: Exception) {
            false
        }
    }
}
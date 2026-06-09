package com.jbr.shortsforge.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.jbr.shortsforge.data.model.VideoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val supportedMimeTypes = setOf(
        "video/mp4",
        "video/quicktime",
        "video/3gpp",
        "video/webm",
        "video/x-matroska"
    )

    fun scanFolder(treeUri: Uri): List<VideoItem> {
        return scanFolderByDocumentId(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    }

    fun scanAutoUploadVideoFolder(rootTreeUri: Uri): List<VideoItem> {
        return scanFirstNamedChildFolder(rootTreeUri, listOf("video", "videos"))
    }

    fun scanNamedChildFolder(rootTreeUri: Uri, folderName: String): List<VideoItem> {
        return scanFirstNamedChildFolder(rootTreeUri, listOf(folderName))
    }

    private fun scanFirstNamedChildFolder(rootTreeUri: Uri, folderNames: List<String>): List<VideoItem> {
        val rootDocId = DocumentsContract.getTreeDocumentId(rootTreeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, rootDocId)
        val acceptedNames = folderNames.map { it.lowercase() }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (it.moveToNext()) {
                val name = it.getString(nameCol) ?: continue
                val mime = it.getString(mimeCol) ?: continue
                if (acceptedNames.contains(name.lowercase()) &&
                    mime == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return scanFolderByDocumentId(rootTreeUri, it.getString(idCol))
                }
            }
        }

        return emptyList()
    }

    private fun scanFolderByDocumentId(treeUri: Uri, documentId: String): List<VideoItem> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val videos = mutableListOf<VideoItem>()
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (it.moveToNext()) {
                val mimeType = it.getString(mimeCol) ?: continue
                if (mimeType !in supportedMimeTypes) continue

                val docId = it.getString(idCol)
                val name = it.getString(nameCol)
                val modified = it.getLong(modCol)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val duration = getVideoDuration(docUri)
                if (duration <= 0L) continue

                videos.add(
                    VideoItem(
                        id = docId,
                        uri = docUri.toString(),
                        fileName = name,
                        durationMs = duration,
                        dateModified = modified
                    )
                )
            }
        }

        return videos.sortedByDescending { it.dateModified }
    }

    private fun getVideoDuration(uri: Uri): Long {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}

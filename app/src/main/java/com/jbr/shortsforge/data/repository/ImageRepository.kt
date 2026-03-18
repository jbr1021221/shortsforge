package com.jbr.shortsforge.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.jbr.shortsforge.data.model.ImageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    /**
     * Scans the SAF tree at [treeUri] and returns all image files
     * matching jpg/jpeg/png/webp. Uses DocumentsContract for maximum
     * compatibility — no external DocumentFile library needed.
     */
    fun scanFolder(treeUri: Uri): List<ImageItem> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val images = mutableListOf<ImageItem>()

        val cursor = context.contentResolver.query(
            childrenUri, projection, null, null, null
        ) ?: return emptyList()

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

                images.add(
                    ImageItem(
                        id = docId,
                        uri = docUri,
                        fileName = name,
                        dateModified = modified
                    )
                )
            }
        }

        return images.sortedByDescending { it.dateModified }
    }
}

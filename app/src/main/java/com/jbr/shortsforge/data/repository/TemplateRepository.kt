package com.jbr.shortsforge.data.repository

import com.jbr.shortsforge.data.database.dao.VideoTemplateDao
import com.jbr.shortsforge.data.model.VideoTemplate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(
    private val dao: VideoTemplateDao
) {
    val allTemplates: Flow<List<VideoTemplate>> = dao.getAllTemplates()

    /** Seeds built-in templates if not already present. Called from ViewModel init. */
    suspend fun seedBuiltInsIfNeeded() {
        if (dao.builtInCount() == 0) {
            BuiltInTemplates.all.forEach { dao.insert(it) }
        }
    }

    suspend fun saveCustomTemplate(template: VideoTemplate): Long {
        return dao.insert(template.copy(isBuiltIn = false))
    }

    suspend fun deleteTemplate(template: VideoTemplate) {
        if (!template.isBuiltIn) dao.delete(template)
    }

    suspend fun getById(id: Long): VideoTemplate? = dao.getById(id)
}

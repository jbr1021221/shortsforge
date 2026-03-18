package com.jbr.shortsforge.data.repository

import com.jbr.shortsforge.data.database.dao.ProjectDao
import com.jbr.shortsforge.data.database.dao.ProjectImageDao
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val projectImageDao: ProjectImageDao
) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun insertProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteProject(project)
        projectImageDao.deleteImagesForProject(project.id)
    }

    suspend fun getProjectById(id: Long): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    fun getImagesForProject(projectId: Long): Flow<List<ProjectImageEntity>> {
        return projectImageDao.getImagesForProject(projectId)
    }

    suspend fun insertProjectImage(image: ProjectImageEntity): Long {
        return projectImageDao.insertImage(image)
    }

    suspend fun updateProjectImage(image: ProjectImageEntity) {
        projectImageDao.updateImage(image)
    }

    suspend fun deleteProjectImage(image: ProjectImageEntity) {
        projectImageDao.deleteImage(image)
    }
}

package com.jbr.shortsforge.data.database.dao

import androidx.room.*
import com.jbr.shortsforge.data.model.ProjectEntity
import com.jbr.shortsforge.data.model.ProjectImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?
}

@Dao
interface ProjectImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProjectImageEntity): Long

    @Update
    suspend fun updateImage(image: ProjectImageEntity)

    @Delete
    suspend fun deleteImage(image: ProjectImageEntity)

    @Query("SELECT * FROM project_images WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getImagesForProject(projectId: Long): Flow<List<ProjectImageEntity>>

    @Query("SELECT * FROM project_images WHERE id = :id")
    suspend fun getImageById(id: Long): ProjectImageEntity?
    
    @Query("DELETE FROM project_images WHERE projectId = :projectId")
    suspend fun deleteImagesForProject(projectId: Long)
}

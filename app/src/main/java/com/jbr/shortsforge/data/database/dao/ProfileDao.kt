package com.jbr.shortsforge.data.database.dao

import androidx.room.*
import com.jbr.shortsforge.data.model.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun clearActiveProfile()

    @Query("UPDATE profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActiveProfile(id: Long)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}
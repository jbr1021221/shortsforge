package com.jbr.shortsforge.data.repository

import com.jbr.shortsforge.data.database.dao.ProfileDao
import com.jbr.shortsforge.data.model.ProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    val allProfiles: Flow<List<ProfileEntity>> = profileDao.getAllProfiles()
    val activeProfile: Flow<ProfileEntity?> = profileDao.getActiveProfile()

    suspend fun createProfile(name: String): Long {
        val profile = ProfileEntity(name = name)
        val id = profileDao.insertProfile(profile)
        // If this is the first profile, make it active automatically
        if (profileDao.getProfileCount() == 1) {
            setActiveProfile(id)
        }
        return id
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: ProfileEntity) {
        profileDao.deleteProfile(profile)
    }

    suspend fun setActiveProfile(id: Long) {
        profileDao.clearActiveProfile()
        profileDao.setActiveProfile(id)
    }

    suspend fun getProfileById(id: Long): ProfileEntity? {
        return profileDao.getProfileById(id)
    }

    suspend fun getProfileCount(): Int {
        return profileDao.getProfileCount()
    }

    // ── Convenience update helpers ─────────────────────────────────────────

    suspend fun updateFolder(profileId: Long, folderUri: String) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(folderUri = folderUri))
    }

    suspend fun updateYouTube(profileId: Long, email: String, name: String) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(ytAccountEmail = email, ytAccountName = name))
    }

    suspend fun updateFacebook(profileId: Long, token: String, pageId: String, pageToken: String) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(
            fbAccessToken = token,
            fbPageId = pageId,
            fbPageAccessToken = pageToken
        ))
    }

    suspend fun updateInstagram(profileId: Long, igUserId: String) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(igUserId = igUserId))
    }

    suspend fun updateTikTok(profileId: Long, accessToken: String, openId: String, key: String, secret: String) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(
            tiktokAccessToken = accessToken,
            tiktokOpenId = openId,
            tiktokClientKey = key,
            tiktokClientSecret = secret
        ))
    }

    suspend fun updateSchedule(profileId: Long, enabled: Boolean, hour: Int, minute: Int, hourly: Boolean) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(
            autoUploadEnabled = enabled,
            autoUploadHour = hour,
            autoUploadMinute = minute,
            hourlyUploadEnabled = hourly
        ))
    }

    suspend fun disconnectFacebook(profileId: Long) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(fbAccessToken = "", fbPageId = "", fbPageAccessToken = ""))
    }

    suspend fun disconnectInstagram(profileId: Long) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(igUserId = ""))
    }

    suspend fun disconnectTikTok(profileId: Long) {
        val profile = profileDao.getProfileById(profileId) ?: return
        profileDao.updateProfile(profile.copy(
            tiktokAccessToken = "", tiktokOpenId = "",
            tiktokClientKey = "", tiktokClientSecret = ""
        ))
    }
}
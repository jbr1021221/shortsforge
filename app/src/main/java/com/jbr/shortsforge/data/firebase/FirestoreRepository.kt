package com.jbr.shortsforge.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.jbr.shortsforge.data.model.AppSettings
import com.jbr.shortsforge.data.model.EditingMode
import com.jbr.shortsforge.data.model.ProfileEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: FirebaseAuthRepository
) {
    private fun userDoc() = authRepository.uid?.let { firestore.collection("users").document(it) }

    // ── AppSettings ───────────────────────────────────────────────────────────

    suspend fun saveSettings(settings: AppSettings) {
        val doc = userDoc() ?: return
        doc.collection("data").document("settings").set(settings.toMap()).await()
    }

    suspend fun loadSettings(): AppSettings? {
        val doc = userDoc() ?: return null
        val snap = doc.collection("data").document("settings").get().await()
        if (!snap.exists()) return null
        return snap.data?.toAppSettings()
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    suspend fun saveProfile(profile: ProfileEntity) {
        val doc = userDoc() ?: return
        doc.collection("profiles").document(profile.id.toString()).set(profile.toMap()).await()
    }

    suspend fun saveAllProfiles(profiles: List<ProfileEntity>) {
        profiles.forEach { saveProfile(it) }
    }

    suspend fun loadProfiles(): List<ProfileEntity> {
        val doc = userDoc() ?: return emptyList()
        val snap = doc.collection("profiles").get().await()
        return snap.documents.mapNotNull { it.data?.toProfileEntity() }
    }

    suspend fun deleteProfile(profileId: Long) {
        val doc = userDoc() ?: return
        doc.collection("profiles").document(profileId.toString()).delete().await()
    }
}

// ── AppSettings ↔ Map ─────────────────────────────────────────────────────────

private fun AppSettings.toMap(): Map<String, Any?> = mapOf(
    "imagesPerShort"        to imagesPerShort,
    "videoDuration"         to videoDuration,
    "aspectRatio"           to aspectRatio,
    "defaultTransition"     to defaultTransition,
    "defaultFilter"         to defaultFilter,
    "outputResolution"      to outputResolution,
    "autoAddTextOverlay"    to autoAddTextOverlay,
    "defaultFileName"       to defaultFileName,
    "reminderEnabled"       to reminderEnabled,
    "reminderHour"          to reminderHour,
    "reminderMinute"        to reminderMinute,
    "autoUploadEnabled"     to autoUploadEnabled,
    "autoUploadHour"        to autoUploadHour,
    "autoUploadMinute"      to autoUploadMinute,
    "autoUploadTitle"       to autoUploadTitle,
    "hourlyUploadEnabled"   to hourlyUploadEnabled,
    "biHourlyUploadEnabled" to biHourlyUploadEnabled,
    "sixHourlyUploadEnabled" to sixHourlyUploadEnabled,
    "ytAccountEmail"        to ytAccountEmail,
    "imageCooldownEnabled"  to imageCooldownEnabled,
    "imageCooldownDays"     to imageCooldownDays,
    "defaultTemplateId"     to defaultTemplateId,
    "unsplashEnabled"       to unsplashEnabled
)

private fun Map<String, Any?>.toAppSettings(): AppSettings {
    fun int(key: String, default: Int) = (get(key) as? Long)?.toInt() ?: (get(key) as? Int) ?: default
    fun bool(key: String, default: Boolean) = get(key) as? Boolean ?: default
    fun str(key: String, default: String) = get(key) as? String ?: default
    return AppSettings(
        imagesPerShort        = int("imagesPerShort", 5),
        videoDuration         = int("videoDuration", 30),
        aspectRatio           = str("aspectRatio", "9:16"),
        defaultTransition     = str("defaultTransition", "Random"),
        defaultFilter         = str("defaultFilter", "Random"),
        outputResolution      = str("outputResolution", "1080p"),
        autoAddTextOverlay    = bool("autoAddTextOverlay", true),
        defaultFileName       = str("defaultFileName", "ShortsForge_Video"),
        reminderEnabled       = bool("reminderEnabled", false),
        reminderHour          = int("reminderHour", 9),
        reminderMinute        = int("reminderMinute", 0),
        autoUploadEnabled     = bool("autoUploadEnabled", false),
        autoUploadHour        = int("autoUploadHour", 10),
        autoUploadMinute      = int("autoUploadMinute", 0),
        autoUploadTitle       = str("autoUploadTitle", ""),
        hourlyUploadEnabled   = bool("hourlyUploadEnabled", false),
        biHourlyUploadEnabled = bool("biHourlyUploadEnabled", false),
        sixHourlyUploadEnabled= bool("sixHourlyUploadEnabled", false),
        ytAccountEmail        = str("ytAccountEmail", ""),
        imageCooldownEnabled  = bool("imageCooldownEnabled", false),
        imageCooldownDays     = int("imageCooldownDays", 7),
        defaultTemplateId     = (get("defaultTemplateId") as? Long),
        unsplashEnabled       = bool("unsplashEnabled", true)
    )
}

// ── ProfileEntity ↔ Map ───────────────────────────────────────────────────────

private fun ProfileEntity.toMap(): Map<String, Any?> = mapOf(
    "id"                     to id,
    "name"                   to name,
    "folderUri"              to folderUri,
    "ytAccountEmail"         to ytAccountEmail,
    "ytAccountName"          to ytAccountName,
    "fbAccessToken"          to fbAccessToken,
    "fbPageId"               to fbPageId,
    "fbPageAccessToken"      to fbPageAccessToken,
    "igUserId"               to igUserId,
    "tiktokAccessToken"      to tiktokAccessToken,
    "tiktokRefreshToken"     to tiktokRefreshToken,
    "tiktokTokenExpiry"      to tiktokTokenExpiry,
    "tiktokOpenId"           to tiktokOpenId,
    "tiktokClientKey"        to tiktokClientKey,
    "tiktokClientSecret"     to tiktokClientSecret,
    "autoUploadEnabled"      to autoUploadEnabled,
    "autoUploadHour"         to autoUploadHour,
    "autoUploadMinute"       to autoUploadMinute,
    "hourlyUploadEnabled"    to hourlyUploadEnabled,
    "biHourlyUploadEnabled"  to biHourlyUploadEnabled,
    "sixHourlyUploadEnabled" to sixHourlyUploadEnabled,
    "autoUploadTitle"        to autoUploadTitle,
    "imagesPerShort"         to imagesPerShort,
    "videoDuration"          to videoDuration,
    "defaultFilter"          to defaultFilter,
    "defaultTransition"      to defaultTransition,
    "autoAddTextOverlay"     to autoAddTextOverlay,
    "uploadSourceMode"       to uploadSourceMode,
    "editingMode"            to editingMode.name,
    "createdAt"              to createdAt,
    "isActive"               to isActive
)

private fun Map<String, Any?>.toProfileEntity(): ProfileEntity {
    fun long(key: String, default: Long = 0L) = (get(key) as? Long) ?: default
    fun int(key: String, default: Int) = (get(key) as? Long)?.toInt() ?: (get(key) as? Int) ?: default
    fun bool(key: String, default: Boolean = false) = get(key) as? Boolean ?: default
    fun str(key: String, default: String = "") = get(key) as? String ?: default
    return ProfileEntity(
        id                    = long("id"),
        name                  = str("name", "Profile"),
        folderUri             = str("folderUri"),
        ytAccountEmail        = str("ytAccountEmail"),
        ytAccountName         = str("ytAccountName"),
        fbAccessToken         = str("fbAccessToken"),
        fbPageId              = str("fbPageId"),
        fbPageAccessToken     = str("fbPageAccessToken"),
        igUserId              = str("igUserId"),
        tiktokAccessToken     = str("tiktokAccessToken"),
        tiktokRefreshToken    = str("tiktokRefreshToken"),
        tiktokTokenExpiry     = long("tiktokTokenExpiry"),
        tiktokOpenId          = str("tiktokOpenId"),
        tiktokClientKey       = str("tiktokClientKey"),
        tiktokClientSecret    = str("tiktokClientSecret"),
        autoUploadEnabled     = bool("autoUploadEnabled"),
        autoUploadHour        = int("autoUploadHour", 10),
        autoUploadMinute      = int("autoUploadMinute", 0),
        hourlyUploadEnabled   = bool("hourlyUploadEnabled"),
        biHourlyUploadEnabled = bool("biHourlyUploadEnabled"),
        sixHourlyUploadEnabled= bool("sixHourlyUploadEnabled"),
        autoUploadTitle       = str("autoUploadTitle"),
        imagesPerShort        = int("imagesPerShort", 5),
        videoDuration         = int("videoDuration", 30),
        defaultFilter         = str("defaultFilter", "Random"),
        defaultTransition     = str("defaultTransition", "Random"),
        autoAddTextOverlay    = bool("autoAddTextOverlay", true),
        uploadSourceMode      = str("uploadSourceMode", "images"),
        editingMode           = EditingMode.values().firstOrNull {
            it.name == str("editingMode", EditingMode.CINEMATIC.name)
        } ?: EditingMode.CINEMATIC,
        createdAt             = long("createdAt", System.currentTimeMillis()),
        isActive              = bool("isActive")
    )
}

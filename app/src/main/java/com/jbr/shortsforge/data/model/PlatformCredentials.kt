package com.jbr.shortsforge.data.model

/**
 * Stores OAuth tokens and config for each social platform.
 * Persisted via PlatformCredentialsRepository (DataStore).
 */
data class PlatformCredentials(
    // ── Facebook ──────────────────────────────────────────────────────────
    val fbAccessToken: String = "",       // User long-lived access token
    val fbPageId: String = "",            // Target Facebook Page ID
    val fbPageAccessToken: String = "",   // Page-level access token

    // ── Instagram ─────────────────────────────────────────────────────────
    val igUserId: String = "",            // Instagram Business/Creator user ID
    // Instagram uses the same FB page token for posting

    // ── TikTok ────────────────────────────────────────────────────────────
    val tiktokAccessToken: String = "",
    val tiktokOpenId: String = "",        // TikTok user open_id
    val tiktokClientKey: String = "",     // From TikTok developer portal
    val tiktokClientSecret: String = ""   // From TikTok developer portal
) {
    val isFacebookConnected get() = fbPageAccessToken.isNotBlank() && fbPageId.isNotBlank()
    val isInstagramConnected get() = igUserId.isNotBlank() && fbPageAccessToken.isNotBlank()
    val isTikTokConnected get() = tiktokAccessToken.isNotBlank() && tiktokOpenId.isNotBlank()
}
package com.jbr.shortsforge.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jbr.shortsforge.data.model.PlatformCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformCredentialsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val FB_ACCESS_TOKEN       = stringPreferencesKey("fb_access_token")
        val FB_PAGE_ID            = stringPreferencesKey("fb_page_id")
        val FB_PAGE_ACCESS_TOKEN  = stringPreferencesKey("fb_page_access_token")
        val IG_USER_ID            = stringPreferencesKey("ig_user_id")
        val TIKTOK_ACCESS_TOKEN   = stringPreferencesKey("tiktok_access_token")
        val TIKTOK_OPEN_ID        = stringPreferencesKey("tiktok_open_id")
        val TIKTOK_CLIENT_KEY     = stringPreferencesKey("tiktok_client_key")
        val TIKTOK_CLIENT_SECRET  = stringPreferencesKey("tiktok_client_secret")
    }

    val credentialsFlow: Flow<PlatformCredentials> = dataStore.data.map { prefs ->
        PlatformCredentials(
            fbAccessToken      = prefs[Keys.FB_ACCESS_TOKEN]      ?: "",
            fbPageId           = prefs[Keys.FB_PAGE_ID]           ?: "",
            fbPageAccessToken  = prefs[Keys.FB_PAGE_ACCESS_TOKEN]  ?: "",
            igUserId           = prefs[Keys.IG_USER_ID]           ?: "",
            tiktokAccessToken  = prefs[Keys.TIKTOK_ACCESS_TOKEN]  ?: "",
            tiktokOpenId       = prefs[Keys.TIKTOK_OPEN_ID]       ?: "",
            tiktokClientKey    = prefs[Keys.TIKTOK_CLIENT_KEY]    ?: "",
            tiktokClientSecret = prefs[Keys.TIKTOK_CLIENT_SECRET] ?: ""
        )
    }

    suspend fun saveFacebook(
        userAccessToken: String,
        pageId: String,
        pageAccessToken: String
    ) {
        dataStore.edit {
            it[Keys.FB_ACCESS_TOKEN]      = userAccessToken
            it[Keys.FB_PAGE_ID]           = pageId
            it[Keys.FB_PAGE_ACCESS_TOKEN]  = pageAccessToken
        }
    }

    suspend fun saveInstagram(igUserId: String) {
        dataStore.edit { it[Keys.IG_USER_ID] = igUserId }
    }

    suspend fun saveTikTok(
        accessToken: String,
        openId: String,
        clientKey: String,
        clientSecret: String
    ) {
        dataStore.edit {
            it[Keys.TIKTOK_ACCESS_TOKEN]  = accessToken
            it[Keys.TIKTOK_OPEN_ID]       = openId
            it[Keys.TIKTOK_CLIENT_KEY]    = clientKey
            it[Keys.TIKTOK_CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun disconnectFacebook() {
        dataStore.edit {
            it.remove(Keys.FB_ACCESS_TOKEN)
            it.remove(Keys.FB_PAGE_ID)
            it.remove(Keys.FB_PAGE_ACCESS_TOKEN)
        }
    }

    suspend fun disconnectInstagram() {
        dataStore.edit { it.remove(Keys.IG_USER_ID) }
    }

    suspend fun disconnectTikTok() {
        dataStore.edit {
            it.remove(Keys.TIKTOK_ACCESS_TOKEN)
            it.remove(Keys.TIKTOK_OPEN_ID)
            it.remove(Keys.TIKTOK_CLIENT_KEY)
            it.remove(Keys.TIKTOK_CLIENT_SECRET)
        }
    }
}
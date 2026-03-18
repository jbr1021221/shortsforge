package com.jbr.shortsforge.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val FOLDER_URI_KEY = stringPreferencesKey("selected_folder_uri")
    }

    /** Emits the persisted folder URI string, or null if none has been saved. */
    val folderUriFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[FOLDER_URI_KEY]
    }

    /** Persists the folder URI string to DataStore. */
    suspend fun saveFolderUri(uriString: String) {
        dataStore.edit { prefs ->
            prefs[FOLDER_URI_KEY] = uriString
        }
    }

    /** Clears the saved folder URI. */
    suspend fun clearFolderUri() {
        dataStore.edit { prefs ->
            prefs.remove(FOLDER_URI_KEY)
        }
    }
}

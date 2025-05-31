package com.example.healthconnectwebhook.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        private val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val IS_SYNC_ENABLED = booleanPreferencesKey("is_sync_enabled")
        private val USER_ID = stringPreferencesKey("user_id")
    }

    val webhookUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEBHOOK_URL] ?: ""
    }

    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SYNC_INTERVAL_MINUTES] ?: 15
    }

    val syncInterval: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SYNC_INTERVAL_MINUTES] ?: 15
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_ID] ?: ""
    }

    val lastSyncTime: Flow<Instant?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIME]?.let { Instant.ofEpochMilli(it) }
    }

    val isSyncEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SYNC_ENABLED] ?: false
    }

    val userId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_ID] ?: "default_user"
    }

    suspend fun setWebhookUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBHOOK_URL] = url
        }
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setSyncInterval(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_INTERVAL_MINUTES] = interval
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = id
        }
    }

    suspend fun setLastSyncTime(time: Instant) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = time.toEpochMilli()
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setUserId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = id
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
} 
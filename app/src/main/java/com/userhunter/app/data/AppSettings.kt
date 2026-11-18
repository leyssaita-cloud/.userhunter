package com.userhunter.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.userhunter.app.core.AppConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.userHunterDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_hunter_settings")

data class AppSettings(
    val language: String = "ar",
    val darkTheme: Boolean = true,
    val requestsPerSecond: Double = AppConfig.DEFAULT_REQUESTS_PER_SECOND,
    val concurrency: Int = AppConfig.DEFAULT_CONCURRENCY,
    val connectTimeoutSeconds: Int = AppConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS.toInt(),
    val readTimeoutSeconds: Int = AppConfig.DEFAULT_READ_TIMEOUT_SECONDS.toInt(),
    val recheckHours: Int = AppConfig.DEFAULT_RECHECK_HOURS,
    val autoResumeWhenOnline: Boolean = true,
    val importedWordListUri: String = ""
)

class AppSettingsRepository(context: Context) {
    private val store = context.applicationContext.userHunterDataStore

    private object Keys {
        val language = stringPreferencesKey("language")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val speed = doublePreferencesKey("requests_per_second")
        val concurrency = intPreferencesKey("concurrency")
        val connectTimeout = intPreferencesKey("connect_timeout_seconds")
        val readTimeout = intPreferencesKey("read_timeout_seconds")
        val recheckHours = intPreferencesKey("recheck_hours")
        val autoResume = booleanPreferencesKey("auto_resume_online")
        val wordListUri = stringPreferencesKey("word_list_uri")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            AppSettings(
                language = prefs[Keys.language] ?: "ar",
                darkTheme = prefs[Keys.darkTheme] ?: true,
                requestsPerSecond = (prefs[Keys.speed] ?: AppConfig.DEFAULT_REQUESTS_PER_SECOND)
                    .coerceIn(0.2, 2.0),
                concurrency = (prefs[Keys.concurrency] ?: AppConfig.DEFAULT_CONCURRENCY).coerceIn(1, 3),
                connectTimeoutSeconds = (prefs[Keys.connectTimeout] ?: 10).coerceIn(5, 60),
                readTimeoutSeconds = (prefs[Keys.readTimeout] ?: 15).coerceIn(5, 60),
                recheckHours = (prefs[Keys.recheckHours] ?: AppConfig.DEFAULT_RECHECK_HOURS).coerceIn(1, 720),
                autoResumeWhenOnline = prefs[Keys.autoResume] ?: true,
                importedWordListUri = prefs[Keys.wordListUri] ?: ""
            )
        }

    suspend fun setLanguage(value: String) = store.edit { it[Keys.language] = value }
    suspend fun setDarkTheme(value: Boolean) = store.edit { it[Keys.darkTheme] = value }
    suspend fun setSpeed(value: Double) = store.edit { it[Keys.speed] = value.coerceIn(0.2, 2.0) }
    suspend fun setConcurrency(value: Int) = store.edit { it[Keys.concurrency] = value.coerceIn(1, 3) }
    suspend fun setConnectTimeout(value: Int) = store.edit { it[Keys.connectTimeout] = value.coerceIn(5, 60) }
    suspend fun setReadTimeout(value: Int) = store.edit { it[Keys.readTimeout] = value.coerceIn(5, 60) }
    suspend fun setRecheckHours(value: Int) = store.edit { it[Keys.recheckHours] = value.coerceIn(1, 720) }
    suspend fun setAutoResume(value: Boolean) = store.edit { it[Keys.autoResume] = value }
    suspend fun setWordListUri(value: String) = store.edit { it[Keys.wordListUri] = value }
}

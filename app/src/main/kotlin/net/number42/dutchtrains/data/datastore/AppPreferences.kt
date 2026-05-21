package net.number42.dutchtrains.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.number42.dutchtrains.domain.model.Station
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val API_KEY    = stringPreferencesKey("api_key")
        val FROM_CODE  = stringPreferencesKey("from_code")
        val FROM_NAME  = stringPreferencesKey("from_name")
        val FROM_UIC   = stringPreferencesKey("from_uic")
        val FROM_LAT   = doublePreferencesKey("from_lat")
        val FROM_LNG   = doublePreferencesKey("from_lng")
        val TO_CODE    = stringPreferencesKey("to_code")
        val TO_NAME    = stringPreferencesKey("to_name")
        val TO_UIC     = stringPreferencesKey("to_uic")
        val TO_LAT     = doublePreferencesKey("to_lat")
        val TO_LNG     = doublePreferencesKey("to_lng")
        val DIRECT_ONLY = booleanPreferencesKey("direct_only")
        val IC_ONLY     = booleanPreferencesKey("ic_only")
        val NOTIFY_PLATFORM_CHANGES = booleanPreferencesKey("notify_platform_changes")
        val NOTIFY_DEPARTURE_TIME = booleanPreferencesKey("notify_departure_time")
        val NOTIFY_ARRIVAL_TIME = booleanPreferencesKey("notify_arrival_time")
        val NOTIFY_PLATFORM_ARRIVAL_CHANGES = booleanPreferencesKey("notify_platform_arrival_changes")
        val NOTIFY_MATERIAL_CHANGES = booleanPreferencesKey("notify_material_changes")
        val DISPLAY_WINDOW_MINUTES = intPreferencesKey("display_window_minutes")
        val FOLLOWED_CTX_RECON = stringPreferencesKey("followed_ctx_recon")
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }

    val fromStationFlow: Flow<Station?> = context.dataStore.data.map { prefs ->
        val code = prefs[Keys.FROM_CODE] ?: return@map null
        Station(
            code = code,
            uicCode = prefs[Keys.FROM_UIC] ?: "",
            name = prefs[Keys.FROM_NAME] ?: "",
            lat = prefs[Keys.FROM_LAT] ?: 0.0,
            lng = prefs[Keys.FROM_LNG] ?: 0.0,
        )
    }

    val toStationFlow: Flow<Station?> = context.dataStore.data.map { prefs ->
        val code = prefs[Keys.TO_CODE] ?: return@map null
        Station(
            code = code,
            uicCode = prefs[Keys.TO_UIC] ?: "",
            name = prefs[Keys.TO_NAME] ?: "",
            lat = prefs[Keys.TO_LAT] ?: 0.0,
            lng = prefs[Keys.TO_LNG] ?: 0.0,
        )
    }

    val directOnlyFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.DIRECT_ONLY] ?: false }
    val icOnlyFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.IC_ONLY] ?: false }
    val notifyPlatformChangesFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_PLATFORM_CHANGES] ?: true }
    val notifyDepartureTimeFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_DEPARTURE_TIME] ?: true }
    val notifyArrivalTimeFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_ARRIVAL_TIME] ?: true }
    val notifyPlatformArrivalChangesFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_PLATFORM_ARRIVAL_CHANGES] ?: false }
    val notifyMaterialChangesFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_MATERIAL_CHANGES] ?: true }
    val displayWindowMinutesFlow: Flow<Int> = context.dataStore.data.map { it[Keys.DISPLAY_WINDOW_MINUTES] ?: 120 }
    val followedCtxReconFlow: Flow<String?> = context.dataStore.data.map { it[Keys.FOLLOWED_CTX_RECON] }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[Keys.API_KEY] = key }
    }

    suspend fun saveFromStation(station: Station?) {
        context.dataStore.edit { prefs ->
            if (station == null) {
                prefs.remove(Keys.FROM_CODE)
                prefs.remove(Keys.FROM_NAME)
                prefs.remove(Keys.FROM_UIC)
                prefs.remove(Keys.FROM_LAT)
                prefs.remove(Keys.FROM_LNG)
            } else {
                prefs[Keys.FROM_CODE] = station.code
                prefs[Keys.FROM_NAME] = station.name
                prefs[Keys.FROM_UIC]  = station.uicCode
                prefs[Keys.FROM_LAT]  = station.lat
                prefs[Keys.FROM_LNG]  = station.lng
            }
        }
    }

    suspend fun saveToStation(station: Station?) {
        context.dataStore.edit { prefs ->
            if (station == null) {
                prefs.remove(Keys.TO_CODE)
                prefs.remove(Keys.TO_NAME)
                prefs.remove(Keys.TO_UIC)
                prefs.remove(Keys.TO_LAT)
                prefs.remove(Keys.TO_LNG)
            } else {
                prefs[Keys.TO_CODE] = station.code
                prefs[Keys.TO_NAME] = station.name
                prefs[Keys.TO_UIC]  = station.uicCode
                prefs[Keys.TO_LAT]  = station.lat
                prefs[Keys.TO_LNG]  = station.lng
            }
        }
    }

    suspend fun saveDirectOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIRECT_ONLY] = enabled }
    }

    suspend fun saveIcOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IC_ONLY] = enabled }
    }

    suspend fun saveNotifyPlatformChanges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_PLATFORM_CHANGES] = enabled }
    }

    suspend fun saveNotifyDepartureTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_DEPARTURE_TIME] = enabled }
    }

    suspend fun saveNotifyArrivalTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ARRIVAL_TIME] = enabled }
    }

    suspend fun saveNotifyPlatformArrivalChanges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_PLATFORM_ARRIVAL_CHANGES] = enabled }
    }

    suspend fun saveNotifyMaterialChanges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_MATERIAL_CHANGES] = enabled }
    }

    suspend fun saveDisplayWindowMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DISPLAY_WINDOW_MINUTES] = minutes.coerceIn(15, 360) }
    }

    suspend fun saveFollowedCtxRecon(ctxRecon: String?) {
        context.dataStore.edit { prefs ->
            if (ctxRecon.isNullOrBlank()) {
                prefs.remove(Keys.FOLLOWED_CTX_RECON)
            } else {
                prefs[Keys.FOLLOWED_CTX_RECON] = ctxRecon
            }
        }
    }
}

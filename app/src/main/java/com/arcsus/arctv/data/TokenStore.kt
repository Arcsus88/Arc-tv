package com.arcsus.arctv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

data class RdCredentials(val clientId: String, val clientSecret: String)

data class RdTokens(val accessToken: String, val refreshToken: String)

/** Persists the OAuth client credentials and tokens in DataStore. */
class TokenStore(context: Context) {

    private val dataStore = context.applicationContext.authDataStore

    private object Keys {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val CLIENT_SECRET = stringPreferencesKey("client_secret")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val AD_APIKEY = stringPreferencesKey("ad_apikey")
    }

    val tokens: Flow<RdTokens?> = dataStore.data.map { prefs ->
        val access = prefs[Keys.ACCESS_TOKEN]
        val refresh = prefs[Keys.REFRESH_TOKEN]
        if (access != null && refresh != null) RdTokens(access, refresh) else null
    }

    /** AllDebrid API key obtained through the PIN flow (or null when not connected). */
    val adApiKey: Flow<String?> = dataStore.data.map { prefs -> prefs[Keys.AD_APIKEY] }

    /** Signed in when either debrid provider is connected. */
    val isAuthorized: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[Keys.ACCESS_TOKEN] != null && prefs[Keys.REFRESH_TOKEN] != null) ||
            prefs[Keys.AD_APIKEY] != null
    }

    suspend fun currentTokens(): RdTokens? = tokens.first()

    suspend fun currentAdApiKey(): String? = adApiKey.first()

    suspend fun saveAdApiKey(apiKey: String) {
        dataStore.edit { prefs -> prefs[Keys.AD_APIKEY] = apiKey }
    }

    suspend fun clearAdApiKey() {
        dataStore.edit { prefs -> prefs.remove(Keys.AD_APIKEY) }
    }

    /** Disconnect Real-Debrid only (AllDebrid, if connected, stays signed in). */
    suspend fun clearRd() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.CLIENT_ID)
            prefs.remove(Keys.CLIENT_SECRET)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
    }

    suspend fun currentCredentials(): RdCredentials? {
        val prefs = dataStore.data.first()
        val id = prefs[Keys.CLIENT_ID]
        val secret = prefs[Keys.CLIENT_SECRET]
        return if (id != null && secret != null) RdCredentials(id, secret) else null
    }

    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        dataStore.edit { prefs ->
            prefs[Keys.CLIENT_ID] = clientId
            prefs[Keys.CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

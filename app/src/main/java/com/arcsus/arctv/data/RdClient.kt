package com.arcsus.arctv.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class RdClient(tokenStore: TokenStore) {

    companion object {
        /** Real-Debrid's public client_id for open-source apps. */
        const val OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM"
        private const val OAUTH_BASE = "https://api.real-debrid.com/oauth/v2/"
        private const val API_BASE = "https://api.real-debrid.com/rest/1.0/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    val oauthApi: RdOauthApi = Retrofit.Builder()
        .baseUrl(OAUTH_BASE)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(converterFactory)
        .build()
        .create(RdOauthApi::class.java)

    val api: RdApi = Retrofit.Builder()
        .baseUrl(API_BASE)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(BearerInterceptor(tokenStore))
                .authenticator(RefreshAuthenticator(tokenStore, oauthApi))
                .build()
        )
        .addConverterFactory(converterFactory)
        .build()
        .create(RdApi::class.java)
}

private class BearerInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val access = runBlocking { tokenStore.currentTokens()?.accessToken }
        val request = if (access != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $access").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * Refreshes the access token when the API answers 401, then retries the request.
 * If the refresh itself fails with an HTTP error the stored session is cleared,
 * which sends the UI back to the sign-in screen.
 */
private class RefreshAuthenticator(
    private val tokenStore: TokenStore,
    private val oauthApi: RdOauthApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        if (responseCount(response) >= 2) return null

        val newAccessToken = runBlocking {
            val tokens = tokenStore.currentTokens() ?: return@runBlocking null
            val sentAuth = response.request.header("Authorization")
            // Another call may have refreshed the token while this one was in flight.
            if (sentAuth != null && sentAuth != "Bearer ${tokens.accessToken}") {
                return@runBlocking tokens.accessToken
            }
            val creds = tokenStore.currentCredentials() ?: return@runBlocking null
            try {
                val refreshed = oauthApi.token(creds.clientId, creds.clientSecret, tokens.refreshToken)
                tokenStore.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                refreshed.accessToken
            } catch (e: retrofit2.HttpException) {
                tokenStore.clear()
                null
            } catch (e: Exception) {
                null
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

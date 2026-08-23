package com.arcsus.arctv.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import retrofit2.Response

class AuthExpiredException : Exception("The sign-in code expired before it was authorised.")

data class PageResult<T>(val items: List<T>, val totalCount: Int)

class RdRepository(
    private val client: RdClient,
    private val tokenStore: TokenStore,
) {

    // ---- Auth ----

    suspend fun startDeviceAuth(): DeviceCodeResponse =
        client.oauthApi.deviceCode(RdClient.OPEN_SOURCE_CLIENT_ID)

    /**
     * Polls until the user authorises the device code, then exchanges it for
     * tokens and persists everything. Throws [AuthExpiredException] if the
     * code expires first.
     */
    suspend fun pollForTokens(device: DeviceCodeResponse) {
        val deadline = System.currentTimeMillis() + device.expiresIn * 1000L
        val intervalMs = device.interval.coerceAtLeast(1) * 1000L
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val response = try {
                client.oauthApi.deviceCredentials(RdClient.OPEN_SOURCE_CLIENT_ID, device.deviceCode)
            } catch (e: java.io.IOException) {
                null // transient network problem; keep polling
            }
            if (response != null && response.isSuccessful) {
                val creds = response.body() ?: throw IllegalStateException("Empty credentials response")
                tokenStore.saveCredentials(creds.clientId, creds.clientSecret)
                val token = client.oauthApi.token(creds.clientId, creds.clientSecret, device.deviceCode)
                tokenStore.saveTokens(token.accessToken, token.refreshToken)
                return
            }
            delay(intervalMs)
        }
        throw AuthExpiredException()
    }

    suspend fun signOut() = tokenStore.clearRd()

    // ---- Downloads ----

    suspend fun downloads(page: Int, limit: Int = 50): PageResult<DownloadItem> =
        pageOf(client.api.downloads(page, limit))

    // ---- Torrents ----

    suspend fun torrents(page: Int, limit: Int = 50): PageResult<TorrentItem> =
        pageOf(client.api.torrents(page, limit))

    suspend fun torrentInfo(id: String): TorrentInfo = client.api.torrentInfo(id)

    suspend fun unrestrict(link: String): UnrestrictedLink = client.api.unrestrictLink(link)

    private fun <T> pageOf(response: Response<List<T>>): PageResult<T> {
        // Real-Debrid answers 204 when the page is empty.
        if (response.code() == 204) return PageResult(emptyList(), 0)
        if (!response.isSuccessful) throw HttpException(response)
        val total = response.headers()["X-Total-Count"]?.toIntOrNull() ?: -1
        return PageResult(response.body().orEmpty(), total)
    }
}

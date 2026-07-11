package com.arcsus.arctv.data

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** https://api.real-debrid.com/oauth/v2/ */
interface RdOauthApi {

    @GET("device/code")
    suspend fun deviceCode(
        @Query("client_id") clientId: String,
        @Query("new_credentials") newCredentials: String = "yes",
    ): DeviceCodeResponse

    /** Returns 403 until the user has entered the code on the verification page. */
    @GET("device/credentials")
    suspend fun deviceCredentials(
        @Query("client_id") clientId: String,
        @Query("code") deviceCode: String,
    ): Response<DeviceCredentialsResponse>

    @FormUrlEncoded
    @POST("token")
    suspend fun token(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0",
    ): TokenResponse
}

/** https://api.real-debrid.com/rest/1.0/ (Bearer-authenticated) */
interface RdApi {

    @GET("downloads")
    suspend fun downloads(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 50,
    ): Response<List<DownloadItem>>

    @GET("torrents")
    suspend fun torrents(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 50,
    ): Response<List<TorrentItem>>

    @GET("torrents/info/{id}")
    suspend fun torrentInfo(@Path("id") id: String): TorrentInfo

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(@Field("link") link: String): UnrestrictedLink
}

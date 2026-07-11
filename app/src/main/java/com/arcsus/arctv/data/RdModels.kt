package com.arcsus.arctv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- OAuth device flow ----

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    val interval: Int = 5,
    @SerialName("expires_in") val expiresIn: Int = 1800,
    @SerialName("verification_url") val verificationUrl: String = "https://real-debrid.com/device",
    @SerialName("direct_verification_url") val directVerificationUrl: String? = null,
)

@Serializable
data class DeviceCredentialsResponse(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("refresh_token") val refreshToken: String,
)

// ---- REST API ----

@Serializable
data class DownloadItem(
    val id: String,
    val filename: String = "",
    val mimeType: String? = null,
    val filesize: Long = 0,
    val link: String = "",
    val host: String = "",
    @SerialName("host_icon") val hostIcon: String? = null,
    val download: String = "",
    val generated: String = "",
    val type: String? = null,
)

@Serializable
data class TorrentItem(
    val id: String,
    val filename: String = "",
    val hash: String = "",
    val bytes: Long = 0,
    val host: String = "",
    val progress: Float = 0f,
    val status: String = "",
    val added: String = "",
    val links: List<String> = emptyList(),
    val ended: String? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
)

@Serializable
data class TorrentFile(
    val id: Int,
    val path: String = "",
    val bytes: Long = 0,
    val selected: Int = 0,
)

@Serializable
data class TorrentInfo(
    val id: String,
    val filename: String = "",
    val bytes: Long = 0,
    val progress: Float = 0f,
    val status: String = "",
    val files: List<TorrentFile> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class UnrestrictedLink(
    val id: String = "",
    val filename: String = "",
    val mimeType: String? = null,
    val filesize: Long = 0,
    val link: String = "",
    val host: String = "",
    val download: String,
    val streamable: Int = 0,
)

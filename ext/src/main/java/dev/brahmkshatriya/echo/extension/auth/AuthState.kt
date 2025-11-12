package dev.brahmkshatriya.echo.extension.auth

data class AuthState(
    val cookie: String,
    val userEmail: String,
    val musicFolderId: String? = null,
    val apiKey: String? = null
)

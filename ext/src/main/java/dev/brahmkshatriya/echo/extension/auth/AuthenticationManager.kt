package dev.brahmkshatriya.echo.extension.auth

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.User
import java.util.concurrent.atomic.AtomicReference

class AuthenticationManager {
    
    private val authState = AtomicReference<AuthState?>(null)
    fun validateAndStoreSession(cookie: String): User {
        val sapisid = extractSapisidFromCookie(cookie)
            ?: throw Exception("Login failed: Required authentication cookie (SAPISID) not found. Please complete Google sign-in.")
        
        val email = extractEmailFromCookie(cookie)
        val userId = email ?: "gdrive_${sapisid.take(16)}" 
        val displayName = email?.substringBefore("@") ?: "Google Drive User"
        
        authState.set(AuthState(
            cookie = cookie,
            userEmail = userId
        ))
        
        return User(
            id = userId,
            name = displayName,
            cover = null,
            extras = mapOf(
                "cookie" to cookie,
                "email" to userId
            )
        )
    }
    
    fun restoreSession(user: User) {
        val cookie = user.extras["cookie"] as? String
        val email = user.extras["email"] as? String ?: user.id
        
        if (cookie != null && email.isNotEmpty()) {
            authState.set(AuthState(cookie = cookie, userEmail = email))
        } else {
            authState.set(null)
        }
    }
    fun getAuthState(): AuthState? = authState.get()

    fun getCurrentUser(): User? {
        return authState.get()?.let { state ->
            User(
                id = state.userEmail,
                name = state.userEmail.substringBefore("@"),
                cover = null,
                extras = mapOf(
                    "cookie" to state.cookie,
                    "email" to state.userEmail
                )
            )
        }
    }

    fun getCookie(): String {
        return authState.get()?.cookie ?: throw ClientException.LoginRequired()
    }

    fun getSapisid(): String {
        val cookie = getCookie()
        return extractSapisidFromCookie(cookie)
            ?: throw ClientException.LoginRequired()
    }

    fun updateMusicFolderId(folderId: String) {
        authState.updateAndGet { it?.copy(musicFolderId = folderId) }
    }

    fun getMusicFolderId(): String? {
        return authState.get()?.musicFolderId
    }

    fun updateApiKey(key: String) {
        authState.updateAndGet { it?.copy(apiKey = key) }
    }

    fun getApiKey(): String? {
        return authState.get()?.apiKey
    }
    
    fun clearSession() {
        authState.set(null)
    }
    fun isAuthenticated(): Boolean {
        return authState.get() != null
    }
    
    private fun extractEmailFromCookie(cookie: String): String? {
        val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        
        return cookie.split(";")
            .firstNotNullOfOrNull { cookiePart ->
                val trimmed = cookiePart.trim()
                when {
                    trimmed.startsWith("remember=") -> {
                        runCatching { 
                            java.net.URLDecoder.decode(trimmed.substringAfter("="), "UTF-8")
                        }.getOrNull()?.takeIf { "@" in it }
                    }
                    trimmed.startsWith("ACCOUNT_CHOOSER=") -> {
                        runCatching { 
                            java.net.URLDecoder.decode(trimmed.substringAfter("="), "UTF-8")
                        }.getOrNull()?.let { emailRegex.find(it)?.value }
                    }
                    else -> null
                }
            }
    }
    
    private fun extractSapisidFromCookie(cookie: String): String? {
        return cookie.split(";")
            .firstNotNullOfOrNull { 
                it.trim().let { trimmed ->
                    when {
                        trimmed.startsWith("SAPISID=") -> trimmed.substringAfter("SAPISID=")
                        trimmed.startsWith("__Secure-3PAPISID=") -> trimmed.substringAfter("__Secure-3PAPISID=")
                        else -> null
                    }
                }
            }?.takeIf { it.isNotEmpty() }
    }
}

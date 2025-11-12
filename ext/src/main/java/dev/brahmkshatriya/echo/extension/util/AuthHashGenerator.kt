package dev.brahmkshatriya.echo.extension.util

import java.security.MessageDigest

object AuthHashGenerator {
    fun generateSapisidhashHeader(
        sapisid: String, 
        origin: String = "https://drive.google.com"
    ): String {
        val timeNow = System.currentTimeMillis() / 1000
        val sapisidhash = MessageDigest.getInstance("SHA-1")
            .digest("$timeNow $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timeNow}_$sapisidhash"
    }
}

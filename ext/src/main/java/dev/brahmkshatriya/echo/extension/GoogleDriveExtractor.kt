package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.util.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GoogleDriveExtractor(
    private val client: OkHttpClient,
    private val cookie: String
) {
    
    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
        private const val DOCTYPE_HTML_PREFIX = "<!DOCTYPE html>"
    }
    
    suspend fun getDownloadUrl(fileId: String): String {
        val downloadUrl = "https://drive.usercontent.google.com/download?id=$fileId"
        
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Accept", ACCEPT)
            .header("Connection", "keep-alive")
            .header("Cookie", cookie)
            .header("Host", "drive.usercontent.google.com")
            .header("User-Agent", USER_AGENT)
            .build()
        
        val bodyContent = client.newCall(request).execute().use { response ->
            val peek = response.peekBody(DOCTYPE_HTML_PREFIX.length.toLong()).string()
            
            if (!peek.startsWith(DOCTYPE_HTML_PREFIX, ignoreCase = true)) {
                return downloadUrl
            }
            
            response.body.string()
        }
        
        return buildConfirmationUrl(downloadUrl, bodyContent)
    }
    
    private fun buildConfirmationUrl(baseUrl: String, htmlContent: String): String {
        val inputRegex = Regex("""<input[^>]+type="hidden"[^>]+name="([^"]+)"[^>]+value="([^"]*)"[^>]*>""")
        
        return baseUrl.toHttpUrl().newBuilder().apply {
            inputRegex.findAll(htmlContent).forEach { match ->
                setQueryParameter(match.groupValues[1], match.groupValues[2])
            }
        }.build().toString()
    }
}

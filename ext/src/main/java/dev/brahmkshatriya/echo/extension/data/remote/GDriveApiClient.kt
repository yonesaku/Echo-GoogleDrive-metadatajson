package dev.brahmkshatriya.echo.extension.data.remote

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.extension.DriveApiResponse
import dev.brahmkshatriya.echo.extension.auth.AuthenticationManager
import dev.brahmkshatriya.echo.extension.util.AuthHashGenerator
import dev.brahmkshatriya.echo.extension.util.toHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class GDriveApiClient(
    private val client: OkHttpClient,
    private val authManager: AuthenticationManager
) {
    
    companion object {
        private const val BOUNDARY = "=====vc17a3rwnndj====="
        private val KEY_REGEX = Regex(""""(\w{39})"""")
    }
    
    suspend fun searchFiles(
        query: String, 
        apiKey: String, 
        pageToken: String? = null
    ): DriveApiResponse = withContext(Dispatchers.IO) {
        if (!authManager.isAuthenticated()) throw ClientException.LoginRequired()
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val tokenParam = pageToken?.let { "&pageToken=$it" } ?: ""
        val requestPath = "/drive/v2internal/files?" +
            "q=$encodedQuery&" +
            "fields=kind,nextPageToken,items(id,title,mimeType,fileSize)&" +
            "maxResults=100" +
            tokenParam +
            "&key=$apiKey"

        val sapisid = authManager.getSapisid()

        val body = """--$BOUNDARY
            |content-type: application/http
            |content-transfer-encoding: binary
            |
            |GET $requestPath
            |authorization: ${AuthHashGenerator.generateSapisidhashHeader(sapisid)}
            |x-goog-authuser: 0
            |
            |--$BOUNDARY--""".trimMargin()
            .toRequestBody("multipart/mixed; boundary=\"$BOUNDARY\"".toMediaType())

        val postUrl = "https://clients6.google.com/batch/drive/v2internal?${'$'}ct=multipart/mixed; boundary=\"$BOUNDARY\"&key=$apiKey"
        
        val request = Request.Builder()
            .url(postUrl)
            .post(body)
            .header("Content-Type", "text/plain; charset=UTF-8")
            .header("Origin", "https://drive.google.com")
            .header("Cookie", authManager.getCookie())
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0")
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    authManager.clearSession()
                    throw ClientException.LoginRequired()
                }
                throw Exception("Drive API request failed: ${response.code}")
            }
            response.body.string()
        }
        
        runCatching {
            MultipartResponseParser.extractAndParse(responseBody, DriveApiResponse.serializer())
        }.getOrElse { e ->
            val preview = responseBody.take(500)
            throw Exception("Failed to parse Drive API response: ${e.message}. Response preview: $preview", e)
        }
    }
    
    suspend fun fetchApiKey(): String = withContext(Dispatchers.IO) {
        val html = client.newCall(
            Request.Builder()
                .url("https://drive.google.com/drive/my-drive")
                .headers(buildHeaders().toHeaders())
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    authManager.clearSession()
                    throw ClientException.LoginRequired()
                }
                throw Exception("Failed to access Drive: ${response.code}")
            }
            response.body.string()
        }
        
        KEY_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Failed to extract API key from Drive page")
    }
    
    suspend fun getOrFetchApiKey(): String {
        authManager.getApiKey()?.let { return it }
        
        val key = fetchApiKey()
        authManager.updateApiKey(key)
        
        return key
    }
    
    private fun buildHeaders(): Map<String, String> {
        val cookieStr = authManager.getCookie()
        return mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Cookie" to cookieStr,
            "Host" to "drive.google.com",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
        )
    }
}

package dev.brahmkshatriya.echo.extension.domain.service

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Companion.toSource
import dev.brahmkshatriya.echo.extension.GoogleDriveExtractor
import dev.brahmkshatriya.echo.extension.auth.AuthenticationManager
import okhttp3.OkHttpClient

class StreamingService(
    private val client: OkHttpClient,
    private val authManager: AuthenticationManager
) {
    
    suspend fun resolveStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        val cookie = authManager.getCookie()
        
        val extractor = GoogleDriveExtractor(client, cookie)
        val downloadUrl = extractor.getDownloadUrl(streamable.id)
        
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Cookie" to cookie,
            "Host" to "drive.usercontent.google.com",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
        )
        
        return Streamable.Source.Http(
            request = downloadUrl.toGetRequest(headers)
        ).toMedia()
    }
}

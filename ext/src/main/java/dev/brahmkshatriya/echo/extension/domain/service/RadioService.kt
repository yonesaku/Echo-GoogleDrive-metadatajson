package dev.brahmkshatriya.echo.extension.domain.service

import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.data.remote.MultipartResponseParser
import dev.brahmkshatriya.echo.extension.data.repository.PlaylistPaginationManager
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils

class RadioService(
    private val paginationManager: PlaylistPaginationManager
) {
    
    suspend fun createRadio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        return when (item) {
            is Playlist -> createRadioFromPlaylist(item, context as? Track)
            else -> Radio(
                id = "empty_${System.currentTimeMillis()}",
                title = "Radio",
                extras = mapOf("tracks" to MultipartResponseParser.encode(
                    emptyList<Track>(),
                    kotlinx.serialization.builtins.ListSerializer(Track.serializer())
                ))
            )
        }
    }
    
    private suspend fun createRadioFromPlaylist(playlist: Playlist, currentTrack: Track?): Radio {
        val folderId = playlist.id
        val batchSize = 50
        
        val startPosition = if (currentTrack != null) {
            runCatching {
                val pagedData = paginationManager.getOrCreatePagedData(folderId)
                0
            }.getOrDefault(0)
        } else {
            0
        }
        
        return Radio(
            id = "radio_playlist_${playlist.id}_${System.currentTimeMillis()}",
            title = "${playlist.title} Radio",
            extras = mapOf(
                "sourceType" to "playlist",
                "sourceId" to playlist.id,
                "folderId" to folderId,
                "currentOffset" to startPosition.toString(),
                "batchSize" to batchSize.toString()
            )
        )
    }
    
    fun loadRadioTracks(radio: Radio): Feed<Track> {
        val folderId = radio.extras["folderId"]
        
        if (folderId == null) {
            val tracksJson = radio.extras["tracks"] ?: return PagedData.Single<Track> { emptyList() }.toFeed()
            val tracks = MultipartResponseParser.parse(
                tracksJson,
                kotlinx.serialization.builtins.ListSerializer(Track.serializer())
            )
            return PagedData.Single { tracks }.toFeed()
        }
        
        val initialOffset = radio.extras["currentOffset"]?.toIntOrNull() ?: 0
        val batchSize = radio.extras["batchSize"]?.toIntOrNull() ?: 50
        
        return PagedData.Continuous<Track> { continuation ->
            val currentOffset = continuation?.toIntOrNull() ?: initialOffset
            
            val nextBatch = paginationManager.loadTracksWithOffset(folderId, currentOffset, batchSize)
            
            val nextOffset = currentOffset + nextBatch.size
            
            val nextContinuation = if (nextBatch.isNotEmpty()) nextOffset.toString() else null
            
            Page(nextBatch, nextContinuation)
        }.toFeed()
    }
}

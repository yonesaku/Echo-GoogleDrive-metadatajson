package dev.brahmkshatriya.echo.extension.data.repository

import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DriveApiResponse
import dev.brahmkshatriya.echo.extension.data.remote.GDriveApiClient
import dev.brahmkshatriya.echo.extension.domain.mapper.DriveToEchoMapper
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistPaginationManager(
    private val apiClient: GDriveApiClient
) {
    
    companion object {
        private const val TRACK_MAP_MAX_SIZE = 50
    }
    
    private val trackMap = object : LinkedHashMap<String, PagedData<Track>>(TRACK_MAP_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PagedData<Track>>?): Boolean {
            return size > TRACK_MAP_MAX_SIZE
        }
    }
    
    private val trackCountCache = object : LinkedHashMap<String, Long>(TRACK_MAP_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > TRACK_MAP_MAX_SIZE
        }
    }
    
    fun getOrCreatePagedData(playlistId: String): PagedData<Track> {
        return trackMap.computeIfAbsent(playlistId) {
            PagedData.Continuous<Track> { continuation ->
                val response = loadFolderContentsPaged(playlistId, continuation)
                val tracks = DriveToEchoMapper.toTracks(response.items)
                Page(tracks, response.nextPageToken)
            }
        }
    }
    
    suspend fun getOrCountTracks(folderId: String): Long {
        return trackCountCache.getOrPut(folderId) {
            countTracksInFolder(folderId)
        }
    }
    
    private suspend fun countTracksInFolder(folderId: String): Long = withContext(Dispatchers.IO) {
        var count = 0L
        var pageToken: String? = null
        
        do {
            val response = loadFolderContentsPaged(folderId, pageToken)
            count += response.items.count { MediaFileUtils.isMediaFile(it.title) }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        
        count
    }
    
    suspend fun loadTracksWithOffset(
        folderId: String, 
        offset: Int, 
        limit: Int
    ): List<Track> {
        val allTracks = mutableListOf<Track>()
        var pageToken: String? = null
        var currentCount = 0
        
        do {
            val response = loadFolderContentsPaged(folderId, pageToken)
            val tracks = DriveToEchoMapper.toTracks(response.items)
            
            if (currentCount + tracks.size <= offset) {
                currentCount += tracks.size
            } else {
                val startIdx = maxOf(0, offset - currentCount)
                val tracksToTake = tracks.drop(startIdx)
                allTracks.addAll(tracksToTake)
                currentCount += tracks.size
                
                if (allTracks.size >= limit) {
                    return allTracks.take(limit)
                }
            }
            
            pageToken = response.nextPageToken
        } while (pageToken != null && allTracks.size < limit)
        
        return allTracks.take(limit)
    }
    
    fun invalidatePlaylist(playlistId: String) {
        trackMap.remove(playlistId)
        trackCountCache.remove(playlistId)
    }

    private suspend fun loadFolderContentsPaged(
        folderId: String, 
        pageToken: String?
    ): DriveApiResponse = withContext(Dispatchers.IO) {
        val key = apiClient.getOrFetchApiKey()
        val query = "trashed=false and '$folderId' in parents"
        apiClient.searchFiles(query, key, pageToken)
    }
}

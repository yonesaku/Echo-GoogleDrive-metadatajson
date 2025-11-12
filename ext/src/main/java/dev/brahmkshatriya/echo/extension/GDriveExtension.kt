package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.auth.AuthenticationManager
import dev.brahmkshatriya.echo.extension.data.remote.GDriveApiClient
import dev.brahmkshatriya.echo.extension.data.repository.MusicFolderRepository
import dev.brahmkshatriya.echo.extension.data.repository.PlaylistPaginationManager
import dev.brahmkshatriya.echo.extension.domain.mapper.DriveToEchoMapper
import dev.brahmkshatriya.echo.extension.domain.service.RadioService
import dev.brahmkshatriya.echo.extension.domain.service.StreamingService
import dev.brahmkshatriya.echo.extension.ui.FeedBuilder
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Settings
import okhttp3.OkHttpClient

class GDriveExtension : ExtensionClient, LoginClient.WebView, 
    HomeFeedClient, LibraryFeedClient, SearchFeedClient, 
    TrackClient, PlaylistClient, RadioClient {
    
    private val client = OkHttpClient.Builder().build()
    
    private lateinit var settings: Settings
    private val authManager = AuthenticationManager()
    private lateinit var apiClient: GDriveApiClient
    private lateinit var folderRepo: MusicFolderRepository
    private lateinit var paginationManager: PlaylistPaginationManager
    private lateinit var radioService: RadioService
    private lateinit var streamingService: StreamingService

    override fun setSettings(settings: Settings) {
        this.settings = settings
        this.apiClient = GDriveApiClient(client, authManager)
        this.folderRepo = MusicFolderRepository(apiClient, authManager)
        this.paginationManager = PlaylistPaginationManager(apiClient)
        this.radioService = RadioService(paginationManager)
        this.streamingService = StreamingService(client, authManager)
    }

    // Login

    override val webViewRequest = object : WebViewRequest.Cookie<List<User>> {
        override val dontCache = true
        override val initialUrl = "https://accounts.google.com/ServiceLogin?service=wise&continue=https://drive.google.com/".toGetRequest()
        override val stopUrlRegex = Regex("https://drive\\.google\\.com/(drive|u)")

        override suspend fun onStop(url: NetworkRequest, cookie: String): List<User> {
            try {
                val user = authManager.validateAndStoreSession(cookie)
                return listOf(user)
            } catch (e: Exception) {
                authManager.clearSession()
                throw Exception("Login verification failed: ${e.message}", e)
            }
        }
    }

    override fun setLoginUser(user: User?) {
        if (user != null) {
            authManager.restoreSession(user)
        } else {
            authManager.clearSession()
        }
    }

    override suspend fun getCurrentUser(): User? {
        return authManager.getCurrentUser()
    }

    // Settings

    override suspend fun getSettingItems() = listOf<dev.brahmkshatriya.echo.common.settings.Setting>()

    // Home Feed

    override suspend fun loadHomeFeed(): Feed<Shelf> {
        return loadMusicLibrary(folderRepo.getMusicFolderId())
    }

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        return emptyList<Shelf>().toFeed()
    }

    private suspend fun loadMusicLibrary(folderId: String): Feed<Shelf> {
        val items = folderRepo.loadFolderContents(folderId)
        
        val (folders, mediaFiles) = DriveToEchoMapper.partitionFiles(items)
        val playlists = DriveToEchoMapper.toPlaylists(folders)
        val tracks = DriveToEchoMapper.toTracks(mediaFiles)
        
        return FeedBuilder.buildLibraryFeed(playlists, tracks, folderId)
    }

    // Search

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        if (query.isBlank()) return loadHomeFeed()
        
        val items = folderRepo.searchInFolder(folderRepo.getMusicFolderId(), query)
        val tracks = DriveToEchoMapper.toTracks(items)
        val playlists = DriveToEchoMapper.toPlaylists(items)
        
        return FeedBuilder.buildSearchFeed(tracks, playlists)
    }

    // Track

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track.copy(
            streamables = listOf(
                Streamable.server(
                    id = track.id,
                    quality = 0,
                    title = "Google Drive"
                )
            )
        )
    }

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        return streamingService.resolveStreamableMedia(streamable, isDownload)
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null 

    // Playlist

    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val totalTrackCount = paginationManager.getOrCountTracks(playlist.id)
        
        return playlist.copy(
            trackCount = totalTrackCount
           
        )
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        val pagedData = paginationManager.getOrCreatePagedData(playlist.id)
        return pagedData.toFeed()
    }
    
    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? = null 

    // Radio
    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        return radioService.createRadio(item, context)
    }

    override suspend fun loadRadio(radio: Radio): Radio = radio

    override suspend fun loadTracks(radio: Radio): Feed<Track> {
        return radioService.loadRadioTracks(radio)
    }
}

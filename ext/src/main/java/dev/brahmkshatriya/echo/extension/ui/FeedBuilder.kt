package dev.brahmkshatriya.echo.extension.ui

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track

object FeedBuilder {
    
    fun buildLibraryFeed(
        playlists: List<Playlist>,
        tracks: List<Track>,
        folderId: String
    ): Feed<Shelf> {
        val shelves = buildList<Shelf> {
            if (playlists.isNotEmpty()) {
                add(Shelf.Lists.Items(
                    id = "${folderId}_playlists",
                    title = "Playlists",
                    list = playlists,
                    subtitle = "${playlists.size} playlists"
                ))
            }
            
            if (tracks.isNotEmpty()) {
                add(Shelf.Lists.Tracks(
                    id = "${folderId}_singles",
                    title = "Singles",
                    list = tracks,
                    subtitle = "${tracks.size} tracks"
                ))
            }
        }
        
        return Feed(emptyList()) { PagedData.Single { shelves }.toFeedData() }
    }

    fun buildSearchFeed(
        tracks: List<Track>,
        playlists: List<Playlist>
    ): Feed<Shelf> {
        val shelves = buildList<Shelf> {
            if (tracks.isNotEmpty()) {
                add(Shelf.Lists.Tracks("search_tracks", "Tracks", tracks))
            }
            if (playlists.isNotEmpty()) {
                add(Shelf.Lists.Items("search_playlists", "Playlists", playlists))
            }
        }
        
        return Feed(emptyList()) { PagedData.Single { shelves }.toFeedData() }
    }
}

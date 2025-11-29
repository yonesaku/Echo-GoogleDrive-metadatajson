
package dev.brahmkshatriya.echo.extension.domain.mapper

import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DriveFile
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils
import dev.brahmkshatriya.echo.extension.data.MetadataManager
import java.util.Date
import java.util.Calendar

object DriveToEchoMapper {

    const val VIDEO_EXTRA_KEY = "isVideo"
    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    fun toTrack(driveFile: DriveFile): Track {
        val metadata = MetadataManager.getMetadata(driveFile.id)
        
        // Use metadata if available, otherwise fall back to defaults
        val title = MediaFileUtils.cleanTitle(driveFile.title)
        
        val coverUrl = metadata?.albumArt 
            ?: MediaFileUtils.getThumbnailUrl(driveFile.id)
        
        // Convert year to Date if available
        val releaseDate = metadata?.year?.let { year ->
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, 0) // January
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        }
        
        return Track(
            id = driveFile.id,
            title = title,
            artists = metadata?.artist?.let { listOf(dev.brahmkshatriya.echo.common.models.Artist(id = "", name = it)) } ?: emptyList(),
            album = metadata?.album?.let { 
                dev.brahmkshatriya.echo.common.models.Album(id = "", title = it) 
            },
            cover = coverUrl.toImageHolder(),
            releaseDate = releaseDate,
            genres = metadata?.genre?.let { listOf(it) } ?: emptyList(),
            extras = buildMap {
                if (MediaFileUtils.isVideoFile(driveFile.title)) {
                    put(VIDEO_EXTRA_KEY, "true")
                }
                metadata?.genre?.let { put("genre", it) }
                metadata?.year?.let { put("year", it.toString()) }
            }
        )
    }

    fun toPlaylist(driveFile: DriveFile): Playlist {
        return Playlist(
            id = driveFile.id,
            title = driveFile.title,
            isEditable = false,
            cover = MediaFileUtils.getThumbnailUrl(driveFile.id).toImageHolder()
        )
    }

    fun partitionFiles(files: List<DriveFile>): Pair<List<DriveFile>, List<DriveFile>> {
        return files.partition { it.mimeType == FOLDER_MIME_TYPE }
    }

    fun toTracks(files: List<DriveFile>): List<Track> {
        return files
            .filter { MediaFileUtils.isMediaFile(it.title) }
            .map { toTrack(it) }
    }

    fun toPlaylists(files: List<DriveFile>): List<Playlist> {
        return files
            .filter { it.mimeType == FOLDER_MIME_TYPE }
            .map { toPlaylist(it) }
    }
}
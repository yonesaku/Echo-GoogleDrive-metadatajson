package dev.brahmkshatriya.echo.extension.domain.mapper

import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DriveFile
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils

object DriveToEchoMapper {
    
    const val VIDEO_EXTRA_KEY = "isVideo"
    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    
    fun toTrack(driveFile: DriveFile): Track {
        return Track(
            id = driveFile.id,
            title = MediaFileUtils.cleanTitle(driveFile.title),
            cover = MediaFileUtils.getThumbnailUrl(driveFile.id).toImageHolder(),
            extras = if (MediaFileUtils.isVideoFile(driveFile.title)) {
                mapOf(VIDEO_EXTRA_KEY to "true")
            } else {
                emptyMap()
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

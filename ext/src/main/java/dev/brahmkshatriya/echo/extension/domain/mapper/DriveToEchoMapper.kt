package dev.brahmkshatriya.echo.extension.domain.mapper

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DriveFile
import dev.brahmkshatriya.echo.extension.util.MediaFileUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DriveToEchoMapper {

    const val VIDEO_EXTRA_KEY = "isVideo"
    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    // Custom metadata storage
    private var customMetadataMap: Map<String, TrackMetadata> = emptyMap()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TrackMetadata(
        val fileId: String,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArt: String? = null,
        val year: String? = null,
        val genre: String? = null,
        val duration: Long? = null
    )

    @Serializable
    data class MetadataLibrary(
        val metadata: List<TrackMetadata>
    )

    /**
     * Load custom metadata from JSON string
     * Call this from GDriveExtension.setSettings()
     */
    fun loadCustomMetadata(jsonString: String?) {
        if (jsonString.isNullOrBlank()) {
            customMetadataMap = emptyMap()
            return
        }

        try {
            val library = json.decodeFromString<MetadataLibrary>(jsonString)
            customMetadataMap = library.metadata.associateBy { it.fileId }
        } catch (e: Exception) {
            e.printStackTrace()
            customMetadataMap = emptyMap()
        }
    }

    fun toTrack(driveFile: DriveFile): Track {
        val customMeta = customMetadataMap[driveFile.id]
        
        val title = customMeta?.title ?: MediaFileUtils.cleanTitle(driveFile.title)
        val coverUrl = customMeta?.albumArt ?: MediaFileUtils.getThumbnailUrl(driveFile.id)
        
        return Track(
            id = driveFile.id,
            title = title,
            artists = customMeta?.artist?.let { 
                listOf(Artist(id = it, name = it))
            } ?: emptyList(),
            album = customMeta?.album?.let {
                Album(
                    id = it,
                    title = it,
                    cover = coverUrl.toImageHolder()
                )
            },
            duration = customMeta?.duration,
            cover = coverUrl.toImageHolder(),
            extras = buildMap {
                if (MediaFileUtils.isVideoFile(driveFile.title)) {
                    put(VIDEO_EXTRA_KEY, "true")
                }
                customMeta?.year?.let { put("year", it) }
                customMeta?.genre?.let { put("genre", it) }
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

/*
 * EXAMPLE JSON FOR CUSTOM METADATA:
 * 
 * {
 *   "metadata": [
 *     {
 *       "fileId": "1ABC123XYZ",
 *       "title": "Custom Song Title",
 *       "artist": "Custom Artist",
 *       "album": "Custom Album",
 *       "albumArt": "https://drive.google.com/uc?export=view&id=1IMG123",
 *       "year": "2024",
 *       "genre": "Pop",
 *       "duration": 180
 *     }
 *   ]
 * }
 * 
 * HOW TO USE:
 * 1. Add a SettingTextInput in GDriveExtension.getSettingItems()
 * 2. In GDriveExtension.setSettings(), call:
 *    DriveToEchoMapper.loadCustomMetadata(settings.getString("custom_metadata"))
 * 3. All tracks with matching fileIds will use custom metadata!
 */
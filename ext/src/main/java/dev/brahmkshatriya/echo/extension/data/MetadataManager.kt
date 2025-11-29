package dev.brahmkshatriya.echo.extension.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

@Serializable
data class TrackMetadata(
    val fileId: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArt: String? = null,
    val genre: String? = null,
    val year: Int? = null
)

@Serializable
data class MetadataRoot(
    val metadata: List<TrackMetadata>
)

object MetadataManager {
    private var metadataMap: Map<String, TrackMetadata> = emptyMap()
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun initialize() {
        try {
            val inputStream = this::class.java.classLoader
                ?.getResourceAsStream("metadata.json")
                ?: return
            
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()
            
            val root = json.decodeFromString<MetadataRoot>(jsonString)
            metadataMap = root.metadata.associateBy { it.fileId }
        } catch (e: Exception) {
            println("Failed to load metadata.json: ${e.message}")
            metadataMap = emptyMap()
        }
    }

    fun getMetadata(fileId: String): TrackMetadata? {
        return metadataMap[fileId]
    }
}
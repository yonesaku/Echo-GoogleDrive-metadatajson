package dev.brahmkshatriya.echo.extension.util

object MediaFileUtils {
    
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "m4v", "3gp", "mpeg", "mpg")
    
    fun isAudioFile(filename: String): Boolean {
        return filename.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
    }
    
    fun isVideoFile(filename: String): Boolean {
        return filename.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }
    
    fun isMediaFile(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS || extension in VIDEO_EXTENSIONS
    }
    
    fun cleanTitle(filename: String): String {
        val nameWithoutExt = filename.substringBeforeLast('.', filename)
        return if (nameWithoutExt.isEmpty()) filename else nameWithoutExt
    }
    
    //Reference: https://stackoverflow.com/a/31542402
    fun getThumbnailUrl(fileId: String): String {
        return "https://drive.google.com/thumbnail?authuser=0&sz=w500&id=$fileId"
    }
}

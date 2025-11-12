package dev.brahmkshatriya.echo.extension.data.repository

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.extension.DriveFile
import dev.brahmkshatriya.echo.extension.auth.AuthenticationManager
import dev.brahmkshatriya.echo.extension.data.remote.GDriveApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicFolderRepository(
    private val apiClient: GDriveApiClient,
    private val authManager: AuthenticationManager
) {
    
    companion object {
        const val MUSIC_FOLDER_NAME = "echomusic"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
    
    suspend fun getMusicFolderId(): String {
        authManager.getMusicFolderId()?.let { return it }
        
        val folderId = searchMusicFolder()
            ?: throw ClientException.LoginRequired()
        
        authManager.updateMusicFolderId(folderId)
        
        return folderId
    }
    
    suspend fun loadFolderContents(folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        if (!authManager.isAuthenticated()) throw ClientException.LoginRequired()
        
        val key = apiClient.getOrFetchApiKey()
        val query = "trashed=false and '$folderId' in parents"
        val response = apiClient.searchFiles(query, key)
        
        response.items
    }
    
    suspend fun searchInFolder(folderId: String, query: String): List<DriveFile> = withContext(Dispatchers.IO) {
        if (!authManager.isAuthenticated()) throw ClientException.LoginRequired()
        
        val key = apiClient.getOrFetchApiKey()
        val searchQuery = "title contains '$query' and trashed=false and '$folderId' in ancestors"
        val response = apiClient.searchFiles(searchQuery, key)
        
        response.items
    }
    
    private suspend fun searchMusicFolder(): String? = withContext(Dispatchers.IO) {
        if (!authManager.isAuthenticated()) throw ClientException.LoginRequired()

        try {
            val key = apiClient.getOrFetchApiKey()
            
            val searchQuery = "title='$MUSIC_FOLDER_NAME' and mimeType='$FOLDER_MIME_TYPE' and trashed=false"
            val response = apiClient.searchFiles(searchQuery, key)
            
            response.items.firstOrNull()?.id
        } catch (e: ClientException.LoginRequired) {
            throw e
        } catch (e: Exception) {
            throw ClientException.LoginRequired()
        }
    }
}

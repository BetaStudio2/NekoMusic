package com.neko.music.data.manager

import android.content.Context
import androidx.room.Room
import com.neko.music.data.database.AppDatabase
import com.neko.music.data.database.PlaylistEntity
import com.neko.music.data.model.Music
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PlaylistManager private constructor(context: Context) {
    
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "music-playlist-db"
    ).build()
    
    private val dao = database.playlistDao()
    
    val playlist: Flow<List<Music>> = dao.getAllPlaylist().map { entities ->
        entities.map { entity ->
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    suspend fun addToPlaylist(music: Music) {
        val entity = PlaylistEntity(
            musicId = music.id,
            title = music.title,
            artist = music.artist,
            album = music.album,
            duration = music.duration,
            filePath = music.filePath,
            coverFilePath = music.coverFilePath,
            uploadUserId = music.uploadUserId,
            createdAt = music.createdAt
        )
        dao.addToPlaylist(entity)
    }
    
    suspend fun removeFromPlaylist(musicId: Int) {
        dao.removeFromPlaylist(musicId)
    }
    
    suspend fun clearPlaylist() {
        dao.clearPlaylist()
    }
    
    suspend fun getPlaylistCount(): Int {
        return dao.getPlaylistCount()
    }
    
    suspend fun isInPlaylist(musicId: Int): Boolean {
        return dao.getMusicById(musicId) != null
    }
    
    suspend fun clearPlaylistExcept(currentMusicId: Int) {
        val allMusic: List<PlaylistEntity> = kotlinx.coroutines.runBlocking {
            dao.getAllPlaylist().first()
        }
        allMusic.forEach { entity: PlaylistEntity ->
            if (entity.musicId != currentMusicId) {
                dao.removeFromPlaylist(entity.musicId)
            }
        }
    }
    
    suspend fun getLastPlayed(): Music? {
        return dao.getLastPlayed()?.let { entity ->
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    companion object {
        @Volatile
        private var instance: PlaylistManager? = null
        
        fun getInstance(context: Context): PlaylistManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
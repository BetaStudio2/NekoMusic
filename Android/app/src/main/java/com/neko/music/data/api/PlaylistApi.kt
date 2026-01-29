package com.neko.music.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlaylistListResponse(
    val success: Boolean,
    val message: String = "",
    val playlists: List<PlaylistInfo>? = null
)

@Serializable
data class PlaylistResponse(
    val success: Boolean,
    val message: String = "",
    val playlist: PlaylistInfo? = null
)

@Serializable
data class PlaylistInfo(
    val id: Int,
    val name: String,
    val description: String? = null,
    val coverPath: String? = null,
    val musicCount: Int,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class UpdatePlaylistRequest(
    val id: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class DeletePlaylistRequest(
    val id: Int
)

class PlaylistApi(private val token: String?) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    private val baseUrl = "https://music.cnmsb.xin/api/user/playlist"
    
    /**
     * 获取我的歌单列表
     */
    suspend fun getMyPlaylists(): PlaylistListResponse {
        return try {
            client.get("https://music.cnmsb.xin/api/user/playlists") {
                headers {
                    append("Authorization", token ?: "")
                }
            }.body()
        } catch (e: Exception) {
            PlaylistListResponse(false, "网络错误: ${e.message}", null)
        }
    }
    
    /**
     * 创建歌单
     */
    suspend fun createPlaylist(name: String): PlaylistResponse {
        return try {
            client.post("$baseUrl/create") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(CreatePlaylistRequest(name, null))
            }.body()
        } catch (e: Exception) {
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
    
    /**
     * 更新歌单
     */
    suspend fun updatePlaylist(playlistId: Int, name: String): PlaylistResponse {
        return try {
            client.post("$baseUrl/update") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(UpdatePlaylistRequest(playlistId, name, null))
            }.body()
        } catch (e: Exception) {
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
    
    /**
     * 删除歌单
     */
    suspend fun deletePlaylist(playlistId: Int): PlaylistResponse {
        return try {
            client.post("$baseUrl/delete") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(DeletePlaylistRequest(playlistId))
            }.body()
        } catch (e: Exception) {
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
}
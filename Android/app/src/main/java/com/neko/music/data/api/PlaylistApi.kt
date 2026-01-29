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
import com.neko.music.data.model.PlaylistResponse

class PlaylistApi(private val token: String?) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    private val baseUrl = "https://music.cnmsb.xin/api"
    
    /**
     * 获取我的歌单列表
     */
    suspend fun getMyPlaylists(): PlaylistResponse {
        return try {
            client.get("$baseUrl/playlist") {
                headers {
                    append("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
    
    /**
     * 创建歌单
     */
    suspend fun createPlaylist(name: String): PlaylistResponse {
        return try {
            client.post("$baseUrl/playlist") {
                headers {
                    append("Authorization", "Bearer $token")
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                    {
                        "name": "$name"
                    }
                    """.trimIndent()
                )
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
            client.put("$baseUrl/playlist/$playlistId") {
                headers {
                    append("Authorization", "Bearer $token")
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                    {
                        "name": "$name"
                    }
                    """.trimIndent()
                )
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
            client.delete("$baseUrl/playlist/$playlistId") {
                headers {
                    append("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
}
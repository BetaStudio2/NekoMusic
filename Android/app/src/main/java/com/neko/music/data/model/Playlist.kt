package com.neko.music.data.model

data class Playlist(
    val id: Int,
    val name: String,
    val musicCount: Int,
    val userId: Int,
    val createdAt: String
)

data class PlaylistResponse(
    val success: Boolean,
    val message: String,
    val data: List<Playlist>? = null
)
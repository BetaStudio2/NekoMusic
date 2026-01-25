package com.neko.music.util

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.getSystemService
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadHelper(private val context: Context) {

    private val downloadManager = context.getSystemService<DownloadManager>()
    private val musicApi = MusicApi(context)

    suspend fun downloadMusic(music: Music): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NekoMusic"
            )

            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val fileName = "${music.artist} - ${music.title}.mp3"
                .replace(Regex("[/\\:*?\"<>|]"), "_")

            val downloadUri = Uri.parse("https://music.cnmsb.xin/api/music/file/${music.id}")

            val request = DownloadManager.Request(downloadUri).apply {
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setTitle(music.title)
                setDescription("正在下载: ${music.artist} - ${music.title}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "NekoMusic/$fileName"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setRequiresCharging(false)
                }
            }

            val downloadId = downloadManager?.enqueue(request)

            if (downloadId != null) {
                continuation.resume(Result.success("下载已开始"))
            } else {
                continuation.resume(Result.failure(Exception("下载管理器不可用")))
            }
        } catch (e: Exception) {
            Log.e("DownloadHelper", "下载失败", e)
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun downloadMusicWithLyrics(music: Music): Result<String> {
        return try {
            downloadMusic(music)

            delay(1000)

            val lyricsResult = musicApi.getMusicLyrics(music)
            lyricsResult.fold(
                onSuccess = { lyrics ->
                    if (lyrics.isNotEmpty()) {
                        saveLyrics(music, lyrics)
                    }
                    Result.success("下载已开始，包含歌词")
                },
                onFailure = {
                    Result.success("下载已开始")
                }
            )
        } catch (e: Exception) {
            Log.e("DownloadHelper", "下载失败", e)
            Result.failure(e)
        }
    }

    private fun saveLyrics(music: Music, lyrics: String) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NekoMusic"
            )

            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val fileName = "${music.artist} - ${music.title}.lrc"
                .replace(Regex("[/\\:*?\"<>|]"), "_")

            val lyricsFile = File(downloadDir, fileName)
            lyricsFile.writeText(lyrics)

            Log.d("DownloadHelper", "歌词已保存: ${lyricsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DownloadHelper", "保存歌词失败", e)
        }
    }
}

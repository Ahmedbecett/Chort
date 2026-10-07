package com.example.ui.screens.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Rivo 3.1.0 — real offline vault: downloads video bytes into private app
 * storage with a metadata sidecar. Sizes, lists and deletes are all genuine.
 */
data class OfflineVideo(
    val id: String,
    val file: File,
    val caption: String,
    val creator: String,
    val downloadedAt: Long,
    val sizeBytes: Long
)

object OfflineStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "offline_videos").apply { mkdirs() }

    fun isDownloaded(context: Context, videoId: String): Boolean =
        File(dir(context), "$videoId.mp4").let { it.exists() && it.length() > 0 }

    suspend fun download(
        context: Context,
        videoId: String,
        videoUrl: String,
        caption: String,
        creator: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = dir(context)
        val tmp = File(targetDir, "$videoId.mp4.tmp")
        val dest = File(targetDir, "$videoId.mp4")
        var connection: HttpURLConnection? = null
        try {
            try {
                if (tmp.exists()) tmp.delete()
            } catch (_: Exception) {
            }
            connection = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "Rivo-Android/offline")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Download failed (HTTP ${connection.responseCode})"))
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var copied = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            withContext(Dispatchers.Main) {
                                onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (copied <= 0) {
                try {
                    tmp.delete()
                } catch (_: Exception) {
                }
                return@withContext Result.failure(Exception("Downloaded file is empty"))
            }
            try {
                if (dest.exists()) dest.delete()
            } catch (_: Exception) {
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                try {
                    tmp.delete()
                } catch (_: Exception) {
                }
            }
            val meta = JSONObject().apply {
                put("id", videoId)
                put("caption", caption.take(200))
                put("creator", creator.take(80))
                put("ts", System.currentTimeMillis())
                put("size", dest.length())
            }
            try {
                File(targetDir, "$videoId.json").writeText(meta.toString())
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) { onProgress(1f) }
            Result.success(dest)
        } catch (e: Exception) {
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
            Result.failure(Exception("Download failed: ${e.message}"))
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun list(context: Context): List<OfflineVideo> {
        val targetDir = try {
            dir(context)
        } catch (_: Exception) {
            return emptyList()
        }
        val files = try {
            targetDir.listFiles { f -> f.extension == "mp4" }?.toList() ?: emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return files.mapNotNull { file ->
            try {
                val id = file.nameWithoutExtension
                val metaFile = File(targetDir, "$id.json")
                var caption = ""
                var creator = ""
                var ts = file.lastModified()
                if (metaFile.exists()) {
                    try {
                        val meta = JSONObject(metaFile.readText())
                        caption = meta.optString("caption")
                        creator = meta.optString("creator")
                        ts = meta.optLong("ts", ts)
                    } catch (_: Exception) {
                    }
                }
                OfflineVideo(id, file, caption, creator, ts, file.length())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.downloadedAt }
    }

    fun totalBytes(context: Context): Long {
        return try {
            dir(context).listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun delete(context: Context, videoId: String): Boolean {
        return try {
            val targetDir = dir(context)
            var ok = true
            try {
                val f = File(targetDir, "$videoId.mp4")
                if (f.exists()) ok = f.delete() && ok
            } catch (_: Exception) {
                ok = false
            }
            try {
                File(targetDir, "$videoId.json").delete()
            } catch (_: Exception) {
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun clearAll(context: Context): Int {
        var count = 0
        try {
            dir(context).listFiles()?.forEach { file ->
                try {
                    if (file.extension == "mp4" && file.delete()) count++
                    if (file.extension == "json") file.delete()
                    if (file.extension == "tmp") file.delete()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return count
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

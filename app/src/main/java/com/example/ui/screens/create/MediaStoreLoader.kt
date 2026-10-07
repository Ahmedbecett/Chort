package com.example.ui.screens.create

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZEVORA 3.1.0 — real on-device media queries for the TikTok-style picker.
 * Images + videos with album buckets, plus a device-audio list for "Add sound".
 */
data class DeviceMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAddedSec: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val bucket: String = "Recents",
    val displayName: String = ""
)

data class MediaAlbum(
    val name: String,
    val count: Int,
    val coverUri: Uri? = null
)

data class DeviceAudio(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val displayName: String
)

object MediaStoreLoader {

    /** All images+videos, newest first. [videosOnly]/[photosOnly] drive the tabs. */
    suspend fun loadMedia(
        context: Context,
        videosOnly: Boolean = false,
        photosOnly: Boolean = false,
        album: String? = null,
        limit: Int = 600
    ): List<DeviceMedia> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceMedia>()
        try {
            if (!photosOnly) {
                out += queryCollection(
                    context,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    isVideo = true,
                    album = album,
                    limit = limit
                )
            }
            if (!videosOnly) {
                out += queryCollection(
                    context,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    isVideo = false,
                    album = album,
                    limit = limit
                )
            }
        } catch (_: Exception) {
        }
        out.sortedByDescending { it.dateAddedSec }.take(limit)
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        album: String?,
        limit: Int
    ): List<DeviceMedia> {
        val out = mutableListOf<DeviceMedia>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (isVideo) projection.add(MediaStore.Video.Media.DURATION)
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $limit"
        try {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                sort
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bucketCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val wCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val hCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext()) {
                    val bucket = if (bucketCol >= 0) (c.getString(bucketCol) ?: "Recents") else "Recents"
                    if (album != null && album != "Recents" && bucket != album) continue
                    val id = c.getLong(idCol)
                    out.add(
                        DeviceMedia(
                            uri = ContentUris.withAppendedId(collection, id),
                            isVideo = isVideo,
                            dateAddedSec = c.getLong(dateCol),
                            durationMs = if (durCol >= 0) c.getLong(durCol) else 0L,
                            width = if (wCol >= 0) c.getInt(wCol) else 0,
                            height = if (hCol >= 0) c.getInt(hCol) else 0,
                            sizeBytes = c.getLong(sizeCol),
                            bucket = bucket,
                            displayName = c.getString(nameCol) ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Real album buckets with counts + covers, "Recents" first. */
    suspend fun loadAlbums(context: Context, includePhotos: Boolean = true): List<MediaAlbum> =
        withContext(Dispatchers.IO) {
            val buckets = linkedMapOf<String, Pair<Int, Uri?>>()
            var total = 0
            var firstUri: Uri? = null
            suspend fun scan(collection: Uri, isVideo: Boolean) {
                try {
                    context.contentResolver.query(
                        collection,
                        arrayOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                        ),
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val bucketCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val bucket = if (bucketCol >= 0) {
                                (c.getString(bucketCol) ?: "Recents")
                            } else "Recents"
                            val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                            if (firstUri == null) firstUri = uri
                            total++
                            val prev = buckets[bucket]
                            buckets[bucket] = ((prev?.first ?: 0) + 1) to (prev?.second ?: uri)
                            if (!isVideo) Unit
                        }
                    }
                } catch (_: Exception) {
                }
            }
            scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            if (includePhotos) scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
            buildList {
                add(MediaAlbum("Recents", total, firstUri))
                buckets.entries.sortedByDescending { it.value.first }.forEach { (name, pair) ->
                    if (name != "Recents") add(MediaAlbum(name, pair.first, pair.second))
                }
            }
        }

    /** Device audio tracks (music + recordings) for the "Add sound" sheet. */
    suspend fun loadAudio(context: Context, limit: Int = 300): List<DeviceAudio> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<DeviceAudio>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                context.contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.Audio.Media.IS_MUSIC
                    ),
                    "${MediaStore.Audio.Media.DURATION} > ?",
                    arrayOf("3000"),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $limit"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val mime = c.getString(mimeCol) ?: ""
                        out.add(
                            DeviceAudio(
                                uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                                title = c.getString(titleCol) ?: c.getString(nameCol) ?: "Unknown track",
                                artist = c.getString(artistCol) ?: "Unknown artist",
                                durationMs = c.getLong(durCol),
                                sizeBytes = c.getLong(sizeCol),
                                mimeType = mime,
                                displayName = c.getString(nameCol) ?: ""
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            // AAC/M4A mux reliably on every device — surface them first.
            out.sortedWith(
                compareBy(
                    { !isMuxFriendly(it.mimeType) },
                    { -it.durationMs }
                )
            )
        }

    fun isMuxFriendly(mime: String): Boolean {
        val m = mime.lowercase()
        return m.contains("mp4a") || m.contains("aac") || m.contains("m4a") ||
            m.contains("3gp") || m.contains("mp4")
    }
}

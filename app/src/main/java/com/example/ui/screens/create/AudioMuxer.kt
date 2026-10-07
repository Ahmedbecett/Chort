package com.example.ui.screens.create

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Rivo 3.1.0 — real sound mixing without any native dependency.
 * Replaces (or adds) the audio track of an MP4 with a device audio file,
 * trimmed to the video duration. AAC/M4A works on every device; MP3 is
 * attempted and honestly reported when the device muxer rejects it.
 */
object AudioMuxer {

    data class MuxResult(val ok: Boolean, val message: String)

    suspend fun replaceAudio(
        context: Context,
        videoSource: Uri,
        audioSource: Uri,
        outFile: File
    ): MuxResult = withContext(Dispatchers.IO) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            try {
                outFile.parentFile?.mkdirs()
                if (outFile.exists()) outFile.delete()
            } catch (_: Exception) {
            }
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(context, videoSource, null)
            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(context, audioSource, null)

            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = try {
                    format.getString(MediaFormat.KEY_MIME) ?: ""
                } catch (_: Exception) {
                    ""
                }
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    videoFormat = format
                    break
                }
            }
            if (videoTrack < 0 || videoFormat == null) {
                return@withContext MuxResult(false, "No video track found in this file")
            }
            val videoDurationUs = try {
                videoFormat.getLong(MediaFormat.KEY_DURATION)
            } catch (_: Exception) {
                0L
            }

            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = try {
                    format.getString(MediaFormat.KEY_MIME) ?: ""
                } catch (_: Exception) {
                    ""
                }
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) {
                return@withContext MuxResult(false, "No audio track found in this sound")
            }

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack: Int
            val outAudioTrack: Int
            try {
                outVideoTrack = muxer.addTrack(videoFormat)
                outAudioTrack = muxer.addTrack(audioFormat)
            } catch (e: Exception) {
                return@withContext MuxResult(
                    false,
                    "This audio format cannot be embedded on this device — try an M4A/AAC file"
                )
            }
            muxer.start()
            muxerStarted = true
            copyTrack(videoExtractor, videoTrack, muxer, outVideoTrack, Long.MAX_VALUE)
            copyTrack(audioExtractor, audioTrack, muxer, outAudioTrack, videoDurationUs)
            MuxResult(true, "Sound attached")
        } catch (e: Exception) {
            try {
                outFile.delete()
            } catch (_: Exception) {
            }
            MuxResult(false, "Could not attach sound: ${e.message}")
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                videoExtractor?.release()
            } catch (_: Exception) {
            }
            try {
                audioExtractor?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        outTrack: Int,
        maxTimeUs: Long
    ) {
        extractor.selectTrack(trackIndex)
        val buffer = ByteBuffer.allocate(512 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val sampleTime = extractor.sampleTime
            if (maxTimeUs > 0 && sampleTime > maxTimeUs) break
            info.set(0, size, sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(outTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(trackIndex)
    }
}

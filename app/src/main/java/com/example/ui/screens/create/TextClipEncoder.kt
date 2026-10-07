package com.example.ui.screens.create

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ZEVORA 3.1.0 — real TEXT-mode clips: renders styled text frames and encodes
 * them into a genuine H.264 MP4 with MediaCodec (ByteBuffer input, no GL),
 * so TEXT posts publish exactly like recorded videos. Canvas text shaping
 * keeps Arabic RTL text correct.
 */
object TextClipEncoder {

    data class EncodeResult(val ok: Boolean, val message: String)

    val STYLE_NAMES = listOf("Ember", "Lagoon", "Honey")
    private val STYLE_COLORS = listOf(
        0xFFFE2C55.toInt() to 0xFF9D4EDD.toInt(),
        0xFF25F4EE.toInt() to 0xFF118AB2.toInt(),
        0xFFFFD166.toInt() to 0xFFFF9F1C.toInt()
    )

    suspend fun encodeTextClip(
        text: String,
        styleIndex: Int,
        durationSec: Int = 3,
        outFile: File
    ): EncodeResult = withContext(Dispatchers.IO) {
        val clean = text.trim().ifBlank { "ZEVORA" }.take(280)
        val bitmap = renderTextBitmap(clean, styleIndex.coerceIn(0, 2), 720, 1280)
        val totalFrames = (durationSec.coerceIn(2, 5)) * 30
        return encodeBitmapClip(bitmap, totalFrames, 30, outFile)
    }

    /** Encodes a still photo into a real H.264 MP4 clip (photo posts). */
    suspend fun encodePhotoClip(
        source: Bitmap,
        durationSec: Int = 3,
        outFile: File
    ): EncodeResult = withContext(Dispatchers.IO) {
        try {
            val width = 720
            val height = 1280
            val targetRatio = width.toFloat() / height.toFloat()
            val srcRatio = source.width.toFloat() / maxOf(1, source.height).toFloat()
            val cropW: Int
            val cropH: Int
            if (srcRatio > targetRatio) {
                cropH = source.height
                cropW = (source.height * targetRatio).toInt().coerceAtMost(source.width)
            } else {
                cropW = source.width
                cropH = (source.width / targetRatio).toInt().coerceAtMost(source.height)
            }
            val cropped = Bitmap.createBitmap(
                source,
                ((source.width - cropW) / 2).coerceAtLeast(0),
                ((source.height - cropH) / 2).coerceAtLeast(0),
                cropW.coerceAtLeast(2),
                cropH.coerceAtLeast(2)
            )
            val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
            if (cropped !== scaled) {
                try {
                    cropped.recycle()
                } catch (_: Exception) {
                }
            }
            encodeBitmapClip(scaled, durationSec.coerceIn(2, 5) * 30, 30, outFile)
        } catch (e: Exception) {
            EncodeResult(false, "Photo clip failed: ${e.message}")
        }
    }

    private suspend fun encodeBitmapClip(
        bitmap: Bitmap,
        totalFrames: Int,
        fps: Int,
        outFile: File
    ): EncodeResult = withContext(Dispatchers.IO) {
        val width = bitmap.width
        val height = bitmap.height
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        try {
            try {
                outFile.parentFile?.mkdirs()
                if (outFile.exists()) outFile.delete()
            } catch (_: Exception) {
            }
            val yuv = argbToNv12(bitmap)
            try {
                bitmap.recycle()
            } catch (_: Exception) {
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var framesQueued = 0
            var eosQueued = false
            var eosSeen = false
            var guard = 0
            while (!eosSeen && guard < 20_000) {
                guard++
                if (!eosQueued) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        if (framesQueued < totalFrames) {
                            val buffer = codec.getInputBuffer(inIndex)
                            if (buffer != null) {
                                buffer.clear()
                                buffer.put(yuv)
                                codec.queueInputBuffer(
                                    inIndex, 0, yuv.size,
                                    framesQueued * 1_000_000L / fps, 0
                                )
                                framesQueued++
                            }
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, 0,
                                totalFrames * 1_000_000L / fps,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            eosQueued = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outIndex)
                        val isCodecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (encoded != null && info.size > 0 && muxerStarted && !isCodecConfig) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosSeen = true
                        }
                    }
                    else -> Unit
                }
            }
            if (!muxerStarted || !eosSeen) {
                return@withContext EncodeResult(false, "Encoder did not finish on this device")
            }
            EncodeResult(true, "Text clip created")
        } catch (e: Exception) {
            try {
                outFile.delete()
            } catch (_: Exception) {
            }
            EncodeResult(false, "Text clip failed: ${e.message}")
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
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderTextBitmap(text: String, style: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (c0, c1) = STYLE_COLORS[style]
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                c0, c1, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        // Soft dark scrim behind the text for legibility.
        val scrim = Paint().apply { color = 0x55000000.toInt() }
        canvas.drawRect(0f, height * 0.30f, width.toFloat(), height * 0.74f, scrim)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(10f, 0f, 4f, 0xAA000000.toInt())
            textAlign = Paint.Align.CENTER
        }
        val layout = StaticLayout.Builder.obtain(
            text, 0, text.length, textPaint, width - 120
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(8f, 1f)
            .setIncludePad(true)
            .build()
        canvas.save()
        canvas.translate(width / 2f, (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()

        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("ZEVORA", width - 36f, height - 48f, mark)
        return bitmap
    }

    /** ARGB bitmap -> NV12 (Y + interleaved U,V) for COLOR_FormatYUV420SemiPlanar. */
    private fun argbToNv12(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIndex = 0
        var uvIndex = w * h
        for (j in 0 until h) {
            for (i in 0 until w) {
                val p = argb[j * w + i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}

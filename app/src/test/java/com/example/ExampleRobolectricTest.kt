package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.ZevoraApiClient
import com.example.data.repository.ZevoraRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var repository: ZevoraRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = ZevoraRepository(context)
    }

    @Test
    fun testAppNameIsZevora() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ZEVORA", appName)
    }

    @Test
    fun testFeedApiLiveConnectionAndVideoParsing() = runBlocking {
        // Query the live feed API
        val feedResponse = try {
            ZevoraApiClient.api.getFeed()
        } catch (e: Exception) {
            null
        }

        assertTrue("Feed response must be successful", feedResponse?.isSuccessful == true)
        val body = feedResponse?.body()
        assertNotNull("Feed response body should not be null", body)
        val videos = body!!.videos
        assertTrue("Database must contain real production videos", videos.isNotEmpty())

        val firstVid = videos.first()
        assertNotNull("Video must have an ID", firstVid.id)
        assertTrue(
            "Video ID must start with vid_, pex_, cov_, or pix_",
            firstVid.id.startsWith("vid_") || firstVid.id.startsWith("pex_") || firstVid.id.startsWith("cov_") || firstVid.id.startsWith("pix_")
        )
        assertNotNull("Video creatorUsername should not be empty", firstVid.creatorUsername)

        val streamUrl = firstVid.videoUrl ?: firstVid.streamUrl ?: ZevoraApiClient.getCanonicalStreamUrl(firstVid.id)
        assertTrue("Stream URL must be HTTPS", streamUrl.startsWith("https://"))
    }

    @Test
    fun testRepositorySyncAndRoomPersistence() = runBlocking {
        repository.syncWithCloud()
        val activeVideos = repository.getActiveVideos().first()
        assertTrue("Active videos in Room should not be empty after sync", activeVideos.isNotEmpty())

        val video = activeVideos.first()
        assertFalse("Production video should not be dummy test URL", video.videoUrl.contains("test.com"))
        assertFalse("Production video should not be empty", video.videoUrl.isBlank())
    }

    @Test
    fun testRetryRefreshesFeedAndRestoresVideos() = runBlocking {
        val result = repository.refreshFeed()
        assertTrue("refreshFeed should succeed", result.isSuccess)
        val count = result.getOrNull() ?: 0
        assertTrue("refreshFeed should return at least 1 video", count > 0)
    }

    @Test
    fun testRefreshVideoUrlResolvesCanonicalStream() = runBlocking {
        val videoId = "vid_fda6bd47573f4bb4"
        val freshUrl = repository.refreshVideoUrl(videoId)
        assertTrue("Fresh URL must be canonical stream", freshUrl.contains("/api/v1/videos/$videoId/stream"))
    }

    @Test
    fun testMedia3MediaItemCreationFromProductionVideo() {
        val testUrl = "https://chort-nine.vercel.app/api/v1/videos/vid_fda6bd47573f4bb4/stream"
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(android.net.Uri.parse(testUrl))
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
            .build()
        assertNotNull(mediaItem.localConfiguration)
        assertEquals(testUrl, mediaItem.localConfiguration?.uri.toString())
    }

    @Test
    fun testAudioMuteVolumeCalculation() {
        var isMuted = false
        var volume = if (isMuted) 0f else 1.0f
        assertEquals(1.0f, volume, 0.001f)

        // Toggle to muted
        isMuted = true
        volume = if (isMuted) 0f else 1.0f
        assertEquals(0f, volume, 0.001f)

        // Toggle back to unmuted
        isMuted = false
        volume = if (isMuted) 0f else 1.0f
        assertEquals(1.0f, volume, 0.001f)
    }
}

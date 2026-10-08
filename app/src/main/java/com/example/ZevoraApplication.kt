package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.repository.ZevoraRepository
import com.example.util.AppPrefs
import com.example.util.CrashHandler
import com.google.firebase.FirebaseApp

class ZevoraApplication : Application(), ImageLoaderFactory {

    lateinit var repository: ZevoraRepository
        private set

    override fun attachBaseContext(base: android.content.Context) {
        CrashHandler.install(base)
        AppPrefs.init(base)
        super.attachBaseContext(AppPrefs.wrapLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.install(this)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            android.util.Log.w("ZevoraApplication", "Firebase init deferred: ${e.message}")
        }
        AppPrefs.init(this)
        repository = ZevoraRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: ZevoraApplication
            private set
    }
}

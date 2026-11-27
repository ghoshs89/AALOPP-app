package com.puzzlequest.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.memory.MemoryCache

class PuzzleQuestApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashStore.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .networkObserverEnabled(false)
            .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
}

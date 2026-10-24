package com.onyx.keyboard

import android.app.Application
import android.content.Context
import com.onyx.keyboard.data.repository.OnyxRepository

class OnyxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

/** Simple process-wide singleton holder (no DI framework needed at this size). */
object AppGraph {

    @Volatile
    private var repoInstance: OnyxRepository? = null

    val repo: OnyxRepository
        get() = repoInstance ?: throw IllegalStateException("AppGraph not initialized")

    fun init(context: Context) {
        if (repoInstance != null) return
        synchronized(this) {
            if (repoInstance != null) return
            repoInstance = OnyxRepository.get(context.applicationContext)
        }
    }
}

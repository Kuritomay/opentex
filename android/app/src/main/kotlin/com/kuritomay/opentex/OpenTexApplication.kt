package com.kuritomay.opentex

import android.app.Application
import com.kuritomay.opentex.data.OpenTexDatabase

class OpenTexApplication : Application() {
    val database by lazy { OpenTexDatabase.create(this) }
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}

package com.komgareader.app

import android.app.Application
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KomgaReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val manufacturer = Build.MANUFACTURER
        val isOnyx = manufacturer.equals("ONYX", ignoreCase = true)
        Log.i("KomgaReaderApp", "Gerät-Hersteller=$manufacturer | Onyx-E-Ink-Modus=${if (isOnyx) "AKTIV" else "INAKTIV (No-Op)"}")
    }
}

package com.komgareader.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.komgareader.app.data.ScreenSaverManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Debug-only screensaver test trigger. Renders a distinctive full-screen test image and installs it
 * as the device screensaver via the real [ScreenSaverManager] (same path the Settings feature uses).
 * Fire from adb:
 *
 *   adb shell am broadcast -a com.komgareader.app.debug.SET_SCREENSAVER \
 *     -n com.komgareader.app.debug/com.komgareader.app.debug.ScreenSaverPocReceiver
 *
 * Then lock the device and observe. Lives in the debug source set only — never ships in release.
 */
class ScreenSaverPocReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun screenSaverManager(): ScreenSaverManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = EntryPointAccessors
            .fromApplication(context.applicationContext, Deps::class.java)
            .screenSaverManager()

        // Render at a deliberately NON-screen size (cover-like 720x1024) to exercise ScreenSaverManager's
        // fit-to-screen scaling — the dimension fix is what makes Onyx accept it.
        val w = 720
        val h = 1024
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Visually unmistakable: BLACK background + white text, so a successful standby update is
        // obvious vs. the earlier white "PoC" demo. Includes a timestamp to distinguish repeated sets.
        Canvas(bmp).apply {
            drawColor(Color.BLACK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = w / 10f
            }
            drawText("RELOAD TEST", w / 2f, h / 2f - paint.textSize, paint)
            paint.textSize = w / 22f
            drawText(System.currentTimeMillis().toString(), w / 2f, h / 2f + paint.textSize, paint)
        }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = manager.applyBytes(bytes)
                Log.i(TAG, "screensaver PoC: ${w}x$h -> set=$ok")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ScreenSaverPoc"
    }
}

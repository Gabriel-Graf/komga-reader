package com.komgareader.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Ersetzt die App-Application im Instrumented-Test durch [HiltTestApplication], damit die
 * `@TestInstallIn`-Module (z.B. in-memory-DB) greifen. Referenziert in `app/build.gradle.kts`
 * als `testInstrumentationRunner`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

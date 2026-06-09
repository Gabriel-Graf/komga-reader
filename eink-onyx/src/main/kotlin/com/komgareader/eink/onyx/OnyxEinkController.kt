package com.komgareader.eink.onyx

import android.util.Log
import android.view.View
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import kotlinx.coroutines.flow.Flow

/**
 * Onyx-Boox-spezifische Implementierung des EinkControllers.
 *
 * Verwendet die [EpdController]-API des Onyx-SDK
 * (`com.onyx.android.sdk:onyxsdk-device:1.3.5`) um E-Ink-Refreshes
 * zu steuern: schnelle DU-Updates beim Blättern, periodisches
 * GC-Full-Refresh gegen Ghosting.
 *
 * Darf NUR auf echter Onyx-Hardware instanziiert werden
 * (Build.MANUFACTURER == "ONYX"). Das AppModule übernimmt diese Wahl.
 */
class OnyxEinkController(
    private val buttonEvents_: Flow<ButtonEvent>,
    private val appPackageName: String,
) : EinkController {

    // canColor = true: korrekt für Onyx Boox Go Color 7 Gen2 (Kaleido-Schirm). Die EpdController-API
    // v1.3.5 bietet keine Geräteklassen-Abfrage — hardcoded ist hier die sichere Wahl für das einzige
    // unterstützte Onyx-Modell. Bei Erweiterung auf mono-Boox-Geräte via Build.MODEL differenzieren.
    override val capabilities = EinkCapabilities(
        hasEink = true,
        canColor = true,
        canInvert = true,
    )

    override val buttonEvents: Flow<ButtonEvent> = buttonEvents_

    // ---------------------------------------------------------------
    // Reader-Lifecycle-Hooks (vom Reader-Composable aufgerufen)
    // ---------------------------------------------------------------

    /**
     * Aktiviert den DU-Schnellmodus für die gesamte App.
     * Muss beim Betreten des Readers aufgerufen werden.
     *
     * Nutzt [EpdController.applyAppScopeUpdate] mit [UpdateMode.DU] —
     * das ist die korrekte API für app-weites Fast-Mode-Rendering in 1.3.x.
     */
    fun enterFastMode() {
        runCatching {
            EpdController.applyAppScopeUpdate(appPackageName, true, true, UpdateMode.DU, 0)
            Log.i(TAG, "Onyx-Fast-Modus (DU) aktiviert — App=$appPackageName")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Aktivieren des Fast-Modus", e)
        }
    }

    /**
     * Deaktiviert den DU-Schnellmodus und kehrt zum Standard-Modus zurück.
     * Muss beim Verlassen des Readers aufgerufen werden.
     */
    fun exitFastMode() {
        runCatching {
            EpdController.clearAppScopeUpdate(true)
            Log.i(TAG, "Onyx-Fast-Modus deaktiviert — App=$appPackageName")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Deaktivieren des Fast-Modus", e)
        }
    }

    /**
     * Erzwingt einen vollständigen GC-Refresh (löscht Ghosting-Artefakte).
     * Sollte alle N Seitenumbrüche aufgerufen werden (z. B. alle 6).
     */
    fun fullRefresh(view: View) {
        runCatching {
            EpdController.refreshScreen(view, UpdateMode.GC)
            Log.d(TAG, "GC-Full-Refresh ausgelöst")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim GC-Full-Refresh", e)
        }
    }

    /**
     * Setzt den Update-Modus einer einzelnen View explizit auf DU.
     * Optional: kann für den Pager-Container aufgerufen werden.
     */
    fun setViewFastMode(view: View) {
        runCatching {
            EpdController.setViewDefaultUpdateMode(view, UpdateMode.DU)
            Log.d(TAG, "View-Update-Modus → DU (Fast)")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Setzen des View-Update-Modus", e)
        }
    }

    /**
     * Setzt den Update-Modus einer View zurück auf den System-Standard.
     */
    fun resetViewMode(view: View) {
        runCatching {
            EpdController.resetViewUpdateMode(view)
            Log.d(TAG, "View-Update-Modus zurückgesetzt")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Zurücksetzen des View-Update-Modus", e)
        }
    }

    // ---------------------------------------------------------------
    // EinkController-Interface
    // ---------------------------------------------------------------

    override fun refresh(region: Region, mode: RefreshMode) {
        // Kein View-Handle hier verfügbar; die Reader-Composable
        // ruft fullRefresh(view) / enterFastMode() direkt auf.
        Log.d(TAG, "refresh($region, $mode) — kein direktes View-Handle")
    }

    override fun setContrast(level: Int) {
        // FrontLightController wäre der richtige Ort dafür;
        // vorerst als No-Op mit Log.
        Log.d(TAG, "setContrast($level) — noch nicht implementiert")
    }

    override fun setInverted(inverted: Boolean) {
        runCatching {
            if (inverted) EpdController.enableNightMode() else EpdController.disableNightMode()
            Log.d(TAG, "setInverted($inverted) — Night-Mode gesetzt")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Setzen des Night-Mode", e)
        }
    }

    companion object {
        private const val TAG = "OnyxEinkController"
    }
}

package com.komgareader.app.ui.shell

import com.komgareader.ui.shell.ShellFormFactor
import com.komgareader.ui.shell.ShellPack

/**
 * Registry der Shell-Packs — analog [com.komgareader.app.ui.theme.UiPackRegistry], eine Schicht höher.
 * Heute zwei Built-ins, Auswahl nach Form-Faktor. Hier hängt sich später ein externer APK-Pack-Lader
 * (Ansatz 3, Phase 4) und ein manueller User-Override ein. Die Built-ins ([DefaultShell]/[PhoneShell])
 * koppeln an app-Komponenten/i18n und bleiben deshalb in `:app` — der [ShellPack]-Vertrag selbst liegt
 * im Modul `:ui-api` (A1).
 */
object ShellPackRegistry {
    fun forFormFactor(formFactor: ShellFormFactor): ShellPack = when (formFactor) {
        ShellFormFactor.COMPACT -> PhoneShell
        ShellFormFactor.EXPANDED -> DefaultShell
    }
}

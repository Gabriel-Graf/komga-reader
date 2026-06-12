package com.komgareader.app.ui.shell

import com.komgareader.ui.shell.ShellFormFactor
import com.komgareader.ui.shell.ShellPack
import com.komgareader.ui.shell.descriptorFor

/**
 * Registry der Shell-Packs — analog [com.komgareader.app.ui.theme.UiPackRegistry], eine Schicht höher.
 * Liefert die EINE host-eigene [DeclarativeShell], geschaltet über den Form-Faktor-Deskriptor
 * ([descriptorFor]). Hier hängt sich später ein externer APK-Pack-Lader (Ansatz 3, Phase 4 / L2) ein,
 * der nur den [com.komgareader.ui.shell.ShellDescriptor] extern liefert — der Renderer bleibt derselbe.
 * Die [DeclarativeShell] koppelt an app-Komponenten/i18n und bleibt deshalb in `:app` — der
 * [ShellPack]-Vertrag selbst liegt im Modul `:ui-api` (A1).
 */
object ShellPackRegistry {
    fun forFormFactor(formFactor: ShellFormFactor): ShellPack = DeclarativeShell(descriptorFor(formFactor))
}

package com.komgareader.app.ui.shell

import androidx.compose.runtime.Composable

/** Vertrag: ein Pack ordnet die [AppShellState]-Stücke zu einem ganzen Home-Skelett an. Built-ins sind
 *  Compose (Ansatz 1); externe deklarative Packs (Ansatz 3) interpretiert später eine `DeclarativeShell`
 *  über dieselbe Surface. */
interface ShellPack {
    @Composable fun Render(state: AppShellState)
}

/** Form-Faktor-Achse (Bildschirmbreite), orthogonal zur Geräteklasse (Theme). */
enum class ShellFormFactor { COMPACT, EXPANDED }

/** Pure Auflösung: <600dp = compact (Phone), sonst expanded (Tablet/E-Ink). Unit-testbar, Compose-frei. */
fun formFactorFor(widthDp: Int): ShellFormFactor =
    if (widthDp < 600) ShellFormFactor.COMPACT else ShellFormFactor.EXPANDED

/**
 * Registry der Shell-Packs — analog [com.komgareader.app.ui.theme.UiPackRegistry], eine Schicht höher.
 * Heute zwei Built-ins, Auswahl nach Form-Faktor. Hier hängt sich später ein externer APK-Pack-Lader
 * (Ansatz 3, Phase 4) und ein manueller User-Override ein.
 */
object ShellPackRegistry {
    fun forFormFactor(formFactor: ShellFormFactor): ShellPack = when (formFactor) {
        ShellFormFactor.COMPACT -> PhoneShell
        ShellFormFactor.EXPANDED -> DefaultShell
    }
}

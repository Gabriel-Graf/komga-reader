package com.komgareader.app.ui.shell

import com.komgareader.domain.model.DisplayMode
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle

/**
 * Aurora ist der Smartphone-Look → seine Nav ist die schwebende Pill-Bottom-Nav (FLOATING_NAV),
 * unabhängig vom Form-Faktor. Pure Funktion (testbar). Der Host kombiniert: ein L2-UI-Pack-Override
 * schlägt diesen Aurora-Default (siehe HomeShellHost). E-Ink-Modus → kein Override (Form-Faktor-Default).
 */
fun auroraShellOverride(mode: DisplayMode): ShellDescriptor? =
    if (mode == DisplayMode.SMARTPHONE) ShellDescriptor(ShellNavStyle.FLOATING_NAV) else null

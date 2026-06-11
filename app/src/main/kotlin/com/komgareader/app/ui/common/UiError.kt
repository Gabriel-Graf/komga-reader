package com.komgareader.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.Strings
import java.io.IOException

/** Semantische Fehlerklasse für nutzerfreundliche Meldungen. */
enum class ErrorKind { NO_CONNECTION, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, SERVER, UNKNOWN }

/** Klare Klasse + rohe technische Detailzeile (z. B. "HTTP 401 Unauthorized"). */
data class UiError(val kind: ErrorKind, val detail: String)

private val HTTP_CODE = Regex("""HTTP (\d{3})""")

/**
 * Bildet ein Throwable auf eine [UiError] ab. `app` hat keine Retrofit-Abhängigkeit, daher:
 * IO-Fehler (auch in der cause-Kette) = keine Verbindung; HTTP-Status wird aus der (Retrofit-)
 * Message geparst. [UiError.detail] trägt die rohe Meldung für die Diagnose-Zeile.
 */
fun uiErrorOf(t: Throwable?): UiError {
    if (t == null) return UiError(ErrorKind.UNKNOWN, "")
    val detail = t.message?.takeIf { it.isNotBlank() } ?: (t::class.simpleName ?: "Fehler")
    val code = HTTP_CODE.find(t.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
    val kind = when {
        code == 401 -> ErrorKind.UNAUTHORIZED
        code == 403 -> ErrorKind.FORBIDDEN
        code == 404 -> ErrorKind.NOT_FOUND
        code != null && code >= 500 -> ErrorKind.SERVER
        code != null -> ErrorKind.UNKNOWN // sonstige 4xx
        isConnectionError(t) -> ErrorKind.NO_CONNECTION
        else -> ErrorKind.UNKNOWN
    }
    return UiError(kind, detail)
}

private fun isConnectionError(t: Throwable): Boolean =
    generateSequence(t) { it.cause }.any { it is IOException }

/** Lokalisierte, klare Meldung für die [ErrorKind] — ohne Compose (z. B. für Snackbars). */
fun ErrorKind.label(s: Strings): String =
    when (this) {
        ErrorKind.NO_CONNECTION -> s.errorNoConnection
        ErrorKind.UNAUTHORIZED -> s.errorUnauthorized
        ErrorKind.FORBIDDEN -> s.errorForbidden
        ErrorKind.NOT_FOUND -> s.errorNotFound
        ErrorKind.SERVER -> s.errorServer
        ErrorKind.UNKNOWN -> s.errorUnknown
    }

/** Einzeilige Snackbar-Meldung: klare Meldung + roher Code in Klammern, falls vorhanden. */
fun UiError.snackbar(s: Strings): String =
    if (detail.isNotBlank()) "${kind.label(s)} ($detail)" else kind.label(s)

/** Lokalisierte, klare Meldung für die [ErrorKind]. */
@Composable
fun ErrorKind.label(): String {
    val s = LocalStrings.current
    return when (this) {
        ErrorKind.NO_CONNECTION -> s.errorNoConnection
        ErrorKind.UNAUTHORIZED -> s.errorUnauthorized
        ErrorKind.FORBIDDEN -> s.errorForbidden
        ErrorKind.NOT_FOUND -> s.errorNotFound
        ErrorKind.SERVER -> s.errorServer
        ErrorKind.UNKNOWN -> s.errorUnknown
    }
}

/**
 * Zweizeiliger Fehlerblock: klare Meldung groß, rohe Detailzeile klein/gedämpft darunter.
 * E-Ink: gedämpftes Detail über `onSurfaceVariant` + `bodySmall`, keine Animation.
 */
@Composable
fun UiErrorText(error: UiError, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(error.kind.label(), textAlign = TextAlign.Center)
        if (error.detail.isNotBlank()) {
            Text(
                error.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

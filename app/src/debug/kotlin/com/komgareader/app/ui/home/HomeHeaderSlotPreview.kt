package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.ui.slots.HomeHeaderSearch
import com.komgareader.ui.slots.HomeHeaderState

/**
 * Swap-Beweis: ein alternatives Home-Header-Layout (Status oben, Aktionen darunter, Menü-Overlay),
 * das dieselbe [HomeHeaderState]-Surface anders anordnet — ohne Tab-Logik anzufassen. Belegt, dass
 * ein UI-Pack den ganzen Header ersetzen kann. NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Composable
fun AlternativeHomeHeader(state: HomeHeaderState) {
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.status()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.actions(this)
        }
        state.menu()
    }
}

@Preview(widthDp = 1264, heightDp = 200)
@Composable
private fun AlternativeHomeHeaderPreview() {
    AlternativeHomeHeader(
        HomeHeaderState(
            status = {},
            search = HomeHeaderSearch("", {}, {}, "Suche", "", null, null, null),
            filter = null,
            menu = {},
            actions = {},
        ),
    )
}

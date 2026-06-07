package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.FilteredReaderImage
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.eink.onyx.OnyxRefresher

/**
 * Roman-Reader (ViewerType.NOVEL): zeigt eine **reflowte** EPUB-Seite, die der
 * [NovelReaderViewModel] über die crengine-Engine zur Pixel-Größe des Viewports
 * umschichtet. Baut auf dem geteilten [ReaderScaffold] auf (Drittel-Tap-Zonen,
 * Overlay, Status-Fuß) — keine Parallel-Linie (siehe `shared-structure-before-variants`).
 *
 * **Keine Animation:** Der Seitenwechsel ist ein sofortiger State-Wechsel
 * (currentPage) + ein gezielter Full-Refresh über den [OnyxRefresher] gegen
 * Ghosting — wie der bewusste Bildwechsel im Comic-Reader (E-Ink, `animation-gating`).
 */
@Composable
fun NovelReaderScreen(
    onBack: () -> Unit,
    novelVm: NovelReaderViewModel = hiltViewModel(),
    refresher: OnyxRefresher? = null,
) {
    val state by novelVm.uiState.collectAsState()
    val reflowConfig by novelVm.reflowConfig.collectAsState()
    val chapters by novelVm.chapters.collectAsState()
    val currentChapterTitle by novelVm.currentChapterTitle.collectAsState()
    val progressPercent by novelVm.progressPercent.collectAsState()
    val rootView = LocalView.current
    val strings = LocalStrings.current
    var typoPanelOpen by remember { mutableStateOf(false) }
    var tocPanelOpen by remember { mutableStateOf(false) }
    var searchPanelOpen by remember { mutableStateOf(false) }

    // Reflow-Seitenwechsel ist ein bewusster Bildwechsel -> sofortiger GC-Full-Refresh
    // gegen Ghosting (No-Op auf Nicht-Boox). Ein Re-Layout (Typo-Änderung) ändert
    // pageCount -> ebenfalls als Full-Refresh-Auslöser einbeziehen.
    LaunchedEffect(state.currentPage, state.pageCount) {
        novelVm.onPageSettled(state.currentPage)
        refresher?.fullRefreshNow(rootView)
    }

    // Max. ein Dialog gleichzeitig über dem Reader (E-Ink-Designsprache): exklusive Verzweigung.
    when {
        typoPanelOpen -> NovelTypoPanel(
            config = reflowConfig,
            onFontSizeEm = novelVm::setFontSizeEm,
            onLineHeight = novelVm::setLineHeight,
            onMargin = novelVm::setMargin,
            onFontFamily = novelVm::setFontFamily,
            onTextAlign = novelVm::setTextAlign,
            onHyphenation = novelVm::setHyphenation,
            onDismiss = { typoPanelOpen = false },
        )
        tocPanelOpen -> NovelTocPanel(
            chapters = chapters,
            onChapterSelected = novelVm::goToAnchor,
            onDismiss = { tocPanelOpen = false },
        )
        searchPanelOpen -> NovelSearchPanel(
            onSearch = novelVm::search,
            onHitSelected = novelVm::goToAnchor,
            onGoToProgress = novelVm::goToProgress,
            onDismiss = { searchPanelOpen = false },
        )
    }

    ReaderScaffold(
        chrome = novelVm,
        title = "${state.currentPage + 1} / ${state.pageCount.coerceAtLeast(1)}",
        onBack = onBack,
        onPrev = novelVm::prevPage,
        onNext = novelVm::nextPage,
        background = Color.White,
        actions = {
            IconButton(onClick = { tocPanelOpen = true }) {
                Icon(
                    AppIcons.TableOfContents,
                    contentDescription = strings.novelToc,
                    tint = Color.White,
                )
            }
            IconButton(onClick = { searchPanelOpen = true }) {
                Icon(
                    AppIcons.Search,
                    contentDescription = strings.novelSearch,
                    tint = Color.White,
                )
            }
            IconButton(onClick = { typoPanelOpen = true }) {
                Icon(
                    AppIcons.Typography,
                    contentDescription = strings.novelTypography,
                    tint = Color.White,
                )
            }
        },
        footer = {
            NovelStatusFooter(
                progressPercent = progressPercent,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                chapterTitle = currentChapterTitle,
            )
        },
    ) {
        // Der Viewport gibt erst hier seine Pixel-Größe her: damit öffnet das VM das
        // EPUB und schichtet es passend um (idempotent).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportW = constraints.maxWidth
            val viewportH = constraints.maxHeight
            LaunchedEffect(viewportW, viewportH) {
                novelVm.open(viewportW, viewportH)
            }

            when {
                state.error != null -> Box(
                    Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error!!, color = Color.Black)
                }
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
                else -> {
                    // Auf Seite UND Layout-Generation keyen: nach einem Re-Layout
                    // (Typo-Änderung) ist der Render-Cache geleert; ohne den
                    // Generations-Key behielte produceState die alte Bitmap-Referenz,
                    // selbst wenn der Seitenindex gleich bleibt -> frisch rendern.
                    val bmp by produceState<Bitmap?>(
                        initialValue = null,
                        key1 = state.currentPage,
                        key2 = state.layoutGeneration,
                    ) {
                        value = novelVm.renderPage(state.currentPage)
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bmp != null) {
                            FilteredReaderImage(
                                bitmap = bmp!!,
                                contentDescription = "Seite ${state.currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status-Fuß des Roman-Readers: Fortschritt in %, Seite X / N und der Titel des
 * aktuellen Kapitels (sofern vorhanden). Flach, ohne Schatten — eine schmale,
 * halbtransparente Leiste am unteren Rand. Alle Texte lokalisiert ([LocalStrings]).
 *
 * [BoxScope]-Erweiterung: die Leiste richtet sich nur am unteren Rand des Scaffolds
 * aus (`align(BottomCenter)`) — **kein** `fillMaxSize`-Box, der sonst über den ganzen
 * Reader läge und die Tap-Zonen-Gesten schlucken würde.
 */
@Composable
private fun BoxScope.NovelStatusFooter(
    progressPercent: Int,
    currentPage: Int,
    pageCount: Int,
    chapterTitle: String?,
) {
    val strings = LocalStrings.current
    val pageLabel = "${strings.novelPageOfCount} ${currentPage + 1} / ${pageCount.coerceAtLeast(1)}"
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$progressPercent %",
            color = Color.Black,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = pageLabel,
            color = Color.Black,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!chapterTitle.isNullOrBlank()) {
            Text(
                text = chapterTitle,
                color = Color.Black,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

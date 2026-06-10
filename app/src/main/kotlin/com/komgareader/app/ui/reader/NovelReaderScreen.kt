package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.FilteredReaderImage
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.eink.onyx.OnyxRefresher

/**
 * Roman-Reader (ViewerType.NOVEL): zeigt eine **reflowte** EPUB-Seite, die der
 * [NovelReaderViewModel] über die crengine-Engine zur Pixel-Größe des Viewports
 * umschichtet. Baut auf dem geteilten [ReaderScaffold] auf (Drittel-Tap-Zonen,
 * Overlay, Status-Fuß) — keine Parallel-Linie (siehe `shared-structure-before-variants`).
 *
 * **Keine Animation:** Der Seitenwechsel ist ein sofortiger State-Wechsel
 * (currentPage). Der Refresh läuft — wie im Paged-Reader — über den geteilten
 * [com.komgareader.domain.eink.RefreshScheduler]: PARTIAL beim Blättern, periodische
 * GC-Full-Promotion gegen Ghosting, forceFull nur beim Re-Layout (E-Ink, `animation-gating`).
 */
@Composable
fun NovelReaderScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    novelVm: NovelReaderViewModel = hiltViewModel(),
    refresher: OnyxRefresher? = null,
) {
    val state by novelVm.uiState.collectAsState()
    val reflowConfig by novelVm.reflowConfig.collectAsState()
    val chapters by novelVm.chapters.collectAsState()
    val currentChapterTitle by novelVm.currentChapterTitle.collectAsState()
    val progressPercent by novelVm.progressPercent.collectAsState()
    val bookTitle by novelVm.bookTitle.collectAsState()
    val bookAuthor by novelVm.bookAuthor.collectAsState()
    val rootView = LocalView.current
    val strings = LocalStrings.current
    var typoPanelOpen by remember { mutableStateOf(false) }
    var tocPanelOpen by remember { mutableStateOf(false) }
    var searchPanelOpen by remember { mutableStateOf(false) }

    // Ein normaler Roman-Seitenwechsel ist KEIN bewusster Bildwechsel, sondern Blättern wie im
    // Paged-Reader → PARTIAL (schneller DU-Refresh), mit periodischer GC-Promotion gegen Ghosting
    // über den geteilten Scheduler (alle N Seiten ein Full). NUR ein Re-Layout (Typo-Änderung →
    // pageCount ändert sich) ist ein bewusster Vollbild-Wechsel → forceFull. Sonst flasht jede
    // Seite voll (Onyx-„Regal"-Anmutung). No-Op auf Nicht-Boox.
    var lastPageCount by remember { mutableStateOf(state.pageCount) }
    LaunchedEffect(state.currentPage, state.pageCount) {
        novelVm.onPageSettled(state.currentPage)
        val relayout = state.pageCount != lastPageCount
        lastPageCount = state.pageCount
        if (novelVm.refreshScheduler.onContentChange(forceFull = relayout) == RefreshMode.FULL) {
            refresher?.fullRefreshNow(rootView)
        }
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
            onFontWeight = novelVm::setFontWeight,
            onDismiss = { typoPanelOpen = false },
        )
        tocPanelOpen -> NovelTocPanel(
            chapters = chapters,
            onChapterSelected = novelVm::goToAnchor,
            onDismiss = { tocPanelOpen = false },
        )
        searchPanelOpen -> NovelSearchPanel(
            pageCount = state.pageCount,
            onSearch = novelVm::search,
            onHitSelected = novelVm::goToAnchor,
            onGoToPage = novelVm::navigateTo,
            onGoToProgress = novelVm::goToProgress,
            onDismiss = { searchPanelOpen = false },
        )
    }

    ReaderScaffold(
        modifier = Modifier.testTag("reader_novel"),
        chrome = novelVm,
        title = "${state.currentPage + 1} / ${state.pageCount.coerceAtLeast(1)}",
        onBack = onBack,
        onHome = onHome,
        onSettings = onSettings,
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
        // Dauerhafter, eigener Page-Header (Autor·Titel | Uhr) + Page-Footer (Kapitel | Seite·%) —
        // ersetzt den abgeschalteten crengine-Streifen. Beide aus der geteilten ReaderInfoBar (DRY).
        persistentBars = {
            NovelPageHeader(author = bookAuthor, title = bookTitle)
            NovelPageFooter(
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
 * Dauerhafter **Page-Header** oben (ersetzt den crengine-Streifen): links Autor·Titel,
 * rechts die laufende Uhrzeit. Aus der geteilten [ReaderInfoBar] (DRY mit dem Footer),
 * Hairline-Trennlinie nach unten zur Seite.
 */
@Composable
private fun BoxScope.NovelPageHeader(author: String, title: String) {
    val left = listOf(author, title).filter { it.isNotBlank() }.joinToString(" · ")
    ReaderInfoBar(
        align = Alignment.TopCenter,
        dividerOnTop = false,
        start = { ReaderInfoText(left) },
        end = { ReaderInfoText(rememberClockText()) },
    )
}

/**
 * Dauerhafter **Page-Footer** unten: links das aktuelle Kapitel, rechts Seite X / N · %.
 * Aus derselben geteilten [ReaderInfoBar] (DRY mit dem Header), Hairline-Trennlinie nach
 * oben zur Seite. Alle Texte lokalisiert ([LocalStrings]).
 */
@Composable
private fun BoxScope.NovelPageFooter(
    progressPercent: Int,
    currentPage: Int,
    pageCount: Int,
    chapterTitle: String?,
) {
    val pageLabel = "${currentPage + 1} / ${pageCount.coerceAtLeast(1)} · $progressPercent %"
    ReaderInfoBar(
        align = Alignment.BottomCenter,
        dividerOnTop = true,
        start = { ReaderInfoText(chapterTitle.orEmpty()) },
        end = { ReaderInfoText(pageLabel) },
    )
}

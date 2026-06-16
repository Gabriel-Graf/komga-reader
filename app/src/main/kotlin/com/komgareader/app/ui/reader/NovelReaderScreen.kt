package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.style.TextAlign
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.common.label
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredReaderImage
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.domain.render.IntRect
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.ReaderBottomSheet
import com.komgareader.ui.slots.ReaderTapZones

/**
 * Roman-Reader (ViewerType.NOVEL): zeigt eine **reflowte** EPUB-Seite, die der
 * [NovelReaderViewModel] über die crengine-Engine zur Pixel-Größe des Viewports
 * umschichtet. Baut auf dem geteilten [ReaderScaffold] auf (Drittel-Tap-Zonen,
 * Overlay, Status-Fuß) — keine Parallel-Linie (siehe `shared-structure-before-variants`).
 *
 * **Keine Animation:** Der Seitenwechsel ist ein sofortiger State-Wechsel
 * (currentPage). Der Refresh läuft — wie im Paged-Reader — über den geteilten
 * Refresh-Verhalten wird dynamisch über den [com.komgareader.app.data.EinkContextController]
 * gesteuert (E-Ink, `animation-gating`).
 */
@Composable
fun NovelReaderScreen(
    readerKind: ReaderKind,
    bookRemoteId: String,
    sourceId: Long,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    novelVm: NovelReaderViewModel = hiltViewModel(),
) {
    val state by novelVm.uiState.collectAsState()
    val reflowConfig by novelVm.reflowConfig.collectAsState()
    val chapters by novelVm.chapters.collectAsState()
    val currentChapterTitle by novelVm.currentChapterTitle.collectAsState()
    val chapterFractions by novelVm.chapterFractions.collectAsState()
    val bookTitle by novelVm.bookTitle.collectAsState()
    val bookAuthor by novelVm.bookAuthor.collectAsState()
    val availableNovelFonts by novelVm.availableNovelFonts.collectAsState()
    val fontSampleFiles by novelVm.fontSampleFiles.collectAsState()
    val bookmarks by novelVm.bookmarksFlow.collectAsState()
    val bookmarkMode by novelVm.bookmarkMode.collectAsState()
    val markerStyleName by novelVm.markerStyle.collectAsState()
    val strings = LocalStrings.current
    var sheetExpanded by remember { mutableStateOf(false) }
    var sheetTab by rememberSaveable { mutableStateOf(NovelSheetTab.TYPOGRAPHY) }
    var searchPanelOpen by remember { mutableStateOf(false) }
    var tocPanelOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<Long?>(null) }

    ReadingSessionEffect(readerKind, bookRemoteId, sourceId, state.currentPage)

    // Report page settle for progress tracking; refresh is device-managed via EinkContext.
    LaunchedEffect(state.currentPage) {
        novelVm.onPageSettled(state.currentPage)
    }

    // Max. ein Dialog gleichzeitig über dem Reader (E-Ink-Designsprache): exklusive Verzweigung.
    when {
        searchPanelOpen -> NovelSearchPanel(
            pageCount = state.pageCount,
            onSearch = novelVm::search,
            onHitSelected = novelVm::goToAnchor,
            onGoToPage = novelVm::navigateTo,
            onGoToProgress = novelVm::goToProgress,
            onDismiss = { searchPanelOpen = false },
        )
        tocPanelOpen -> NovelTocPanel(
            chapters = chapters,
            onChapterSelected = novelVm::goToAnchor,
            onDismiss = { tocPanelOpen = false },
        )
    }

    // Hardware back closes the bottom sheet first.
    BackHandler(sheetExpanded) { sheetExpanded = false }

    // Bookmark-Label umbenennen (E-Ink-Dialog mit einem Textfeld).
    renameId?.let { id ->
        val existing = bookmarks.firstOrNull { it.id == id }
        BookmarkRenameDialog(
            initial = existing?.label.orEmpty(),
            onConfirm = { novelVm.renameBookmark(id, it); renameId = null },
            onDismiss = { renameId = null },
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
        tapZones = if (bookmarkMode) {
            null
        } else {
            ReaderTapZones.HorizontalThirds(novelVm::prevPage, novelVm::toggleChrome, novelVm::nextPage)
        },
        showTapZoneHints = !bookmarkMode,
        // Hold off the system back-edge swipes so they don't collide with reading gestures (the
        // system home swipe-up is an OS-level gesture and cannot be disabled by the app).
        gestureExclusion = true,
        actions = {
            IconButton(onClick = { novelVm.toggleBookmarkMode() }) {
                Icon(
                    if (bookmarkMode) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    contentDescription = strings.novelBookmarkMode,
                    tint = Color.White,
                )
            }
            IconButton(onClick = { tocPanelOpen = true }) {
                Icon(AppIcons.ListView, contentDescription = strings.novelToc, tint = Color.White)
            }
            IconButton(onClick = { searchPanelOpen = true }) {
                Icon(
                    AppIcons.Search,
                    contentDescription = strings.novelSearch,
                    tint = Color.White,
                )
            }
        },
        // Dauerhafter, eigener Page-Header (Autor·Titel | Uhr) + Page-Footer (Kapitel | Seite·%) —
        // ersetzt den abgeschalteten crengine-Streifen. Beide aus der geteilten ReaderInfoBar (DRY).
        persistentBars = {
            NovelPageHeader(author = bookAuthor, title = bookTitle)
            NovelPageFooter(
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                chapterTitle = currentChapterTitle,
                chapterFractions = chapterFractions,
            )
        },
        bottomSheet = ReaderBottomSheet(
            expanded = sheetExpanded,
            onExpandedChange = { sheetExpanded = it },
            peekLabel = strings.novelSettings,
            content = {
                NovelSettingsSheet(
                    selectedTab = sheetTab,
                    onTabChange = { sheetTab = it },
                    config = reflowConfig,
                    onFontSizeEm = novelVm::setFontSizeEm,
                    onLineHeight = novelVm::setLineHeight,
                    onFontWeight = novelVm::setFontWeight,
                    onMargin = novelVm::setMargin,
                    onTextAlign = novelVm::setTextAlign,
                    onHyphenation = novelVm::setHyphenation,
                    onFontFamily = novelVm::setFontFamily,
                    bookmarks = bookmarks,
                    onBookmarkJump = novelVm::jumpToBookmark,
                    onBookmarkRename = { renameId = it },
                    onBookmarkDelete = novelVm::deleteBookmark,
                    onBookmarkJumped = { sheetExpanded = false },
                    availableFonts = availableNovelFonts,
                    fontFiles = fontSampleFiles,
                )
            },
        ),
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(state.error!!.kind.label(), color = Color.Black, textAlign = TextAlign.Center)
                        if (state.error!!.detail.isNotBlank()) {
                            Text(
                                state.error!!.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (bookmarkMode) {
                                    Modifier.pointerInput(state.currentPage, bookmarkMode) {
                                        detectTapGestures { offset ->
                                            novelVm.onWordTap(offset.x.toInt(), offset.y.toInt())
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            FilteredReaderImage(
                                bitmap = bmp!!,
                                contentDescription = "Seite ${state.currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            BookmarkMarkers(
                                novelVm = novelVm,
                                bookmarks = bookmarks,
                                currentPage = state.currentPage,
                                layoutGeneration = state.layoutGeneration,
                                markerStyleName = markerStyleName,
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
 * Dauerhafter **Page-Footer** unten: links das aktuelle Kapitel, **mittig** der
 * KOReader-artige Fortschrittsbalken (Pill mit Kapitel-Punkten), rechts Seite X / N.
 * Aus derselben geteilten [ReaderInfoBar] (DRY mit dem Header), Hairline-Trennlinie nach
 * oben zur Seite. Alle Texte lokalisiert ([LocalStrings]).
 */
@Composable
private fun BoxScope.NovelPageFooter(
    currentPage: Int,
    pageCount: Int,
    chapterTitle: String?,
    chapterFractions: List<Float>,
) {
    val last = (pageCount - 1).coerceAtLeast(1)
    val progress = (currentPage.toFloat() / last).coerceIn(0f, 1f)
    val pageLabel = "${currentPage + 1} / ${pageCount.coerceAtLeast(1)}"
    ReaderInfoBar(
        align = Alignment.BottomCenter,
        dividerOnTop = true,
        start = { ReaderInfoText(chapterTitle.orEmpty()) },
        center = {
            NovelProgressBar(
                progress = progress,
                chapterFractions = chapterFractions,
            )
        },
        end = { ReaderInfoText(pageLabel) },
    )
}

/**
 * KOReader-artiger Fortschrittsbalken: ein längliches **Oval (Pill)** als **Umriss** (leer = weiß mit
 * dünnem Rand). Der Fortschritt ist eine **mittige dünne Linie**, die von links bis [progress] wächst
 * — oben und unten im Oval bleibt weiß, sodass es eine Linie *im* Oval ist, kein voll geschwärztes
 * Oval. Auf der Mittellinie sitzen **kleine Kapitel-Punkte** ([chapterFractions], je Startposition
 * 0–1): kommende heben sich auf dem Weiß ab, gelesene gehen in der Fortschritts-Linie unter.
 *
 * Füllt die volle Breite seines Slots. **E-Ink (Pflicht):** monochrom schwarz, **keine Animation** —
 * der Füllstand folgt direkt der Seite (sofortiger State-Wechsel).
 */
@Composable
private fun NovelProgressBar(
    progress: Float,
    chapterFractions: List<Float>,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) {
        val h = size.height
        val w = size.width
        val r = h / 2f
        val strokePx = BAR_STROKE.toPx()
        // Oval-Umriss (Pill): dünner schwarzer Rand, weißes Inneres.
        drawRoundRect(
            color = Color.Black,
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = strokePx),
            topLeft = Offset(strokePx / 2f, strokePx / 2f),
            size = Size(w - strokePx, h - strokePx),
        )
        // Fortschritt als mittige dünne Linie zwischen den Rundungen; oben/unten bleibt weiß.
        val left = r
        val trackW = (w - 2f * r).coerceAtLeast(0f)
        val lineH = h * 0.30f
        val lineR = lineH / 2f
        val fillW = (trackW * progress).coerceIn(0f, trackW)
        if (fillW > 0f) {
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(left, (h - lineH) / 2f),
                size = Size(fillW, lineH),
                cornerRadius = CornerRadius(lineR, lineR),
            )
        }
        // Kleine Kapitel-Punkte auf der Mittellinie (dezente Ticks, gleiche Stärke wie die Linie).
        val dotR = lineR
        chapterFractions.forEach { f ->
            val cx = left + trackW * f.coerceIn(0f, 1f)
            drawCircle(color = Color.Black, radius = dotR, center = Offset(cx, h / 2f))
        }
    }
}

private val BAR_HEIGHT = 12.dp
private val BAR_STROKE = 1.5.dp

/**
 * Zeichnet die Bookmark-Marker über die gerenderte Seite. Die Rects kommen aus der
 * crengine-Engine ([NovelReaderViewModel.bookmarkRectsForCurrentPage]) im selben Pixel-Raum
 * wie die 1:1 dargestellte Seiten-Bitmap. **Monochrom schwarz, keine Animation** (E-Ink):
 * je nach [BookmarkMarkerStyle] entweder ein Margin-Balken links oder eine Unterstreichung.
 */
@Composable
private fun BookmarkMarkers(
    novelVm: NovelReaderViewModel,
    bookmarks: List<NovelBookmark>,
    currentPage: Int,
    layoutGeneration: Int,
    markerStyleName: String,
) {
    val rects by produceState(
        initialValue = emptyMap<String, IntRect>(),
        currentPage,
        layoutGeneration,
        bookmarks,
    ) {
        value = novelVm.bookmarkRectsForCurrentPage()
    }
    if (rects.isEmpty()) return
    val margin = markerStyleName == BookmarkMarkerStyle.MARGIN.name
    Canvas(Modifier.fillMaxSize()) {
        rects.values.forEach { r ->
            if (margin) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(0f, r.top.toFloat()),
                    size = Size(8f, (r.bottom - r.top).toFloat()),
                )
            } else {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(r.left.toFloat(), (r.bottom - 3).toFloat()),
                    size = Size((r.right - r.left).toFloat(), 3f),
                )
            }
        }
    }
}

/**
 * Umbenennen-Dialog für ein Bookmark-Label: ein einzelnes Textfeld im [EinkModal]
 * (gleiche E-Ink-Form wie das Sammlung-Umbenennen). Ein leerer Wert ist erlaubt
 * (= kein Label); das VM interpretiert `null`/leer als „kein Label".
 */
@Composable
private fun BookmarkRenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var label by remember { mutableStateOf(initial) }
    EinkModal(
        title = strings.novelBookmarkRename,
        onDismiss = onDismiss,
        confirmLabel = strings.save,
        onConfirm = { onConfirm(label.trim()) },
        dismissLabel = strings.cancel,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val progressPercent by novelVm.progressPercent.collectAsState()
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
    var bookmarkPanelOpen by remember { mutableStateOf(false) }
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
        bookmarkPanelOpen -> NovelBookmarkPanel(
            bookmarks = bookmarks,
            onJump = novelVm::jumpToBookmark,
            onRename = { renameId = it },
            onDelete = novelVm::deleteBookmark,
            onDismiss = { bookmarkPanelOpen = false },
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
        actions = {
            IconButton(onClick = { novelVm.toggleBookmarkMode() }) {
                Icon(
                    if (bookmarkMode) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    contentDescription = strings.novelBookmarkMode,
                    tint = Color.White,
                )
            }
            IconButton(onClick = { bookmarkPanelOpen = true }) {
                Icon(AppIcons.ListView, contentDescription = strings.novelBookmarks, tint = Color.White)
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
                progressPercent = progressPercent,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                chapterTitle = currentChapterTitle,
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
                    chapters = chapters,
                    onChapterSelected = { anchor ->
                        novelVm.goToAnchor(anchor)
                        sheetExpanded = false
                    },
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

package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.domain.source.PageRef

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebtoonReaderScreen(
    pages: List<PageRef>,
    authHeaders: Map<String, String>,
    initialPage: Int,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onBack: () -> Unit,
    onPageVisible: (Int) -> Unit,
    onToggleMode: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val ctx = LocalContext.current
    val pageCount = pages.size

    // Fortschritt tracken anhand des ersten sichtbaren Index
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageVisible(listState.firstVisibleItemIndex)
    }

    Scaffold(
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    title = {
                        Text("${listState.firstVisibleItemIndex + 1} / $pageCount")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleMode) {
                            Icon(
                                Icons.Filled.ViewDay,
                                contentDescription = "Zu Paged-Modus wechseln",
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (chromeVisible) padding else PaddingValues(0.dp)),
                contentPadding = PaddingValues(0.dp),
            ) {
                itemsIndexed(pages) { index, pageRef ->
                    val request = remember(pageRef.url, authHeaders) {
                        ImageRequest.Builder(ctx)
                            .data(pageRef.url)
                            .apply { authHeaders.forEach { addHeader(it.key, it.value) } }
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = "Seite ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Transparentes Overlay: nur Single-Tap in der Mitte togglet Chrome.
            // Wir überdecken die gesamte Fläche, konsumieren aber nur Taps (keine Drags),
            // damit der LazyColumn-Scroll ungehindert funktioniert.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val inHorizontalMiddle = offset.x in (width / 3f)..(width * 2f / 3f)
                            val inVerticalMiddle = offset.y in (height / 3f)..(height * 2f / 3f)
                            if (inHorizontalMiddle && inVerticalMiddle) {
                                onToggleChrome()
                            }
                        }
                    },
            )

            // Seitenzähler unten
            if (chromeVisible) {
                Text(
                    text = "${listState.firstVisibleItemIndex + 1} / $pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

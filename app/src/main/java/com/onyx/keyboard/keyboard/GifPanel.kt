package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.onyx.keyboard.R

/**
 * Optional network GIF panel (Giphy public API). Disabled by default —
 * enabling it is a conscious user decision made in Quick settings.
 */
@Composable
fun GifPanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    val gifNet by controller.gifNet.collectAsState()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(query, gifNet) {
        if (!gifNet) {
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        failed = false
        items = runCatching { GiphyClient.search(query.ifBlank { "trending" }) }
            .getOrElse { emptyList() }
        failed = items.isEmpty()
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(286.dp)
            .padding(horizontal = 12.dp)
    ) {
        PanelHeader(controller, stringResource(R.string.panel_gif))

        if (!gifNet) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.gif_disabled),
                    color = theme.keyText.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        } else {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(stringResource(R.string.gif_hint), color = theme.keyText.copy(alpha = 0.45f))
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.07f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = theme.keyText,
                    unfocusedTextColor = theme.keyText,
                    cursorColor = theme.accent,
                    focusedIndicatorColor = theme.accent.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(color = theme.accent)
                    failed -> Text(
                        text = stringResource(R.string.gif_failed),
                        color = theme.keyText.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items, key = { it.id }) { gif ->
                            AsyncImage(
                                model = gif.previewUrl,
                                contentDescription = "GIF",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1.2f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { controller.insertGif(gif) }
                            )
                        }
                    }
                }
            }
        }
    }
}

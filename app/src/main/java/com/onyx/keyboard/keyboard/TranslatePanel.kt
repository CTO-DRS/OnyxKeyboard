package com.onyx.keyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.keyboard.R
import com.onyx.keyboard.intelligence.Phrasebook

/** Offline instant-translate panel backed by the local bilingual phrasebook. */
@Composable
fun TranslatePanel(controller: OnyxKeyboardController) {
    val theme by controller.theme.collectAsState()
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Phrasebook.search(query) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(286.dp)
            .padding(horizontal = 12.dp)
    ) {
        PanelHeader(controller, stringResource(R.string.panel_translate))

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(stringResource(R.string.translate_hint), color = theme.keyText.copy(alpha = 0.45f))
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

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(results, key = { it.first + it.second }) { (ar, en) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ar,
                        color = theme.keyText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "→",
                        color = theme.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = en,
                        color = theme.accent,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.translate_none),
                        color = theme.keyText.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.buttonSemantics

/** The notices file shown by [LicencesScreen], in the app's assets. */
private const val NOTICES_ASSET = "licenses/third_party_notices.txt"

/** A paragraph this short, with no full stop, is a heading ("Inside the app"). */
private const val HEADING_MAX_CHARS = 40

/**
 * Settings → Open-source licences: what the app is built on and the licenses'
 * full texts. Read from [NOTICES_ASSET] a paragraph at a time, so TalkBack
 * moves by paragraph and can jump by heading.
 */
@Composable
fun LicencesScreen(
    textColor: Color,
    labelColor: Color,
    headerColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paragraphs = remember {
        context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() }
            .split(Regex("\n\\s*\n"))
            // The license texts are wrapped for a terminal; the screen wraps its own.
            .map { p -> p.lines().joinToString(" ") { it.trim() }.trim() }
            .filter { it.isNotEmpty() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_licences),
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(paragraphs) { paragraph ->
                val isHeading = paragraph.length <= HEADING_MAX_CHARS && !paragraph.endsWith(".")
                if (isHeading) {
                    Text(
                        text = paragraph,
                        color = headerColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { heading() },
                    )
                } else {
                    // Selectable, so a license or a name can be copied.
                    SelectionContainer {
                        Text(text = paragraph, color = labelColor, fontSize = 15.sp)
                    }
                }
            }
        }

        // Back: as in Settings, the top bar's last column, and first for TalkBack.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .semantics { isTraversalGroup = true; traversalIndex = -1f }
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) { Spacer(modifier = Modifier.weight(1f)) }
            val backLabel = stringResource(R.string.btn_back)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            highContrast && whiteMode -> Color.Black
                            highContrast -> Color.White
                            else -> Color.White.copy(alpha = 0.25f)
                        }
                    )
                    .clickable { onBack() }
                    .buttonSemantics(backLabel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = backLabel,
                    color = when {
                        highContrast && whiteMode -> Color.White
                        highContrast -> Color.Black
                        else -> textColor
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

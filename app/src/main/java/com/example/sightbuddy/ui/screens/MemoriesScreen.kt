package com.example.sightbuddy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.core.Memory
import com.example.sightbuddy.ui.buttonSemantics
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.foundation.layout.PaddingValues
import com.example.sightbuddy.ui.theme.BrandPalette

/**
 * Saved chats: a list, like Envision's library, where each chat opens to be
 * read, and can be renamed or deleted from its ⋮ menu (or TalkBack's actions).
 */
@Composable
fun MemoriesScreen(
    memories: List<Memory>,
    darkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    featureLabel: (String) -> String,
    onRename: (Memory, String) -> Unit,
    onDelete: (Memory) -> Unit,
    onBack: () -> Unit,
) {
    var open by remember { mutableStateOf<Memory?>(null) }
    var renaming by remember { mutableStateOf<Memory?>(null) }
    var deleting by remember { mutableStateOf<Memory?>(null) }

    // Brand colours, except in high contrast, which keeps black and white.
    val brand = com.example.sightbuddy.ui.theme.brandPalette(darkTheme)
    val background = when {
        highContrast -> if (whiteMode) Color.White else Color.Black
        else -> brand.wall
    }
    val textColor = when {
        highContrast -> if (whiteMode) Color.Black else Color.White
        else -> brand.ink
    }
    val secondary = if (highContrast) textColor else brand.inkMuted
    val headingColor = if (highContrast) textColor else brand.label
    val palette = if (highContrast) null else brand
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    fun subtitle(m: Memory) = featureLabel(m.feature) + " · " + dateFormat.format(Date(m.createdAt))

    BackHandler { if (open != null) open = null else onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        val shown = open?.let { o -> memories.firstOrNull { it.id == o.id } }
        if (shown != null) {
            MemoryReader(shown, subtitle(shown), textColor, secondary, headingColor, highContrast, palette)
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(top = 96.dp)) {
                Text(
                    text = stringResource(R.string.memories_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = headingColor,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
                if (memories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memories_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(memories, key = { it.id }) { memory ->
                        // A card per chat, like a message on the wall; in high
                        // contrast an outline instead.
                        val cardShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .then(
                                    if (palette != null) {
                                        Modifier
                                            .background(palette.surface)
                                            .border(1.dp, palette.edge, cardShape)
                                    } else {
                                        Modifier.border(2.dp, textColor, cardShape)
                                    }
                                ),
                        ) {
                            MemoryRow(
                                memory = memory,
                                subtitle = subtitle(memory),
                                textColor = textColor,
                                secondary = secondary,
                                onOpen = { open = memory },
                                onRename = { renaming = memory },
                                onDelete = { deleting = memory },
                            )
                        }
                    }
                }
            }
        }

        // Back: the same box and place as in Settings (the top bar's last column).
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // First in TalkBack's order, as it is on screen: drawn after the
                // list, it came last, so focus left on it (where the button that
                // opened this screen was) made a swipe right find nothing and a
                // swipe left walk the list backwards.
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
                            else -> brand.surface
                        }
                    )
                    .then(if (palette != null) Modifier.border(1.dp, palette.edge, RoundedCornerShape(4.dp)) else Modifier)
                    .clickable { if (open != null) open = null else onBack() }
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

    renaming?.let { memory ->
        var name by remember(memory.id) { mutableStateOf(memory.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.memories_rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onRename(memory, name.trim())
                        renaming = null
                    },
                ) { Text(stringResource(R.string.memories_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.memories_cancel)) }
            },
        )
    }

    deleting?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.memories_delete_confirm, memory.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(memory)
                    if (open?.id == memory.id) open = null
                    deleting = null
                }) { Text(stringResource(R.string.memories_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.memories_cancel)) }
            },
        )
    }
}

@Composable
private fun MemoryRow(
    memory: Memory,
    subtitle: String,
    textColor: Color,
    secondary: Color,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.memories_rename)
    val deleteLabel = stringResource(R.string.memories_delete)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            // TalkBack users reach Rename and Delete from the row's actions menu.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(renameLabel) { onRename(); true },
                    CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                )
            }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(memory.name, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondary)
        }
        Box {
            val optionsLabel = stringResource(R.string.memories_options_cd, memory.name)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable { menuOpen = true }
                    .buttonSemantics(optionsLabel),
                contentAlignment = Alignment.Center,
            ) {
                Text("⋮", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(renameLabel) }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text(deleteLabel) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

/**
 * A saved chat, to read: the same bubbles as the live chat, from the top. Each
 * message is its own selectable block with a heading, as in the chat.
 */
@Composable
private fun MemoryReader(
    memory: Memory,
    subtitle: String,
    textColor: Color,
    secondary: Color,
    headingColor: Color,
    highContrast: Boolean,
    palette: BrandPalette?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 96.dp),
    ) {
        Text(
            text = memory.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = headingColor,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondary)
        Spacer(modifier = Modifier.height(16.dp))
        val entries = remember(memory.id) {
            memory.entries.map { e ->
                TranscriptEntry(
                    TranscriptEntry.Kind.entries.firstOrNull { it.name == e.kind } ?: TranscriptEntry.Kind.SYSTEM,
                    e.text,
                )
            }
        }
        ChatMessages(
            entries = entries,
            pendingQuestion = null,
            working = false,
            emptyText = null,
            textColor = textColor,
            accent = palette?.selected ?: textColor,
            highContrast = highContrast,
            scroll = rememberScrollState(),
            palette = palette,
            followLatest = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
    }
}

package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sightbuddy.R
import com.example.sightbuddy.features.vision.CocoFinnish
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.features.vision.COCO_OBJECTS

@Composable
fun CocoObjectsSettingsScreen(
    settingsManager: SettingsManager,
    highContrast: Boolean,
    whiteMode: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val hiddenObjects by settingsManager.hiddenCocoObjects.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val textColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        defaultTextColor
    }
    val labelColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White.copy(alpha = 0.85f)
    } else {
        defaultTextColor
    }

    val searchCd = stringResource(R.string.objects_search_cd)
    val backCd = stringResource(R.string.objects_back_cd)

    val filteredObjects = remember(searchQuery) {
        if (searchQuery.isBlank()) COCO_OBJECTS
        else COCO_OBJECTS.filter {
            it.contains(searchQuery.trim(), ignoreCase = true) ||
                CocoFinnish.displayName(it).contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_object_list),
                style = MaterialTheme.typography.displaySmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.objects_list_body),
                color = labelColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.objects_search_hint),
                        color = labelColor.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = labelColor,
                    unfocusedTextColor = labelColor,
                    cursorColor = labelColor,
                    focusedBorderColor = if (highContrast) labelColor else Color(0xFF3DBAD0),
                    unfocusedBorderColor = labelColor.copy(alpha = 0.4f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = searchCd },
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredObjects, key = { it }) { obj ->
                    val visible = obj !in hiddenObjects
                    CocoObjectVisibilityRow(
                        label = CocoFinnish.displayName(obj).replaceFirstChar { it.uppercase() },
                        visible = visible,
                        onVisibleChange = { settingsManager.setCocoObjectVisible(obj, it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(64.dp)
                .background(
                    when {
                        highContrast && whiteMode -> Color.Black
                        highContrast -> Color.White
                        else -> Color.White.copy(alpha = 0.15f)
                    },
                    shape = CircleShape,
                )
                .clickable { onBack() }
                .semantics { contentDescription = backCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.btn_back),
                color = when {
                    highContrast && whiteMode -> Color.White
                    highContrast -> Color.Black
                    else -> textColor
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CocoObjectVisibilityRow(
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    labelColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean,
) {
    val shownCd = stringResource(R.string.objects_shown_in_picker)
    val hiddenCd = stringResource(R.string.objects_hidden_from_picker)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = "$label. ${if (visible) shownCd else hiddenCd}."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = visible,
            onCheckedChange = onVisibleChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (highContrast && whiteMode) Color.White else Color.Black,
                checkedTrackColor = if (highContrast) {
                    if (whiteMode) Color.Black else Color.White
                } else Color(0xFF3DBAD0),
                uncheckedThumbColor = if (highContrast && whiteMode) {
                    Color(0xFF757575)
                } else {
                    Color.Gray
                },
                uncheckedTrackColor = if (highContrast && whiteMode) {
                    Color(0xFFD6D6D6)
                } else {
                    Color.DarkGray
                },
            ),
        )
    }
}

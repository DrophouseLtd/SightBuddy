package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
@Composable
fun ObjectPickerDialog(
    visibleObjects: List<String>,
    highContrast: Boolean,
    onObjectSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val titleCd = stringResource(R.string.picker_title_cd)
    val searchCd = stringResource(R.string.picker_search_cd)
    val cancelCd = stringResource(R.string.picker_cancel_cd)

    // Filter on what the user actually sees: the Finnish name in Finnish,
    // the English label otherwise.
    val filteredObjects = remember(searchQuery, visibleObjects) {
        if (searchQuery.isBlank()) visibleObjects
        else visibleObjects.filter {
            val shown = CocoFinnish.displayName(it)
            it.contains(searchQuery.trim(), ignoreCase = true) ||
                shown.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = titleCd }
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.picker_filter_hint), color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = if (highContrast) Color.White else Color(0xFF3DBAD0),
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = searchCd }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredObjects) { obj ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                            .clickable { onObjectSelected(obj) }
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                            .semantics { contentDescription = CocoFinnish.displayName(obj) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = CocoFinnish.displayName(obj).replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highContrast) Color.White else Color(0xFF3DBAD0),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onDismiss() }
                    .padding(vertical = 18.dp)
                    .semantics { contentDescription = cancelCd },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_cancel),
                    color = if (highContrast) Color.Black else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

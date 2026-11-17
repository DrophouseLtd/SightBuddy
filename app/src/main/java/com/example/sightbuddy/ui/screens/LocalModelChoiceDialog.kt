package com.example.sightbuddy.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.sightbuddy.R
import com.example.sightbuddy.core.llm.LocalModelCatalog
import com.example.sightbuddy.core.llm.LocalModelSpec

/** Explicit choice only: downloading still requires the existing confirmation. */
@Composable
fun LocalModelChoiceDialog(
    selected: LocalModelSpec,
    installed: (LocalModelSpec) -> Boolean,
    ready: Boolean,
    loading: Boolean,
    busy: Boolean,
    message: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_model_choice_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Text(stringResource(R.string.local_model_experimental_note))
                LocalModelCatalog.all.forEach { spec ->
                    val name = stringResource(if (spec.experimental) R.string.local_model_e4b else R.string.local_model_e2b)
                    val state = if (installed(spec)) stringResource(R.string.local_model_downloaded) else stringResource(R.string.local_model_not_downloaded)
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = spec == selected, enabled = !busy,
                            role = Role.RadioButton, onClick = { onSelect(spec.id) },
                        ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = spec == selected, onClick = null, enabled = !busy)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(name)
                            Text(state + ". " + Formatter.formatShortFileSize(context, spec.sizeBytes))
                            if (spec == selected) Text(stringResource(when {
                                ready -> R.string.local_model_ready
                                loading -> R.string.local_model_loading
                                else -> R.string.local_model_not_loaded
                            }))
                        }
                    }
                }
                if (busy) Text(stringResource(R.string.local_model_busy))
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.local_model_close)) } },
    )
}

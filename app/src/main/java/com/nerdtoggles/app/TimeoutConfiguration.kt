package com.nerdtoggles.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun TimeoutConfiguration(state: FeatureState?, close: () -> Unit) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf(context.timeoutCycleText()) }
    val durations = parseTimeoutCycle(input)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = close) { Text("Back to controls") }
            Text("Screen Timeout", style = MaterialTheme.typography.headlineSmall)
            Text("Current setting: ${state?.reading?.value ?: "Unknown"}")
            Text("Enter durations in seconds, separated by commas, in the order you want them to cycle.")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cycle durations in seconds") },
                isError = durations == null,
                supportingText = {
                    Text(durations?.joinToString(" → ", transform = ::formatTimeout)
                        ?: "Enter distinct whole seconds from 5 to 1,800.")
                },
            )
            Text("If the current timeout is outside the cycle, the next tap sets the first duration.")
            if (state?.access != Access.Granted) Text("Allow Modify system settings in Setup to use the tile.")
            Button(
                enabled = durations != null,
                onClick = {
                    if (durations != null) {
                        context.saveTimeoutCycle(durations)
                        close()
                    }
                },
            ) { Text("Save cycle") }
        }
    }
}

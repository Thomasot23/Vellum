// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage

@Composable
fun EnrollmentScreen(
    snapshot: Snapshot,
    error: String?,
    onConfigure: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("") }
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Vellum Panel", style = MaterialTheme.typography.headlineLarge)
            if (snapshot.stage == Stage.SETUP) {
                Text("Enter the HTTPS address of your Vellum server.")
                OutlinedTextField(
                    server,
                    { server = it },
                    label = { Text("Server address") },
                    singleLine = true,
                )
                Button(
                    onClick = { onConfigure(server) },
                    enabled = server.isNotBlank(),
                ) { Text("Connect") }
            } else {
                Text(
                    when (snapshot.stage) {
                        Stage.PENDING -> "Waiting for approval in Vellum."
                        Stage.UNASSIGNED -> "Connected. Assign room content to this panel in Vellum."
                        Stage.UNSUPPORTED -> "The assigned content is not a room. Choose room content in Vellum."
                        else -> "The server is unavailable. Retrying automatically."
                    },
                )
                snapshot.mac?.let { Text("Device ID: $it") }
                Button(onClick = onRetry) { Text("Retry") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

package com.realretrolabz.fs42remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.realretrolabz.fs42remote.network.Fs42ApiClient
import com.realretrolabz.fs42remote.ui.theme.FS42RemoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FS42RemoteTheme {
                Fs42RemoteApp()
            }
        }
    }
}

@Composable
fun Fs42RemoteApp(modifier: Modifier = Modifier) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("10.0.0.99") }
    var port by rememberSaveable { mutableStateOf("4242") }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }

    RemoteCanvasPlaceholder(
        modifier = modifier,
        onOpenSettings = { settingsOpen = true },
    )

    if (settingsOpen) {
        ServerSettingsDialog(
            host = host,
            port = port,
            testResult = testResult,
            onHostChange = { host = it },
            onPortChange = { port = it },
            onTestResultChange = { testResult = it },
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
fun RemoteCanvasPlaceholder(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Text(
                text = "FS42 Remote",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart),
            )

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp),
            ) {
                Text(
                    text = "⚙",
                    color = Color.White,
                    fontSize = 32.sp,
                )
            }
        }
    }
}

@Composable
fun ServerSettingsDialog(
    host: String,
    port: String,
    testResult: String?,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTestResultChange: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val client = remember { Fs42ApiClient() }
    val scope = rememberCoroutineScope()
    var testing by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("Host / IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val parsedPort = port.toIntOrNull()
                        if (host.isBlank() || parsedPort == null) {
                            onTestResultChange("Enter a host and numeric port.")
                            return@Button
                        }

                        testing = true
                        onTestResultChange("Testing http://$host:$parsedPort/player/status")
                        scope.launch {
                            val result = client.getPlayerStatus(host.trim(), parsedPort)
                            onTestResultChange(
                                result.fold(
                                    onSuccess = { "Connected:\n${it.take(600)}" },
                                    onFailure = { "Connection failed: ${it.message ?: it::class.simpleName}" },
                                ),
                            )
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (testing) "Testing..." else "Test Connection")
                }
                testResult?.let {
                    Text(it)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun RemoteCanvasPlaceholderPreview() {
    FS42RemoteTheme {
        Fs42RemoteApp()
    }
}

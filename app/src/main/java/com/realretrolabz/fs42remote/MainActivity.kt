package com.realretrolabz.fs42remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realretrolabz.fs42remote.network.Fs42ApiClient
import com.realretrolabz.fs42remote.ui.theme.FS42RemoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()
        setContent {
            FS42RemoteTheme {
                Fs42RemoteApp()
            }
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun Fs42RemoteApp(modifier: Modifier = Modifier) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("10.0.0.99") }
    var port by rememberSaveable { mutableStateOf("4242") }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var displayText by rememberSaveable { mutableStateOf("READY") }

    DefaultRemoteScreen(
        modifier = modifier,
        onOpenSettings = { settingsOpen = true },
        displayText = displayText,
        onZoneTapped = { displayText = it },
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
fun DefaultRemoteScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    displayText: String = "READY",
    onZoneTapped: (String) -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val assetAspectRatio = 1536f / 2303f
            val containerAspectRatio = maxWidth.value / maxHeight.value
            val imageWidth = if (containerAspectRatio < assetAspectRatio) {
                maxHeight * assetAspectRatio
            } else {
                maxWidth
            }
            val imageHeight = if (containerAspectRatio < assetAspectRatio) {
                maxHeight
            } else {
                maxWidth / assetAspectRatio
            }
            val imageLeft = (maxWidth - imageWidth) / 2
            val imageTop = (maxHeight - imageHeight) / 2

            Image(
                painter = painterResource(id = R.drawable.default_remote_asset),
                contentDescription = "FS42 remote skin",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            DefaultDisplayZone(
                rect = DefaultRemoteDisplayRect,
                imageLeft = imageLeft,
                imageTop = imageTop,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                text = displayText,
            )

            DefaultRemoteZones.forEach { zone ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = imageLeft + imageWidth * zone.x,
                            y = imageTop + imageHeight * zone.y,
                        )
                        .size(
                            width = imageWidth * zone.w,
                            height = imageHeight * zone.h,
                        )
                        .clickable { onZoneTapped(zone.label) },
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .systemBarsPadding()
                    .padding(16.dp)
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
fun DefaultDisplayZone(
    rect: DefaultRemoteRect,
    imageLeft: androidx.compose.ui.unit.Dp,
    imageTop: androidx.compose.ui.unit.Dp,
    imageWidth: androidx.compose.ui.unit.Dp,
    imageHeight: androidx.compose.ui.unit.Dp,
    text: String,
) {
    Box(
        modifier = Modifier
            .offset(
                x = imageLeft + imageWidth * rect.x,
                y = imageTop + imageHeight * rect.y,
            )
            .size(
                width = imageWidth * rect.w,
                height = imageHeight * rect.h,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFF66FF88),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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

data class DefaultRemoteRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

data class DefaultRemoteZone(
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

val DefaultRemoteDisplayRect = DefaultRemoteRect(
    x = 0.29f,
    y = 0.85f,
    w = 0.43f,
    h = 0.105f,
)

val DefaultRemoteZones = listOf(
    DefaultRemoteZone("POWER", 0.295f, 0.078f, 0.122f, 0.058f),
    DefaultRemoteZone("GUIDE", 0.43f, 0.144f, 0.145f, 0.06f),
    DefaultRemoteZone("CH+", 0.296f, 0.21f, 0.115f, 0.08f),
    DefaultRemoteZone("CH-", 0.296f, 0.29f, 0.115f, 0.075f),
    DefaultRemoteZone("VOL+", 0.586f, 0.21f, 0.115f, 0.08f),
    DefaultRemoteZone("VOL-", 0.586f, 0.29f, 0.115f, 0.075f),
    DefaultRemoteZone("1", 0.291f, 0.428f, 0.12f, 0.057f),
    DefaultRemoteZone("2", 0.434f, 0.428f, 0.12f, 0.057f),
    DefaultRemoteZone("3", 0.578f, 0.428f, 0.12f, 0.057f),
    DefaultRemoteZone("4", 0.291f, 0.499f, 0.12f, 0.057f),
    DefaultRemoteZone("5", 0.434f, 0.499f, 0.12f, 0.057f),
    DefaultRemoteZone("6", 0.578f, 0.499f, 0.12f, 0.057f),
    DefaultRemoteZone("7", 0.291f, 0.571f, 0.12f, 0.057f),
    DefaultRemoteZone("8", 0.434f, 0.571f, 0.12f, 0.057f),
    DefaultRemoteZone("9", 0.578f, 0.571f, 0.12f, 0.057f),
    DefaultRemoteZone("MUTE", 0.291f, 0.642f, 0.12f, 0.058f),
    DefaultRemoteZone("0", 0.434f, 0.642f, 0.12f, 0.058f),
    DefaultRemoteZone("LAST", 0.578f, 0.642f, 0.12f, 0.058f),
    DefaultRemoteZone("PPV PAGE LEFT", 0.291f, 0.754f, 0.12f, 0.06f),
    DefaultRemoteZone("PPV SELECT", 0.431f, 0.759f, 0.13f, 0.055f),
    DefaultRemoteZone("PPV PAGE RIGHT", 0.586f, 0.754f, 0.12f, 0.06f),
)

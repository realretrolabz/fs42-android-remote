package com.realretrolabz.fs42remote

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.realretrolabz.fs42remote.network.Fs42ApiClient
import com.realretrolabz.fs42remote.ui.theme.FS42RemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    val context = LocalContext.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("10.0.0.99") }
    var port by rememberSaveable { mutableStateOf("4242") }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var displayText by rememberSaveable { mutableStateOf("READY") }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var clickEffectEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedZoneLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var displayBoxSelected by rememberSaveable { mutableStateOf(false) }
    var elementPickerOpen by rememberSaveable { mutableStateOf(false) }
    var imageDialogOpen by rememberSaveable { mutableStateOf(false) }
    var installedRemotesOpen by rememberSaveable { mutableStateOf(false) }
    var skinName by rememberSaveable { mutableStateOf("") }
    var currentSkinId by rememberSaveable { mutableStateOf(DefaultSkinId) }
    var contentMode by rememberSaveable { mutableStateOf(SkinContentMode.Fill) }
    var backgroundFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundAspectRatio by rememberSaveable { mutableStateOf(DefaultRemoteAssetAspectRatio) }
    var zones by remember { mutableStateOf(DefaultRemoteZones) }
    var displayRect by remember { mutableStateOf(DefaultRemoteDisplayRect) }
    var displayBoxVisible by remember { mutableStateOf(true) }
    var installedRemotes by remember { mutableStateOf(listInstalledRemotes(context)) }
    var serverNickname by rememberSaveable { mutableStateOf("FS42") }
    var currentServerProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedServerProfiles by remember { mutableStateOf(listServerProfiles(context)) }
    var channelBuffer by rememberSaveable { mutableStateOf("") }
    var currentChannel by rememberSaveable { mutableStateOf<Int?>(null) }
    var previousChannel by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingTuneJob by remember { mutableStateOf<Job?>(null) }
    val client = remember { Fs42ApiClient() }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            displayText = "IMAGE"
            copyImageFromUri(context, uri).fold(
                onSuccess = { imageFile ->
                    backgroundFilePath = imageFile.absolutePath
                    backgroundAspectRatio = imageFile.imageAspectRatio() ?: backgroundAspectRatio
                    displayText = "IMAGE SET"
                    testResult = "Selected image:\n${imageFile.name}"
                },
                onFailure = { error ->
                    displayText = "IMG ERR"
                    testResult = "Image failed: ${error.message ?: error::class.simpleName}"
                },
            )
        }
    }
    val skinImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            displayText = "IMPORT"
            importSkinPackage(context, uri).fold(
                onSuccess = { imported ->
                    installedRemotes = listInstalledRemotes(context)
                    loadInstalledSkin(context, imported.id).fold(
                        onSuccess = { loaded ->
                            skinName = loaded.name
                            currentSkinId = imported.id
                            contentMode = loaded.contentMode
                            backgroundFilePath = loaded.backgroundFile.absolutePath
                            backgroundAspectRatio = loaded.aspectRatio
                            zones = loaded.zones
                            displayRect = loaded.displayRect
                            displayBoxVisible = loaded.displayBoxVisible
                            selectedZoneLabel = null
                            displayBoxSelected = false
                            displayText = "LOADED"
                            testResult = "Imported skin:\n${loaded.name}"
                        },
                        onFailure = { error ->
                            displayText = "LOAD ERR"
                            testResult = "Import loaded poorly: ${error.message ?: error::class.simpleName}"
                        },
                    )
                },
                onFailure = { error ->
                    displayText = "IMP ERR"
                    testResult = "Import failed: ${error.message ?: error::class.simpleName}"
                },
            )
        }
    }

    fun parsedPort(): Int? = port.toIntOrNull()

    fun showResult(prefix: String, result: Result<String>) {
        displayText = result.fold(
            onSuccess = { body ->
                val channel = body.extractChannelNumber()
                if (channel != null) {
                    currentChannel = channel
                }
                prefix
            },
            onFailure = { error -> "ERR ${error.message ?: error::class.simpleName}" },
        )
    }

    fun executeCommand(command: RemoteCommand) {
        val trimmedHost = host.trim()
        val parsedPort = parsedPort()
        if (trimmedHost.isBlank() || parsedPort == null) {
            displayText = "SET SERVER"
            settingsOpen = true
            return
        }

        when (command) {
            RemoteCommand.DIGIT_0,
            RemoteCommand.DIGIT_1,
            RemoteCommand.DIGIT_2,
            RemoteCommand.DIGIT_3,
            RemoteCommand.DIGIT_4,
            RemoteCommand.DIGIT_5,
            RemoteCommand.DIGIT_6,
            RemoteCommand.DIGIT_7,
            RemoteCommand.DIGIT_8,
            RemoteCommand.DIGIT_9 -> {
                channelBuffer = (channelBuffer + command.digit).take(4)
                displayText = "CH $channelBuffer"
                pendingTuneJob?.cancel()
                val bufferedChannel = channelBuffer
                pendingTuneJob = scope.launch {
                    delay(1_200)
                    val channel = bufferedChannel.toIntOrNull() ?: return@launch
                    val oldChannel = currentChannel
                    showResult("CH $channel", client.get(trimmedHost, parsedPort, "/player/channels/$channel"))
                    previousChannel = oldChannel
                    currentChannel = channel
                    channelBuffer = ""
                }
            }

            else -> scope.launch {
                when (command) {
                    RemoteCommand.POWER_STOP -> {
                        displayText = "POWER"
                        showResult("STOPPED", client.get(trimmedHost, parsedPort, "/player/commands/stop"))
                    }

                    RemoteCommand.GUIDE -> {
                        displayText = "GUIDE"
                        showResult("GUIDE", client.post(trimmedHost, parsedPort, "/player/channels/guide"))
                    }

                    RemoteCommand.CHANNEL_UP -> {
                        displayText = "CH+"
                        val oldChannel = currentChannel
                        showResult("CH+", client.get(trimmedHost, parsedPort, "/player/channels/up"))
                        client.getPlayerStatus(trimmedHost, parsedPort).getOrNull()?.extractChannelNumber()?.let {
                            previousChannel = oldChannel
                            currentChannel = it
                        }
                    }

                    RemoteCommand.CHANNEL_DOWN -> {
                        displayText = "CH-"
                        val oldChannel = currentChannel
                        showResult("CH-", client.get(trimmedHost, parsedPort, "/player/channels/down"))
                        client.getPlayerStatus(trimmedHost, parsedPort).getOrNull()?.extractChannelNumber()?.let {
                            previousChannel = oldChannel
                            currentChannel = it
                        }
                    }

                    RemoteCommand.VOLUME_UP -> {
                        displayText = "VOL+"
                        showResult("VOL+", client.get(trimmedHost, parsedPort, "/player/volume/up"))
                    }

                    RemoteCommand.VOLUME_DOWN -> {
                        displayText = "VOL-"
                        showResult("VOL-", client.get(trimmedHost, parsedPort, "/player/volume/down"))
                    }

                    RemoteCommand.MUTE -> {
                        displayText = "MUTE"
                        showResult("MUTE", client.get(trimmedHost, parsedPort, "/player/volume/mute"))
                    }

                    RemoteCommand.LAST_CHANNEL -> {
                        val last = previousChannel
                        if (last == null) {
                            displayText = "NO LAST"
                        } else {
                            displayText = "LAST $last"
                            showResult("CH $last", client.get(trimmedHost, parsedPort, "/player/channels/$last"))
                            previousChannel = currentChannel
                            currentChannel = last
                        }
                    }

                    RemoteCommand.PPV_MENU -> {
                        val channel = currentChannel ?: client.getPlayerStatus(trimmedHost, parsedPort)
                            .getOrNull()
                            ?.extractChannelNumber()
                        if (channel == null) {
                            displayText = "NO CH"
                        } else {
                            currentChannel = channel
                            displayText = "PPV $channel"
                            showResult("PPV", client.get(trimmedHost, parsedPort, "/ppv/$channel"))
                        }
                    }

                    RemoteCommand.PPV_PAGE_PREV,
                    RemoteCommand.PPV_PAGE_NEXT,
                    RemoteCommand.PPV_SELECT -> {
                        val channel = currentChannel ?: client.getPlayerStatus(trimmedHost, parsedPort)
                            .getOrNull()
                            ?.extractChannelNumber()
                        if (channel == null) {
                            displayText = "NO CH"
                        } else {
                            currentChannel = channel
                            val key = when (command) {
                                RemoteCommand.PPV_PAGE_PREV -> "PageDown"
                                RemoteCommand.PPV_PAGE_NEXT -> "PageUp"
                                else -> "Enter"
                            }
                            displayText = "PPV $key"
                            showResult("PPV", client.post(trimmedHost, parsedPort, "/ppv/$channel/key/$key"))
                        }
                    }

                    RemoteCommand.CUSTOM_HTTP,
                    RemoteCommand.DIGIT_0,
                    RemoteCommand.DIGIT_1,
                    RemoteCommand.DIGIT_2,
                    RemoteCommand.DIGIT_3,
                    RemoteCommand.DIGIT_4,
                    RemoteCommand.DIGIT_5,
                    RemoteCommand.DIGIT_6,
                    RemoteCommand.DIGIT_7,
                    RemoteCommand.DIGIT_8,
                    RemoteCommand.DIGIT_9 -> Unit
                }
            }
        }
    }

    DefaultRemoteScreen(
        modifier = modifier,
        onOpenSettings = { settingsOpen = true },
        displayText = displayText,
        editMode = editMode,
        clickEffectEnabled = clickEffectEnabled,
        backgroundFilePath = backgroundFilePath,
        backgroundAspectRatio = backgroundAspectRatio,
        contentMode = contentMode,
        zones = zones,
        displayRect = displayRect,
        displayBoxVisible = displayBoxVisible,
        selectedZoneLabel = selectedZoneLabel,
        displayBoxSelected = displayBoxSelected,
        onZoneSelected = { zone ->
            selectedZoneLabel = zone.label
            displayBoxSelected = false
            displayText = "EDIT ${zone.label}"
        },
        onZoneMoved = { changedZone, deltaX, deltaY ->
            zones = zones.map { zone ->
                if (zone.label == changedZone.label) zone.moveBy(deltaX, deltaY) else zone
            }
        },
        onZoneResized = { changedZone, deltaW, deltaH ->
            zones = zones.map { zone ->
                if (zone.label == changedZone.label) zone.resizeBy(deltaW, deltaH) else zone
            }
        },
        onDisplaySelected = {
            selectedZoneLabel = null
            displayBoxSelected = true
            displayText = "EDIT DISPLAY"
        },
        onDisplayMoved = { deltaX, deltaY ->
            displayRect = displayRect.moveBy(deltaX, deltaY)
        },
        onDisplayResized = { deltaW, deltaH ->
            displayRect = displayRect.resizeBy(deltaW, deltaH)
        },
        onZoneTapped = ::executeCommand,
    )

    if (settingsOpen) {
        OptionsScreen(
            host = host,
            port = port,
            serverNickname = serverNickname,
            testResult = testResult,
            editMode = editMode,
            clickEffectEnabled = clickEffectEnabled,
            contentMode = contentMode,
            skinName = skinName,
            savedServerProfiles = savedServerProfiles,
            onHostChange = { host = it },
            onPortChange = { port = it },
            onServerNicknameChange = { serverNickname = it },
            onTestResultChange = { testResult = it },
            onSkinNameChange = {
                skinName = it
            },
            onEditModeChange = {
                editMode = it
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = if (it) "EDIT MODE" else "READY"
            },
            onClickEffectChange = { clickEffectEnabled = it },
            onContentModeChange = { contentMode = it },
            onSaveServerProfile = {
                val parsedPort = port.toIntOrNull()
                if (host.isBlank() || parsedPort == null) {
                    testResult = "Enter a host and numeric port before saving."
                } else {
                    val cleanName = serverNickname.trim().ifBlank { host.trim() }
                    val profile = ServerProfile(
                        id = currentServerProfileId ?: newServerProfileId(cleanName),
                        nickname = cleanName,
                        host = host.trim(),
                        port = parsedPort.toString(),
                    )
                    saveServerProfile(context, profile).fold(
                        onSuccess = {
                            currentServerProfileId = profile.id
                            serverNickname = profile.nickname
                            host = profile.host
                            port = profile.port
                            savedServerProfiles = listServerProfiles(context)
                            testResult = "Saved server profile:\n${profile.nickname}"
                        },
                        onFailure = { error ->
                            testResult = "Server save failed: ${error.message ?: error::class.simpleName}"
                        },
                    )
                }
            },
            onLoadServerProfile = { profile ->
                currentServerProfileId = profile.id
                serverNickname = profile.nickname
                host = profile.host
                port = profile.port
                testResult = "Loaded server profile:\n${profile.nickname}"
            },
            onDeleteServerProfile = { profile ->
                deleteServerProfile(context, profile.id).fold(
                    onSuccess = {
                        savedServerProfiles = listServerProfiles(context)
                        if (currentServerProfileId == profile.id) {
                            currentServerProfileId = null
                        }
                        testResult = "Deleted server profile:\n${profile.nickname}"
                    },
                    onFailure = { error ->
                        testResult = "Server delete failed: ${error.message ?: error::class.simpleName}"
                    },
                )
            },
            onAddElement = { elementPickerOpen = true },
            onChooseImage = { imageDialogOpen = true },
            onImportSkin = { skinImporter.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onOpenInstalledRemotes = {
                installedRemotes = listInstalledRemotes(context)
                installedRemotesOpen = true
            },
            onSaveSkin = {
                scope.launch {
                    displayText = "SAVING"
                    val result = saveCurrentSkinPackage(
                        context = context,
                        skinId = currentSkinId,
                        skinName = skinName,
                        backgroundFilePath = backgroundFilePath,
                        aspectRatio = backgroundAspectRatio,
                        contentMode = contentMode,
                        zones = zones,
                        displayRect = displayRect,
                        displayBoxVisible = displayBoxVisible,
                    )
                    result.fold(
                        onSuccess = { saveResult ->
                            currentSkinId = saveResult.skinId
                            installedRemotes = listInstalledRemotes(context)
                            displayText = "SAVED"
                            testResult = "Saved installed skin and exported:\n${saveResult.exportDescription}"
                        },
                        onFailure = { error ->
                            displayText = "SAVE ERR"
                            testResult = "Save failed: ${error.message ?: error::class.simpleName}"
                        },
                    )
                }
            },
            onClearLayout = {
                zones = emptyList()
                displayBoxVisible = false
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = "CLEARED"
            },
            onResetDefault = {
                skinName = ""
                currentSkinId = DefaultSkinId
                contentMode = SkinContentMode.Fill
                backgroundFilePath = null
                backgroundAspectRatio = DefaultRemoteAssetAspectRatio
                zones = DefaultRemoteZones
                displayRect = DefaultRemoteDisplayRect
                displayBoxVisible = true
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = "DEFAULT"
            },
            onDismiss = { settingsOpen = false },
        )
    }

    if (elementPickerOpen) {
        ElementPickerDialog(
            onElementSelected = { choice ->
                when (choice) {
                    RemoteElementChoice.DisplayText -> {
                        displayBoxVisible = true
                        selectedZoneLabel = null
                        displayBoxSelected = true
                        displayText = "EDIT DISPLAY"
                    }

                    is RemoteElementChoice.Button -> {
                        val label = uniqueZoneLabel(choice.label, zones)
                        val newZone = DefaultRemoteZone(
                            label = label,
                            command = choice.command,
                            x = 0.39f,
                            y = 0.36f,
                            w = 0.22f,
                            h = 0.07f,
                        )
                        zones = zones + newZone
                        selectedZoneLabel = label
                        displayBoxSelected = false
                        displayText = "EDIT $label"
                    }
                }
                elementPickerOpen = false
                settingsOpen = false
                editMode = true
            },
            onDismiss = { elementPickerOpen = false },
        )
    }

    if (imageDialogOpen) {
        ImageInputDialog(
            onChooseImage = {
                imageDialogOpen = false
                imagePicker.launch(arrayOf("image/*"))
            },
            onUseDefault = {
                skinName = ""
                currentSkinId = DefaultSkinId
                contentMode = SkinContentMode.Fill
                backgroundFilePath = null
                backgroundAspectRatio = DefaultRemoteAssetAspectRatio
                zones = DefaultRemoteZones
                displayRect = DefaultRemoteDisplayRect
                displayBoxVisible = true
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = "DEFAULT"
                imageDialogOpen = false
            },
            onDismiss = { imageDialogOpen = false },
        )
    }

    if (installedRemotesOpen) {
        InstalledRemotesDialog(
            remotes = installedRemotes,
            onLoadDefault = {
                skinName = ""
                currentSkinId = DefaultSkinId
                contentMode = SkinContentMode.Fill
                backgroundFilePath = null
                backgroundAspectRatio = DefaultRemoteAssetAspectRatio
                zones = DefaultRemoteZones
                displayRect = DefaultRemoteDisplayRect
                displayBoxVisible = true
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = "DEFAULT"
                installedRemotesOpen = false
            },
            onLoadRemote = { remote ->
                scope.launch {
                    loadInstalledSkin(context, remote.id).fold(
                        onSuccess = { loaded ->
                            skinName = loaded.name
                            currentSkinId = remote.id
                            contentMode = loaded.contentMode
                            backgroundFilePath = loaded.backgroundFile.absolutePath
                            backgroundAspectRatio = loaded.aspectRatio
                            zones = loaded.zones
                            displayRect = loaded.displayRect
                            displayBoxVisible = loaded.displayBoxVisible
                            selectedZoneLabel = null
                            displayBoxSelected = false
                            displayText = "LOADED"
                            testResult = "Loaded skin:\n${loaded.name}"
                            installedRemotesOpen = false
                        },
                        onFailure = { error ->
                            displayText = "LOAD ERR"
                            testResult = "Load failed: ${error.message ?: error::class.simpleName}"
                        },
                    )
                }
            },
            onDeleteRemote = { remote ->
                scope.launch {
                    deleteInstalledSkin(context, remote.id).fold(
                        onSuccess = {
                            installedRemotes = listInstalledRemotes(context)
                            testResult = "Deleted skin:\n${remote.name}"
                            if (currentSkinId == remote.id) {
                                skinName = ""
                                currentSkinId = DefaultSkinId
                                contentMode = SkinContentMode.Fill
                                backgroundFilePath = null
                                backgroundAspectRatio = DefaultRemoteAssetAspectRatio
                                zones = DefaultRemoteZones
                                displayRect = DefaultRemoteDisplayRect
                                displayBoxVisible = true
                                selectedZoneLabel = null
                                displayBoxSelected = false
                                displayText = "DEFAULT"
                            } else {
                                displayText = "DELETED"
                            }
                        },
                        onFailure = { error ->
                            displayText = "DEL ERR"
                            testResult = "Delete failed: ${error.message ?: error::class.simpleName}"
                        },
                    )
                }
            },
            onDismiss = { installedRemotesOpen = false },
        )
    }
}

@Composable
fun DefaultRemoteScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    displayText: String = "READY",
    editMode: Boolean = false,
    clickEffectEnabled: Boolean = true,
    backgroundFilePath: String? = null,
    backgroundAspectRatio: Float = DefaultRemoteAssetAspectRatio,
    contentMode: SkinContentMode = SkinContentMode.Fill,
    zones: List<DefaultRemoteZone> = DefaultRemoteZones,
    displayRect: DefaultRemoteRect = DefaultRemoteDisplayRect,
    displayBoxVisible: Boolean = true,
    selectedZoneLabel: String? = null,
    displayBoxSelected: Boolean = false,
    onZoneSelected: (DefaultRemoteZone) -> Unit = {},
    onZoneMoved: (DefaultRemoteZone, Float, Float) -> Unit = { _, _, _ -> },
    onZoneResized: (DefaultRemoteZone, Float, Float) -> Unit = { _, _, _ -> },
    onDisplaySelected: () -> Unit = {},
    onDisplayMoved: (Float, Float) -> Unit = { _, _ -> },
    onDisplayResized: (Float, Float) -> Unit = { _, _ -> },
    onZoneTapped: (RemoteCommand) -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black,
        contentColor = Color.White,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val fileBitmap = remember(backgroundFilePath) {
                backgroundFilePath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
            }
            val assetAspectRatio = backgroundAspectRatio
            val containerAspectRatio = maxWidth.value / maxHeight.value
            val shouldScaleByHeight = when (contentMode) {
                SkinContentMode.Fill -> containerAspectRatio < assetAspectRatio
                SkinContentMode.Fit -> containerAspectRatio > assetAspectRatio
            }
            val imageWidth = if (shouldScaleByHeight) maxHeight * assetAspectRatio else maxWidth
            val imageHeight = if (shouldScaleByHeight) maxHeight else maxWidth / assetAspectRatio
            val imageLeft = (maxWidth - imageWidth) / 2
            val imageTop = (maxHeight - imageHeight) / 2
            val imageContentScale = when (contentMode) {
                SkinContentMode.Fill -> ContentScale.Crop
                SkinContentMode.Fit -> ContentScale.Fit
            }

            if (fileBitmap != null) {
                Image(
                    bitmap = fileBitmap,
                    contentDescription = "FS42 remote skin",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = imageContentScale,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.default_remote_asset),
                    contentDescription = "FS42 remote skin",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = imageContentScale,
                )
            }

            val imageWidthPx = with(density) { imageWidth.toPx() }
            val imageHeightPx = with(density) { imageHeight.toPx() }

            if (displayBoxVisible) {
                DefaultDisplayZone(
                    rect = displayRect,
                    imageLeft = imageLeft,
                    imageTop = imageTop,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    imageWidthPx = imageWidthPx,
                    imageHeightPx = imageHeightPx,
                    text = displayText,
                    editMode = editMode,
                    selected = displayBoxSelected,
                    onSelected = onDisplaySelected,
                    onMoved = onDisplayMoved,
                    onResized = onDisplayResized,
                )
            }

            zones.forEach { zone ->
                val interactionSource = remember(zone.label) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val selected = editMode && selectedZoneLabel == zone.label
                val zoneShape = RoundedCornerShape(8.dp)

                Box(
                    modifier = Modifier
                        .offset(
                            x = imageLeft + imageWidth * zone.x + if (clickEffectEnabled && pressed) 1.dp else 0.dp,
                            y = imageTop + imageHeight * zone.y + if (clickEffectEnabled && pressed) 2.dp else 0.dp,
                        )
                        .size(
                            width = imageWidth * zone.w,
                            height = imageHeight * zone.h,
                        )
                        .zIndex(if (selected) 3f else 1f)
                        .then(
                            if (editMode) {
                                if (selected) {
                                    Modifier
                                } else {
                                    Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) {
                                        onZoneSelected(zone)
                                    }
                                }
                            } else {
                                Modifier.clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onZoneTapped(zone.command)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = if (selected) {
                                        Color(0xFF66FF88).copy(alpha = 0.30f)
                                    } else {
                                        Color.White.copy(alpha = 0.16f)
                                    },
                                    shape = zoneShape,
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFF66FF88) else Color.White.copy(alpha = 0.82f),
                                    shape = zoneShape,
                                ),
                        )
                        Text(
                            text = zone.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        if (selected) {
                            DragHandle(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .zIndex(6f)
                                    .pointerInput(zone.label, imageWidthPx, imageHeightPx) {
                                        detectDragGestures(
                                            onDragStart = { onZoneSelected(zone) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onZoneMoved(
                                                    zone,
                                                    dragAmount.x / imageWidthPx,
                                                    dragAmount.y / imageHeightPx,
                                                )
                                            },
                                        )
                                    },
                                label = "MOVE",
                                color = Color.White,
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .zIndex(6f)
                                    .size(36.dp)
                                    .background(
                                        color = Color(0xFF66FF88),
                                        shape = RoundedCornerShape(5.dp),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(5.dp),
                                    )
                                    .pointerInput(zone.label, imageWidthPx, imageHeightPx) {
                                        detectDragGestures(
                                            onDragStart = { onZoneSelected(zone) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onZoneResized(
                                                    zone,
                                                    dragAmount.x / imageWidthPx,
                                                    dragAmount.y / imageHeightPx,
                                                )
                                            },
                                        )
                                    },
                            )
                        }
                    } else if (clickEffectEnabled && pressed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = Color.Black.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(10.dp),
                                ),
                        )
                    }
                }
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
                    text = if (editMode) "✎" else "⚙",
                    color = Color.White,
                    fontSize = 32.sp,
                )
            }

            if (editMode) {
                Text(
                    text = "EDIT",
                    color = Color(0xFF66FF88),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(
                            color = Color.Black.copy(alpha = 0.62f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
    imageWidthPx: Float,
    imageHeightPx: Float,
    text: String,
    editMode: Boolean,
    selected: Boolean,
    onSelected: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onResized: (Float, Float) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .offset(
                x = imageLeft + imageWidth * rect.x,
                y = imageTop + imageHeight * rect.y,
            )
            .size(
                width = imageWidth * rect.w,
                height = imageHeight * rect.h,
            )
            .zIndex(if (selected) 4f else 2f)
            .then(
                if (editMode) {
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = interactionSource,
                        ) { onSelected() }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (selected) {
                            Color(0xFF66FF88).copy(alpha = 0.22f)
                        } else {
                            Color.Black.copy(alpha = 0.28f)
                        },
                        shape = shape,
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color(0xFF66FF88) else Color(0xFF66FF88).copy(alpha = 0.72f),
                        shape = shape,
                    ),
            )
        }
        Text(
            text = text,
            color = Color(0xFF66FF88),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (editMode && selected) {
            DragHandle(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(7f)
                    .pointerInput("display-move", imageWidthPx, imageHeightPx) {
                        detectDragGestures(
                            onDragStart = { onSelected() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMoved(
                                    dragAmount.x / imageWidthPx,
                                    dragAmount.y / imageHeightPx,
                                )
                            },
                        )
                    },
                label = "MOVE",
                color = Color.White,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(7f)
                    .size(36.dp)
                    .background(
                        color = Color(0xFF66FF88),
                        shape = RoundedCornerShape(5.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(5.dp),
                    )
                    .pointerInput("display-resize", imageWidthPx, imageHeightPx) {
                        detectDragGestures(
                            onDragStart = { onSelected() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResized(
                                    dragAmount.x / imageWidthPx,
                                    dragAmount.y / imageHeightPx,
                                )
                            },
                        )
                    },
            )
        }
    }
}

@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    label: String,
    color: Color,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun OptionsScreen(
    host: String,
    port: String,
    serverNickname: String,
    testResult: String?,
    editMode: Boolean,
    clickEffectEnabled: Boolean,
    contentMode: SkinContentMode,
    skinName: String,
    savedServerProfiles: List<ServerProfile>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onServerNicknameChange: (String) -> Unit,
    onTestResultChange: (String?) -> Unit,
    onSkinNameChange: (String) -> Unit,
    onEditModeChange: (Boolean) -> Unit,
    onClickEffectChange: (Boolean) -> Unit,
    onContentModeChange: (SkinContentMode) -> Unit,
    onSaveServerProfile: () -> Unit,
    onLoadServerProfile: (ServerProfile) -> Unit,
    onDeleteServerProfile: (ServerProfile) -> Unit,
    onAddElement: () -> Unit,
    onChooseImage: () -> Unit,
    onImportSkin: () -> Unit,
    onOpenInstalledRemotes: () -> Unit,
    onSaveSkin: () -> Unit,
    onClearLayout: () -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val client = remember { Fs42ApiClient() }
    val scope = rememberCoroutineScope()
    var testing by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.Themes) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color(0xFF101214),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Options",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedSection.ordinal,
                containerColor = Color(0xFF101214),
                contentColor = Color.White,
            ) {
                SettingsSectionTab(
                    label = "Themes",
                    selected = selectedSection == SettingsSection.Themes,
                    onClick = { selectedSection = SettingsSection.Themes },
                )
                SettingsSectionTab(
                    label = "Server",
                    selected = selectedSection == SettingsSection.Server,
                    onClick = { selectedSection = SettingsSection.Server },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (selectedSection) {
                    SettingsSection.Themes -> {
                        Text(
                            text = "Themes",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SettingsSwitchRow(
                            label = "Edit Mode",
                            checked = editMode,
                            onCheckedChange = onEditModeChange,
                        )
                        SettingsSwitchRow(
                            label = "Button Press Effect",
                            checked = clickEffectEnabled,
                            onCheckedChange = onClickEffectChange,
                        )
                        SettingsSwitchRow(
                            label = "Fill Screen",
                            checked = contentMode == SkinContentMode.Fill,
                            onCheckedChange = { enabled ->
                                onContentModeChange(if (enabled) SkinContentMode.Fill else SkinContentMode.Fit)
                            },
                        )
                        OutlinedTextField(
                            value = skinName,
                            onValueChange = onSkinNameChange,
                            label = { Text("Skin Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = onAddElement,
                            enabled = editMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add Element")
                        }
                        Button(
                            onClick = onChooseImage,
                            enabled = editMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Image")
                        }
                        Button(
                            onClick = onSaveSkin,
                            enabled = editMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Skin")
                        }
                        Button(
                            onClick = onOpenInstalledRemotes,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Installed Skins")
                        }
                        Button(
                            onClick = onImportSkin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Import Skin")
                        }
                        Button(
                            onClick = onClearLayout,
                            enabled = editMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Clear Layout")
                        }
                        Button(
                            onClick = onResetDefault,
                            enabled = editMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset Default")
                        }
                    }

                    SettingsSection.Server -> {
                        Text(
                            text = "Server Connection",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = serverNickname,
                            onValueChange = onServerNicknameChange,
                            label = { Text("Server Nickname") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                            onClick = onSaveServerProfile,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Server")
                        }
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
                        Text(
                            text = "Saved Servers",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (savedServerProfiles.isEmpty()) {
                            Text(
                                text = "No saved servers yet.",
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                        savedServerProfiles.forEach { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { onLoadServerProfile(profile) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("${profile.nickname}  ${profile.host}:${profile.port}")
                                }
                                TextButton(onClick = { onDeleteServerProfile(profile) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }

                testResult?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.84f),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
    )
}

enum class SettingsSection {
    Themes,
    Server,
}

@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun ElementPickerDialog(
    onElementSelected: (RemoteElementChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Element") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Display", fontWeight = FontWeight.Bold)
                Button(
                    onClick = { onElementSelected(RemoteElementChoice.DisplayText) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Display Text Box")
                }
                Text("Buttons", fontWeight = FontWeight.Bold)
                RemoteButtonChoices.forEach { choice ->
                    Button(
                        onClick = { onElementSelected(choice) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(choice.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ImageInputDialog(
    onChooseImage: () -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onChooseImage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose Image from Device")
                }
                Button(
                    onClick = onUseDefault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use Default Remote Image")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun InstalledRemotesDialog(
    remotes: List<InstalledRemoteSummary>,
    onLoadDefault: () -> Unit,
    onLoadRemote: (InstalledRemoteSummary) -> Unit,
    onDeleteRemote: (InstalledRemoteSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Installed Skins") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onLoadDefault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(DefaultSkinName)
                }
                remotes.forEach { remote ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onLoadRemote(remote) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(remote.name)
                        }
                        TextButton(
                            onClick = { onDeleteRemote(remote) },
                        ) {
                            Text("Delete")
                        }
                    }
                }
                if (remotes.isEmpty()) {
                    Text(
                        text = "No saved skins yet.",
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
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

data class InstalledRemoteSummary(
    val id: String,
    val name: String,
)

data class LoadedSkin(
    val name: String,
    val backgroundFile: File,
    val aspectRatio: Float,
    val contentMode: SkinContentMode,
    val zones: List<DefaultRemoteZone>,
    val displayRect: DefaultRemoteRect,
    val displayBoxVisible: Boolean,
)

data class ServerProfile(
    val id: String,
    val nickname: String,
    val host: String,
    val port: String,
)

data class SaveSkinResult(
    val skinId: String,
    val exportDescription: String,
)

private fun DefaultRemoteRect.moveBy(deltaX: Float, deltaY: Float): DefaultRemoteRect {
    return copy(
        x = (x + deltaX).coerceIn(0f, 1f - w),
        y = (y + deltaY).coerceIn(0f, 1f - h),
    )
}

private fun DefaultRemoteRect.resizeBy(deltaW: Float, deltaH: Float): DefaultRemoteRect {
    val minSize = 0.04f
    return copy(
        w = (w + deltaW).coerceIn(minSize, 1f - x),
        h = (h + deltaH).coerceIn(minSize, 1f - y),
    )
}

data class DefaultRemoteZone(
    val label: String,
    val command: RemoteCommand,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

private fun DefaultRemoteZone.moveBy(deltaX: Float, deltaY: Float): DefaultRemoteZone {
    return copy(
        x = (x + deltaX).coerceIn(0f, 1f - w),
        y = (y + deltaY).coerceIn(0f, 1f - h),
    )
}

private fun DefaultRemoteZone.resizeBy(deltaW: Float, deltaH: Float): DefaultRemoteZone {
    val minSize = 0.025f
    return copy(
        w = (w + deltaW).coerceIn(minSize, 1f - x),
        h = (h + deltaH).coerceIn(minSize, 1f - y),
    )
}

sealed interface RemoteElementChoice {
    data object DisplayText : RemoteElementChoice

    data class Button(
        val label: String,
        val command: RemoteCommand,
    ) : RemoteElementChoice
}

enum class RemoteCommand(val digit: String? = null) {
    POWER_STOP,
    GUIDE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    LAST_CHANNEL,
    DIGIT_0("0"),
    DIGIT_1("1"),
    DIGIT_2("2"),
    DIGIT_3("3"),
    DIGIT_4("4"),
    DIGIT_5("5"),
    DIGIT_6("6"),
    DIGIT_7("7"),
    DIGIT_8("8"),
    DIGIT_9("9"),
    PPV_MENU,
    PPV_PAGE_PREV,
    PPV_PAGE_NEXT,
    PPV_SELECT,
    CUSTOM_HTTP,
}

enum class SkinContentMode(val jsonName: String) {
    Fill("fill"),
    Fit("fit"),
}

val RemoteButtonChoices = listOf(
    RemoteElementChoice.Button("POWER", RemoteCommand.POWER_STOP),
    RemoteElementChoice.Button("GUIDE", RemoteCommand.GUIDE),
    RemoteElementChoice.Button("CH+", RemoteCommand.CHANNEL_UP),
    RemoteElementChoice.Button("CH-", RemoteCommand.CHANNEL_DOWN),
    RemoteElementChoice.Button("VOL+", RemoteCommand.VOLUME_UP),
    RemoteElementChoice.Button("VOL-", RemoteCommand.VOLUME_DOWN),
    RemoteElementChoice.Button("MUTE", RemoteCommand.MUTE),
    RemoteElementChoice.Button("LAST", RemoteCommand.LAST_CHANNEL),
    RemoteElementChoice.Button("0", RemoteCommand.DIGIT_0),
    RemoteElementChoice.Button("1", RemoteCommand.DIGIT_1),
    RemoteElementChoice.Button("2", RemoteCommand.DIGIT_2),
    RemoteElementChoice.Button("3", RemoteCommand.DIGIT_3),
    RemoteElementChoice.Button("4", RemoteCommand.DIGIT_4),
    RemoteElementChoice.Button("5", RemoteCommand.DIGIT_5),
    RemoteElementChoice.Button("6", RemoteCommand.DIGIT_6),
    RemoteElementChoice.Button("7", RemoteCommand.DIGIT_7),
    RemoteElementChoice.Button("8", RemoteCommand.DIGIT_8),
    RemoteElementChoice.Button("9", RemoteCommand.DIGIT_9),
    RemoteElementChoice.Button("PPV", RemoteCommand.PPV_MENU),
    RemoteElementChoice.Button("PAGE LEFT", RemoteCommand.PPV_PAGE_PREV),
    RemoteElementChoice.Button("PAGE RIGHT", RemoteCommand.PPV_PAGE_NEXT),
    RemoteElementChoice.Button("SELECT", RemoteCommand.PPV_SELECT),
)

private const val DefaultSkinId = "default"
private const val DefaultSkinName = "Default FS42 Remote"
private const val DefaultRemoteAssetAspectRatio = 1600f / 2843f

private fun uniqueZoneLabel(baseLabel: String, zones: List<DefaultRemoteZone>): String {
    val existingLabels = zones.map { it.label }.toSet()
    if (baseLabel !in existingLabels) {
        return baseLabel
    }

    var suffix = 2
    while ("$baseLabel $suffix" in existingLabels) {
        suffix += 1
    }
    return "$baseLabel $suffix"
}

private suspend fun saveCurrentSkinPackage(
    context: Context,
    skinId: String,
    skinName: String,
    backgroundFilePath: String?,
    aspectRatio: Float,
    contentMode: SkinContentMode,
    zones: List<DefaultRemoteZone>,
    displayRect: DefaultRemoteRect,
    displayBoxVisible: Boolean,
): Result<SaveSkinResult> = withContext(Dispatchers.IO) {
    runCatching {
        val cleanName = skinName.trim()
            .takeUnless { skinId == DefaultSkinId && it == DefaultSkinName }
            .orEmpty()
            .ifBlank { "FS42 Custom Remote" }
        val cleanSkinId = if (skinId == DefaultSkinId) newSkinId(cleanName) else skinId
        val skinDirectory = File(context.filesDir, "skins/$cleanSkinId")
        val assetsDirectory = File(skinDirectory, "assets")
        val exportDirectory = File(context.filesDir, "exports")
        val backgroundFile = File(assetsDirectory, "remote-background.webp")
        val skinJsonFile = File(skinDirectory, "skin.json")
        val readmeFile = File(skinDirectory, "README.md")
        val packageFile = File(exportDirectory, "${cleanName.toSkinId()}.fs42skin")

        assetsDirectory.mkdirs()
        exportDirectory.mkdirs()

        if (backgroundFilePath != null) {
            File(backgroundFilePath).inputStream().use { input ->
                backgroundFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            context.resources.openRawResource(R.drawable.default_remote_asset).use { input ->
                backgroundFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val backgroundDimensions = backgroundFile.imageDimensions()
        skinJsonFile.writeText(
            buildSkinJson(
                name = cleanName,
                backgroundFile = "assets/remote-background.webp",
                aspectRatio = aspectRatio,
                contentMode = contentMode,
                backgroundWidth = backgroundDimensions?.first,
                backgroundHeight = backgroundDimensions?.second,
                zones = zones,
                displayRect = displayRect,
                displayBoxVisible = displayBoxVisible,
            ).toString(2),
        )

        readmeFile.writeText(
            """
            # FS42 Custom Remote

            This `.fs42skin` file is a ZIP package for the FS42 Android Remote Builder.

            Contents:
            - skin.json
            - assets/remote-background.webp

            Coordinates are normalized to the background image.
            """.trimIndent(),
        )

        packageFile.outputStream().use { fileOutput ->
            ZipOutputStream(fileOutput).use { zip ->
                zip.addFile(entryName = "skin.json", file = skinJsonFile)
                zip.addFile(entryName = "README.md", file = readmeFile)
                zip.addFile(entryName = "assets/remote-background.webp", file = backgroundFile)
            }
        }

        val exportDescription = exportSkinPackageToDocuments(context, packageFile, packageFile.name)
            ?: packageFile.absolutePath

        SaveSkinResult(
            skinId = cleanSkinId,
            exportDescription = exportDescription,
        )
    }
}

private fun buildSkinJson(
    name: String,
    backgroundFile: String,
    aspectRatio: Float,
    contentMode: SkinContentMode,
    backgroundWidth: Int? = null,
    backgroundHeight: Int? = null,
    zones: List<DefaultRemoteZone>,
    displayRect: DefaultRemoteRect,
    displayBoxVisible: Boolean,
): JSONObject {
    val backgroundJson = JSONObject()
        .put("file", backgroundFile)
        .put("contentMode", contentMode.jsonName)
        .put("aspectRatio", aspectRatio)
    if (backgroundWidth != null && backgroundHeight != null) {
        backgroundJson
            .put("width", backgroundWidth)
            .put("height", backgroundHeight)
    }

    val root = JSONObject()
        .put("schemaVersion", 1)
        .put("name", name)
        .put("author", "")
        .put("description", "Created with FS42 Android Remote Builder")
        .put("background", backgroundJson)

    val zoneArray = JSONArray()
    zones.forEachIndexed { index, zone ->
        zoneArray.put(
            JSONObject()
                .put("id", "${zone.label.toSkinId()}-$index")
                .put("name", zone.label)
                .put("command", zone.command.name)
                .put("rect", zone.rectJson()),
        )
    }
    root.put("zones", zoneArray)

    val displayZones = JSONArray()
    if (displayBoxVisible) {
        displayZones.put(
            JSONObject()
                .put("id", "display")
                .put("name", "Display")
                .put("mode", "AUTO")
                .put("rect", displayRect.rectJson()),
        )
    }
    root.put("displayZones", displayZones)

    return root
}

private suspend fun copyImageFromUri(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val extension = context.imageExtension(uri)
        val imageFile = File(context.filesDir, "working/background-${System.currentTimeMillis()}.$extension")
        imageFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected image." }
            imageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        imageFile
    }
}

private suspend fun importSkinPackage(context: Context, uri: Uri): Result<InstalledRemoteSummary> = withContext(Dispatchers.IO) {
    runCatching {
        val importRoot = File(context.cacheDir, "skin-import-${UUID.randomUUID()}")
        importRoot.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected skin package." }
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val safeFile = safeZipDestination(importRoot, entry)
                    if (entry.isDirectory) {
                        safeFile.mkdirs()
                    } else {
                        safeFile.parentFile?.mkdirs()
                        safeFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val skinJsonFile = File(importRoot, "skin.json")
        require(skinJsonFile.isFile) { "Missing skin.json." }
        val json = JSONObject(skinJsonFile.readText())
        require(json.optInt("schemaVersion") == 1) { "Unsupported skin schema." }
        val name = json.optString("name").ifBlank { "Imported Remote" }
        val backgroundPath = json.getJSONObject("background").getString("file")
        require(!backgroundPath.contains("..")) { "Invalid background path." }
        val importedBackground = File(importRoot, backgroundPath)
        require(importedBackground.isFile) { "Missing background image." }

        val installedId = newSkinId(name)
        val skinDirectory = File(context.filesDir, "skins/$installedId")
        val assetsDirectory = File(skinDirectory, "assets")
        assetsDirectory.mkdirs()
        val backgroundFile = File(assetsDirectory, "remote-background.webp")
        importedBackground.inputStream().use { input ->
            backgroundFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val installedJson = buildSkinJson(
            name = name,
            backgroundFile = "assets/remote-background.webp",
            aspectRatio = json.getJSONObject("background")
                .optDouble("aspectRatio", backgroundFile.imageAspectRatio()?.toDouble() ?: DefaultRemoteAssetAspectRatio.toDouble())
                .toFloat(),
            contentMode = json.parseContentMode(),
            backgroundWidth = backgroundFile.imageDimensions()?.first,
            backgroundHeight = backgroundFile.imageDimensions()?.second,
            zones = json.parseZones(),
            displayRect = json.parseDisplayRect(),
            displayBoxVisible = json.parseDisplayVisible(),
        )
        File(skinDirectory, "skin.json").writeText(installedJson.toString(2))
        importRoot.deleteRecursively()
        InstalledRemoteSummary(installedId, name)
    }
}

private fun loadInstalledSkin(context: Context, skinId: String): Result<LoadedSkin> = runCatching {
    require(skinId != DefaultSkinId) { "The default remote is bundled, not installed." }
    val skinDirectory = File(context.filesDir, "skins/$skinId")
    val skinJsonFile = File(skinDirectory, "skin.json")
    require(skinJsonFile.isFile) { "Missing installed skin." }
    val json = JSONObject(skinJsonFile.readText())
    val backgroundPath = json.getJSONObject("background").getString("file")
    require(!backgroundPath.contains("..")) { "Invalid background path." }
    val backgroundFile = File(skinDirectory, backgroundPath)
    require(backgroundFile.isFile) { "Missing background image." }
    LoadedSkin(
        name = json.optString("name").ifBlank { "Installed Remote" },
        backgroundFile = backgroundFile,
        aspectRatio = json.getJSONObject("background")
            .optDouble("aspectRatio", backgroundFile.imageAspectRatio()?.toDouble() ?: DefaultRemoteAssetAspectRatio.toDouble())
            .toFloat(),
        contentMode = json.parseContentMode(),
        zones = json.parseZones(),
        displayRect = json.parseDisplayRect(),
        displayBoxVisible = json.parseDisplayVisible(),
    )
}

private fun listInstalledRemotes(context: Context): List<InstalledRemoteSummary> {
    val skinsDirectory = File(context.filesDir, "skins")
    return skinsDirectory.listFiles()
        ?.filter { it.isDirectory && it.name != DefaultSkinId && File(it, "skin.json").isFile }
        ?.mapNotNull { directory ->
            runCatching {
                val json = JSONObject(File(directory, "skin.json").readText())
                InstalledRemoteSummary(
                    id = directory.name,
                    name = json.optString("name").ifBlank { directory.name },
                )
            }.getOrNull()
        }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
}

private fun listServerProfiles(context: Context): List<ServerProfile> {
    val profilesFile = serverProfilesFile(context)
    if (!profilesFile.isFile) {
        return emptyList()
    }

    return runCatching {
        val profileArray = JSONArray(profilesFile.readText())
        buildList {
            for (index in 0 until profileArray.length()) {
                val profileJson = profileArray.optJSONObject(index) ?: continue
                val id = profileJson.optString("id")
                val host = profileJson.optString("host")
                val port = profileJson.optString("port")
                if (id.isBlank() || host.isBlank() || port.isBlank()) {
                    continue
                }
                add(
                    ServerProfile(
                        id = id,
                        nickname = profileJson.optString("nickname").ifBlank { host },
                        host = host,
                        port = port,
                    ),
                )
            }
        }.sortedBy { it.nickname.lowercase() }
    }.getOrDefault(emptyList())
}

private fun saveServerProfile(context: Context, profile: ServerProfile): Result<Unit> = runCatching {
    require(profile.host.isNotBlank()) { "Host is required." }
    require(profile.port.toIntOrNull() != null) { "Port must be numeric." }
    val profiles = (listServerProfiles(context).filterNot { it.id == profile.id } + profile)
        .sortedBy { it.nickname.lowercase() }
    val profileArray = JSONArray()
    profiles.forEach { saved ->
        profileArray.put(
            JSONObject()
                .put("id", saved.id)
                .put("nickname", saved.nickname)
                .put("host", saved.host)
                .put("port", saved.port),
        )
    }
    val profilesFile = serverProfilesFile(context)
    profilesFile.parentFile?.mkdirs()
    profilesFile.writeText(profileArray.toString(2))
}

private fun deleteServerProfile(context: Context, profileId: String): Result<Unit> = runCatching {
    require(profileId.isNotBlank()) { "Missing server profile id." }
    val profiles = listServerProfiles(context).filterNot { it.id == profileId }
    val profileArray = JSONArray()
    profiles.forEach { saved ->
        profileArray.put(
            JSONObject()
                .put("id", saved.id)
                .put("nickname", saved.nickname)
                .put("host", saved.host)
                .put("port", saved.port),
        )
    }
    val profilesFile = serverProfilesFile(context)
    profilesFile.parentFile?.mkdirs()
    profilesFile.writeText(profileArray.toString(2))
}

private fun serverProfilesFile(context: Context): File {
    return File(context.filesDir, "server-profiles.json")
}

private suspend fun deleteInstalledSkin(context: Context, skinId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        require(skinId != DefaultSkinId) { "The default remote cannot be deleted." }
        val skinDirectory = File(context.filesDir, "skins/$skinId")
        require(!skinId.contains("..") && !skinId.contains(File.separator)) { "Invalid skin id." }
        if (skinDirectory.exists()) {
            require(skinDirectory.deleteRecursively()) { "Could not delete installed skin." }
        }
    }
}

private fun exportSkinPackageToDocuments(context: Context, packageFile: File, fileName: String): String? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/FS42 Remotes")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: return@runCatching null
            context.contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "Unable to open export destination." }
                packageFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            "Documents/FS42 Remotes/$fileName"
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FS42 Remotes")
            directory.mkdirs()
            val exportFile = File(directory, fileName)
            packageFile.copyTo(exportFile, overwrite = true)
            exportFile.absolutePath
        }
    }.getOrNull()
}

private fun DefaultRemoteRect.rectJson(): JSONObject {
    return JSONObject()
        .put("x", x)
        .put("y", y)
        .put("w", w)
        .put("h", h)
}

private fun DefaultRemoteZone.rectJson(): JSONObject {
    return JSONObject()
        .put("x", x)
        .put("y", y)
        .put("w", w)
        .put("h", h)
}

private fun JSONObject.parseContentMode(): SkinContentMode {
    val background = optJSONObject("background")
    return when (background?.optString("contentMode", SkinContentMode.Fit.jsonName)?.lowercase()) {
        SkinContentMode.Fill.jsonName -> SkinContentMode.Fill
        else -> SkinContentMode.Fit
    }
}

private fun JSONObject.parseZones(): List<DefaultRemoteZone> {
    val zoneArray = optJSONArray("zones") ?: return emptyList()
    return buildList {
        for (index in 0 until zoneArray.length()) {
            val zoneJson = zoneArray.optJSONObject(index) ?: continue
            val command = runCatching {
                RemoteCommand.valueOf(zoneJson.getString("command"))
            }.getOrNull() ?: continue
            val rect = zoneJson.optJSONObject("rect") ?: continue
            val zone = DefaultRemoteZone(
                label = zoneJson.optString("name").ifBlank { command.name },
                command = command,
                x = rect.optDouble("x", 0.1).toFloat(),
                y = rect.optDouble("y", 0.1).toFloat(),
                w = rect.optDouble("w", 0.1).toFloat(),
                h = rect.optDouble("h", 0.1).toFloat(),
            ).coerceValid()
            if (zone.w > 0f && zone.h > 0f) {
                add(zone)
            }
        }
    }
}

private fun JSONObject.parseDisplayRect(): DefaultRemoteRect {
    val displayZones = optJSONArray("displayZones")
    val rect = displayZones
        ?.optJSONObject(0)
        ?.optJSONObject("rect")
        ?: return DefaultRemoteDisplayRect
    return DefaultRemoteRect(
        x = rect.optDouble("x", DefaultRemoteDisplayRect.x.toDouble()).toFloat(),
        y = rect.optDouble("y", DefaultRemoteDisplayRect.y.toDouble()).toFloat(),
        w = rect.optDouble("w", DefaultRemoteDisplayRect.w.toDouble()).toFloat(),
        h = rect.optDouble("h", DefaultRemoteDisplayRect.h.toDouble()).toFloat(),
    ).coerceValid()
}

private fun JSONObject.parseDisplayVisible(): Boolean {
    return (optJSONArray("displayZones")?.length() ?: 0) > 0
}

private fun DefaultRemoteRect.coerceValid(): DefaultRemoteRect {
    val coercedW = w.coerceIn(0.025f, 1f)
    val coercedH = h.coerceIn(0.025f, 1f)
    return copy(
        x = x.coerceIn(0f, 1f - coercedW),
        y = y.coerceIn(0f, 1f - coercedH),
        w = coercedW,
        h = coercedH,
    )
}

private fun DefaultRemoteZone.coerceValid(): DefaultRemoteZone {
    val rect = DefaultRemoteRect(x, y, w, h).coerceValid()
    return copy(x = rect.x, y = rect.y, w = rect.w, h = rect.h)
}

private fun String.toSkinId(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "zone" }
}

private fun newSkinId(name: String): String {
    return "${name.toSkinId()}-${System.currentTimeMillis()}"
}

private fun newServerProfileId(name: String): String {
    return "server-${name.toSkinId()}-${System.currentTimeMillis()}"
}

private fun Context.imageExtension(uri: Uri): String {
    val type = contentResolver.getType(uri)
    val extension = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return extension?.takeIf { it.isNotBlank() } ?: "webp"
}

private fun File.imageAspectRatio(): Float? {
    val dimensions = imageDimensions() ?: return null
    return dimensions.first.toFloat() / dimensions.second.toFloat()
}

private fun File.imageDimensions(): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        return null
    }
    return options.outWidth to options.outHeight
}

private fun safeZipDestination(root: File, entry: ZipEntry): File {
    val destination = File(root, entry.name)
    val rootPath = root.canonicalPath
    val destinationPath = destination.canonicalPath
    require(destinationPath == rootPath || destinationPath.startsWith("$rootPath${File.separator}")) {
        "Invalid ZIP entry: ${entry.name}"
    }
    return destination
}

private fun ZipOutputStream.addFile(entryName: String, file: File) {
    putNextEntry(ZipEntry(entryName))
    file.inputStream().use { input ->
        input.copyTo(this)
    }
    closeEntry()
}

val DefaultRemoteDisplayRect = DefaultRemoteRect(
    x = 0.258f,
    y = 0.840f,
    w = 0.470f,
    h = 0.093f,
)

val DefaultRemoteZones = listOf(
    DefaultRemoteZone("POWER", RemoteCommand.POWER_STOP, 0.265f, 0.063f, 0.128f, 0.061f),
    DefaultRemoteZone("GUIDE", RemoteCommand.GUIDE, 0.423f, 0.128f, 0.159f, 0.067f),
    DefaultRemoteZone("VOL+", RemoteCommand.VOLUME_UP, 0.264f, 0.194f, 0.135f, 0.087f),
    DefaultRemoteZone("VOL-", RemoteCommand.VOLUME_DOWN, 0.264f, 0.287f, 0.135f, 0.078f),
    DefaultRemoteZone("CH+", RemoteCommand.CHANNEL_UP, 0.602f, 0.194f, 0.133f, 0.087f),
    DefaultRemoteZone("CH-", RemoteCommand.CHANNEL_DOWN, 0.602f, 0.287f, 0.133f, 0.078f),
    DefaultRemoteZone("1", RemoteCommand.DIGIT_1, 0.260f, 0.408f, 0.146f, 0.066f),
    DefaultRemoteZone("2", RemoteCommand.DIGIT_2, 0.424f, 0.408f, 0.149f, 0.066f),
    DefaultRemoteZone("3", RemoteCommand.DIGIT_3, 0.592f, 0.408f, 0.148f, 0.066f),
    DefaultRemoteZone("4", RemoteCommand.DIGIT_4, 0.260f, 0.479f, 0.146f, 0.066f),
    DefaultRemoteZone("5", RemoteCommand.DIGIT_5, 0.424f, 0.479f, 0.149f, 0.066f),
    DefaultRemoteZone("6", RemoteCommand.DIGIT_6, 0.592f, 0.479f, 0.148f, 0.066f),
    DefaultRemoteZone("7", RemoteCommand.DIGIT_7, 0.260f, 0.550f, 0.146f, 0.065f),
    DefaultRemoteZone("8", RemoteCommand.DIGIT_8, 0.424f, 0.550f, 0.149f, 0.065f),
    DefaultRemoteZone("9", RemoteCommand.DIGIT_9, 0.592f, 0.550f, 0.148f, 0.065f),
    DefaultRemoteZone("MUTE", RemoteCommand.MUTE, 0.260f, 0.621f, 0.146f, 0.066f),
    DefaultRemoteZone("0", RemoteCommand.DIGIT_0, 0.424f, 0.621f, 0.149f, 0.066f),
    DefaultRemoteZone("LAST", RemoteCommand.LAST_CHANNEL, 0.592f, 0.621f, 0.148f, 0.066f),
    DefaultRemoteZone("PPV PAGE LEFT", RemoteCommand.PPV_PAGE_PREV, 0.234f, 0.733f, 0.123f, 0.064f),
    DefaultRemoteZone("PPV", RemoteCommand.PPV_MENU, 0.368f, 0.734f, 0.126f, 0.064f),
    DefaultRemoteZone("PPV SELECT", RemoteCommand.PPV_SELECT, 0.506f, 0.735f, 0.133f, 0.064f),
    DefaultRemoteZone("PPV PAGE RIGHT", RemoteCommand.PPV_PAGE_NEXT, 0.648f, 0.733f, 0.121f, 0.064f),
)

private val ChannelNumberPattern = """"channel_number"\s*:\s*(\d+)""".toRegex()

private fun String.extractChannelNumber(): Int? {
    return ChannelNumberPattern.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
    val view = LocalView.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("10.0.0.99") }
    var port by rememberSaveable { mutableStateOf("4242") }
    var guideBridgePort by rememberSaveable { mutableStateOf("4243") }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var displayText by rememberSaveable { mutableStateOf("READY") }
    var displayMode by rememberSaveable { mutableStateOf(DisplayMode.Status) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var clickEffectEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedZoneLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var displayBoxSelected by rememberSaveable { mutableStateOf(false) }
    var elementPickerOpen by rememberSaveable { mutableStateOf(false) }
    var shapePickerButtonLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var polygonDraftButtonLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var polygonDraftPoints by remember { mutableStateOf(emptyList<ZonePoint>()) }
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
    var undoStack by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redoStack by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
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

    fun currentEditorSnapshot(): EditorSnapshot = EditorSnapshot(
        contentMode = contentMode,
        backgroundFilePath = backgroundFilePath,
        backgroundAspectRatio = backgroundAspectRatio,
        zones = zones,
        displayRect = displayRect,
        displayBoxVisible = displayBoxVisible,
    )

    fun restoreEditorSnapshot(snapshot: EditorSnapshot) {
        contentMode = snapshot.contentMode
        backgroundFilePath = snapshot.backgroundFilePath
        backgroundAspectRatio = snapshot.backgroundAspectRatio
        zones = snapshot.zones
        displayRect = snapshot.displayRect
        displayBoxVisible = snapshot.displayBoxVisible
        selectedZoneLabel = null
        displayBoxSelected = false
    }

    fun rememberEditState() {
        undoStack = (undoStack + currentEditorSnapshot()).takeLast(MaxEditorHistory)
        redoStack = emptyList()
    }

    fun clearEditHistory() {
        undoStack = emptyList()
        redoStack = emptyList()
    }

    fun undoEdit() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + currentEditorSnapshot()).takeLast(MaxEditorHistory)
        undoStack = undoStack.dropLast(1)
        restoreEditorSnapshot(previous)
        displayText = "UNDO"
    }

    fun redoEdit() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + currentEditorSnapshot()).takeLast(MaxEditorHistory)
        redoStack = redoStack.dropLast(1)
        restoreEditorSnapshot(next)
        displayText = "REDO"
    }

    LaunchedEffect(settingsOpen) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        if (settingsOpen) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            displayText = "IMAGE"
            copyImageFromUri(context, uri).fold(
                onSuccess = { imageFile ->
                    rememberEditState()
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
                            clearEditHistory()
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
            displayMode = DisplayMode.Error
            settingsOpen = true
            return
        }
        if (command != RemoteCommand.GUIDE) {
            displayMode = DisplayMode.Status
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
                        if (displayMode == DisplayMode.GuideScroll) {
                            displayMode = DisplayMode.NowPlaying
                            displayText = client.getPlayerStatus(trimmedHost, parsedPort).fold(
                                onSuccess = { body -> body.toNowPlayingDisplayText() },
                                onFailure = { error ->
                                    displayMode = DisplayMode.Error
                                    "ERR ${error.message ?: error::class.simpleName}"
                                },
                            )
                        } else if (displayMode == DisplayMode.NowPlaying) {
                            displayMode = DisplayMode.Loading
                            displayText = "SYSTEM..."
                            val guidePort = guideBridgePort.toIntOrNull()
                            if (guidePort == null) {
                                displayMode = DisplayMode.Error
                                displayText = "SET GUIDE PORT"
                            } else {
                                client.get(trimmedHost, guidePort, "/system/status").fold(
                                    onSuccess = { body ->
                                        displayMode = DisplayMode.SystemStatus
                                        displayText = body.extractVfdText() ?: "SYSTEM DATA\nUNAVAILABLE"
                                    },
                                    onFailure = { error ->
                                        displayMode = DisplayMode.Error
                                        displayText = "SYSTEM ERR\n${error.message ?: error::class.simpleName}"
                                    },
                                )
                            }
                        } else {
                            displayMode = DisplayMode.Loading
                            displayText = "GUIDE..."
                            val guidePort = guideBridgePort.toIntOrNull()
                            if (guidePort == null) {
                                displayMode = DisplayMode.Error
                                displayText = "SET GUIDE PORT"
                            } else {
                                client.get(trimmedHost, guidePort, "/guide/view").fold(
                                    onSuccess = { body ->
                                        displayMode = DisplayMode.GuideScroll
                                        displayText = body.extractVfdText() ?: "NO GUIDE DATA"
                                    },
                                    onFailure = { error ->
                                        displayMode = DisplayMode.Error
                                        displayText = "GUIDE ERR\n${error.message ?: error::class.simpleName}"
                                    },
                                )
                            }
                        }
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
        displayMode = displayMode,
        editMode = editMode,
        clickEffectEnabled = clickEffectEnabled,
        backgroundFilePath = backgroundFilePath,
        backgroundAspectRatio = backgroundAspectRatio,
        contentMode = contentMode,
        zones = zones,
        displayRect = displayRect,
        displayBoxVisible = displayBoxVisible,
        polygonDraftLabel = polygonDraftButtonLabel,
        polygonDraftPoints = polygonDraftPoints,
        selectedZoneLabel = selectedZoneLabel,
        displayBoxSelected = displayBoxSelected,
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
        onZoneSelected = { zone ->
            selectedZoneLabel = zone.label
            displayBoxSelected = false
            displayText = "EDIT ${zone.label}"
        },
        onEditGestureStarted = ::rememberEditState,
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
        onPolygonPointMoved = { changedZone, pointIndex, deltaX, deltaY ->
            zones = zones.map { zone ->
                if (zone.label == changedZone.label) zone.movePolygonPointBy(pointIndex, deltaX, deltaY) else zone
            }
        },
        onPolygonDraftPointAdded = { point ->
            if (polygonDraftPoints.size < MaxPolygonPoints) {
                polygonDraftPoints = polygonDraftPoints + point
                displayText = "POINTS ${polygonDraftPoints.size}/${MaxPolygonPoints}"
            }
        },
        onPolygonDraftUndo = {
            polygonDraftPoints = polygonDraftPoints.dropLast(1)
            displayText = "POINTS ${polygonDraftPoints.size}/${MaxPolygonPoints}"
        },
        onPolygonDraftCancel = {
            polygonDraftButtonLabel = null
            polygonDraftPoints = emptyList()
            displayText = "EDIT MODE"
        },
        onPolygonDraftFinish = {
            val buttonChoice = polygonDraftButtonLabel?.let { label ->
                RemoteButtonChoices
                    .filterIsInstance<RemoteElementChoice.Button>()
                    .firstOrNull { it.label == label }
            }
            if (buttonChoice != null && polygonDraftPoints.size >= MinPolygonPoints) {
                rememberEditState()
                val label = uniqueZoneLabel(buttonChoice.label, zones)
                val newZone = createPolygonZone(
                    label = label,
                    command = buttonChoice.command,
                    imagePoints = polygonDraftPoints,
                )
                zones = zones + newZone
                selectedZoneLabel = label
                displayBoxSelected = false
                polygonDraftButtonLabel = null
                polygonDraftPoints = emptyList()
                displayText = "EDIT $label"
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
        onUndo = ::undoEdit,
        onRedo = ::redoEdit,
        onZoneTapped = ::executeCommand,
    )

    if (settingsOpen) {
        OptionsScreen(
            host = host,
            port = port,
            guideBridgePort = guideBridgePort,
            serverNickname = serverNickname,
            testResult = testResult,
            editMode = editMode,
            clickEffectEnabled = clickEffectEnabled,
            contentMode = contentMode,
            skinName = skinName,
            savedServerProfiles = savedServerProfiles,
            onHostChange = { host = it },
            onPortChange = { port = it },
            onGuideBridgePortChange = { guideBridgePort = it },
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
            onContentModeChange = {
                if (contentMode != it) {
                    rememberEditState()
                    contentMode = it
                }
            },
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
                        guideBridgePort = guideBridgePort.toIntOrNull()?.toString() ?: "4243",
                    )
                    saveServerProfile(context, profile).fold(
                        onSuccess = {
                            currentServerProfileId = profile.id
                            serverNickname = profile.nickname
                            host = profile.host
                            port = profile.port
                            guideBridgePort = profile.guideBridgePort
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
                guideBridgePort = profile.guideBridgePort
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
                rememberEditState()
                zones = emptyList()
                displayBoxVisible = false
                selectedZoneLabel = null
                displayBoxSelected = false
                displayText = "CLEARED"
            },
            onResetDefault = {
                rememberEditState()
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
                clearEditHistory()
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
                        rememberEditState()
                        displayBoxVisible = true
                        selectedZoneLabel = null
                        displayBoxSelected = true
                        displayText = "EDIT DISPLAY"
                    }

                    is RemoteElementChoice.Button -> {
                        shapePickerButtonLabel = choice.label
                    }
                }
                elementPickerOpen = false
                if (choice is RemoteElementChoice.DisplayText) {
                    settingsOpen = false
                    editMode = true
                }
            },
            onDismiss = { elementPickerOpen = false },
        )
    }

    shapePickerButtonLabel?.let { buttonLabel ->
        val buttonChoice = RemoteButtonChoices
            .filterIsInstance<RemoteElementChoice.Button>()
            .firstOrNull { it.label == buttonLabel }
        if (buttonChoice != null) {
            ZoneShapePickerDialog(
                buttonLabel = buttonChoice.label,
                onShapeSelected = { shape ->
                    shapePickerButtonLabel = null
                    settingsOpen = false
                    editMode = true
                    if (shape == ZoneShape.Polygon) {
                        polygonDraftButtonLabel = buttonChoice.label
                        polygonDraftPoints = emptyList()
                        selectedZoneLabel = null
                        displayBoxSelected = false
                        displayText = "POINTS 0/${MaxPolygonPoints}"
                    } else {
                        rememberEditState()
                        val label = uniqueZoneLabel(buttonChoice.label, zones)
                        val newZone = DefaultRemoteZone(
                            label = label,
                            command = buttonChoice.command,
                            x = 0.39f,
                            y = 0.36f,
                            w = 0.22f,
                            h = 0.07f,
                            shape = shape,
                        )
                        zones = zones + newZone
                        selectedZoneLabel = label
                        displayBoxSelected = false
                        displayText = "EDIT $label"
                    }
                },
                onDismiss = { shapePickerButtonLabel = null },
            )
        } else {
            shapePickerButtonLabel = null
        }
    }

    if (imageDialogOpen) {
        ImageInputDialog(
            onChooseImage = {
                imageDialogOpen = false
                imagePicker.launch(arrayOf("image/*"))
            },
            onUseDefault = {
                rememberEditState()
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
                clearEditHistory()
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
                clearEditHistory()
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
                            clearEditHistory()
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
                                clearEditHistory()
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
    displayMode: DisplayMode = DisplayMode.Status,
    editMode: Boolean = false,
    clickEffectEnabled: Boolean = true,
    backgroundFilePath: String? = null,
    backgroundAspectRatio: Float = DefaultRemoteAssetAspectRatio,
    contentMode: SkinContentMode = SkinContentMode.Fill,
    zones: List<DefaultRemoteZone> = DefaultRemoteZones,
    displayRect: DefaultRemoteRect = DefaultRemoteDisplayRect,
    displayBoxVisible: Boolean = true,
    polygonDraftLabel: String? = null,
    polygonDraftPoints: List<ZonePoint> = emptyList(),
    selectedZoneLabel: String? = null,
    displayBoxSelected: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onZoneSelected: (DefaultRemoteZone) -> Unit = {},
    onEditGestureStarted: () -> Unit = {},
    onZoneMoved: (DefaultRemoteZone, Float, Float) -> Unit = { _, _, _ -> },
    onZoneResized: (DefaultRemoteZone, Float, Float) -> Unit = { _, _, _ -> },
    onPolygonPointMoved: (DefaultRemoteZone, Int, Float, Float) -> Unit = { _, _, _, _ -> },
    onPolygonDraftPointAdded: (ZonePoint) -> Unit = {},
    onPolygonDraftUndo: () -> Unit = {},
    onPolygonDraftCancel: () -> Unit = {},
    onPolygonDraftFinish: () -> Unit = {},
    onDisplaySelected: () -> Unit = {},
    onDisplayMoved: (Float, Float) -> Unit = { _, _ -> },
    onDisplayResized: (Float, Float) -> Unit = { _, _ -> },
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
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
                    displayMode = displayMode,
                    editMode = editMode,
                    selected = displayBoxSelected,
                    onSelected = onDisplaySelected,
                    onEditGestureStarted = onEditGestureStarted,
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
                                } else if (zone.shape == ZoneShape.Rectangle) {
                                    Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) {
                                        onZoneSelected(zone)
                                    }
                                } else {
                                    Modifier.pointerInput(zone) {
                                        detectTapGestures { offset ->
                                            if (zone.containsLocalPoint(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())) {
                                                onZoneSelected(zone)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (zone.shape == ZoneShape.Rectangle) {
                                    Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onZoneTapped(zone.command)
                                    }
                                } else {
                                    Modifier.pointerInput(zone) {
                                        detectTapGestures { offset ->
                                            if (zone.containsLocalPoint(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onZoneTapped(zone.command)
                                            }
                                        }
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (editMode) {
                        ZoneShapeOverlay(
                            zone = zone,
                            selected = selected,
                            modifier = Modifier.fillMaxSize(),
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
                                            onDragStart = {
                                                onZoneSelected(zone)
                                                onEditGestureStarted()
                                            },
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
                                            onDragStart = {
                                                onZoneSelected(zone)
                                                onEditGestureStarted()
                                            },
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
                            if (zone.shape == ZoneShape.Polygon) {
                                zone.polygonPoints.take(MaxPolygonPoints).forEachIndexed { pointIndex, point ->
                                    PolygonVertexHandle(
                                        point = point,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .zIndex(8f),
                                        onDragStart = onEditGestureStarted,
                                        onDrag = { deltaX, deltaY ->
                                            onPolygonPointMoved(zone, pointIndex, deltaX, deltaY)
                                        },
                                    )
                                }
                            }
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

            if (polygonDraftLabel != null) {
                PolygonDraftOverlay(
                    label = polygonDraftLabel,
                    points = polygonDraftPoints,
                    imageLeft = imageLeft,
                    imageTop = imageTop,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    onPointAdded = onPolygonDraftPointAdded,
                    onUndo = onPolygonDraftUndo,
                    onCancel = onPolygonDraftCancel,
                    onFinish = onPolygonDraftFinish,
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
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.BottomStart)
                        .size(56.dp),
                ) {
                    Text(
                        text = "↶",
                        color = Color.White.copy(alpha = if (canUndo) 1f else 0.34f),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.BottomEnd)
                        .size(56.dp),
                ) {
                    Text(
                        text = "↷",
                        color = Color.White.copy(alpha = if (canRedo) 1f else 0.34f),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    displayMode: DisplayMode,
    editMode: Boolean,
    selected: Boolean,
    onSelected: () -> Unit,
    onEditGestureStarted: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onResized: (Float, Float) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }
    var scrollBlock by remember(text, displayMode) { mutableStateOf(0) }
    val displayBlocks = remember(text) {
        text.split(Regex("\\n\\s*\\n"))
            .map { block ->
                block.lines()
                    .map { it.trimEnd() }
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("\n")
            }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("READY") }
    }
    val visibleVfdText = if ((displayMode == DisplayMode.GuideScroll || displayMode == DisplayMode.SystemStatus) && displayBlocks.size > 1) {
        displayBlocks[scrollBlock % displayBlocks.size]
    } else {
        text
    }

    LaunchedEffect(displayMode, text) {
        scrollBlock = 0
        if (displayMode == DisplayMode.GuideScroll || displayMode == DisplayMode.SystemStatus) {
            while (true) {
                val currentBlock = displayBlocks[scrollBlock % displayBlocks.size]
                val nextBlock = displayBlocks[(scrollBlock + 1) % displayBlocks.size]
                val currentStation = currentBlock.lineSequence().firstOrNull().orEmpty()
                val nextStation = nextBlock.lineSequence().firstOrNull().orEmpty()
                delay(
                    if (displayMode == DisplayMode.SystemStatus) {
                        2_500
                    } else if (currentStation != nextStation) {
                        1_850
                    } else {
                        950
                    },
                )
                scrollBlock += 1
            }
        }
    }

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
        VfdDisplayBackground(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            shape = shape,
            emphasized = displayMode == DisplayMode.GuideScroll ||
                displayMode == DisplayMode.NowPlaying ||
                displayMode == DisplayMode.SystemStatus,
        )
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
            text = visibleVfdText.uppercase(),
            color = when (displayMode) {
                DisplayMode.Error -> Color(0xFFFF6666)
                else -> Color(0xFF7CFF9E)
            },
            style = TextStyle(
                fontFamily = VfdFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                shadow = Shadow(
                    color = Color(0xFF35FF6B).copy(alpha = 0.70f),
                    blurRadius = 12f,
                ),
            ),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            maxLines = 2,
        )
        if (editMode && selected) {
            DragHandle(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(7f)
                    .pointerInput("display-move", imageWidthPx, imageHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                onSelected()
                                onEditGestureStarted()
                            },
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
                            onDragStart = {
                                onSelected()
                                onEditGestureStarted()
                            },
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
fun ZoneShapeOverlay(
    zone: DefaultRemoteZone,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val fillColor = if (selected) {
        Color(0xFF66FF88).copy(alpha = 0.30f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val borderColor = if (selected) Color(0xFF66FF88) else Color.White.copy(alpha = 0.82f)
    val borderWidth = if (selected) 2.dp else 1.dp

    when (zone.shape) {
        ZoneShape.Rectangle -> {
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = modifier
                    .background(fillColor, shape = shape)
                    .border(borderWidth, borderColor, shape),
            )
        }

        ZoneShape.Circle -> {
            Box(
                modifier = modifier
                    .background(fillColor, shape = CircleShape)
                    .border(borderWidth, borderColor, CircleShape),
            )
        }

        ZoneShape.Polygon -> {
            Canvas(modifier = modifier) {
                val path = zone.polygonPath(size.width, size.height)
                drawPath(path = path, color = fillColor)
                drawPath(path = path, color = borderColor, style = androidx.compose.ui.graphics.drawscope.Stroke(borderWidth.toPx()))
            }
        }
    }
}

@Composable
fun PolygonVertexHandle(
    point: ZonePoint,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .offset(
                    x = maxWidth * point.x - 10.dp,
                    y = maxHeight * point.y - 10.dp,
                )
                .size(20.dp)
                .background(Color(0xFF66FF88), shape = CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.65f), CircleShape)
                .pointerInput(point, widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (widthPx > 0f && heightPx > 0f) {
                                onDrag(dragAmount.x / widthPx, dragAmount.y / heightPx)
                            }
                        },
                    )
                },
        )
    }
}

@Composable
fun PolygonDraftOverlay(
    label: String,
    points: List<ZonePoint>,
    imageLeft: androidx.compose.ui.unit.Dp,
    imageTop: androidx.compose.ui.unit.Dp,
    imageWidth: androidx.compose.ui.unit.Dp,
    imageHeight: androidx.compose.ui.unit.Dp,
    onPointAdded: (ZonePoint) -> Unit,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(x = imageLeft, y = imageTop)
            .size(width = imageWidth, height = imageHeight)
            .zIndex(20f),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size < MaxPolygonPoints) {
                            onPointAdded(
                                ZonePoint(
                                    x = (offset.x / size.width).coerceIn(0f, 1f),
                                    y = (offset.y / size.height).coerceIn(0f, 1f),
                                ),
                            )
                        }
                    }
                },
        ) {
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points.first().x * size.width, points.first().y * size.height)
                points.drop(1).forEach { point ->
                    path.lineTo(point.x * size.width, point.y * size.height)
                }
                drawPath(path, Color(0xFF66FF88), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                if (points.size >= MinPolygonPoints) {
                    val closedPath = Path().apply {
                        moveTo(points.first().x * size.width, points.first().y * size.height)
                        points.drop(1).forEach { point ->
                            lineTo(point.x * size.width, point.y * size.height)
                        }
                        close()
                    }
                    drawPath(closedPath, Color(0xFF66FF88).copy(alpha = 0.16f))
                }
            }
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = Color(0xFF66FF88),
                    radius = 8.dp.toPx(),
                    center = Offset(point.x * size.width, point.y * size.height),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.7f),
                    radius = 8.dp.toPx(),
                    center = Offset(point.x * size.width, point.y * size.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$label POLYGON ${points.size}/${MaxPolygonPoints}",
                color = Color(0xFF66FF88),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUndo,
                    enabled = points.isNotEmpty(),
                ) {
                    Text("Undo")
                }
                Button(
                    onClick = onFinish,
                    enabled = points.size >= MinPolygonPoints,
                ) {
                    Text("Finish")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
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
fun VfdDisplayBackground(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    emphasized: Boolean,
) {
    Box(
        modifier = modifier
            .background(
                color = if (emphasized) Color(0xFF020805) else Color.Black,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Color(0xFF4AFF7D).copy(alpha = if (emphasized) 0.34f else 0.16f),
                shape = shape,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = 6.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color(0xFF55FF8A).copy(alpha = if (emphasized) 0.10f else 0.05f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += gap
            }
        }
    }
}

@Composable
fun OptionsScreen(
    host: String,
    port: String,
    guideBridgePort: String,
    serverNickname: String,
    testResult: String?,
    editMode: Boolean,
    clickEffectEnabled: Boolean,
    contentMode: SkinContentMode,
    skinName: String,
    savedServerProfiles: List<ServerProfile>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onGuideBridgePortChange: (String) -> Unit,
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
                        OutlinedTextField(
                            value = guideBridgePort,
                            onValueChange = onGuideBridgePortChange,
                            label = { Text("Guide Bridge Port") },
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
fun ZoneShapePickerDialog(
    buttonLabel: String,
    onShapeSelected: (ZoneShape) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Button Shape") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(buttonLabel, fontWeight = FontWeight.Bold)
                ZoneShape.entries.forEach { shape ->
                    Button(
                        onClick = { onShapeSelected(shape) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(shape.label)
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

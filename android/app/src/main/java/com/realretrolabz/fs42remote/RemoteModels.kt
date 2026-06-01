package com.realretrolabz.fs42remote

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

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

data class EditorSnapshot(
    val contentMode: SkinContentMode,
    val backgroundFilePath: String?,
    val backgroundAspectRatio: Float,
    val zones: List<DefaultRemoteZone>,
    val displayRect: DefaultRemoteRect,
    val displayBoxVisible: Boolean,
)

data class ServerProfile(
    val id: String,
    val nickname: String,
    val host: String,
    val port: String,
    val guideBridgePort: String,
)

data class SaveSkinResult(
    val skinId: String,
    val exportDescription: String,
)

fun DefaultRemoteRect.moveBy(deltaX: Float, deltaY: Float): DefaultRemoteRect {
    return copy(
        x = (x + deltaX).coerceIn(0f, 1f - w),
        y = (y + deltaY).coerceIn(0f, 1f - h),
    )
}

fun DefaultRemoteRect.resizeBy(deltaW: Float, deltaH: Float): DefaultRemoteRect {
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
    val shape: ZoneShape = ZoneShape.Rectangle,
    val polygonPoints: List<ZonePoint> = emptyList(),
)

fun DefaultRemoteZone.moveBy(deltaX: Float, deltaY: Float): DefaultRemoteZone {
    return copy(
        x = (x + deltaX).coerceIn(0f, 1f - w),
        y = (y + deltaY).coerceIn(0f, 1f - h),
    )
}

fun DefaultRemoteZone.resizeBy(deltaW: Float, deltaH: Float): DefaultRemoteZone {
    val minSize = 0.025f
    return copy(
        w = (w + deltaW).coerceIn(minSize, 1f - x),
        h = (h + deltaH).coerceIn(minSize, 1f - y),
    )
}

fun DefaultRemoteZone.movePolygonPointBy(pointIndex: Int, deltaX: Float, deltaY: Float): DefaultRemoteZone {
    if (shape != ZoneShape.Polygon || pointIndex !in polygonPoints.indices) {
        return this
    }
    return copy(
        polygonPoints = polygonPoints.mapIndexed { index, point ->
            if (index == pointIndex) {
                ZonePoint(
                    x = (point.x + deltaX).coerceIn(0f, 1f),
                    y = (point.y + deltaY).coerceIn(0f, 1f),
                )
            } else {
                point
            }
        },
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

enum class DisplayMode {
    Status,
    NowPlaying,
    GuideScroll,
    SystemStatus,
    Loading,
    Error,
}

val VfdFontFamily = FontFamily(Font(R.font.dotmatri))

enum class ZoneShape(val jsonName: String, val label: String) {
    Rectangle("rect", "Rectangle"),
    Circle("circle", "Circle"),
    Polygon("polygon", "Polygon"),
}

data class ZonePoint(
    val x: Float,
    val y: Float,
)

val DefaultPolygonPoints = listOf(
    ZonePoint(0f, 0f),
    ZonePoint(1f, 0f),
    ZonePoint(1f, 1f),
    ZonePoint(0f, 1f),
)

const val MinPolygonPoints = 3
const val MaxPolygonPoints = 24

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

const val DefaultSkinId = "default"
const val DefaultSkinName = "Default FS42 Remote"
const val DefaultRemoteAssetAspectRatio = 1600f / 2843f
const val MaxEditorHistory = 50

package com.realretrolabz.fs42remote

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun uniqueZoneLabel(baseLabel: String, zones: List<DefaultRemoteZone>): String {
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

suspend fun saveCurrentSkinPackage(
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
        val zoneJson = JSONObject()
            .put("id", "${zone.label.toSkinId()}-$index")
            .put("name", zone.label)
            .put("command", zone.command.name)
            .put("shape", zone.shape.jsonName)
            .put("rect", zone.rectJson())
        if (zone.shape == ZoneShape.Polygon) {
            zoneJson.put("points", zone.polygonJson())
        }
        zoneArray.put(zoneJson)
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

suspend fun copyImageFromUri(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
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

suspend fun importSkinPackage(context: Context, uri: Uri): Result<InstalledRemoteSummary> = withContext(Dispatchers.IO) {
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

fun loadInstalledSkin(context: Context, skinId: String): Result<LoadedSkin> = runCatching {
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

fun listInstalledRemotes(context: Context): List<InstalledRemoteSummary> {
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

fun listServerProfiles(context: Context): List<ServerProfile> {
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
                        guideBridgePort = profileJson.optString("guideBridgePort").ifBlank { "4243" },
                    ),
                )
            }
        }.sortedBy { it.nickname.lowercase() }
    }.getOrDefault(emptyList())
}

fun saveServerProfile(context: Context, profile: ServerProfile): Result<Unit> = runCatching {
    require(profile.host.isNotBlank()) { "Host is required." }
    require(profile.port.toIntOrNull() != null) { "Port must be numeric." }
    require(profile.guideBridgePort.toIntOrNull() != null) { "Guide bridge port must be numeric." }
    val profiles = (listServerProfiles(context).filterNot { it.id == profile.id } + profile)
        .sortedBy { it.nickname.lowercase() }
    val profileArray = JSONArray()
    profiles.forEach { saved ->
        profileArray.put(
            JSONObject()
                .put("id", saved.id)
                .put("nickname", saved.nickname)
                .put("host", saved.host)
                .put("port", saved.port)
                .put("guideBridgePort", saved.guideBridgePort),
        )
    }
    val profilesFile = serverProfilesFile(context)
    profilesFile.parentFile?.mkdirs()
    profilesFile.writeText(profileArray.toString(2))
}

fun deleteServerProfile(context: Context, profileId: String): Result<Unit> = runCatching {
    require(profileId.isNotBlank()) { "Missing server profile id." }
    val profiles = listServerProfiles(context).filterNot { it.id == profileId }
    val profileArray = JSONArray()
    profiles.forEach { saved ->
        profileArray.put(
            JSONObject()
                .put("id", saved.id)
                .put("nickname", saved.nickname)
                .put("host", saved.host)
                .put("port", saved.port)
                .put("guideBridgePort", saved.guideBridgePort),
        )
    }
    val profilesFile = serverProfilesFile(context)
    profilesFile.parentFile?.mkdirs()
    profilesFile.writeText(profileArray.toString(2))
}

private fun serverProfilesFile(context: Context): File {
    return File(context.filesDir, "server-profiles.json")
}

suspend fun deleteInstalledSkin(context: Context, skinId: String): Result<Unit> = withContext(Dispatchers.IO) {
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

private fun DefaultRemoteZone.polygonJson(): JSONArray {
    val points = JSONArray()
    polygonPoints.take(MaxPolygonPoints).forEach { point ->
        points.put(
            JSONObject()
                .put("x", point.x)
                .put("y", point.y),
        )
    }
    return points
}

private fun JSONObject.parseContentMode(): SkinContentMode {
    val background = optJSONObject("background")
    return when (background?.optString("contentMode", SkinContentMode.Fill.jsonName)?.lowercase()) {
        SkinContentMode.Fit.jsonName -> SkinContentMode.Fit
        else -> SkinContentMode.Fill
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
            val shape = zoneJson.parseZoneShape()
            val zone = DefaultRemoteZone(
                label = zoneJson.optString("name").ifBlank { command.name },
                command = command,
                x = rect.optDouble("x", 0.1).toFloat(),
                y = rect.optDouble("y", 0.1).toFloat(),
                w = rect.optDouble("w", 0.1).toFloat(),
                h = rect.optDouble("h", 0.1).toFloat(),
                shape = shape,
                polygonPoints = if (shape == ZoneShape.Polygon) zoneJson.parsePolygonPoints() else emptyList(),
            ).coerceValid()
            if (zone.w > 0f && zone.h > 0f) {
                add(zone)
            }
        }
    }
}

private fun JSONObject.parseZoneShape(): ZoneShape {
    val rawShape = optString("shape", ZoneShape.Rectangle.jsonName).lowercase()
    return ZoneShape.entries.firstOrNull { it.jsonName == rawShape } ?: ZoneShape.Rectangle
}

private fun JSONObject.parsePolygonPoints(): List<ZonePoint> {
    val points = optJSONArray("points") ?: return emptyList()
    return buildList {
        for (index in 0 until points.length().coerceAtMost(MaxPolygonPoints)) {
            val point = points.optJSONObject(index) ?: continue
            add(
                ZonePoint(
                    x = point.optDouble("x", 0.0).toFloat(),
                    y = point.optDouble("y", 0.0).toFloat(),
                ),
            )
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

private fun String.toSkinId(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "zone" }
}

private fun newSkinId(name: String): String {
    return "${name.toSkinId()}-${System.currentTimeMillis()}"
}

fun newServerProfileId(name: String): String {
    return "server-${name.toSkinId()}-${System.currentTimeMillis()}"
}

private fun Context.imageExtension(uri: Uri): String {
    val type = contentResolver.getType(uri)
    val extension = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return extension?.takeIf { it.isNotBlank() } ?: "webp"
}

fun File.imageAspectRatio(): Float? {
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


fun DefaultRemoteRect.coerceValid(): DefaultRemoteRect {
    val coercedW = w.coerceIn(0.025f, 1f)
    val coercedH = h.coerceIn(0.025f, 1f)
    return copy(
        x = x.coerceIn(0f, 1f - coercedW),
        y = y.coerceIn(0f, 1f - coercedH),
        w = coercedW,
        h = coercedH,
    )
}

fun DefaultRemoteZone.coerceValid(): DefaultRemoteZone {
    val rect = DefaultRemoteRect(x, y, w, h).coerceValid()
    val coercedPoints = polygonPoints
        .take(MaxPolygonPoints)
        .map { point ->
            ZonePoint(
                x = point.x.coerceIn(0f, 1f),
                y = point.y.coerceIn(0f, 1f),
            )
        }
    val validShape = if (shape == ZoneShape.Polygon && coercedPoints.size < MinPolygonPoints) {
        ZoneShape.Rectangle
    } else {
        shape
    }
    return copy(
        x = rect.x,
        y = rect.y,
        w = rect.w,
        h = rect.h,
        shape = validShape,
        polygonPoints = if (validShape == ZoneShape.Polygon) coercedPoints else emptyList(),
    )
}

# AGENTS.md — FS42 Custom Android Remote Builder

## Project summary

Build a native Android app for creating and using custom FieldStation42 remote-control skins.

The app should let a normal, non-technical user:

1. Choose or import a remote-control background image.
2. Place tappable zones over the image using a visual editor.
3. Assign each zone to a FieldStation42 command from a friendly command picker.
4. Use the skin as a working Android remote.
5. Export/import shareable skin packages as ZIP-based `.fs42skin` files.

The user should not need to edit JSON manually. JSON is only the internal/export format.

This project should not require the full FieldStation42 repo in the Android workspace. Treat FieldStation42 as a LAN HTTP API target.

---

## Core concept

The app is not just a fixed Android remote. It is a skinnable remote builder.

A remote skin consists of:

- A background image, such as a generated remote image, photo of a real remote, hand-drawn image, or novelty image.
- A list of rectangular tap zones.
- A command assigned to each zone.
- Optional metadata and preview image.

In use mode:

```text
background image
+ invisible tap zones
+ FS42 API calls
= functional custom remote
```

In edit mode:

```text
background image
+ visible translucent zones
+ drag/resize handles
+ command picker
= visual remote editor
```

---

## Target platform

Use native Android.

Recommended stack:

- Kotlin
- Jetpack Compose
- OkHttp or Ktor client for HTTP
- Kotlin serialization or Moshi for JSON
- Android Storage Access Framework for importing/exporting `.fs42skin` files
- Local app storage for installed skins and copied image assets

Avoid a WebView-first design unless explicitly requested. The goal is a slick Android-native feel.

---

## Design requirements

### Normal users must not edit JSON

The user-facing workflow should be visual:

```text
Create Skin
→ Choose Image
→ Add Button Zone
→ Drag / Resize Zone
→ Choose Command
→ Save
→ Use Remote
→ Export Skin
```

The app may store JSON internally, but the UI should expose plain controls:

- Add Button
- Move
- Resize
- Rename
- Change Command
- Test
- Duplicate
- Delete
- Import Skin
- Export Skin

### Skin package format

Use a ZIP file with a custom extension:

```text
Example: Woodgrain-FS42-Remote.fs42skin
```

Internally:

```text
Woodgrain-FS42-Remote.fs42skin
├─ skin.json
├─ preview.png                  optional but recommended
└─ assets/
   ├─ remote-background.png     required
   └─ other-assets...           optional
```

The file extension should be `.fs42skin`, but internally it is a normal ZIP archive.

### Skin JSON schema

Suggested schema v1:

```json
{
  "schemaVersion": 1,
  "name": "Woodgrain FS42 Remote",
  "author": "",
  "description": "",
  "background": {
    "file": "assets/remote-background.png",
    "contentMode": "fit",
    "aspectRatio": 0.571
  },
  "zones": [
    {
      "id": "power",
      "name": "POWER",
      "command": "POWER_STOP",
      "rect": {
        "x": 0.22,
        "y": 0.08,
        "w": 0.18,
        "h": 0.06
      }
    }
  ]
}
```

Coordinates are normalized to the displayed background image, not the device screen:

```text
x = 0.25 means 25% from the left edge of the image
y = 0.40 means 40% from the top edge of the image
w = 0.20 means 20% of image width
h = 0.08 means 8% of image height
```

This is critical for skin portability across different Android screen sizes.

### Content mode

Support at least:

```text
fit
fill
stretch
```

Recommended MVP: implement only `fit` first, because it preserves aspect ratio and makes zone math safer.

---

## User-facing screens

### 1. Server Settings

Fields:

- Server nickname
- Host/IP, default example: `10.0.0.99`
- Port, default: `4242`
- Test Connection button

Build API base URL as:

```text
http://{host}:{port}
```

Do not hardcode only `10.0.0.99`; that is the user's current FS42 machine, but other users will differ.

### 2. Skin Manager

Features:

- List installed skins
- Preview image
- Use skin
- Edit skin
- Duplicate skin
- Import `.fs42skin`
- Export `.fs42skin`
- Delete skin

### 3. Remote Use Screen

Displays the selected skin image full-screen/tall-screen with invisible tap zones.

Behavior:

- Tapping a zone executes its assigned command.
- Optional brief translucent highlight on tap, about 100ms.
- Optional haptic feedback.
- Optional top/bottom overlay with connection status and current channel.

### 4. Remote Edit Screen

Displays the skin image with visible zones.

Required actions:

- Add Zone
- Select Zone
- Drag Zone
- Resize Zone
- Change Command
- Rename Zone
- Test Zone
- Delete Zone
- Save

Zone editor bottom sheet:

```text
Selected Zone: CH+
[Change Command] [Test] [Duplicate] [Delete]
```

Zone display:

- Translucent rectangle
- Border
- Label text
- Resize handles

### 5. Command Picker

Friendly categories:

```text
Channel
├─ CH+
├─ CH-
├─ Last Channel
├─ Number 0
├─ Number 1
├─ Number 2
├─ Number 3
├─ Number 4
├─ Number 5
├─ Number 6
├─ Number 7
├─ Number 8
└─ Number 9

Volume
├─ VOL+
├─ VOL-
└─ Mute

Playback / System
├─ Power / Stop
└─ Guide

PPV
├─ PPV
├─ Page Left
├─ Page Right
└─ Select

Advanced
└─ Custom HTTP Command
```

---

## Built-in remote command enum

Use stable internal command names. Do not store raw endpoint URLs for built-in commands.

```kotlin
enum class RemoteCommand {
    POWER_STOP,
    GUIDE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    LAST_CHANNEL,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    PPV_MENU,
    PPV_PAGE_PREV,
    PPV_PAGE_NEXT,
    PPV_SELECT,
    CUSTOM_HTTP
}
```

The API client should translate these commands to HTTP calls.

---

## FieldStation42 API reference

Assume the FS42 server is reachable at:

```text
http://{host}:{port}
```

User's current example:

```text
http://10.0.0.99:4242
```

The Android app must let the user configure this.

### Status

#### Get current player status

```http
GET /player/status
```

Example:

```bash
curl http://10.0.0.99:4242/player/status
```

Purpose:

- Current channel detection
- Current show/title metadata
- Current file/status metadata

Important: this does not display anything on the FS42 video output. It only returns JSON to the client.

Expected useful fields may include some of the following depending on FS42 version/status:

```json
{
  "status": "playing",
  "network_name": "TOON",
  "channel_number": 55,
  "timestamp": "2026-05-29T22:30:00",
  "title": "Dexter's Laboratory",
  "duration": "0:04:12/0:22:00",
  "file_path": "/media/FS42DB/fs42/catalog/...",
  "content_type": "feature"
}
```

The app should parse defensively. Do not assume every field is always present.

#### Queue connection check

```http
GET /player/status/queue_connected
```

Example:

```bash
curl http://10.0.0.99:4242/player/status/queue_connected
```

Purpose:

- Debug screen
- Verify API has a player command queue reference

This does not guarantee playback is healthy; it only indicates command queue connectivity.

## Channel commands

### Channel up

```http
GET /player/channels/up
```

Example:

```bash
curl http://10.0.0.99:4242/player/channels/up
```

### Channel down

```http
GET /player/channels/down
```

Example:

```bash
curl http://10.0.0.99:4242/player/channels/down
```

### Direct channel tune

```http
GET /player/channels/{channelNumber}
```

Example:

```bash
curl http://10.0.0.99:4242/player/channels/55
```

Use this for direct tuning after numeric entry is resolved by the app.

Client behavior:

- Digits append to an app-local channel buffer.
- The app tunes `/player/channels/{buffer}` automatically after a short input timeout or after the configured maximum digit count.
- After successful tune, reset the buffer.
- Do not add `CLR` or `ENTER` commands/buttons back into the project.

### Guide

```http
POST /player/channels/guide
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/player/channels/guide
```

Purpose:

- Tunes/activates the configured guide channel.

---

## Volume commands

These control the FS42 backend machine audio, not Android device volume.

### Volume up

```http
GET /player/volume/up
POST /player/volume/up
```

Example:

```bash
curl http://10.0.0.99:4242/player/volume/up
```

### Volume down

```http
GET /player/volume/down
POST /player/volume/down
```

Example:

```bash
curl http://10.0.0.99:4242/player/volume/down
```

### Mute

```http
GET /player/volume/mute
POST /player/volume/mute
```

Example:

```bash
curl http://10.0.0.99:4242/player/volume/mute
```

FS42 may use Linux audio tools such as `pactl`, `amixer`, or `wpctl` depending on the backend system.

---

## Player/system command

### Power / Stop

```http
GET /player/commands/stop
POST /player/commands/stop
```

Example:

```bash
curl http://10.0.0.99:4242/player/commands/stop
```

Purpose:

- Stops/exits the FS42 player.

Be careful with this command. In the UI label it can be `POWER`, but functionally it is stop/exit, not system shutdown.

---

## PPV commands

PPV/web-render controls are not normal channel socket commands. They are keyboard-like commands sent through FS42's PPV API.

Current known PPV key allowlist:

```text
PageUp
PageDown
Enter
```

Do not implement arrow-key navigation unless the FS42 backend is patched to allow it.

### Load PPV listing for current channel

```http
GET /ppv/{channelNumber}
```

Example:

```bash
curl http://10.0.0.99:4242/ppv/55
```

Purpose:

- Load PPV content list for a channel.
- Useful for a PPV browser screen.
- The basic remote can also use this as a `PPV` button action.

### Play selected PPV file

```http
POST /ppv/{channelNumber}/play_file
Content-Type: application/json

{
  "file_path": "/path/to/file.mp4"
}
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/ppv/55/play_file \
  -H "Content-Type: application/json" \
  -d '{"file_path":"/path/to/movie.mp4"}'
```

This is for a PPV browser, not necessarily the simple retro remote face.

### PPV previous page

Map UI left arrow or `PAGE-` to:

```http
POST /ppv/{channelNumber}/key/PageDown
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/ppv/55/key/PageDown
```

### PPV next page

Map UI right arrow or `PAGE+` to:

```http
POST /ppv/{channelNumber}/key/PageUp
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/ppv/55/key/PageUp
```

### PPV select

Map `SELECT` to:

```http
POST /ppv/{channelNumber}/key/Enter
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/ppv/55/key/Enter
```

---

## Optional ticker command

Not part of the core fixed remote layout, but useful for advanced/custom commands.

```http
POST /player/ticker
Content-Type: application/json

{
  "message": "Dinner is ready",
  "header": "FS42",
  "style": "fieldstation",
  "iterations": 2
}
```

Example:

```bash
curl -X POST http://10.0.0.99:4242/player/ticker \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Dinner is ready",
    "header": "FS42",
    "style": "fieldstation",
    "iterations": 2
  }'
```

This displays a ticker/overlay on the FS42 video output if supported by the active player setup.

---

## Built-in command execution mapping

Implement a single command dispatcher.

Pseudo-code:

```kotlin
suspend fun execute(command: RemoteCommand) {
    when (command) {
        POWER_STOP -> api.get("/player/commands/stop")
        GUIDE -> api.post("/player/channels/guide")
        CHANNEL_UP -> api.get("/player/channels/up")
        CHANNEL_DOWN -> api.get("/player/channels/down")
        VOLUME_UP -> api.get("/player/volume/up")
        VOLUME_DOWN -> api.get("/player/volume/down")
        MUTE -> api.get("/player/volume/mute")
        LAST_CHANNEL -> tunePreviousChannel()
        DIGIT_0 -> appendDigit("0")
        DIGIT_1 -> appendDigit("1")
        DIGIT_2 -> appendDigit("2")
        DIGIT_3 -> appendDigit("3")
        DIGIT_4 -> appendDigit("4")
        DIGIT_5 -> appendDigit("5")
        DIGIT_6 -> appendDigit("6")
        DIGIT_7 -> appendDigit("7")
        DIGIT_8 -> appendDigit("8")
        DIGIT_9 -> appendDigit("9")
        PPV_MENU -> openOrLoadPpvForCurrentChannel()
        PPV_PAGE_PREV -> api.post("/ppv/$currentChannel/key/PageDown")
        PPV_PAGE_NEXT -> api.post("/ppv/$currentChannel/key/PageUp")
        PPV_SELECT -> api.post("/ppv/$currentChannel/key/Enter")
        CUSTOM_HTTP -> executeCustomHttpCommand()
    }
}
```

---

## App-local state behavior

### Channel buffer

The numeric keypad should not immediately tune on each digit. Instead:

```text
Tap 5 → buffer = "5"
Tap 5 → buffer = "55"
Short timeout → GET /player/channels/55
```

The channel buffer resets after a successful tune or when a new numeric sequence starts.

No `CLR` or `ENTER` button/command should be implemented.

### Last channel

FieldStation42 does not need to implement `LAST` server-side.

The app tracks:

```text
currentChannel
previousChannel
```

On `LAST`:

```text
GET /player/channels/{previousChannel}
```

Update the two values after the command.

Use `/player/status` to periodically refresh current channel.

### Current channel for PPV

PPV commands need a channel number:

```text
/ppv/{channelNumber}/key/PageUp
```

Use the app's current channel state. If unknown, call:

```http
GET /player/status
```

Then parse `channel_number` or equivalent defensively.

---

## Recommended default retro skin layout

The bundled/default skin should use this exact button set only:

```text
POWER        [FS logo]

       GUIDE

VOL+         CH+
VOL-         CH-

1   2   3
4   5   6
7   8   9
MUTE 0 LAST

←   PPV SELECT  →

[blank display area]
```

Do not add extra buttons to the default skin.

Use `SELECT`, not `ORDER`.

The display area should be available for app-side status display, but skins may leave it blank visually.

---

## Display-zone behavior

A display zone is an optional live text region drawn by the Android app over the background image.

Suggested display modes:

```text
AUTO
CHANNEL_INPUT
NOW_PLAYING
GUIDE_SCROLL
PPV_MODE
ERROR_STATUS
CUSTOM_TEXT
```

Recommended behavior:

- Digit buttons show the channel buffer, for example `CH 055`.
- GUIDE may show guide data if the optional companion guide API is available.
- PPV shows a PPV mode message, for example `PPV MODE  ◀ ▶ PAGE  SELECT`.
- Connection errors show a short error such as `FS42 OFFLINE`.
- If the skin has no display zone, use a native sheet/dialog/snackbar instead.

Do not burn display text into the remote background image. It must be rendered live by the app so custom skins can define their own display area.

---

## Android implementation notes

### Image and zone rendering

Use a Compose `Box`:

```text
Box
├─ Image(background)
├─ zones as transparent clickable Boxes
└─ edit handles/labels when editMode == true
```

Important:

- Compute the actual displayed image rect after content scaling.
- Apply normalized zone coordinates relative to that image rect.
- Do not position zones relative to the full screen if the image is letterboxed.

### Zone hit testing

For use mode, each zone should become a transparent clickable area.

Tap behavior:

- Optional haptic feedback
- Optional brief highlight
- Execute command
- Show error toast/snackbar if command fails

### Edit mode movement

The editor should update normalized coordinates.

When the user drags a zone by pixels:

```text
newX = oldX + dragDeltaX / displayedImageWidth
newY = oldY + dragDeltaY / displayedImageHeight
```

Clamp values so zones stay within the image:

```text
0 <= x <= 1 - w
0 <= y <= 1 - h
```

### Resize handles

MVP can use one bottom-right resize handle first.

Later improvement:

- corner handles
- edge handles
- snap/grid mode
- copy/paste zones

---

## Import/export behavior

### Export

When exporting a skin:

1. Write `skin.json`.
2. Copy background image into `assets/`.
3. Generate or copy `preview.png`.
4. Zip into `.fs42skin`.
5. Let user share/save through Android Storage Access Framework.

### Import

When importing:

1. User selects `.fs42skin`.
2. App unzips into temporary directory.
3. Validate `skin.json`.
4. Validate referenced background image exists.
5. Validate zones.
6. Copy assets into app-local skin directory.
7. Install skin.
8. Show preview.

### Validation rules

Reject or repair invalid packages:

- Missing `skin.json`
- Unsupported `schemaVersion`
- Missing background image
- Invalid coordinates
- Zone width/height <= 0
- Unknown built-in command
- Path traversal entries in ZIP, such as `../evil`

Security requirement: never extract ZIP entries outside the intended import directory.

---

## Suggested project structure

```text
fs42-android-remote/
├─ app/
│  └─ src/main/java/.../fs42remote/
│     ├─ MainActivity.kt
│     ├─ data/
│     │  ├─ RemoteSkin.kt
│     │  ├─ RemoteZone.kt
│     │  ├─ RemoteDisplayZone.kt
│     │  ├─ RemoteCommand.kt
│     │  ├─ RemoteProfile.kt
│     │  └─ SkinPackage.kt
│     ├─ network/
│     │  └─ Fs42ApiClient.kt
│     ├─ storage/
│     │  ├─ SkinRepository.kt
│     │  ├─ SkinImporter.kt
│     │  └─ SkinExporter.kt
│     └─ ui/
│        ├─ RemoteUseScreen.kt
│        ├─ RemoteEditScreen.kt
│        ├─ CommandPickerScreen.kt
│        ├─ SkinManagerScreen.kt
│        └─ ServerSettingsScreen.kt
└─ AGENTS.md
```

---

## MVP priorities

Build in this order:

1. Server settings screen with test connection to `/player/status`.
2. Hardcoded default skin profile with zones and built-in command execution.
3. Remote use screen with background image + tappable zones.
4. Channel buffer with auto-tune timeout, plus Last Channel logic.
5. Skin editor: add/move/resize zones.
6. Command picker.
7. Save/load skins locally.
8. Export `.fs42skin`.
9. Import `.fs42skin`.
10. Optional custom HTTP command support.

Do not start with cloud sync, marketplace, accounts, or online sharing.

---

## UX principles

- Keep the main use screen clean and immersive.
- Hide technical details from normal users.
- Use friendly labels, not endpoint names.
- Show clear connection errors.
- Give tap feedback.
- Let users test a zone while editing.
- Do not require JSON editing.
- Do not require the FS42 repo in the Android project.
- Treat FS42 as an HTTP server.

---

## Optional companion guide API sidecar

The Android app may support a separate guide API service, but this must remain optional.

Purpose:

```text
Expose FieldStation42 guide/schedule data as simple JSON
without modifying the upstream FieldStation42 repo.
```

Recommended sidecar location in a companion repo:

```text
fs42-remotes/
├─ android/
│  └─ FS42RemoteBuilder/
├─ sidecars/
│  └─ fs42-guide-api/
│     ├─ guide_api.py
│     ├─ requirements.txt
│     └─ README.md
└─ AGENTS.md
```

Recommended ports:

```text
FS42 core API:          4242
FS42 companion guide:   4243
HLS stream:             8088
```

The sidecar may rely on FS42 Python modules. This is acceptable because it runs beside an installed FieldStation42 system. The Android app should not import, bundle, parse, or depend on FS42 Python code directly.

Expected sidecar endpoint:

```http
GET /guide/view
```

Example base URL:

```text
http://10.0.0.99:4243
```

Expected response shape:

```json
{
  "timings": ["7:00 PM", "7:30 PM", "8:00 PM"],
  "channels": [
    {
      "network_name": "PBS",
      "channel_number": 2,
      "programs": [
        {
          "title": "Arthur",
          "width": 1800,
          "started_earlier": false,
          "ends_later": false
        }
      ]
    }
  ]
}
```

Android behavior:

```text
If companion guide API is configured and reachable:
    use /guide/view for display-zone guide scrolling.

If unavailable:
    fall back to /player/status.
    do not break normal remote operation.
```

The GUIDE command should still call the FS42 core API unless the user explicitly changes the behavior:

```http
POST /player/channels/guide
```

Optional display behavior after GUIDE press:

```text
1. POST http://<host>:4242/player/channels/guide
2. GET  http://<host>:4243/guide/view, if configured
3. Scroll guide data in the active display zone
```

Do not require the guide sidecar for the MVP remote. Treat it as an enhancement.

---

## Terminology

Use these names consistently:

- Skin: background image + zones + metadata.
- Zone: rectangular tappable area.
- Command: action assigned to a zone.
- Profile: server connection settings + selected skin.
- `.fs42skin`: shareable ZIP package containing skin JSON and assets.

---

## Current known FS42 endpoint summary

```text
GET  /player/status
GET  /player/status/queue_connected
GET  /player/channels/up
GET  /player/channels/down
GET  /player/channels/{number}
POST /player/channels/guide

GET  /player/commands/stop
POST /player/commands/stop

GET  /player/volume/up
POST /player/volume/up
GET  /player/volume/down
POST /player/volume/down
GET  /player/volume/mute
POST /player/volume/mute

GET  /ppv/{channelNumber}
POST /ppv/{channelNumber}/play_file
POST /ppv/{channelNumber}/key/PageDown
POST /ppv/{channelNumber}/key/PageUp
POST /ppv/{channelNumber}/key/Enter

POST /player/ticker

Optional companion guide sidecar:
GET  http://<host>:4243/guide/view
```

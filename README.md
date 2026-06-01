# FS42 Remotes

Native Android remote tooling and host-side helpers for FieldStation42.

## Layout

- `android/` contains the Android remote app.
- `fs42-guide-bridge/` contains the optional guide/status bridge.
- `install_guide_bridge.sh` installs the guide bridge onto the FieldStation42 host.

## Guide Bridge Host Install

The guide bridge is not installed on the Android device. It is installed on the same machine that already runs the original FieldStation42 repo, usually directly into that FieldStation42 checkout.

The installer copies the bridge files into the FieldStation42 folder and prefers the existing FieldStation42 Python venv at `env/` for dependencies and runtime.

```bash
./install_guide_bridge.sh
```

The Android app then talks to the bridge over LAN, defaulting to port `4243`.

## APK

A debug-signed APK for sideload testing is checked in at `release/fs42-remote-debug.apk`.

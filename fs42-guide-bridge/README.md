# FS42 Guide Bridge

Small host-side helper for reading FieldStation42 guide information and serving Android-friendly JSON for the remote app display area.

The bridge calls the existing FieldStation42 API, condenses the current and next scheduled program for each visible station, and returns both structured JSON and a ready-to-scroll VFD text script.

## Endpoints

- `GET /health`
- `GET /guide/view`
- `GET /system/status`

Example `vfd_text`:

```text
PBS
NOW: ARTHUR

PBS
8:30P: BOB ROSS

MTV
NOW: MUSIC VIDEOS

MTV
9:00P: TRL
```

Example system `vfd_text`:

```text
TEMP: 52.0C 125.6F

CPU: 18%
MEM: 42%
```

## Run

Install beside an existing FieldStation42 install directory:

```bash
../install_guide_bridge.sh
```

This bridge is meant to run on the FieldStation42 host, on top of an existing FieldStation42 install. The installer copies the bridge into the FieldStation42 folder, writes `guide_bridge.env`, creates `run_guide_bridge.sh`, and can optionally install a user systemd service.

If the FieldStation42 venv exists at `env/`, the installer offers to install the bridge requirements there and the runner uses `env/bin/python3` by default.

Run directly from this folder for development:

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python guide_bridge.py
```

Defaults:

- FS42 API: `http://127.0.0.1:4242`
- Bridge: `http://0.0.0.0:4243`
- Request timeout: `8` seconds

Configuration can be set through environment variables or a local `.env` file. See `.env.example`.

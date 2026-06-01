#!/usr/bin/env bash

set -euo pipefail

REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
BRIDGE_DIR="${BRIDGE_DIR:-$REPO_DIR/fs42-guide-bridge}"
DEFAULT_FS42_DIR="$(pwd)"
if [ ! -f "$DEFAULT_FS42_DIR/field_player.py" ]; then
    DEFAULT_FS42_DIR="$REPO_DIR/reference/FieldStation42"
fi
FS42_DIR="${FS42_DIR:-$DEFAULT_FS42_DIR}"
SYSTEMD_USER_DIR="${SYSTEMD_USER_DIR:-$HOME/.config/systemd/user}"
SERVICE_FILE="$SYSTEMD_USER_DIR/fs42-guide-bridge.service"

FS42_BASE_URL="${FS42_BASE_URL:-http://127.0.0.1:4242}"
BRIDGE_HOST="${BRIDGE_HOST:-0.0.0.0}"
BRIDGE_PORT="${BRIDGE_PORT:-4243}"
GUIDE_LOOKBACK_HOURS="${GUIDE_LOOKBACK_HOURS:-3}"
GUIDE_LOOKAHEAD_HOURS="${GUIDE_LOOKAHEAD_HOURS:-8}"
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-8}"

SOURCE_BRIDGE_SCRIPT="$BRIDGE_DIR/guide_bridge.py"
SOURCE_REQUIREMENTS="$BRIDGE_DIR/requirements.txt"
TARGET_BRIDGE_SCRIPT="$FS42_DIR/guide_bridge.py"
TARGET_REQUIREMENTS="$FS42_DIR/guide_bridge_requirements.txt"
TARGET_ENV="$FS42_DIR/guide_bridge.env"
TARGET_RUNNER="$FS42_DIR/run_guide_bridge.sh"
FS42_PYTHON="$FS42_DIR/env/bin/python3"
FS42_PIP="$FS42_DIR/env/bin/pip"

yes_no() {
    local prompt="$1"
    local default="${2:-Y}"
    local answer suffix

    if [[ "$default" =~ ^[Yy]$ ]]; then
        suffix="Y/n"
    else
        suffix="y/N"
    fi

    read -r -p "$prompt ($suffix): " answer
    answer="${answer:-$default}"
    [[ "$answer" =~ ^[Yy]$ ]]
}

prompt_value() {
    local prompt="$1"
    local default="$2"
    local answer

    read -r -p "$prompt [$default]: " answer
    echo "${answer:-$default}"
}

require_file() {
    if [ ! -f "$1" ]; then
        echo "Missing required file: $1" >&2
        exit 1
    fi
}

refresh_paths() {
    TARGET_BRIDGE_SCRIPT="$FS42_DIR/guide_bridge.py"
    TARGET_REQUIREMENTS="$FS42_DIR/guide_bridge_requirements.txt"
    TARGET_ENV="$FS42_DIR/guide_bridge.env"
    TARGET_RUNNER="$FS42_DIR/run_guide_bridge.sh"
    FS42_PYTHON="$FS42_DIR/env/bin/python3"
    FS42_PIP="$FS42_DIR/env/bin/pip"
}

resolve_fs42_dir() {
    while [ ! -f "$FS42_DIR/field_player.py" ]; do
        echo ""
        echo "Could not find FieldStation42 at:"
        echo "  $FS42_DIR"
        echo ""
        echo "Enter the path to your existing FieldStation42 install."
        echo "For example: /home/pi/FieldStation42"
        FS42_DIR="$(prompt_value "FieldStation42 directory" "$FS42_DIR")"
        refresh_paths
    done
}

detect_public_host() {
    local host
    host="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
    if [ -n "$host" ]; then
        echo "$host"
    else
        echo "127.0.0.1"
    fi
}

load_existing_env_config() {
    if [ ! -f "$TARGET_ENV" ]; then
        return
    fi

    local key value
    while IFS='=' read -r key value; do
        case "$key" in
            FS42_BASE_URL)
                FS42_BASE_URL="${value:-$FS42_BASE_URL}"
                ;;
            BRIDGE_HOST)
                BRIDGE_HOST="${value:-$BRIDGE_HOST}"
                ;;
            BRIDGE_PORT)
                BRIDGE_PORT="${value:-$BRIDGE_PORT}"
                ;;
            GUIDE_LOOKBACK_HOURS)
                GUIDE_LOOKBACK_HOURS="${value:-$GUIDE_LOOKBACK_HOURS}"
                ;;
            GUIDE_LOOKAHEAD_HOURS)
                GUIDE_LOOKAHEAD_HOURS="${value:-$GUIDE_LOOKAHEAD_HOURS}"
                ;;
            REQUEST_TIMEOUT)
                REQUEST_TIMEOUT="${value:-$REQUEST_TIMEOUT}"
                ;;
        esac
    done < "$TARGET_ENV"
}

write_env_config() {
    cat > "$TARGET_ENV" <<CONFIG
FS42_BASE_URL=$FS42_BASE_URL
BRIDGE_HOST=$BRIDGE_HOST
BRIDGE_PORT=$BRIDGE_PORT
GUIDE_LOOKBACK_HOURS=$GUIDE_LOOKBACK_HOURS
GUIDE_LOOKAHEAD_HOURS=$GUIDE_LOOKAHEAD_HOURS
REQUEST_TIMEOUT=$REQUEST_TIMEOUT
CONFIG
}

write_runner() {
    cat > "$TARGET_RUNNER" <<'RUNNER'
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
set -a
source "$SCRIPT_DIR/guide_bridge.env"
set +a

if [ -x "$SCRIPT_DIR/env/bin/python3" ]; then
    exec "$SCRIPT_DIR/env/bin/python3" "$SCRIPT_DIR/guide_bridge.py"
fi

exec /usr/bin/env python3 "$SCRIPT_DIR/guide_bridge.py"
RUNNER
    chmod +x "$TARGET_RUNNER"
}

install_service() {
    mkdir -p "$SYSTEMD_USER_DIR"
    cat > "$SERVICE_FILE" <<SERVICE
[Unit]
Description=FieldStation42 Guide Bridge
After=fs42-player.service network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$FS42_DIR
ExecStart=$TARGET_RUNNER
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=default.target
SERVICE
    systemctl --user daemon-reload
}

require_file "$SOURCE_BRIDGE_SCRIPT"
require_file "$SOURCE_REQUIREMENTS"
resolve_fs42_dir
load_existing_env_config

DETECTED_PUBLIC_HOST="$(detect_public_host)"

echo ""
echo "FieldStation42 Guide Bridge installer"
echo ""
echo "Repo:              $REPO_DIR"
echo "Bridge source:     $BRIDGE_DIR"
echo "FieldStation42:    $FS42_DIR"
echo "Systemd user dir:  $SYSTEMD_USER_DIR"
echo ""
echo "Existing/default settings:"
echo "  FS42_BASE_URL=$FS42_BASE_URL"
echo "  BRIDGE_HOST=$BRIDGE_HOST"
echo "  BRIDGE_PORT=$BRIDGE_PORT"
echo "  GUIDE_LOOKBACK_HOURS=$GUIDE_LOOKBACK_HOURS"
echo "  GUIDE_LOOKAHEAD_HOURS=$GUIDE_LOOKAHEAD_HOURS"
echo "  REQUEST_TIMEOUT=$REQUEST_TIMEOUT"
echo "  Detected host/IP=$DETECTED_PUBLIC_HOST"
echo ""

FS42_BASE_URL="$(prompt_value "FS42 API base URL" "$FS42_BASE_URL")"
BRIDGE_HOST="$(prompt_value "Bridge bind host" "$BRIDGE_HOST")"
BRIDGE_PORT="$(prompt_value "Bridge port" "$BRIDGE_PORT")"
GUIDE_LOOKBACK_HOURS="$(prompt_value "Schedule lookback hours" "$GUIDE_LOOKBACK_HOURS")"
GUIDE_LOOKAHEAD_HOURS="$(prompt_value "Schedule lookahead hours" "$GUIDE_LOOKAHEAD_HOURS")"
REQUEST_TIMEOUT="$(prompt_value "FS42 request timeout seconds" "$REQUEST_TIMEOUT")"

INSTALL_DEPS=false
INSTALL_SERVICE=false
ENABLE_SERVICE=false
ENABLE_LINGER=false

if [ -x "$FS42_PIP" ]; then
    if yes_no "Install/update bridge Python requirements into FieldStation42 env?" "Y"; then
        INSTALL_DEPS=true
    fi
else
    echo ""
    echo "No FieldStation42 pip found at $FS42_PIP."
    echo "Skipping automatic dependency installation."
fi

if yes_no "Install/update fs42-guide-bridge.service?" "Y"; then
    INSTALL_SERVICE=true
    if yes_no "Enable and start fs42-guide-bridge.service now?" "Y"; then
        ENABLE_SERVICE=true
    fi
    if yes_no "Enable user lingering so the bridge can run before login?" "N"; then
        ENABLE_LINGER=true
    fi
fi

echo ""
echo "Installing guide bridge files into FieldStation42:"
echo "  $TARGET_BRIDGE_SCRIPT"
echo "  $TARGET_REQUIREMENTS"
echo "  $TARGET_ENV"
echo "  $TARGET_RUNNER"

cp "$SOURCE_BRIDGE_SCRIPT" "$TARGET_BRIDGE_SCRIPT"
cp "$SOURCE_REQUIREMENTS" "$TARGET_REQUIREMENTS"
chmod +x "$TARGET_BRIDGE_SCRIPT"
write_env_config
write_runner

if [ "$INSTALL_DEPS" = true ]; then
    "$FS42_PIP" install -r "$TARGET_REQUIREMENTS"
fi

if [ "$INSTALL_SERVICE" = true ]; then
    install_service
    if [ "$ENABLE_SERVICE" = true ]; then
        systemctl --user enable --now fs42-guide-bridge.service
    fi
fi

if [ "$ENABLE_LINGER" = true ]; then
    loginctl enable-linger "$USER"
fi

echo ""
echo "Done."
echo ""
echo "Manual runner:"
echo "  $TARGET_RUNNER"
echo ""
echo "Bridge URL:"
echo "  http://$DETECTED_PUBLIC_HOST:$BRIDGE_PORT/guide/view"
echo "  http://$DETECTED_PUBLIC_HOST:$BRIDGE_PORT/system/status"
echo ""
echo "Useful commands:"
echo "  curl http://127.0.0.1:$BRIDGE_PORT/health"
echo "  curl http://127.0.0.1:$BRIDGE_PORT/guide/view"
echo "  curl http://127.0.0.1:$BRIDGE_PORT/system/status"
echo "  systemctl --user status fs42-guide-bridge.service"
echo "  journalctl --user -u fs42-guide-bridge.service -f"

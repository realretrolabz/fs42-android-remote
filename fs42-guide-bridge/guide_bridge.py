import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException

load_dotenv()

FS42_BASE_URL = os.getenv("FS42_BASE_URL", "http://127.0.0.1:4242").rstrip("/")
BRIDGE_HOST = os.getenv("BRIDGE_HOST", "0.0.0.0")
BRIDGE_PORT = int(os.getenv("BRIDGE_PORT", "4243"))
GUIDE_LOOKBACK_HOURS = int(os.getenv("GUIDE_LOOKBACK_HOURS", "3"))
GUIDE_LOOKAHEAD_HOURS = int(os.getenv("GUIDE_LOOKAHEAD_HOURS", "8"))
REQUEST_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT", "8"))

app = FastAPI(title="FS42 Guide Bridge")


@app.get("/health")
async def health() -> dict[str, Any]:
    fs42 = {
        "ok": False,
        "summary_count": 0,
        "error": None,
    }
    async with httpx.AsyncClient(base_url=FS42_BASE_URL, timeout=REQUEST_TIMEOUT, follow_redirects=True) as client:
        try:
            summary = await fetch_summary(client)
            fs42["ok"] = True
            fs42["summary_count"] = len(summary)
        except HTTPException as exc:
            fs42["error"] = exc.detail

    return {
        "ok": True,
        "fs42_base_url": FS42_BASE_URL,
        "fs42": fs42,
    }


@app.get("/guide/view")
async def guide_view() -> dict[str, Any]:
    now = datetime.now().replace(microsecond=0)
    start = now - timedelta(hours=GUIDE_LOOKBACK_HOURS)
    end = now + timedelta(hours=GUIDE_LOOKAHEAD_HOURS)

    async with httpx.AsyncClient(base_url=FS42_BASE_URL, timeout=REQUEST_TIMEOUT, follow_redirects=True) as client:
        summary = await fetch_summary(client)
        stations = visible_scheduled_stations(summary)
        channels = []
        for station in stations:
            channel = await build_channel_view(client, station, now, start, end)
            channels.append(channel)

    return {
        "generated_at": now.isoformat(),
        "fs42_base_url": FS42_BASE_URL,
        "vfd_text": format_vfd_text(channels),
        "channels": channels,
    }


@app.get("/system/status")
async def system_status() -> dict[str, Any]:
    now = datetime.now().replace(microsecond=0)
    temperature_c = read_temperature_c()
    memory = read_memory_status()
    load = read_load_status()

    return {
        "generated_at": now.isoformat(),
        "temperature_c": temperature_c,
        "temperature_f": celsius_to_fahrenheit(temperature_c),
        "cpu_load_percent": load["cpu_load_percent"],
        "load_average_1m": load["load_average_1m"],
        "cpu_count": load["cpu_count"],
        "memory_used_percent": memory["used_percent"],
        "memory_used_mb": memory["used_mb"],
        "memory_total_mb": memory["total_mb"],
        "vfd_text": format_system_vfd_text(temperature_c, load, memory),
    }


async def fetch_summary(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    payload = await fetch_json(client, "/summary")
    summary = payload.get("summary_data")
    if not isinstance(summary, list):
        keys = ", ".join(payload.keys()) if isinstance(payload, dict) else type(payload).__name__
        raise HTTPException(
            status_code=502,
            detail=f"FS42 summary API returned an unexpected shape. Expected summary_data list, got: {keys}",
        )
    return summary


async def fetch_json(client: httpx.AsyncClient, path: str, **kwargs: Any) -> dict[str, Any]:
    try:
        response = await client.get(path, **kwargs)
        response.raise_for_status()
    except httpx.HTTPStatusError as exc:
        body = exc.response.text[:400].strip()
        raise HTTPException(
            status_code=502,
            detail=f"FS42 returned HTTP {exc.response.status_code} for {path}: {body}",
        ) from exc
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Could not reach FS42 at {FS42_BASE_URL}: {exc}") from exc

    try:
        payload = response.json()
    except ValueError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"FS42 returned non-JSON for {path}: {response.text[:400].strip()}",
        ) from exc

    if not isinstance(payload, dict):
        raise HTTPException(status_code=502, detail=f"FS42 returned non-object JSON for {path}.")
    return payload


def visible_scheduled_stations(summary: list[dict[str, Any]]) -> list[dict[str, Any]]:
    stations = [
        station for station in summary
        if not station.get("hidden", False)
        and station.get("_has_schedule", False)
        and station.get("network_name")
    ]
    return sorted(stations, key=lambda station: station.get("channel_number", 9999))


async def build_channel_view(
    client: httpx.AsyncClient,
    station: dict[str, Any],
    now: datetime,
    start: datetime,
    end: datetime,
) -> dict[str, Any]:
    network_name = station["network_name"]
    try:
        blocks = await fetch_schedule_blocks(client, network_name, start, end)
    except HTTPException as exc:
        return {
            "network_name": network_name,
            "network_long_name": station.get("network_long_name", ""),
            "channel_number": station.get("channel_number"),
            "now": None,
            "next": None,
            "error": str(exc.detail),
        }
    current_block = find_current_block(blocks, now)
    next_block = find_next_block(blocks, now, current_block)

    return {
        "network_name": network_name,
        "network_long_name": station.get("network_long_name", ""),
        "channel_number": station.get("channel_number"),
        "now": program_json(current_block),
        "next": program_json(next_block),
    }


async def fetch_schedule_blocks(
    client: httpx.AsyncClient,
    network_name: str,
    start: datetime,
    end: datetime,
) -> list[dict[str, Any]]:
    safe_network_name = quote(network_name, safe="")
    payload = await fetch_json(
        client,
        f"/schedules/{safe_network_name}",
        params={
            "start": start.isoformat(),
            "end": end.isoformat(),
        },
    )
    blocks = payload.get("schedule_blocks", [])
    return blocks if isinstance(blocks, list) else []


def find_current_block(blocks: list[dict[str, Any]], now: datetime) -> dict[str, Any] | None:
    for block in sorted_blocks(blocks):
        start = parse_time(block.get("start_time"))
        end = parse_time(block.get("end_time"))
        if start and end and start <= now < end:
            return block
    return None


def find_next_block(
    blocks: list[dict[str, Any]],
    now: datetime,
    current_block: dict[str, Any] | None,
) -> dict[str, Any] | None:
    current_end = parse_time(current_block.get("end_time")) if current_block else None
    for block in sorted_blocks(blocks):
        start = parse_time(block.get("start_time"))
        if not start:
            continue
        if current_end and start >= current_end:
            return block
        if not current_end and start > now:
            return block
    return None


def sorted_blocks(blocks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(blocks, key=lambda block: parse_time(block.get("start_time")) or datetime.max)


def program_json(block: dict[str, Any] | None) -> dict[str, Any] | None:
    if not block:
        return None
    return {
        "title": clean_title(block.get("title")),
        "start_time": block.get("start_time"),
        "end_time": block.get("end_time"),
    }


def format_vfd_text(channels: list[dict[str, Any]]) -> str:
    sections = []
    for channel in channels:
        network_name = clean_title(channel.get("network_name")) or "UNKNOWN"
        now_title = channel.get("now", {}).get("title") if channel.get("now") else "OFF AIR"
        next_program = channel.get("next")
        sections.append("\n".join([
            network_name,
            f"NOW: {now_title or 'OFF AIR'}",
        ]))
        if next_program:
            next_time = format_time(parse_time(next_program.get("start_time")))
            sections.append("\n".join([
                network_name,
                f"{next_time}: {next_program.get('title') or 'NO DATA'}",
            ]))
        else:
            sections.append("\n".join([
                network_name,
                "NEXT: NO DATA",
            ]))
    return "\n\n".join(sections)


def clean_title(value: Any) -> str:
    return str(value or "").strip().upper()


def parse_time(value: Any) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        return None


def format_time(value: datetime | None) -> str:
    if not value:
        return "NEXT"
    suffix = "A" if value.hour < 12 else "P"
    hour = value.hour % 12 or 12
    return f"{hour}:{value.minute:02d}{suffix}"


def read_temperature_c() -> float | None:
    thermal_paths = sorted(Path("/sys/class/thermal").glob("thermal_zone*/temp"))
    for path in thermal_paths:
        try:
            raw_temp = float(path.read_text().strip())
        except (OSError, ValueError):
            continue
        temperature_c = raw_temp / 1000 if raw_temp > 200 else raw_temp
        if -40 <= temperature_c <= 130:
            return round(temperature_c, 1)
    return None


def celsius_to_fahrenheit(temperature_c: float | None) -> float | None:
    if temperature_c is None:
        return None
    return round((temperature_c * 9 / 5) + 32, 1)


def read_load_status() -> dict[str, Any]:
    cpu_count = os.cpu_count() or 1
    try:
        load_average_1m = os.getloadavg()[0]
    except OSError:
        load_average_1m = 0.0
    cpu_load_percent = min(999.0, max(0.0, (load_average_1m / cpu_count) * 100))
    return {
        "cpu_count": cpu_count,
        "load_average_1m": round(load_average_1m, 2),
        "cpu_load_percent": round(cpu_load_percent),
    }


def read_memory_status() -> dict[str, Any]:
    values: dict[str, int] = {}
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            key, raw_value = line.split(":", 1)
            values[key] = int(raw_value.strip().split()[0])
    except (OSError, ValueError, IndexError):
        return {
            "total_mb": None,
            "used_mb": None,
            "used_percent": None,
        }

    total_kb = values.get("MemTotal")
    available_kb = values.get("MemAvailable")
    if not total_kb or available_kb is None:
        return {
            "total_mb": None,
            "used_mb": None,
            "used_percent": None,
        }

    used_kb = max(0, total_kb - available_kb)
    return {
        "total_mb": round(total_kb / 1024),
        "used_mb": round(used_kb / 1024),
        "used_percent": round((used_kb / total_kb) * 100),
    }


def format_system_vfd_text(
    temperature_c: float | None,
    load: dict[str, Any],
    memory: dict[str, Any],
) -> str:
    temperature_f = celsius_to_fahrenheit(temperature_c)
    if temperature_c is None or temperature_f is None:
        temperature_line = "TEMP: N/A"
    else:
        temperature_line = f"TEMP: {temperature_c:.1f}C {temperature_f:.1f}F"

    cpu_load = load.get("cpu_load_percent")
    memory_load = memory.get("used_percent")
    cpu_line = f"CPU: {cpu_load}%" if cpu_load is not None else "CPU: N/A"
    memory_line = f"MEM: {memory_load}%" if memory_load is not None else "MEM: N/A"
    return f"{temperature_line}\n\n{cpu_line}\n{memory_line}"


if __name__ == "__main__":
    uvicorn.run(app, host=BRIDGE_HOST, port=BRIDGE_PORT)

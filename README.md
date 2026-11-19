# VoidTV (`void-tv`)

> **Sovereign, High-End & Lightweight Android TV Leanback Launcher with Dynamic Live Coding**  
> Custom engineered for Android TV 14 (`UKR1.260308`) · 1280x720 / 4K Landscape · Pure D-Pad Focus Matrix · Zero Telemetry · Hot-Reload REPL Engine

---

## Overview

VoidTV replaces the 165MB bloated Google Android TV Home launcher with an ultra-lightweight (< 15MB RSS), ad-free, 10-foot television cockpit.

Built specifically for power users, developers, and edge systems engineers who want instant response, custom categorizations, hardware HDMI switching, and real-time live coding without APK reinstallation.

## Core Features

- **Ultra-Low Memory Footprint**: Runs in < 15MB RAM RSS (a 91% reduction from Google TV launcher's 165MB).
- **Instant Boot**: Cold start in < 80ms, hot resume in < 5ms.
- **Zero Telemetry**: No BigQuery payloads, no Google Primes, no Google Play Billing, no Firebase promotional push listeners.
- **10-Foot Spatial D-Pad Focus Engine**: 2D grid matrix navigation with smooth cubic-bezier spring focus scaling and glowing borders.
- **Dynamic Live-Coding & Hot-Reload Subsystem**:
  - **FileObserver**: Watches `/sdcard/voidtv/layout.json` and hot-reloads within 16ms upon saving or pushing.
  - **Embedded REPL Server (`:4444`)**: Real-time evaluation of JavaScript/CSS styling and layout mutations via HTTP/WebSocket.
- **Remote Digit Shortcuts**: Press remote keys `0`–`9` for instant app launching.

## Live Coding CLI (`live-push.sh`)

```bash
# Push updated layout JSON to TV
./live-push.sh layout

# Apply a custom accent color live on TV
./live-push.sh color "#00E5FF"

# Evaluate arbitrary JavaScript live on the screen
./live-push.sh eval "document.getElementById('hud-model').textContent = 'SOVEREIGN TV';"

# Launch an application via package name
./live-push.sh launch "com.google.android.youtube.tv"
```

## Build & Deploy

```bash
# Build standalone signed APK
./build.sh

# Deploy to connected TV via ADB Wi-Fi
adb -s 192.168.0.114:5555 install -r voidtv-release.apk
```

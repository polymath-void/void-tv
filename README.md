# VoidTV (`void-tv`)

> **Sovereign, High-End & Lightweight Android TV Leanback Launcher with Dynamic Live Coding**  
> Custom engineered for Android TV 14 (`UKR1.260308`) · 1280x720 / 4K Landscape · Pure D-Pad Focus Matrix · Zero Telemetry · Hot-Reload REPL Engine

---

## 1. Overview

VoidTV replaces the 165MB bloated Google Android TV Home launcher with an ultra-lightweight (< 15MB RSS), ad-free, 10-foot television cockpit.

Built specifically for power users, developers, and edge systems engineers who want instant response, custom categorizations, hardware HDMI switching, in-app file/browser overlays, and real-time live coding without APK reinstallation.

### Key Metrics
- **Ultra-Low Memory Footprint**: Runs in < 15MB RAM RSS (a 91% reduction from Google TV launcher's 165MB).
- **Instant Boot**: Cold start in < 80ms, hot resume in < 5ms.
- **Zero Telemetry**: No BigQuery payloads, no Google Primes, no Google Play Billing, no Firebase promotional push listeners.
- **Microscopic Binary**: ~60 KB signed release APK built via pure `aapt2` + `javac` + `d8` pipeline (no Gradle overhead).

---

## 2. System Architecture

VoidTV separates persistent shell tools from user-editable layout data:

```
┌─────────────────────────────────────────────────────────────┐
│                       VoidTV Android Host                   │
│  MainActivity.java (Leanback Host & Window Key Event Router)│
│  TvBridge.java     (Native Bridge: Apps, Inputs, Files)    │
│  LiveServer.java   (Port 4444 HTTP REST Control Plane)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                WebView Container (Hardware Accel)
                               │
       ┌───────────────────────┴───────────────────────┐
       ▼                                               ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│  builtins.js (Shell Pinned)  │       │  layout.json (Data Seed)     │
│  • Edit Favorites            │       │  • User Favorites Row        │
│  • Launcher Settings         │       │  • Hardware Inputs Row       │
│  • Web Browser               │       │  • All Installed Apps        │
│  • File Manager              │       │  • Custom User Rows          │
│  • Input Sources (HDMI/AV)   │       └──────────────┬───────────────┘
│  • TV Settings               │                      │
│  • Reload Layout             │                      ▼
└──────────────┬───────────────┘             /sdcard/voidtv/layout.json
               │                                      │
               └───────────────► renderLayout ◄───────┘
                                       │
                              FocusEngine 2D Matrix
```

### Core Components
1. **Pinned Built-in App Registry (`assets/runtime/builtins.js`)**:
   - Manages core system tools via `window.VoidApps.register(...)`.
   - Rendered as the pinned **VOID SYSTEM** row before all dynamic layout rows.
   - Cannot be hidden or deleted in the customizer. Supports collapsible folding (`Fold apps`) with state preserved in `localStorage`.
2. **Native TV Input Switcher (`TvInputManager`)**:
   - Queries hardware TV input services (`com.apps.passthrough`, `com.apps.dtv`, `com.apps.atv`).
   - Tunes directly to HDMI 1, HDMI 2, and AV via `TvContract.buildChannelUriForPassthroughInput()`, or opens the native system input chooser (`com.android.tv.action.VIEW_INPUTS`).
3. **10-Foot Spatial D-Pad Focus Engine (`assets/runtime/app.js`)**:
   - Pure 2D grid matrix (`focusMatrix[row][col]`) supporting D-pad up/down/left/right, Enter/Select, Back, and Menu.
   - Smooth cubic-bezier spring focus scaling (`transform: scale(1.06)`) with high-contrast glowing accent borders.
   - Suppresses Android WebView's default amber/orange focus outline for a clean TV UI.
4. **Embedded Widevine DRM Browser Overlay**:
   - Full-featured in-app browser with auto-granted Widevine EME (`RESOURCE_PROTECTED_MEDIA_ID`) for premium video streaming.
   - Supports HTML5 video fullscreen transitions, remote page up/down scrolling, and URL suggestion history.
5. **10-Foot File Manager Overlay**:
   - Direct file explorer for local flash and USB storage with automatic MIME resolution and intent launching.

---

## 3. Remote Control Shortcuts

| Remote Button | Screen Mode | Action |
| --- | --- | --- |
| **D-Pad Directionals** | Any | Move active focus across 2D matrix / tabs / cards |
| **Enter / Center** | Any | Launch app, activate input, or open overlay |
| **Menu Button** | Launcher | Quick-toggle favorite on focused app card |
| **Menu Button** | Browser | Open URL search and navigation dialog |
| **Back Button** | Overlays | Close overlay / customizer / file manager / browser |
| **Digit Keys `0`–`9`** | Launcher | Instant numeric shortcut launch for apps |

---

## 4. Live Coding & Agent Control Plane (`:4444`)

`LiveServer.java` embeds a lightweight, zero-CPU blocking HTTP daemon on port `4444`. It provides complete remote observability and headless automation for AI agents and developers.

### Endpoints

| Endpoint | Method | Description |
| --- | --- | --- |
| `/ping` | `GET` | Returns `PONG` health check. |
| `/state` | `GET` | Dumps active mode, cursor coordinates, and visible UI targets with center coordinates. |
| `/inputs` | `GET` | Dumps JSON array of detected TV inputs (HDMI, AV, Tuner). |
| `/input?id=<ID>` | `GET` | Switches hardware input to specified port or opens input panel. |
| `/key?k=<KEY>` | `GET` | Injects hardware KeyEvent (`UP`, `DOWN`, `LEFT`, `RIGHT`, `SELECT`, `BACK`, `MENU`, `HOME`, `0-9`). |
| `/tap?label=<TXT>` | `GET` | Clicks on-screen card or button by label matching. |
| `/launch?pkg=<PKG>`| `GET` | Launches application package directly via `PackageManager`. |
| `/open?url=<URL>` | `GET` | Opens target URL inside the in-app DRM browser overlay. |
| `/eval` | `POST` | Evaluates arbitrary JavaScript inside the launcher WebView. |

---

## 5. Live CLI Tool (`live-push.sh`)

```bash
# Push layout updates to TV (triggers sub-16ms FileObserver reload)
./live-push.sh layout

# Query live UI state
./live-push.sh state

# List available TV inputs
./live-push.sh inputs

# Switch to HDMI 1 or HDMI 2
./live-push.sh input HDMI1
./live-push.sh input HDMI2

# Dispatch remote navigation or clicks
./live-push.sh key RIGHT
./live-push.sh key SELECT
./live-push.sh tap "Web Browser"

# Open URL in browser
./live-push.sh open "https://fast.com"

# Apply accent color dynamically
./live-push.sh color "#00E5FF"

# Evaluate JavaScript live on TV screen
./live-push.sh eval "document.getElementById('hud-model').textContent = 'SOVEREIGN TV';"
```

---

## 6. Zero-Gradle Build Pipeline (`./build.sh`)

VoidTV does not use Gradle or Android Studio. It compiles directly using user-space Android SDK build-tools:

```bash
./build.sh
```

**Pipeline Stages:**
1. `aapt2 compile`: Compiles Android XML drawables, styles, and values into `compiled_res.zip`.
2. `aapt2 link`: Packages manifest, assets (`assets/runtime/`), and compiled resources into unaligned APK.
3. `javac`: Compiles Java sources with Android SDK 34 platform JAR.
4. `d8`: Translates JVM `.class` bytecode to Android Dalvik `classes.dex`.
5. `zipalign`: 4-byte aligns APK data structures.
6. `apksigner`: Cryptographically signs APK using `debug.keystore` (V1 + V2 + V3 schemes).

**Output**: `voidtv-release.apk` (~60 KB) in under 7 seconds.

---

## 7. Deployment & Verification

```bash
# Connect over Wi-Fi ADB
adb connect 192.168.0.114:5555

# Install or upgrade
adb -s 192.168.0.114:5555 install -r voidtv-release.apk

# Restart launcher
adb -s 192.168.0.114:5555 shell am force-stop com.polymath.voidtv
adb -s 192.168.0.114:5555 shell am start -n com.polymath.voidtv/.MainActivity

# Verify live health
./live-push.sh ping
./live-push.sh state
```

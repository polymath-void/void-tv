# VoidTV (`void-tv`)

> **Sovereign, High-End & Lightweight Android TV Leanback Launcher with Dynamic Live Coding**  
> Custom engineered for Android TV 14 (`UKR1.260308`) · 1280x720 / 4K Landscape · Pure D-Pad Focus Matrix · Zero Telemetry · Hot-Reload REPL Engine

---

## 1. Overview

VoidTV replaces the 165MB bloated Google Android TV Home launcher with an ultra-lightweight (< 15MB RSS), ad-free, 10-foot television cockpit.

Built specifically for power users, developers, and edge systems engineers who want instant response, custom categorizations, hardware HDMI switching, in-app file/browser overlays, and real-time live coding without APK reinstallation.

### Key Architectural Properties
- **Programmatic View Hierarchy**: Zero XML layouts. Single Activity (`MainActivity.java`), one launcher WebView, one overlay browser WebView, and a virtual cursor layer constructed entirely in code.
- **Zero Telemetry**: No BigQuery payloads, no Google Primes, no Google Play Billing, no Firebase marketing daemons.
- **Microscopic Binary**: 88 KB signed release APK built via pure user-space Android SDK tools (`aapt2` + `javac` + `d8` + `zipalign` + `apksigner`), with zero Gradle overhead.
- **Origin-Guarded Control Plane**: In-process HTTP daemon with origin verification and token authorization (`X-Void-Token`) preventing drive-by escapes from browsed sites.

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

## 4. In-Built Web Server & PWA Remote Controller (`:4444`)

`LiveServer.java` embeds a lightweight, zero-CPU blocking HTTP daemon on port `4444`. In addition to providing complete remote observability and headless automation for AI agents, it hosts an installable **Progressive Web App (PWA) TV Remote Controller** directly from the TV:

- **100% Standalone TV Operation**: Navigate using the ergonomic D-pad, Back, Home, Menu, Power, Volume (`VOL+`, `VOL-`, `MUTE`), and Media playback controls.
- **Full Smartphone Keyboard**: Type text, URLs, and search queries on your mobile keyboard and send them directly to the TV (`/text?t=...`), or use real-time keystroke mode (`/key?k=...` forwarding `A`-`Z`, `0`-`9`, `SPACE`, `BACKSPACE`, `ENTER`).
- **In-App Browser Touchpad**: Drag surface with cursor acceleration and tap-to-click (`/mouse?dx=..&dy=..&click=..`).
- **Live Installed Apps Grid**: Browse and launch TV apps with real icons extracted from Android `PackageManager` (`/apps`, `/launch?pkg=..`).
- **TV Hardware Input Switcher**: Switch to HDMI 1, HDMI 2, or AV with one tap (`/inputs`, `/input?id=..`).
- **PWA Installation**: Open `http://<tv-ip>:4444` on iOS Safari or Android Chrome and tap "Add to Home Screen" or "Install App". The remote runs in standalone mode offline or online with zero dependencies.

> For comprehensive architecture, PWA implementation details, and REST API specs, refer to:
> - [PWA Remote Controller Guide (`docs/remote-controller.md`)](file:///media/polymath/workspace/projects/void-tv/docs/remote-controller.md)
> - [Launcher Architecture & Forensic Mapping (`docs/tv_launcher_architecture.md`)](file:///media/polymath/workspace/projects/void-tv/docs/tv_launcher_architecture.md)
> - [Built-in App Registry (`docs/builtin-apps.md`)](file:///media/polymath/workspace/projects/void-tv/docs/builtin-apps.md)

### Endpoints

| Endpoint | Method | Description |
| --- | --- | --- |
| `/` | `GET` | Serves the VoidTV Remote Controller PWA application shell. |
| `/manifest.webmanifest` | `GET` | Web App Manifest with icons, standalone display mode, and theme color. |
| `/sw.js` | `GET` | Service worker for offline shell caching and fast load. |
| `/ping` | `GET` | Returns `PONG` health check. |
| `/state` | `GET` | Dumps active mode, cursor coordinates, and visible UI targets with center coordinates. |
| `/apps` | `GET` | Dumps JSON array of installed TV apps with package names and base64 icons. |
| `/inputs` | `GET` | Dumps JSON array of detected TV inputs (HDMI, AV, Tuner). |
| `/input?id=<ID>` | `GET` | Switches hardware input to specified port or opens input panel. |
| `/key?k=<KEY>` | `GET` | Injects hardware KeyEvent (`UP`, `DOWN`, `LEFT`, `RIGHT`, `SELECT`, `BACK`, `MENU`, `HOME`, `POWER`, `VOL_UP`, `VOL_DOWN`, `MUTE`, `PLAY_PAUSE`, `A-Z`, `0-9`, `SPACE`, `BACKSPACE`, `ENTER`). |
| `/text?t=<TXT>` | `GET` | Injects text / URL into TV search dialog, browser, or active field. |
| `/mouse?dx=&dy=&click=` | `GET` | Moves in-app browser cursor by relative deltas and triggers clicks. |
| `/volume?v=<ACTION>` | `GET` | Adjusts TV system volume (`VOL_UP`, `VOL_DOWN`, `MUTE`). |
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

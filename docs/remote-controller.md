# In-Built Web Server & Standalone PWA Remote Controller Architecture

> **Component**: `LiveServer.java` · Port `4444` · Zero-Dependency Embedded HTTP Daemon  
> **Client**: Progressive Web App (PWA) · `assets/remote/` · Standalone Installable  
> **Target**: Android TV 14 (`com.polymath.voidtv`)

---

## 1. Overview

VoidTV integrates a zero-dependency, in-process HTTP web server (`LiveServer.java`) listening on TCP port `4444`. In addition to serving the developer/agent observability plane (`/state`, `/eval`, `/ping`), it directly hosts a high-performance, mobile-first **Progressive Web App (PWA) TV Remote Controller**.

Any smartphone, tablet, or PC on the same Wi-Fi network can navigate to `http://<tv-ip>:4444/`, install the application to the home screen as a standalone PWA, and completely control the TV without a physical remote and without ADB.

```
                      ┌──────────────────────────────────────────────┐
                      │             Smartphone / Tablet              │
                      │  Mobile Browser / Installed Standalone PWA   │
                      │  • Full Smartphone Keyboard (Live + Text)    │
                      │  • Ergonomic D-Pad & Haptic Feedback         │
                      │  • Complete Remote Replacement (Vol/Power)   │
                      │  • In-App Browser Virtual Touchpad           │
                      │  • TV App Launcher Grid (Live TV Icons)      │
                      │  • TV Input Switcher (HDMI 1/2, AV)          │
                      │  • Service Worker Cache & Offline Shell      │
                      └──────────────────────┬───────────────────────┘
                                             │ HTTP/1.1 (Port 4444)
                                             ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                             VoidTV Android TV Host                                 │
│                                                                                    │
│   ┌────────────────────────────────────────────────────────────────────────────┐   │
│   │                    LiveServer.java (Daemon Port 4444)                      │   │
│   │                                                                            │   │
│   │  Static Asset Router:               REST API Router:                       │   │
│   │  • / → remote/index.html            • GET /apps     (Live package list)    │   │
│   │  • /manifest.webmanifest            • GET /state    (TV mode & focus)      │   │
│   │  • /sw.js (Service Worker)          • GET /key      (A-Z, DPAD, Vol, Media)│   │
│   │  • /remote.css, /remote.js          • GET /text     (Phone keyboard entry) │   │
│   │  • /icon-*.png, /icon.svg           • GET /mouse    (Touchpad dx/dy/click) │   │
│   │                                     • GET /volume   (AudioManager control) │   │
│   │                                     • GET /inputs   (TvInputManager)       │   │
│   │                                     • GET /launch   (Intent dispatch)      │   │
│   └──────────────────────┬───────────────────────────────┬─────────────────────┘   │
│                          │ AssetManager                  │ LiveServer.Host         │
│                          ▼                               ▼                         │
│               ┌──────────────────────┐       ┌───────────────────────────────┐     │
│               │ assets/remote/*      │       │ MainActivity & TvBridge       │     │
│               │ (Baked in APK)       │       │ • Full Keyboard & Key Dispatch│     │
│               └──────────────────────┘       │ • AudioManager & Volume Engine│     │
│                                              │ • TouchEvent & Cursor Engine  │     │
│                                              │ • PackageManager & App Icons  │     │
│                                              │ • TvInputManager Switching    │     │
│                                              └───────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Key Capabilities

### A. 100% Standalone TV Operation (Zero ADB Required)
- **Ergonomic D-Pad**: High-contrast directional buttons (Up, Down, Left, Right) with a central glowing `OK` button and tactile haptic vibration (`navigator.vibrate(15)`).
- **Core TV Controls**: Dedicated buttons for `◀ BACK`, `⌂ HOME`, `☰ MENU`, and `⏻ POWER`.
- **Hardware Volume & Media Controls**: `VOL -`, `MUTE`, `VOL +`, `PAGE ▲`, `PAGE ▼`, plus media playback (`⏮`, `⏪`, `⏯`, `⏩`, `⏭`).
- **Numeric Dialer**: Full 0–9 keypad drawer for direct TV channel tuning and numeric app shortcuts.
- **Independence from ADB**: All key events, volume changes, and app launches are dispatched via native Android APIs (`Activity.dispatchKeyEvent`, `AudioManager`, `startActivity`). ADB does not need to be enabled, connected, or running.

### B. Dual-Mode Smartphone Keyboard
Typing on a TV with a directional remote is notoriously slow. VoidTV solves this with two smartphone keyboard modes:
1. **Text Injection Mode (`/text?t=...`)**:
   - Type search terms, long URLs, or passwords into the smartphone input sheet.
   - Tap **"🚀 SEND TO TV"** or **"🌐 OPEN IN BROWSER"**.
   - If the in-app browser search dialog is active, it directly populates the `EditText`.
   - If on the launcher, it triggers search or opens the URL in the in-app DRM browser overlay.
   - For third-party apps, it falls back to native `input text` injection.
2. **Real-Time Keystroke Mode (`/key?k=...`)**:
   - Toggle **Real-Time Keystroke Mode** ON.
   - The phone captures individual keypresses and immediately dispatches them to the TV:
     - Letters `A`–`Z` (`KEYCODE_A`–`KEYCODE_Z`)
     - Digits `0`–`9` (`KEYCODE_0`–`KEYCODE_9`)
     - Controls: `SPACE`, `BACKSPACE` (`KEYCODE_DEL`), `ENTER`, `TAB`, `ESCAPE`
     - Quick shortcut pills: `https://`, `.com`, `youtube`, `fast.com`.

### C. In-App Browser Virtual Touchpad
- When browsing the web in the VoidTV in-app browser, the **🖱️ MOUSE** tab transforms your smartphone into a high-sensitivity trackpad.
- **Relative Deltas**: Dragging sends continuous relative movement (`/mouse?dx=..&dy=..`) with velocity smoothing to control the on-screen virtual cursor.
- **Tap-to-Click**: A quick tap without drag dispatches an atomic `ACTION_DOWN` + `ACTION_UP` touch event directly at cursor coordinates.
- **Scroll & Navigation Toolbar**: Dedicated scroll up/down buttons, plus quick browser tools: Back, Forward, Home, Reload, Search, and Exit Browser.

### D. Live TV Apps Grid with Authentic Icons
- The **📱 APPS** tab queries `/apps` and dynamically renders every installed TV application.
- **Real TV Icons**: App icons are extracted as PNG base64 strings directly from Android `PackageManager` (`icon.loadIcon(pm)`).
- **Instant Search Filter**: Filter apps in real-time as you type on your phone.
- **One-Tap Launch**: Tapping any card fires `/launch?pkg=...` to launch the app instantly on the TV.

### E. TV Hardware Input Switcher
- The **📺 INPUTS** tab queries `TvInputManager` for hardware and passthrough ports (`/inputs`).
- One-tap switching to `HDMI 1`, `HDMI 2`, `AV / Composite`, or opening the native TV input selection panel (`/input?id=...`).

---

## 3. Progressive Web App (PWA) Specification

The remote controller meets all modern W3C Progressive Web App standards for home screen installation on iOS Safari and Android Chrome:

### Manifest (`assets/remote/manifest.webmanifest`)
- `name`: `"VoidTV Remote"`
- `short_name`: `"Void Remote"`
- `start_url`: `"/"`
- `scope`: `"/"`
- `display`: `"standalone"`
- `background_color`: `"#0A0A10"`
- `theme_color`: `"#00E5FF"`
- `orientation`: `"portrait"`
- Icons: `icon-192.png` (192x192), `icon-512.png` (512x512 any & maskable), and `icon.svg` vector.
- App Shortcuts: Direct jumps to `D-Pad Remote`, `Keyboard`, `Touchpad`, and `TV Apps`.

### Service Worker (`assets/remote/sw.js`)
- **Shell Caching**: Pre-caches the application shell (`/`, `/index.html`, `/remote.css`, `/remote.js`, `/manifest.webmanifest`, icons) for instant sub-millisecond loading.
- **Stale-While-Revalidate**: Static assets load instantly from cache while revalidating in the background.
- **Network-Only for APIs**: All dynamic routes (`/key`, `/state`, `/apps`, `/text`, `/volume`, `/mouse`, `/input`, `/launch`) bypass the cache to ensure zero latency and real-time execution.

### Installation Instructions
- **Android (Chrome)**: Tap the in-app **"📥 INSTALL"** button on the top HUD, or open the Chrome menu (`⋮`) and select **"Install app"**.
- **iOS (Safari)**: Tap the **Share** button (box with upward arrow) and select **"Add to Home Screen"**.

---

## 4. HTTP REST API Reference (`:4444`)

All endpoints are accessible over standard HTTP on port `4444`. Responses include `Access-Control-Allow-Origin: *`.

### Static PWA Endpoints

| Endpoint | Method | Response Type | Description |
| --- | --- | --- | --- |
| `/` or `/index.html` | `GET` | `text/html; charset=utf-8` | Remote Controller PWA application shell |
| `/manifest.webmanifest` | `GET` | `application/manifest+json` | Web App Manifest for PWA installation |
| `/sw.js` | `GET` | `application/javascript` | Service Worker with `Service-Worker-Allowed: /` |
| `/remote.css` | `GET` | `text/css; charset=utf-8` | Mobile responsive styling sheet |
| `/remote.js` | `GET` | `application/javascript` | Remote controller client application logic |
| `/icon-*.png`, `/icon.svg` | `GET` | `image/png`, `image/svg+xml` | Application icons |

### Remote Control & TV Action Endpoints

| Endpoint | Method | Query / Body Params | Description |
| --- | --- | --- | --- |
| `/ping` | `GET` | — | Returns `PONG` health check. |
| `/state` | `GET` | — | Returns JSON dump of active mode, cursor coordinates, and visible UI items. |
| `/apps` | `GET` | — | Returns JSON array of installed apps with `label`, `packageName`, `activityName`, and `icon` (base64 PNG). |
| `/inputs` | `GET` | — | Returns JSON array of detected TV inputs (HDMI, AV, Tuner). |
| `/input` | `GET` | `id=<HDMI1\|HDMI2\|AV\|select>` | Switches TV input or opens native input selector. |
| `/key` | `GET` | `k=<KEY>` | Dispatches hardware KeyEvent (`UP`, `DOWN`, `LEFT`, `RIGHT`, `SELECT`, `BACK`, `MENU`, `HOME`, `POWER`, `VOL_UP`, `VOL_DOWN`, `MUTE`, `PLAY_PAUSE`, `A`-`Z`, `0`-`9`, `SPACE`, `BACKSPACE`, `ENTER`, `TAB`). |
| `/text` | `GET` | `t=<STRING>` | Injects text string or URL into TV search dialog, browser, or active field. |
| `/mouse` | `GET` | `dx=<FLOAT>&dy=<FLOAT>&click=<BOOL>` | Moves in-app browser cursor by relative deltas and triggers clicks. |
| `/volume` | `GET` | `v=<VOL_UP\|VOL_DOWN\|MUTE>` | Directly adjusts TV system volume via `AudioManager`. |
| `/tap` | `GET` | `label=<LABEL>` | Clicks on-screen card or button by label matching. |
| `/launch` | `GET` | `pkg=<PACKAGE>` | Launches application directly via `PackageManager`. |
| `/open` | `GET` | `url=<URL>` | Opens target URL inside the in-app DRM browser overlay. |
| `/eval` | `POST` | `<JAVASCRIPT>` | Evaluates arbitrary JavaScript inside the launcher WebView. |

---

## 5. Security & Network Considerations

- **Local Wi-Fi Only**: The server listens on `0.0.0.0:4444`. It is designed for private local networks and is not exposed to the public internet.
- **HTTP vs HTTPS**: The server uses plain `HTTP`. When accessing from mobile browsers, always prefix `http://` (e.g. `http://192.168.0.114:4444`), as modern browsers default to `https://` which will fail with SSL errors.
- **Zero Background Overhead**: `LiveServer.java` uses lightweight blocking sockets in standard Java. CPU usage is 0% when idle and RAM overhead is negligible (< 100 KB).

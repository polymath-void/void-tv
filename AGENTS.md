# AGENTS.md

WebView-based Android TV home launcher for a single physical TV at `192.168.0.114:5555`. Native code is three Java files in `src/com/polymath/voidtv/`; the entire UI is `assets/runtime/`. No Gradle, no tests, no CI.

## Commands

```bash
./build.sh                                        # aapt2 -> javac -> d8 -> zipalign -> apksigner
adb connect 192.168.0.114:5555
adb -s 192.168.0.114:5555 install -r voidtv-release.apk
./live-push.sh {layout|state|key <name>|tap <label>|open <url>|eval <js>|color <#hex>|launch <pkg>|ping}
```

- `build.sh` deletes all previous outputs on every run and hard-codes `JAVA_HOME=/home/polymath/.local/jdk` plus the SDK at `/home/polymath/phone-backups-A142/termux/android-sdk`. Check those paths first when the build errors; do not add Gradle.
- `javac` compiles `src/com/polymath/voidtv/*.java` with a flat glob. A new subpackage is silently skipped unless `build.sh` is updated.
- If a reinstall fails on a signature mismatch, `debug.keystore` was regenerated (it is untracked and auto-created by `build.sh`). Uninstall first.
- The APK registers as `HOME` + `LEANBACK_LAUNCHER` and is the active home on this TV. The stock `com.google.android.tvlauncher` is disabled for user 0 because the firmware pins it; re-enable with `adb shell pm enable --user 0 com.google.android.tvlauncher` to get the old launcher back.
- Uninstall/reinstall while the app is running can leave a stale second window and an inconsistent `/state`; `am force-stop` then `am start` after installing.

## What actually hot-reloads

Only `/sdcard/voidtv/layout.json`, through a `FileObserver` that calls `window.HotReload.reloadConfig()`. Anything else — `app.js`, `builtins.js`, `style.css`, `*.html`, `res/`, Java — needs `./build.sh` plus reinstall, because assets are baked in and served from `file:///android_asset/runtime/`.

`assets/runtime/layout.json` is a seed file, not the runtime config. The app reads `/sdcard/voidtv/layout.json`, then WebView `localStorage`. Editing the bundled copy changes nothing on the TV, and `./live-push.sh layout` overwrites whatever was configured on the device.

## Remote input

`MainActivity.onKeyDown` maps DPAD, ENTER, BACK, MENU and `0`–`9`, returns `true`, and forwards them into JS as `window.FocusEngine.onKey(...)`. Those keys never reach the `keydown` listener in `app.js`; only unmapped keys do. Add a new key in the native `switch` first, then handle it in `FocusEngine`, `Customizer`, or `FileManager`. The browser overlay is a separate WebView with its own Back/Menu handling and bypasses `FocusEngine` entirely.

## Debugging and automation

`android:debuggable` is never set, so `chrome://inspect` will not attach, and `app.js` has no `console.log` plumbing. `uiautomator`/`aops screen` also sees nothing useful: the entire UI lives in one WebView, so the accessibility dump reports zero elements.

Drive and inspect the app through `:4444` instead — `GET /state` returns the current mode, focus cell, and every visible control with label, action, and centre coordinates; `POST /key`, `/tap`, `/launch`, `/open` act on it. `./live-push.sh state`, `key`, `tap`, `open` wrap those. `window.AgentAPI` in `app.js` is the JS side; `MainActivity` implements `LiveServer.Host`.

## Port 4444

`LiveServer` is a blocking HTTP server with no WebSocket and no authentication, contrary to `README.md` and `docs/`. Endpoints:

- `GET /ping` → `PONG`
- `GET /state` → JSON (`mode`, `focus`, `items[]`, or `url`/`pretty`/`canGo[Back|Forward]`/`dialog` in browser mode). In `CUSTOMIZER` mode `focus` is the overlay's own cursor and `tab` is the open tab; row controls report labels like `INPUT SOURCES · up`.
- `GET /inputs` → JSON array of hardware and passthrough inputs detected by `TvInputManager` (`id`, `label`, `typeName`, `isPassthrough`).
- `GET /input?id=<HDMI1|HDMI2|AV|select>` → tunes directly to the input via `TvContract.buildChannelUriForPassthroughInput` or launches `com.android.tv.action.VIEW_INPUTS`.
- `/key?k=RIGHT` → dispatches a real KeyEvent through the focused window (works in the address-bar dialog too)
- `/tap?label=Netflix`, `/launch?pkg=...`, `/open?url=...` → JSON result
- `POST /eval` → fire-and-forget JS in the launcher WebView

`live-push.sh` targets `TV_IP` and `PORT` when set. Use `./live-push.sh {layout|state|inputs|input <id>|key <name>|tap <label>|open <url>|eval <js>|color <#hex>|launch <pkg>|ping}`.

## Browser

The overlay WebView and its home page are separate from the launcher. The address bar normalises input (`example.com`, `192.168.0.114:4444/ping`, `localhost:8080`, `voidtv` → home) and shows a D-pad-navigable suggestion strip: open/search for what was typed, plus the six most recent URLs from SharedPreferences `voidtv_browser`. Displayed URLs are shortened to `🔒 host/path`; full URLs are only in `getUrl()`.

## Structure

- `index.html` + `app.js` render the home screen. `TvBridge`, exposed to JS as `window.Android`, supplies installed apps (leanback category first, then launcher), base64 icons, directory listing, Settings, and the browser overlay.
- `assets/runtime/builtins.js` holds the built-in app registry (`window.VoidApps`). The **VOID SYSTEM** row is rendered from it before all layout rows and cannot be hidden or deleted by the customizer; `layout.json` may reference apps with `"action": "builtin:<id>"` or the legacy aliases (`customizer`, `settings`, `browser`, `files`). A leading `Fold apps` tile collapses the row to itself; the state is remembered in `localStorage` under `voidtv_system_folded`. See `docs/builtin-apps.md`.
- Sections with `"dynamic": "installed"` come from `TvBridge`; items launch by `pkg` or route by `action`. Actions with `input:HDMI*`, `input:AV`, and `input:select` invoke `TvBridge.switchInput()` which resolves passthrough inputs via `TvInputManager` and tunes via `TvContract.buildChannelUriForPassthroughInput` or opens the system input selector.
- Focus is drawn by `.focused` only. WebView moves real DOM focus onto a card after a tap and paints an amber outline of its own, so `updateFocus()` and `AgentAPI.tap()` call `blurDomFocus()` and `style.css` removes `:focus` outlines inside the launcher/customizer/file-manager containers. Do not reintroduce `outline` styling there.
- `README.md` and `docs/tv_launcher_architecture.md` describe intent (Kotlin host, WebSocket REPL, HDMI switching). Verify claims against the code before relying on them.

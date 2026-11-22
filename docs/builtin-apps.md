# Built-in App Architecture

VoidTV has two independent layers. Mixing them is what made the system tools
disappear when the "tools" row was deleted in the customizer.

```
layout.json (data)                builtins.js (shell)
├── favorites row                 ├── Edit Favorites
├── inputs row                    ├── Launcher Settings
├── all apps row                  ├── Web Browser
└── any user rows                 ├── File Manager
                                  ├── Input Sources
                                  ├── TV Settings
        │                         └── Reload Layout
        │                                 │
        └────────────► renderLayout ◄─────┘
                          │
                    focusMatrix / FocusEngine
                          │
                 executeAction -> built-in id
```

## 1. The registry

`assets/runtime/builtins.js` runs before `app.js` and publishes `window.VoidApps`:

```js
VoidApps.register({ id, label, icon, order, run })
VoidApps.all()      // sorted by order
VoidApps.get(id)
VoidApps.open(id)   // calls run() lazily, returns boolean
```

Rules:

- `id` is stable and is the contract with `layout.json`, `/state` and `/tap`.
- `run()` is called on demand and owns its transition. An app that shows an
  overlay must set `currentMode` and handle its own keys, exactly like
  `Customizer` and `FileManager` do.
- Registration must not touch the DOM or native bridge at load time. Everything
  resolves lazily so script order stays `builtins.js` → `app.js`.
- A missing or throwing `run()` fails soft: the launcher shows
  "App unavailable: <id>" instead of breaking the row.

## 2. Rendering

`renderLayout()` calls `renderBuiltins()` before the data-driven sections, so a
pinned **VOID SYSTEM** row is always the first row. It is built from
`VoidApps.all()`, never from `layout.json`:

- It cannot be hidden, reordered, renamed or deleted by the customizer.
- It participates in `focusMatrix`, so D-pad navigation and `updateFocus()`
  treat it like any other row.
- Cards carry `data-label` and `data-action="builtin:<id>"`, which is what
  `/tap` and `/state` use.
- A leading `Fold apps` tile (`data-action="fold:system"`) collapses the row to
  that one tile. The flag lives in `localStorage` as `voidtv_system_folded`, so
  it survives restarts. Folded, the built-in cards are not in the DOM at all;
  unfold with `/tap "VOID SYSTEM"` before tapping an app by label.

## 3. Action resolution

A layout item can reach an app three ways:

| Form | Example | Resolution |
| --- | --- | --- |
| Explicit | `"action": "builtin:files"` | `builtin:` prefix stripped, id opened |
| Legacy alias | `"action": "files"` | `BUILTIN_ALIASES` maps to `files` |
| Package | `"pkg": "com.netflix.ninja"` | `Android.launchApp` |

Legacy aliases (`customizer`, `settings`, `browser`, `files`, `favorites`, `inputs`) are
kept forever so existing device `layout.json` files keep working after upgrade.
Unknown actions fall through to a toast.

## 4. Adding a built-in

1. Add one `register({...})` call in `builtins.js` with the next `order` slot.
2. Implement `run()` against an existing overlay or native bridge method.
3. Rebuild (`./build.sh`) and install; assets are baked into the APK.

No manifest, resource or layout change is needed, and no `layout.json` migration
is required because the id is new and additive.

## 5. Agent & Remote Controller surface

Built-ins are ordinary cards, so they are addressable from both the control API and the PWA Remote Controller:

```bash
./live-push.sh state                      # includes action: "builtin:launcher-settings"
./live-push.sh tap "Launcher Settings"
./live-push.sh tap "VOID SYSTEM"          # unfolds system row if folded
```

The TV launcher HUD displays the live remote URL: `REMOTE: <TV_IP>:4444`. Any smartphone connected to the local network can open this address, access the **📱 APPS** tab, and launch built-in system tools or installed packages directly.

## 6. Non-goals

- No third-party installable apps. The registry is compile-time, in-process and
  has no remote loading path.
- No per-app state persistence. Built-ins are stateless entry points; the state
  they edit lives in `layout.json` and `localStorage`.
- Built-ins are not listed in the customizer's rows tab on purpose: that tab
  edits data rows only.

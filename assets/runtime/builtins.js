// VoidTV built-in app registry.
//
// Built-ins are shell features, not layout data: they are registered in code,
// rendered in a pinning row that layout.json cannot hide, reorder or delete, and
// invoked by stable id. A layout item can point at one with "action": "<id>".
//
// Contract: { id, label, icon, order, run() }
//   id     stable string used by layout.json, /state and AgentAPI
//   label  text shown on the card and used by /tap matching
//   icon   emoji or single character
//   order  ascending sort within the pinning row
//   run    invoked lazily on the UI thread; must open its own overlay/mode
(function () {
  const registry = new Map();

  function register(def) {
    if (!def || !def.id || typeof def.run !== 'function') {
      throw new Error('VoidApps.register needs { id, label, run }');
    }
    registry.set(def.id, Object.assign({ icon: '✦', order: 100 }, def));
  }

  function all() {
    return Array.from(registry.values()).sort((a, b) => a.order - b.order);
  }

  function get(id) {
    return registry.get(id) || null;
  }

  function open(id) {
    const app = get(id);
    if (!app) return false;
    try {
      app.run();
      return true;
    } catch (e) {
      return false;
    }
  }

  window.VoidApps = { register, all, get, open };

  // ---- Built-in definitions ------------------------------------------------

  register({
    id: 'favorites',
    label: 'Edit Favorites',
    icon: '⭐',
    order: 10,
    run: () => window.Customizer.open('favs')
  });

  register({
    id: 'launcher-settings',
    label: 'Launcher Settings',
    icon: '🎛️',
    order: 20,
    run: () => window.Customizer.open('rows')
  });

  register({
    id: 'remote-qr',
    label: 'Remote QR',
    icon: '📱',
    order: 25,
    run: () => {
      if (window.RemoteQROverlay) {
        window.RemoteQROverlay.open();
      } else {
        window.showToast && window.showToast('Remote QR unavailable');
      }
    }
  });

  register({
    id: 'browser',
    label: 'Web Browser',
    icon: '🌐',
    order: 30,
    run: () => {
      if (window.Android && window.Android.openBrowser) {
        window.Android.openBrowser('file:///android_asset/runtime/browser_home.html');
      } else {
        window.showToast && window.showToast('Browser unavailable');
      }
    }
  });

  register({
    id: 'files',
    label: 'File Manager',
    icon: '📁',
    order: 40,
    run: () => window.FileManager.open()
  });

  register({
    id: 'inputs',
    label: 'Input Sources',
    icon: '📺',
    order: 45,
    run: () => {
      if (window.Android && window.Android.switchInput) {
        window.Android.switchInput('select');
      } else {
        window.showToast && window.showToast('Inputs unavailable');
      }
    }
  });

  register({
    id: 'tv-settings',
    label: 'TV Settings',
    icon: '⚙',
    order: 50,
    run: () => {
      if (window.Android && window.Android.openSettings) {
        window.Android.openSettings();
      }
    }
  });

  register({
    id: 'reload',
    label: 'Reload Layout',
    icon: '⟳',
    order: 60,
    run: () => window.HotReload.reloadConfig(true)
  });
})();

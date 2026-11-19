// VoidTV Runtime & 2D Spatial Focus Engine
(function () {
  let focusMatrix = []; // 2D array of focusable DOM elements
  let currentRow = 0;
  let currentCol = 0;
  let installedApps = [];

  // Default layout configuration
  let config = {
    theme: {
      accent: "#00e5ff",
      glowColor: "rgba(0, 229, 255, 0.45)",
      focusScale: 1.08
    },
    sections: [
      {
        id: "favorites",
        title: "FAVORITE APPS",
        type: "cards",
        items: [
          { label: "YouTube", pkg: "com.google.android.youtube.tv", icon: "▶" },
          { label: "Netflix", pkg: "com.netflix.ninja", icon: "N" },
          { label: "Prime Video", pkg: "com.amazon.amazonvideo.livingroom", icon: "P" },
          { label: "Live TV", pkg: "com.apps.livetv", icon: "📺" },
          { label: "Settings", action: "settings", icon: "⚙" }
        ]
      },
      {
        id: "inputs",
        title: "INPUT SOURCES",
        type: "chips",
        items: [
          { label: "HDMI 1", action: "input:HDMI1" },
          { label: "HDMI 2", action: "input:HDMI2" },
          { label: "AV / Composite", action: "input:AV" }
        ]
      },
      {
        id: "all_apps",
        title: "INSTALLED APPLICATIONS",
        type: "cards",
        dynamic: "installed"
      }
    ]
  };

  // Clock & Telemetry updater
  function initClock() {
    function update() {
      const now = new Date();
      const hours = String(now.getHours()).padStart(2, '0');
      const mins = String(now.getMinutes()).padStart(2, '0');
      document.getElementById('hud-clock').textContent = `${hours}:${mins}`;

      const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      document.getElementById('hud-date').textContent = `${days[now.getDay()]}, ${months[now.getMonth()]} ${now.getDate()}`;

      if (window.Android && window.Android.getSystemTelemetry) {
        try {
          const stats = JSON.parse(window.Android.getSystemTelemetry());
          if (stats.jvm_used_mb) {
            document.getElementById('hud-ram').textContent = `RAM: ${stats.jvm_used_mb}MB`;
          }
          if (stats.device_model) {
            document.getElementById('hud-model').textContent = stats.device_model;
          }
        } catch (e) {}
      }
    }
    setInterval(update, 1000);
    update();
  }

  // Render UI rows
  function renderLayout() {
    const container = document.getElementById('tv-container');
    container.innerHTML = '';
    focusMatrix = [];

    // Query installed apps from Android bridge if available
    if (window.Android && window.Android.getInstalledApps) {
      try {
        installedApps = JSON.parse(window.Android.getInstalledApps());
      } catch (e) {
        installedApps = [];
      }
    }

    config.sections.forEach((section, sIdx) => {
      const secDiv = document.createElement('div');
      secDiv.className = 'section';

      const title = document.createElement('div');
      title.className = 'section-title';
      title.textContent = section.title;
      secDiv.appendChild(title);

      const row = document.createElement('div');
      row.className = section.type === 'cards' ? 'row-cards' : 'row-chips';

      let items = section.items || [];
      if (section.dynamic === 'installed') {
        items = installedApps.map(a => ({
          label: a.label,
          pkg: a.packageName,
          icon: a.label ? a.label.substring(0, 1).toUpperCase() : 'A'
        }));
      }

      const rowElements = [];
      items.forEach((item, iIdx) => {
        const el = document.createElement('div');
        if (section.type === 'cards') {
          el.className = 'card';
          el.innerHTML = `
            <div class="card-icon">${item.icon || '✦'}</div>
            <div class="card-title">${item.label}</div>
          `;
        } else {
          el.className = 'chip';
          el.textContent = item.label;
        }

        el.addEventListener('click', () => executeAction(item));
        row.appendChild(el);
        rowElements.push(el);
      });

      if (rowElements.length > 0) {
        secDiv.appendChild(row);
        container.appendChild(secDiv);
        focusMatrix.push(rowElements);
      }
    });

    updateFocus();
  }

  function executeAction(item) {
    if (item.action === 'settings') {
      if (window.Android && window.Android.openSettings) {
        window.Android.openSettings();
      } else {
        showToast("Open Settings");
      }
    } else if (item.pkg) {
      if (window.Android && window.Android.launchApp) {
        window.Android.launchApp(item.pkg);
      } else {
        showToast("Launching " + item.pkg);
      }
    } else {
      showToast("Selected: " + (item.label || item.action));
    }
  }

  function updateFocus() {
    document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));

    if (focusMatrix.length === 0) return;
    if (currentRow < 0) currentRow = 0;
    if (currentRow >= focusMatrix.length) currentRow = focusMatrix.length - 1;

    const row = focusMatrix[currentRow];
    if (currentCol < 0) currentCol = 0;
    if (currentCol >= row.length) currentCol = row.length - 1;

    const activeEl = row[currentCol];
    if (activeEl) {
      activeEl.classList.add('focused');
      activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
    }
  }

  function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2000);
  }

  // 2D Focus Navigation Engine
  window.FocusEngine = {
    onKey: function (key) {
      if (focusMatrix.length === 0) return;

      switch (key) {
        case 'UP':
          if (currentRow > 0) {
            currentRow--;
            currentCol = Math.min(currentCol, focusMatrix[currentRow].length - 1);
            updateFocus();
          }
          break;
        case 'DOWN':
          if (currentRow < focusMatrix.length - 1) {
            currentRow++;
            currentCol = Math.min(currentCol, focusMatrix[currentRow].length - 1);
            updateFocus();
          }
          break;
        case 'LEFT':
          if (currentCol > 0) {
            currentCol--;
            updateFocus();
          }
          break;
        case 'RIGHT':
          if (currentCol < focusMatrix[currentRow].length - 1) {
            currentCol++;
            updateFocus();
          }
          break;
        case 'SELECT':
          const activeEl = focusMatrix[currentRow][currentCol];
          if (activeEl) activeEl.click();
          break;
        case 'BACK':
          // Return focus to top row
          currentRow = 0;
          currentCol = 0;
          updateFocus();
          break;
      }
    }
  };

  // Live Hot-Reload Engine
  window.HotReload = {
    reloadConfig: function () {
      if (window.Android && window.Android.getConfigFile) {
        const json = window.Android.getConfigFile("layout.json");
        if (json && json.length > 0) {
          try {
            config = JSON.parse(json);
            if (config.theme) {
              if (config.theme.accent) {
                document.documentElement.style.setProperty('--accent', config.theme.accent);
              }
              if (config.theme.glowColor) {
                document.documentElement.style.setProperty('--accent-glow', config.theme.glowColor);
              }
            }
            renderLayout();
            showToast("Hot-Reload Applied");
            return;
          } catch (e) {}
        }
      }
      renderLayout();
      showToast("Config Refreshed");
    }
  };

  // Keyboard navigation fallback for testing in web browser
  window.addEventListener('keydown', (e) => {
    switch (e.key) {
      case 'ArrowUp': window.FocusEngine.onKey('UP'); break;
      case 'ArrowDown': window.FocusEngine.onKey('DOWN'); break;
      case 'ArrowLeft': window.FocusEngine.onKey('LEFT'); break;
      case 'ArrowRight': window.FocusEngine.onKey('RIGHT'); break;
      case 'Enter': window.FocusEngine.onKey('SELECT'); break;
      case 'Backspace': window.FocusEngine.onKey('BACK'); break;
    }
  });

  // Boot UI
  initClock();
  renderLayout();
})();

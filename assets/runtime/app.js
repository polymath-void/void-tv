// VoidTV Runtime & 2D Spatial Focus Engine with In-App Browser & File Manager
(function () {
  let focusMatrix = [];
  let currentRow = 0;
  let currentCol = 0;
  let installedApps = [];
  let currentMode = 'LAUNCHER'; // 'LAUNCHER' or 'FILE_MANAGER'

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
          { label: "Media Player", pkg: "com.apps.usbmediaplayer", icon: "📁" }
        ]
      },
      {
        id: "tools",
        title: "VOID SYSTEM TOOLS",
        type: "cards",
        items: [
          { label: "Web Browser", action: "browser", icon: "🌐" },
          { label: "File Manager", action: "files", icon: "📁" },
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
        title: "ALL APPLICATIONS",
        type: "cards",
        dynamic: "installed"
      }
    ]
  };

  // Clock & Telemetry
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
          if (stats.jvm_used_mb !== undefined) {
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

  // Render Home Launcher
  function renderLayout() {
    const container = document.getElementById('tv-container');
    container.innerHTML = '';
    focusMatrix = [];

    if (window.Android && window.Android.getInstalledApps) {
      try {
        installedApps = JSON.parse(window.Android.getInstalledApps());
      } catch (e) {
        installedApps = [];
      }
    }

    config.sections.forEach((section) => {
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
          icon: a.icon || (a.label ? a.label.substring(0, 1).toUpperCase() : '✦')
        }));
      } else {
        items.forEach(item => {
          if (item.pkg) {
            const found = installedApps.find(a => a.packageName === item.pkg);
            if (found && found.icon) item.icon = found.icon;
          }
        });
      }

      const rowElements = [];
      items.forEach((item) => {
        const el = document.createElement('div');
        if (section.type === 'cards') {
          el.className = 'card';
          let iconHtml = '';
          if (item.icon && item.icon.startsWith('data:image/')) {
            iconHtml = `<img class="card-icon-img" src="${item.icon}" alt="">`;
          } else {
            iconHtml = `<div class="card-icon-char">${item.icon || '✦'}</div>`;
          }
          el.innerHTML = `
            <div class="card-icon">${iconHtml}</div>
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

    if (currentMode === 'LAUNCHER') {
      updateFocus();
    }
  }

  function executeAction(item) {
    if (item.action === 'browser') {
      if (window.Android && window.Android.openBrowser) {
        window.Android.openBrowser("file:///android_asset/runtime/browser_home.html");
      } else {
        showToast("Opening Browser...");
      }
    } else if (item.action === 'files') {
      window.FileManager.open();
    } else if (item.action === 'settings') {
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

  // File Manager Engine
  window.FileManager = {
    currentPath: '/storage/emulated/0',
    items: [],
    fmMatrix: [], // 2D grid matrix of file elements (4 columns)
    fmRow: 0,
    fmCol: 0,

    open: function (targetPath) {
      currentMode = 'FILE_MANAGER';
      document.getElementById('fm-overlay').style.display = 'flex';
      this.currentPath = targetPath || '/storage/emulated/0';
      this.loadDirectory(this.currentPath);
    },

    close: function () {
      document.getElementById('fm-overlay').style.display = 'none';
      currentMode = 'LAUNCHER';
      updateFocus();
    },

    loadDirectory: function (path) {
      this.currentPath = path;
      document.getElementById('fm-path').textContent = path;
      const grid = document.getElementById('fm-grid');
      grid.innerHTML = '';
      this.fmMatrix = [];
      this.items = [];

      let raw = "[]";
      if (window.Android && window.Android.listDirectory) {
        raw = window.Android.listDirectory(path);
      }
      try {
        this.items = JSON.parse(raw);
      } catch (e) {
        this.items = [];
      }

      // Add parent directory navigation card if not at root
      if (this.currentPath !== '/storage/emulated/0' && this.currentPath !== '/') {
        this.items.unshift({
          name: ".. [Parent Directory]",
          path: this.getParentPath(this.currentPath),
          isDir: true,
          formattedSize: "Folder"
        });
      }

      const elements = [];
      this.items.forEach((item) => {
        const div = document.createElement('div');
        div.className = 'fm-item';

        let icon = '📄';
        if (item.isDir) {
          icon = item.name.startsWith('..') ? '⬆️' : '📁';
        } else {
          const ext = item.ext || '';
          if (['mp4', 'mkv', 'avi', 'mov'].includes(ext)) icon = '🎬';
          else if (['mp3', 'wav', 'flac', 'aac'].includes(ext)) icon = '🎵';
          else if (['jpg', 'jpeg', 'png', 'webp'].includes(ext)) icon = '🖼️';
          else if (['apk'].includes(ext)) icon = '📦';
          else if (['txt', 'json', 'log', 'sh', 'py'].includes(ext)) icon = '📝';
        }

        div.innerHTML = `
          <div class="fm-item-icon">${icon}</div>
          <div class="fm-item-details">
            <div class="fm-item-name">${item.name}</div>
            <div class="fm-item-size">${item.formattedSize || ''}</div>
          </div>
        `;

        div.addEventListener('click', () => {
          this.onItemClick(item);
        });

        grid.appendChild(div);
        elements.push(div);
      });

      // Construct 4-column 2D focus matrix
      const cols = 4;
      for (let i = 0; i < elements.length; i += cols) {
        this.fmMatrix.push(elements.slice(i, i + cols));
      }

      this.fmRow = 0;
      this.fmCol = 0;
      this.updateFmFocus();
    },

    getParentPath: function (path) {
      const idx = path.lastIndexOf('/');
      if (idx <= 0) return '/';
      return path.substring(0, idx);
    },

    onItemClick: function (item) {
      if (item.isDir) {
        this.loadDirectory(item.path);
      } else {
        if (window.Android && window.Android.openFile) {
          const ok = window.Android.openFile(item.path);
          if (!ok) showToast("Could not open " + item.name);
        } else {
          showToast("Opening file: " + item.name);
        }
      }
    },

    updateFmFocus: function () {
      document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));
      if (this.fmMatrix.length === 0) return;

      if (this.fmRow < 0) this.fmRow = 0;
      if (this.fmRow >= this.fmMatrix.length) this.fmRow = this.fmMatrix.length - 1;

      const row = this.fmMatrix[this.fmRow];
      if (this.fmCol < 0) this.fmCol = 0;
      if (this.fmCol >= row.length) this.fmCol = row.length - 1;

      const active = row[this.fmCol];
      if (active) {
        active.classList.add('focused');
        active.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    },

    onKey: function (key) {
      if (this.fmMatrix.length === 0) return;

      switch (key) {
        case 'UP':
          if (this.fmRow > 0) {
            this.fmRow--;
            this.fmCol = Math.min(this.fmCol, this.fmMatrix[this.fmRow].length - 1);
            this.updateFmFocus();
          }
          break;
        case 'DOWN':
          if (this.fmRow < this.fmMatrix.length - 1) {
            this.fmRow++;
            this.fmCol = Math.min(this.fmCol, this.fmMatrix[this.fmRow].length - 1);
            this.updateFmFocus();
          }
          break;
        case 'LEFT':
          if (this.fmCol > 0) {
            this.fmCol--;
            this.updateFmFocus();
          }
          break;
        case 'RIGHT':
          if (this.fmCol < this.fmMatrix[this.fmRow].length - 1) {
            this.fmCol++;
            this.updateFmFocus();
          }
          break;
        case 'SELECT':
          const active = this.fmMatrix[this.fmRow][this.fmCol];
          if (active) active.click();
          break;
        case 'BACK':
          if (this.currentPath !== '/storage/emulated/0' && this.currentPath !== '/') {
            this.loadDirectory(this.getParentPath(this.currentPath));
          } else {
            this.close();
          }
          break;
      }
    }
  };

  document.getElementById('fm-close-btn').addEventListener('click', () => {
    window.FileManager.close();
  });

  // Global D-Pad Dispatcher
  window.FocusEngine = {
    onKey: function (key) {
      if (currentMode === 'FILE_MANAGER') {
        window.FileManager.onKey(key);
        return;
      }

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

  // Keyboard navigation fallback
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

  initClock();
  renderLayout();
})();

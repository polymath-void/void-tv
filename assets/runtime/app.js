// VoidTV Runtime & 2D Spatial Focus Engine with In-App Browser, File Manager & Built-In Customizer
(function () {
  let focusMatrix = [];
  let currentRow = 0;
  let currentCol = 0;
  let installedApps = [];
  let currentMode = 'LAUNCHER'; // 'LAUNCHER', 'FILE_MANAGER', or 'CUSTOMIZER'

  const DEFAULT_CONFIG = {
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
          { label: "Dashboard Config", action: "customizer", icon: "🎛️" },
          { label: "Web Browser", action: "browser", icon: "🌐" },
          { label: "File Manager", action: "files", icon: "📁" },
          { label: "TV Settings", action: "settings", icon: "⚙" }
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

  let config = JSON.parse(JSON.stringify(DEFAULT_CONFIG));
  let ignoreReloadUntil = 0;

  function applyTheme() {
    if (config.theme) {
      if (config.theme.accent) {
        document.documentElement.style.setProperty('--accent', config.theme.accent);
      }
      if (config.theme.glowColor) {
        document.documentElement.style.setProperty('--accent-glow', config.theme.glowColor);
      }
      if (config.theme.focusScale) {
        document.documentElement.style.setProperty('--focus-scale', config.theme.focusScale);
      }
    }
  }

  function loadConfigFromStorage() {
    let json = "";
    if (window.Android && window.Android.getConfigFile) {
      json = window.Android.getConfigFile("layout.json");
    }
    if (!json || json.trim().length === 0) {
      try {
        json = localStorage.getItem("voidtv_layout_config") || "";
      } catch (e) {}
    }

    if (json && json.trim().length > 0) {
      try {
        const parsed = JSON.parse(json);
        if (parsed.sections && parsed.sections.length > 0) {
          config = parsed;
        }
      } catch (e) {}
    }

    applyTheme();
  }

  function saveConfigToStorage() {
    applyTheme();
    const str = JSON.stringify(config, null, 2);
    try {
      localStorage.setItem("voidtv_layout_config", str);
    } catch (e) {}
    // The FileObserver sees our own write; ignore that echo.
    ignoreReloadUntil = Date.now() + 1500;
    if (window.Android && window.Android.saveConfigFile) {
      window.Android.saveConfigFile("layout.json", str);
    }
  }

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

      if (window.Android && window.Android.getLocalIpAddress) {
        try {
          const ip = window.Android.getLocalIpAddress();
          if (ip && ip !== '127.0.0.1') {
            const el = document.getElementById('hud-remote');
            if (el) el.textContent = `REMOTE: ${ip}:4444`;
          }
        } catch (e) {}
      }
    }
    setInterval(update, 1000);
    update();
  }

  // Render Home Launcher
  const SYSTEM_FOLD_KEY = 'voidtv_system_folded';

  function systemFolded() {
    try { return localStorage.getItem(SYSTEM_FOLD_KEY) === '1'; } catch (e) { return false; }
  }

  function setSystemFolded(folded) {
    try { localStorage.setItem(SYSTEM_FOLD_KEY, folded ? '1' : '0'); } catch (e) {}
  }

  function renderBuiltins(container) {
    const apps = (window.VoidApps && window.VoidApps.all()) || [];
    if (apps.length === 0) return;
    const folded = systemFolded();

    const secDiv = document.createElement('div');
    secDiv.className = 'section';

    const title = document.createElement('div');
    title.className = 'section-title';
    title.textContent = 'VOID SYSTEM · ' + apps.length + ' APPS';
    secDiv.appendChild(title);

    const row = document.createElement('div');
    row.className = 'row-cards';

    const rowElements = [];

    // Fold control: the row collapses to this single tile.
    const foldTile = document.createElement('div');
    foldTile.className = 'card card-fold';
    foldTile.dataset.label = 'VOID SYSTEM';
    foldTile.dataset.action = 'fold:system';
    foldTile.innerHTML = `
      <div class="card-icon"><div class="card-icon-char">${folded ? '▸' : '▾'}</div></div>
      <div class="card-title">${folded ? 'Show apps' : 'Fold apps'}</div>
    `;
    foldTile.addEventListener('click', () => {
      setSystemFolded(!systemFolded());
      renderLayout();
      updateFocus();
    });
    row.appendChild(foldTile);
    rowElements.push(foldTile);

    if (folded) {
      secDiv.appendChild(row);
      container.appendChild(secDiv);
      focusMatrix.push(rowElements);
      return;
    }

    apps.forEach((app) => {
      const el = document.createElement('div');
      el.className = 'card';
      el.dataset.label = app.label;
      el.dataset.action = 'builtin:' + app.id;
      el.innerHTML = `
        <div class="card-icon"><div class="card-icon-char">${app.icon || '✦'}</div></div>
        <div class="card-title">${app.label}</div>
      `;
      el.addEventListener('click', () => executeBuiltin(app.id));
      row.appendChild(el);
      rowElements.push(el);
    });

    secDiv.appendChild(row);
    container.appendChild(secDiv);
    focusMatrix.push(rowElements);
  }

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

    renderBuiltins(container);

    config.sections.forEach((section) => {
      if (section.hidden) return; // Skip hidden rows

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
        if (item.pkg) el.dataset.pkg = item.pkg;
        if (item.action) el.dataset.action = item.action;
        if (item.label) el.dataset.label = item.label;

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

  // Legacy layout action names -> built-in ids. Keeps old layout.json files working.
  const BUILTIN_ALIASES = {
    customizer: 'launcher-settings',
    settings: 'tv-settings',
    browser: 'browser',
    files: 'files',
    favorites: 'favorites',
    inputs: 'inputs'
  };

  function executeBuiltin(id) {
    if (window.VoidApps && window.VoidApps.open(id)) return;
    showToast('App unavailable: ' + id);
  }

  function executeAction(item) {
    if (item.builtin) {
      executeBuiltin(item.builtin);
      return;
    }

    const action = item.action ? String(item.action) : '';
    if (action.startsWith('builtin:')) {
      executeBuiltin(action.slice('builtin:'.length));
      return;
    }
    if (action && BUILTIN_ALIASES[action]) {
      executeBuiltin(BUILTIN_ALIASES[action]);
      return;
    }

    if (action.startsWith('input:')) {
      const inputId = action.slice('input:'.length);
      if (window.Android && window.Android.switchInput) {
        window.Android.switchInput(inputId);
      } else {
        showToast("Switching input: " + (item.label || inputId));
      }
      return;
    }

    if (item.pkg) {
      if (window.Android && window.Android.launchApp) {
        window.Android.launchApp(item.pkg);
      } else {
        showToast("Launching " + item.pkg);
      }
    } else {
      showToast("Selected: " + (item.label || item.action));
    }
  }

  function toggleFavoriteDirect(pkg, label) {
    let favSection = config.sections.find(s => s.id === 'favorites');
    if (!favSection) {
      favSection = { id: "favorites", title: "FAVORITE APPS", type: "cards", items: [] };
      config.sections.unshift(favSection);
    }
    if (!favSection.items) favSection.items = [];

    const existingIdx = favSection.items.findIndex(it => it.pkg === pkg);
    if (existingIdx >= 0) {
      favSection.items.splice(existingIdx, 1);
      showToast("Removed from Favorites");
    } else {
      favSection.items.push({ label: label || pkg, pkg: pkg, icon: "▶" });
      showToast("Added " + (label || pkg) + " to Favorites ⭐");
    }

    saveConfigToStorage();
    renderLayout();
  }

  /** Keep the WebView's UA focus ring off cards; launcher focus is class-based. */
  function blurDomFocus() {
    const a = document.activeElement;
    if (a && a !== document.body && typeof a.blur === 'function') a.blur();
  }

  function updateFocus() {
    blurDomFocus();
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
  window.showToast = showToast;

  // ==========================================================
  // Dashboard & Favorites Customizer Engine
  // ==========================================================
  window.Customizer = {
    activeTab: 'favs',
    czMatrix: [],
    czRow: 0,
    czCol: 0,

    open: function (tab) {
      currentMode = 'CUSTOMIZER';
      document.getElementById('customizer-overlay').style.display = 'flex';
      this.switchTab(tab || 'favs');
    },

    close: function () {
      document.getElementById('customizer-overlay').style.display = 'none';
      currentMode = 'LAUNCHER';
      saveConfigToStorage();
      renderLayout();
      updateFocus();
    },

    switchTab: function (tab, keepBar) {
      this.activeTab = tab;
      document.querySelectorAll('.cz-tab').forEach(b => {
        b.classList.toggle('active', b.dataset.tab === tab);
      });

      const body = document.getElementById('customizer-body');
      body.innerHTML = '';
      this.czMatrix = [];

      // Row 0 is always the top tabs bar
      const tabElements = Array.from(document.querySelectorAll('.cz-tab, .customizer-close-btn'));
      this.czMatrix.push(tabElements);

      if (tab === 'favs') {
        this.renderFavsTab(body);
      } else if (tab === 'rows') {
        this.renderRowsTab(body);
      } else if (tab === 'theme') {
        this.renderThemeTab(body);
      } else if (tab === 'backup') {
        this.renderBackupTab(body);
      }

      if (keepBar) {
        // Stay on the tab bar so LEFT/RIGHT can keep walking the tabs.
        this.czRow = 0;
        this.czCol = Math.min(this.czCol, tabElements.length - 1);
      } else {
        this.czRow = 1;
        this.czCol = 0;
      }
      this.updateFocus();
    },

    /** Rebuild the active tab in place and keep the cursor on the same control. */
    refreshCurrentTab: function () {
      const row = this.czRow;
      const col = this.czCol;
      this.switchTab(this.activeTab, true);
      this.czRow = Math.min(row, this.czMatrix.length - 1);
      const line = this.czMatrix[this.czRow] || [];
      this.czCol = Math.min(col, Math.max(0, line.length - 1));
      this.updateFocus();
    },

    /** Moving onto a tab opens it; no SELECT needed. */
    autoActivateTab: function () {
      if (this.czRow !== 0) return;
      const el = this.czMatrix[0] ? this.czMatrix[0][this.czCol] : null;
      if (!el || !el.classList || !el.classList.contains('cz-tab')) return;
      const tab = el.dataset.tab;
      if (!tab || tab === this.activeTab) return;
      this.switchTab(tab, true);
    },

    renderFavsTab: function (container) {
      const favSection = config.sections.find(s => s.id === 'favorites');
      const favPkgs = favSection && favSection.items ? favSection.items.map(i => i.pkg) : [];

      const intro = document.createElement('div');
      intro.className = 'cz-section-intro';
      intro.innerHTML = `
        <div class="cz-subhead">Favorite Applications Dock</div>
        <div class="cz-subdesc">${favPkgs.length} Selected • Press [SELECT] to Toggle • Press [BACK] to Save & Exit</div>
      `;
      container.appendChild(intro);

      const grid = document.createElement('div');
      grid.className = 'cz-grid';

      let gridRow = [];
      installedApps.forEach((app) => {
        const isFav = favPkgs.includes(app.packageName);
        const card = document.createElement('div');
        card.className = `cz-card ${isFav ? 'is-fav' : ''}`;

        let iconSrc = app.icon || '';
        let iconHtml = iconSrc ? `<img class="cz-card-icon" src="${iconSrc}" alt="">` : `<div class="card-icon-char">✦</div>`;

        card.innerHTML = `
          ${iconHtml}
          <div class="cz-card-details">
            <div class="cz-card-name">${app.label}</div>
            <div class="cz-card-pkg">${app.packageName}</div>
          </div>
          <span class="${isFav ? 'cz-badge-fav' : 'cz-badge-add'}">${isFav ? '★ FAVORITE' : '☆ ADD'}</span>
        `;

        card.addEventListener('click', () => {
          this.toggleFavorite(app);
        });

        grid.appendChild(card);
        gridRow.push(card);

        if (gridRow.length === 4) {
          this.czMatrix.push(gridRow);
          gridRow = [];
        }
      });

      if (gridRow.length > 0) {
        this.czMatrix.push(gridRow);
      }
      container.appendChild(grid);
    },

    toggleFavorite: function (app) {
      toggleFavoriteDirect(app.packageName, app.label);
      this.renderFavsTabRebuild();
    },

    renderFavsTabRebuild: function () {
      this.refreshCurrentTab();
    },

    renderRowsTab: function (container) {
      const intro = document.createElement('div');
      intro.className = 'cz-section-intro';
      intro.innerHTML = `
        <div class="cz-subhead">Home Dashboard Rows & Ordering</div>
        <div class="cz-subdesc">Toggle Visibility & Reorder Rows with Remote Controls</div>
      `;
      container.appendChild(intro);

      const list = document.createElement('div');
      list.className = 'cz-rows-list';

      config.sections.forEach((sec, idx) => {
        const item = document.createElement('div');
        item.className = 'cz-row-item';

        const isHidden = !!sec.hidden;
        item.innerHTML = `
          <div class="cz-row-info">
            <div class="cz-row-title">${sec.title}</div>
            <div class="cz-row-type">ID: ${sec.id} • Type: ${sec.type}</div>
          </div>
          <div class="cz-row-controls">
            <button class="cz-btn cz-btn-toggle ${isHidden ? 'inactive' : 'active'}" data-action="row:${sec.id}:toggle">${isHidden ? '✗ HIDDEN' : '✓ VISIBLE'}</button>
            <button class="cz-btn" data-action="row:${sec.id}:up" ${idx === 0 ? 'disabled style="opacity:0.3"' : ''}>⬆ UP</button>
            <button class="cz-btn" data-action="row:${sec.id}:down" ${idx === config.sections.length - 1 ? 'disabled style="opacity:0.3"' : ''}>⬇ DOWN</button>
          </div>
        `;

        const btnToggle = item.querySelector('.cz-btn-toggle');
        const btnUp = item.querySelector('[data-action$=":up"]');
        const btnDown = item.querySelector('[data-action$=":down"]');

        btnToggle.dataset.label = `${sec.title} · ${isHidden ? 'hidden' : 'visible'}`;
        btnUp.dataset.label = `${sec.title} · up`;
        btnDown.dataset.label = `${sec.title} · down`;

        btnToggle.addEventListener('click', () => {
          sec.hidden = !sec.hidden;
          saveConfigToStorage();
          this.refreshCurrentTab();
        });

        btnUp.addEventListener('click', () => {
          if (idx > 0) {
            const temp = config.sections[idx - 1];
            config.sections[idx - 1] = config.sections[idx];
            config.sections[idx] = temp;
            saveConfigToStorage();
            this.refreshCurrentTab();
          }
        });

        btnDown.addEventListener('click', () => {
          if (idx < config.sections.length - 1) {
            const temp = config.sections[idx + 1];
            config.sections[idx + 1] = config.sections[idx];
            config.sections[idx] = temp;
            saveConfigToStorage();
            this.refreshCurrentTab();
          }
        });

        list.appendChild(item);
        this.czMatrix.push([btnToggle, btnUp, btnDown]);
      });

      container.appendChild(list);
    },

    renderThemeTab: function (container) {
      const themes = [
        { name: "Cyber Cyan", accent: "#00e5ff", glow: "rgba(0, 229, 255, 0.45)" },
        { name: "Neon Violet", accent: "#b388ff", glow: "rgba(179, 136, 255, 0.45)" },
        { name: "Matrix Emerald", accent: "#00e676", glow: "rgba(0, 230, 118, 0.45)" },
        { name: "Amber Gold", accent: "#ffd600", glow: "rgba(255, 214, 0, 0.45)" },
        { name: "Crimson Red", accent: "#ff1744", glow: "rgba(255, 23, 68, 0.45)" },
        { name: "Pure Mono", accent: "#ffffff", glow: "rgba(255, 255, 255, 0.45)" }
      ];

      const intro = document.createElement('div');
      intro.className = 'cz-section-intro';
      intro.innerHTML = `
        <div class="cz-subhead">Visual Theme & Accent Colors</div>
        <div class="cz-subdesc">Instant Real-Time Preview on TV Display</div>
      `;
      container.appendChild(intro);

      const grid = document.createElement('div');
      grid.className = 'cz-theme-grid';

      let row = [];
      themes.forEach((th) => {
        const isCurrent = config.theme && config.theme.accent === th.accent;
        const card = document.createElement('div');
        card.className = `cz-theme-card ${isCurrent ? 'active' : ''}`;
        card.innerHTML = `
          <div class="cz-color-circle" style="background:${th.accent}; color:${th.accent};"></div>
          <div class="cz-theme-details">
            <div class="cz-theme-name">${th.name}</div>
            <div class="cz-theme-hex">${th.accent}</div>
          </div>
        `;

        card.addEventListener('click', () => {
          config.theme = config.theme || {};
          config.theme.accent = th.accent;
          config.theme.glowColor = th.glow;
          saveConfigToStorage();
          showToast(`Applied ${th.name} Theme`);
          this.refreshCurrentTab();
        });

        grid.appendChild(card);
        row.push(card);
        if (row.length === 3) {
          this.czMatrix.push(row);
          row = [];
        }
      });
      if (row.length > 0) this.czMatrix.push(row);

      container.appendChild(grid);
    },

    renderBackupTab: function (container) {
      const intro = document.createElement('div');
      intro.className = 'cz-section-intro';
      intro.innerHTML = `
        <div class="cz-subhead">Configuration Backup & Reset</div>
        <div class="cz-subdesc">Direct Storage Sync to /sdcard/voidtv/layout.json</div>
      `;
      container.appendChild(intro);

      const box = document.createElement('div');
      box.className = 'cz-action-box';

      const rowReset = document.createElement('div');
      rowReset.className = 'cz-action-row';
      rowReset.innerHTML = `
        <div>
          <div style="font-size:15px; font-weight:700; color:#fff;">Reset to Default Layout</div>
          <div style="font-size:12px; color:#777;">Restore pristine layout, stock rows and standard favorites.</div>
        </div>
        <button class="cz-btn" style="border-color:#ff3366; color:#ff3366;">RESET DEFAULTS</button>
      `;
      const btnReset = rowReset.querySelector('button');
      btnReset.addEventListener('click', () => {
        config = JSON.parse(JSON.stringify(DEFAULT_CONFIG));
        saveConfigToStorage();
        showToast("Reset to Defaults");
        this.close();
      });

      const rowReload = document.createElement('div');
      rowReload.className = 'cz-action-row';
      rowReload.innerHTML = `
        <div>
          <div style="font-size:15px; font-weight:700; color:#fff;">Reload From Disk</div>
          <div style="font-size:12px; color:#777;">Force re-read from /sdcard/voidtv/layout.json.</div>
        </div>
        <button class="cz-btn">RELOAD DISK</button>
      `;
      const btnReload = rowReload.querySelector('button');
      btnReload.addEventListener('click', () => {
        loadConfigFromStorage();
        showToast("Reloaded from Disk");
        this.close();
      });

      box.appendChild(rowReset);
      box.appendChild(rowReload);
      container.appendChild(box);

      this.czMatrix.push([btnReset]);
      this.czMatrix.push([btnReload]);
    },

    updateFocus: function () {
      document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));

      if (this.czMatrix.length === 0) return;
      if (this.czRow < 0) this.czRow = 0;
      if (this.czRow >= this.czMatrix.length) this.czRow = this.czMatrix.length - 1;

      const row = this.czMatrix[this.czRow];
      if (!row || row.length === 0) return;
      if (this.czCol < 0) this.czCol = 0;
      if (this.czCol >= row.length) this.czCol = row.length - 1;

      const el = row[this.czCol];
      if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    },

    onKey: function (key) {
      if (this.czMatrix.length === 0) return;

      switch (key) {
        case 'UP':
          if (this.czRow > 0) {
            this.czRow--;
            if (this.czRow === 0) {
              // Enter the bar on the active tab, never on a neighbour.
              const bar = this.czMatrix[0] || [];
              const activeIdx = bar.findIndex(el => el.dataset && el.dataset.tab === this.activeTab);
              this.czCol = activeIdx >= 0 ? activeIdx : 0;
            } else {
              this.czCol = Math.min(this.czCol, this.czMatrix[this.czRow].length - 1);
            }
            this.updateFocus();
            this.autoActivateTab();
          }
          break;
        case 'DOWN':
          if (this.czRow < this.czMatrix.length - 1) {
            this.czRow++;
            this.czCol = this.czRow === 1 ? 0 : Math.min(this.czCol, this.czMatrix[this.czRow].length - 1);
            this.updateFocus();
          }
          break;
        case 'LEFT':
          if (this.czCol > 0) {
            this.czCol--;
            this.updateFocus();
            this.autoActivateTab();
          }
          break;
        case 'RIGHT':
          if (this.czRow < this.czMatrix.length && this.czCol < this.czMatrix[this.czRow].length - 1) {
            this.czCol++;
            this.updateFocus();
            this.autoActivateTab();
          }
          break;
        case 'SELECT':
          const activeEl = this.czMatrix[this.czRow] ? this.czMatrix[this.czRow][this.czCol] : null;
          if (!activeEl) break;
          if (this.czRow === 0 && activeEl.classList.contains('cz-tab')) {
            // Enter the tab content from the bar.
            this.switchTab(activeEl.dataset.tab);
          } else {
            activeEl.click();
          }
          break;
        case 'BACK':
          this.close();
          break;
      }
    }
  };

  // Wire Customizer Tabs
  document.querySelectorAll('.cz-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      window.Customizer.switchTab(btn.dataset.tab);
    });
  });

  document.getElementById('cz-close-btn').addEventListener('click', () => {
    window.Customizer.close();
  });

  // ==========================================================
  // File Manager Engine
  // ==========================================================
  window.FileManager = {
    currentPath: '/storage/emulated/0',
    items: [],
    fmMatrix: [],
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

      const allEntries = [];
      if (path !== '/' && path !== '/storage/emulated/0') {
        allEntries.push({
          name: '..',
          path: this.getParentPath(path),
          isDir: true,
          formattedSize: 'Parent Folder',
          icon: '↩'
        });
      }

      this.items.forEach(it => {
        let icon = '📄';
        if (it.isDir) {
          icon = '📁';
        } else {
          const ext = it.ext || '';
          if (['mp4', 'mkv', 'avi', 'mov', 'webm'].includes(ext)) icon = '🎬';
          else if (['mp3', 'flac', 'wav', 'aac', 'ogg'].includes(ext)) icon = '🎵';
          else if (['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext)) icon = '🖼️';
          else if (['apk'].includes(ext)) icon = '📦';
          else if (['json', 'txt', 'xml', 'log', 'md'].includes(ext)) icon = '📝';
        }
        it.icon = icon;
        allEntries.push(it);
      });

      let currentRowElements = [];
      allEntries.forEach((file) => {
        const card = document.createElement('div');
        card.className = 'fm-item';
        card.innerHTML = `
          <div class="fm-item-icon">${file.icon}</div>
          <div class="fm-item-details">
            <div class="fm-item-name">${file.name}</div>
            <div class="fm-item-size">${file.formattedSize}</div>
          </div>
        `;

        card.addEventListener('click', () => {
          if (file.isDir) {
            this.loadDirectory(file.path);
          } else {
            if (window.Android && window.Android.openFile) {
              const ok = window.Android.openFile(file.path);
              if (!ok) showToast("Cannot open file: " + file.name);
            }
          }
        });

        grid.appendChild(card);
        currentRowElements.push(card);

        if (currentRowElements.length === 4) {
          this.fmMatrix.push(currentRowElements);
          currentRowElements = [];
        }
      });

      if (currentRowElements.length > 0) {
        this.fmMatrix.push(currentRowElements);
      }

      this.fmRow = 0;
      this.fmCol = 0;
      this.updateFocus();
    },

    getParentPath: function (path) {
      const idx = path.lastIndexOf('/');
      if (idx <= 0) return '/';
      return path.substring(0, idx);
    },

    updateFocus: function () {
      document.querySelectorAll('.focused').forEach(el => el.classList.remove('focused'));

      if (this.fmMatrix.length === 0) return;
      if (this.fmRow < 0) this.fmRow = 0;
      if (this.fmRow >= this.fmMatrix.length) this.fmRow = this.fmMatrix.length - 1;

      const row = this.fmMatrix[this.fmRow];
      if (!row || row.length === 0) return;
      if (this.fmCol < 0) this.fmCol = 0;
      if (this.fmCol >= row.length) this.fmCol = row.length - 1;

      const el = row[this.fmCol];
      if (el) {
        el.classList.add('focused');
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    },

    onKey: function (key) {
      if (this.fmMatrix.length === 0) {
        if (key === 'BACK') this.close();
        return;
      }

      switch (key) {
        case 'UP':
          if (this.fmRow > 0) {
            this.fmRow--;
            this.fmCol = Math.min(this.fmCol, this.fmMatrix[this.fmRow].length - 1);
            this.updateFocus();
          }
          break;
        case 'DOWN':
          if (this.fmRow < this.fmMatrix.length - 1) {
            this.fmRow++;
            this.fmCol = Math.min(this.fmCol, this.fmMatrix[this.fmRow].length - 1);
            this.updateFocus();
          }
          break;
        case 'LEFT':
          if (this.fmCol > 0) {
            this.fmCol--;
            this.updateFocus();
          }
          break;
        case 'RIGHT':
          if (this.fmRow < this.fmMatrix.length && this.fmCol < this.fmMatrix[this.fmRow].length - 1) {
            this.fmCol++;
            this.updateFocus();
          }
          break;
        case 'SELECT':
          const el = this.fmMatrix[this.fmRow] ? this.fmMatrix[this.fmRow][this.fmCol] : null;
          if (el) el.click();
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

  // ==========================================================
  // Remote Controller QR Code Overlay
  // ==========================================================
  const RemoteQROverlay = {
    open: function () {
      let ip = '127.0.0.1';
      if (window.Android && window.Android.getLocalIpAddress) {
        ip = window.Android.getLocalIpAddress() || '127.0.0.1';
      }
      const remoteUrl = 'http://' + ip + ':4444/';

      const isA11y = (window.Android && window.Android.isAccessibilityServiceEnabled) ? window.Android.isAccessibilityServiceEnabled() : false;
      let modal = document.getElementById('remote-qr-modal');
      if (!modal) {
        modal = document.createElement('div');
        modal.id = 'remote-qr-modal';
        modal.className = 'remote-qr-modal-backdrop';
        modal.innerHTML = `
          <div class="remote-qr-card">
            <div class="remote-qr-header">
              <span class="remote-qr-icon">📱</span>
              <div class="remote-qr-title">VOIDTV REMOTE CONTROLLER</div>
            </div>
            <div class="remote-qr-desc">Scan with your smartphone camera to connect instantly</div>
            <div id="remote-qr-container" class="remote-qr-canvas-box"></div>
            <div class="remote-qr-url">${remoteUrl}</div>
            <div id="remote-qr-status" class="remote-qr-instructions" style="font-weight: 600; margin-top: 4px;"></div>
            <div id="remote-qr-actions" style="display: flex; flex-direction: column; width: 100%; gap: 8px; margin-top: 10px;">
              <button id="remote-qr-a11y-btn" class="remote-qr-btn" style="display: none; background: rgba(255, 179, 0, 0.2); border-color: #FFB300; color: #FFD54F;">⚙ ENABLE IN TV SETTINGS</button>
              <button id="remote-qr-close-btn" class="remote-qr-btn focused" data-action="close_remote_qr">✕ CLOSE (BACK / ENTER)</button>
            </div>
          </div>
        `;
        document.body.appendChild(modal);

        document.getElementById('remote-qr-close-btn').addEventListener('click', () => {
          RemoteQROverlay.close();
        });

        document.getElementById('remote-qr-a11y-btn').addEventListener('click', () => {
          if (window.Android && window.Android.openAccessibilitySettings) {
            window.Android.openAccessibilitySettings();
          }
        });
      } else {
        const urlEl = modal.querySelector('.remote-qr-url');
        if (urlEl) urlEl.textContent = remoteUrl;
      }

      const statusEl = document.getElementById('remote-qr-status');
      const a11yBtn = document.getElementById('remote-qr-a11y-btn');
      if (statusEl && a11yBtn) {
        if (isA11y) {
          statusEl.textContent = '✓ System-Wide Remote Control: ACTIVE';
          statusEl.style.color = '#00E5FF';
          a11yBtn.style.display = 'none';
        } else {
          statusEl.textContent = '⚠ System Remote: INACTIVE (Enable in TV Settings for YouTube / Netflix control)';
          statusEl.style.color = '#FFB300';
          a11yBtn.style.display = 'block';
        }
      }

      const container = document.getElementById('remote-qr-container');
      if (container) {
        container.innerHTML = '';
        if (window.QRCode) {
          try {
            new QRCode(container, {
              text: remoteUrl,
              width: 220,
              height: 220,
              colorDark: "#000000",
              colorLight: "#FFFFFF",
              correctLevel: QRCode.CorrectLevel.M
            });
          } catch (e) {
            container.textContent = 'QR Code generation error: ' + e.message;
          }
        } else {
          container.textContent = 'QRCode library loading...';
        }
      }

      modal.style.display = 'flex';
      currentMode = 'REMOTE_QR';
      const btn = document.getElementById('remote-qr-close-btn');
      if (btn) btn.focus();
    },

    close: function () {
      const modal = document.getElementById('remote-qr-modal');
      if (modal) modal.style.display = 'none';
      currentMode = 'LAUNCHER';
      updateFocus();
    }
  };
  window.RemoteQROverlay = RemoteQROverlay;

  // ==========================================================
  // Global Focus & Remote Navigation Engine
  // ==========================================================
  window.FocusEngine = {
    onKey: function (key) {
      if (currentMode === 'REMOTE_QR') {
        if (key === 'BACK') {
          window.RemoteQROverlay.close();
          return;
        }
        const closeBtn = document.getElementById('remote-qr-close-btn');
        const a11yBtn = document.getElementById('remote-qr-a11y-btn');
        if (key === 'UP' || key === 'DOWN') {
          if (a11yBtn && a11yBtn.style.display !== 'none' && closeBtn) {
            if (document.activeElement === closeBtn) {
              a11yBtn.focus();
            } else {
              closeBtn.focus();
            }
          }
          return;
        }
        if (key === 'SELECT' || key === 'ENTER') {
          if (document.activeElement && typeof document.activeElement.click === 'function') {
            document.activeElement.click();
          } else {
            window.RemoteQROverlay.close();
          }
          return;
        }
        return;
      }

      if (currentMode === 'CUSTOMIZER') {
        window.Customizer.onKey(key);
        return;
      }

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
          const activeEl = focusMatrix[currentRow] ? focusMatrix[currentRow][currentCol] : null;
          if (activeEl) activeEl.click();
          break;
        case 'BACK':
          currentRow = 0;
          currentCol = 0;
          updateFocus();
          break;
        case 'MENU':
          // Remote MENU button: If on an app card, toggle favorite! Else open customizer!
          const focusedCard = focusMatrix[currentRow] ? focusMatrix[currentRow][currentCol] : null;
          if (focusedCard && focusedCard.dataset && focusedCard.dataset.pkg) {
            toggleFavoriteDirect(focusedCard.dataset.pkg, focusedCard.dataset.label);
          } else {
            window.Customizer.open('favs');
          }
          break;
      }
    }
  };

  // Live Hot-Reload Engine
  window.HotReload = {
    reloadConfig: function (force) {
      // Our own save echoes through the FileObserver; a mid-write read would
      // swap in a stale config and orphan the rendered tabs' section objects.
      if (!force && Date.now() < ignoreReloadUntil) return;
      // Never replace config under an open overlay; it re-renders on close.
      if (currentMode !== 'LAUNCHER') return;
      const before = JSON.stringify(config);
      loadConfigFromStorage();
      if (!force && JSON.stringify(config) === before) return;
      renderLayout();
      updateFocus();
      showToast("Layout Refreshed ⟳");
    }
  };

  // ==========================================================
  // Agent Control Surface (drives :4444 /state, /key, /tap)
  // ==========================================================
  const AGENT_SELECTOR = '.card, .chip, .cz-tab, .cz-card, .cz-theme-card, .cz-btn, .cz-action-row button, .fm-item, .fm-close-btn, .customizer-close-btn';

  function agentVisible(el) {
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  function agentLabel(el) {
    if (el.dataset && el.dataset.label) return String(el.dataset.label).trim();
    return (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 48);
  }

  function agentRoot() {
    if (currentMode === 'REMOTE_QR') return document.getElementById('remote-qr-modal');
    if (currentMode === 'CUSTOMIZER') return document.getElementById('customizer-overlay');
    if (currentMode === 'FILE_MANAGER') return document.getElementById('fm-overlay');
    return document.getElementById('tv-container');
  }

  function agentElements() {
    const root = agentRoot();
    if (!root) return [];
    return Array.from(root.querySelectorAll(AGENT_SELECTOR)).filter(agentVisible);
  }

  function agentCursor() {
    if (currentMode === 'CUSTOMIZER' && window.Customizer) {
      return { row: window.Customizer.czRow, col: window.Customizer.czCol };
    }
    if (currentMode === 'FILE_MANAGER' && window.FileManager) {
      return { row: window.FileManager.fmRow, col: window.FileManager.fmCol };
    }
    return { row: currentRow, col: currentCol };
  }

  window.AgentAPI = {
    state: function () {
      const items = [];
      agentElements().forEach((el) => {
        if (items.length >= 80) return;
        const r = el.getBoundingClientRect();
        items.push({
          label: agentLabel(el),
          action: el.dataset.action || el.dataset.pkg || '',
          focused: el.classList.contains('focused'),
          x: Math.round(r.left + r.width / 2),
          y: Math.round(r.top + r.height / 2)
        });
      });
      const out = {
        mode: currentMode,
        focus: agentCursor(),
        count: items.length,
        items: items
      };
      if (currentMode === 'CUSTOMIZER') out.tab = window.Customizer.activeTab;
      return out;
    },

    tap: function (label) {
      const want = String(label || '').trim().toLowerCase();
      if (!want) return { ok: false, error: 'empty label' };
      const els = agentElements();
      const target = els.find((el) => agentLabel(el).toLowerCase() === want) ||
                     els.find((el) => agentLabel(el).toLowerCase().includes(want));
      if (!target) return { ok: false, error: 'no match', label: label };
      target.click();
      blurDomFocus();
      return { ok: true, label: agentLabel(target) };
    },

    key: function (key) {
      const k = String(key || '').toUpperCase();
      window.FocusEngine.onKey(k);
      return { ok: true, key: k };
    }
  };

  // Keyboard navigation fallback
  window.addEventListener('keydown', (e) => {
    switch (e.key) {
      case 'ArrowUp': e.preventDefault(); window.FocusEngine.onKey('UP'); break;
      case 'ArrowDown': e.preventDefault(); window.FocusEngine.onKey('DOWN'); break;
      case 'ArrowLeft': e.preventDefault(); window.FocusEngine.onKey('LEFT'); break;
      case 'ArrowRight': e.preventDefault(); window.FocusEngine.onKey('RIGHT'); break;
      case 'Enter': e.preventDefault(); window.FocusEngine.onKey('SELECT'); break;
      case 'Backspace': e.preventDefault(); window.FocusEngine.onKey('BACK'); break;
      case 'm':
      case 'M':
      case 'ContextMenu':
        e.preventDefault();
        window.FocusEngine.onKey('MENU');
        break;
    }
  });

  initClock();
  loadConfigFromStorage();
  renderLayout();

  // Start on the first user row; the pinned VOID SYSTEM row stays one press up.
  if (currentMode === 'LAUNCHER' && focusMatrix.length > 1) {
    currentRow = 1;
    currentCol = 0;
    updateFocus();
  }
})();

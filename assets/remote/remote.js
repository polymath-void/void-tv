(function () {
  'use strict';

  // --- Haptics Helper ---
  function vibrate(ms) {
    if (navigator.vibrate) {
      try { navigator.vibrate(ms || 15); } catch (e) {}
    }
  }

  // --- Toast Notification ---
  const toastEl = document.getElementById('toast');
  let toastTimer = null;
  function showToast(msg) {
    if (!toastEl) return;
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastEl.classList.remove('show');
    }, 1800);
  }

  // --- Network Dispatchers ---
  async function apiGet(endpoint, params) {
    const url = new URL(endpoint, window.location.origin);
    if (params) {
      Object.keys(params).forEach((k) => url.searchParams.set(k, params[k]));
    }
    try {
      const res = await fetch(url.toString(), { cache: 'no-store' });
      return await res.json();
    } catch (e) {
      return { ok: false, error: e.message };
    }
  }

  async function sendKey(keyName) {
    vibrate(15);
    const res = await apiGet('/key', { k: keyName });
    if (!res.ok) {
      // fallback or error
    }
    return res;
  }

  async function sendText(text, openInBrowser) {
    if (!text) return;
    vibrate(20);
    if (openInBrowser) {
      showToast('Opening in TV Browser...');
      await apiGet('/open', { url: text });
    } else {
      showToast('Sent to TV: ' + text);
      await apiGet('/text', { t: text });
    }
  }

  async function sendVolume(action) {
    vibrate(20);
    showToast('Volume: ' + action);
    return await apiGet('/volume', { v: action });
  }

  async function sendLaunch(pkg, label) {
    vibrate(30);
    showToast('Launching ' + (label || pkg) + '...');
    return await apiGet('/launch', { pkg: pkg });
  }

  async function sendInput(inputId) {
    vibrate(25);
    showToast('Switching to ' + inputId + '...');
    return await apiGet('/input', { id: inputId });
  }

  async function sendMouse(dx, dy, click) {
    return await apiGet('/mouse', { dx: Math.round(dx || 0), dy: Math.round(dy || 0), click: click ? 'true' : 'false' });
  }

  // --- PWA Registration & Install Banner ---
  let deferredPrompt = null;
  const btnInstall = document.getElementById('btn-install');

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js', { scope: '/' })
        .then((reg) => console.log('ServiceWorker registered:', reg.scope))
        .catch((err) => console.log('ServiceWorker failed:', err));
    });
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    if (btnInstall) btnInstall.style.display = 'inline-block';
  });

  if (btnInstall) {
    btnInstall.addEventListener('click', async () => {
      vibrate(25);
      if (deferredPrompt) {
        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === 'accepted') {
          btnInstall.style.display = 'none';
        }
        deferredPrompt = null;
      } else {
        showToast('To install: Tap Share -> Add to Home Screen');
      }
    });
  }

  // --- Tab Navigation ---
  const tabButtons = document.querySelectorAll('.tab-btn');
  const panels = document.querySelectorAll('.panel');

  function switchTab(tabId) {
    vibrate(10);
    tabButtons.forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tab === tabId);
    });
    panels.forEach((p) => {
      p.classList.toggle('active', p.id === 'panel-' + tabId);
    });
    if (tabId === 'apps') {
      loadApps();
    }
  }

  tabButtons.forEach((btn) => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  // --- Key Buttons Binding ---
  document.querySelectorAll('[data-key]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const k = btn.dataset.key;
      if (k === 'VOL_UP' || k === 'VOL_DOWN' || k === 'MUTE') {
        sendVolume(k);
      } else {
        sendKey(k);
      }
    });
  });

  // Power Button
  const btnPower = document.getElementById('btn-power');
  if (btnPower) {
    btnPower.addEventListener('click', () => sendKey('POWER'));
  }

  // Quick Text Buttons
  document.querySelectorAll('.quick-text').forEach((btn) => {
    btn.addEventListener('click', () => {
      vibrate(15);
      const text = btn.dataset.text;
      const input = document.getElementById('kb-text-input');
      if (input) {
        input.value = (input.value || '') + text;
        input.focus();
      }
    });
  });

  // --- Keyboard Tab Handling ---
  const kbInput = document.getElementById('kb-text-input');
  const kbBtnClear = document.getElementById('kb-btn-clear');
  const kbBtnSend = document.getElementById('kb-btn-send');
  const kbBtnBrowser = document.getElementById('kb-btn-browser');

  if (kbBtnClear && kbInput) {
    kbBtnClear.addEventListener('click', () => {
      kbInput.value = '';
      kbInput.focus();
    });
  }

  if (kbBtnSend && kbInput) {
    kbBtnSend.addEventListener('click', () => {
      const val = kbInput.value.trim();
      if (val) {
        sendText(val, false);
      }
    });
  }

  if (kbBtnBrowser && kbInput) {
    kbBtnBrowser.addEventListener('click', () => {
      const val = kbInput.value.trim();
      if (val) {
        sendText(val, true);
      }
    });
  }

  if (kbInput) {
    kbInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        const val = kbInput.value.trim();
        if (val) sendText(val, false);
      }
    });
  }

  // --- Real-Time Keystroke Mode ---
  const toggleLive = document.getElementById('kb-toggle-live');
  const liveContainer = document.getElementById('live-input-container');
  const liveCapturer = document.getElementById('live-key-capturer');

  if (toggleLive && liveContainer && liveCapturer) {
    toggleLive.addEventListener('change', () => {
      vibrate(15);
      if (toggleLive.checked) {
        liveContainer.style.display = 'flex';
        liveCapturer.focus();
        showToast('Live typing mode active');
      } else {
        liveContainer.style.display = 'none';
      }
    });

    liveCapturer.addEventListener('keydown', (e) => {
      let key = null;
      if (e.key === 'Backspace') key = 'BACKSPACE';
      else if (e.key === 'Enter') key = 'ENTER';
      else if (e.key === ' ') key = 'SPACE';
      else if (e.key === 'ArrowUp') key = 'UP';
      else if (e.key === 'ArrowDown') key = 'DOWN';
      else if (e.key === 'ArrowLeft') key = 'LEFT';
      else if (e.key === 'ArrowRight') key = 'RIGHT';
      else if (e.key.length === 1) {
        key = e.key.toUpperCase();
      }

      if (key) {
        sendKey(key);
      }
    });

    liveCapturer.addEventListener('input', () => {
      liveCapturer.value = '';
    });
  }

  // --- Browser Touchpad ---
  const touchpadSurface = document.getElementById('touchpad-surface');
  let tpStartX = 0;
  let tpStartY = 0;
  let tpLastX = 0;
  let tpLastY = 0;
  let tpMoved = false;
  let tpLastSend = 0;

  if (touchpadSurface) {
    touchpadSurface.addEventListener('touchstart', (e) => {
      if (e.touches.length === 1) {
        const t = e.touches[0];
        tpStartX = t.clientX;
        tpStartY = t.clientY;
        tpLastX = t.clientX;
        tpLastY = t.clientY;
        tpMoved = false;
      }
    }, { passive: true });

    touchpadSurface.addEventListener('touchmove', (e) => {
      if (e.touches.length === 1) {
        const t = e.touches[0];
        const dx = (t.clientX - tpLastX) * 1.5;
        const dy = (t.clientY - tpLastY) * 1.5;
        tpLastX = t.clientX;
        tpLastY = t.clientY;

        if (Math.abs(t.clientX - tpStartX) > 4 || Math.abs(t.clientY - tpStartY) > 4) {
          tpMoved = true;
        }

        const now = Date.now();
        if (now - tpLastSend > 30) {
          tpLastSend = now;
          sendMouse(dx, dy, false);
        }
      }
    }, { passive: true });

    touchpadSurface.addEventListener('touchend', (e) => {
      if (!tpMoved) {
        // Tap without movement = Click
        vibrate(20);
        sendMouse(0, 0, true);
        showToast('Click');
      }
    });
  }

  const tpBtnClick = document.getElementById('tp-btn-click');
  if (tpBtnClick) {
    tpBtnClick.addEventListener('click', () => {
      vibrate(20);
      sendMouse(0, 0, true);
    });
  }
  const tpBtnScrollUp = document.getElementById('tp-btn-scroll-up');
  if (tpBtnScrollUp) {
    tpBtnScrollUp.addEventListener('click', () => sendKey('PAGE_UP'));
  }
  const tpBtnScrollDown = document.getElementById('tp-btn-scroll-down');
  if (tpBtnScrollDown) {
    tpBtnScrollDown.addEventListener('click', () => sendKey('PAGE_DOWN'));
  }

  // Browser Toolbar Buttons
  document.getElementById('tp-btn-back')?.addEventListener('click', () => sendKey('BACK'));
  document.getElementById('tp-btn-fwd')?.addEventListener('click', () => apiGet('/tap', { label: 'FWD' }));
  document.getElementById('tp-btn-home')?.addEventListener('click', () => apiGet('/tap', { label: 'HOME' }));
  document.getElementById('tp-btn-reload')?.addEventListener('click', () => apiGet('/tap', { label: 'RELOAD' }));
  document.getElementById('tp-btn-search')?.addEventListener('click', () => apiGet('/tap', { label: 'SEARCH' }));
  document.getElementById('tp-btn-exit')?.addEventListener('click', () => apiGet('/tap', { label: 'EXIT' }));

  // --- TV Apps Loader ---
  const appsGrid = document.getElementById('apps-grid');
  const appsFilterInput = document.getElementById('apps-filter-input');
  const appsBtnRefresh = document.getElementById('apps-btn-refresh');
  let installedApps = [];

  async function loadApps() {
    if (!appsGrid) return;
    appsGrid.innerHTML = '<div class="apps-loading">Loading TV apps...</div>';
    try {
      const res = await fetch('/apps', { cache: 'no-store' });
      installedApps = await res.json();
      renderApps(installedApps);
    } catch (e) {
      appsGrid.innerHTML = '<div class="apps-loading">Failed to load apps: ' + e.message + '</div>';
    }
  }

  function renderApps(list) {
    if (!appsGrid) return;
    appsGrid.innerHTML = '';
    const query = (appsFilterInput ? appsFilterInput.value : '').trim().toLowerCase();
    const filtered = query
      ? list.filter((a) => (a.label || '').toLowerCase().includes(query) || (a.packageName || '').toLowerCase().includes(query))
      : list;

    if (filtered.length === 0) {
      appsGrid.innerHTML = '<div class="apps-loading">No apps found matching query</div>';
      return;
    }

    filtered.forEach((app) => {
      const card = document.createElement('div');
      card.className = 'app-card';
      const img = document.createElement('img');
      img.className = 'app-icon';
      img.src = app.icon || '/icon-192.png';
      img.alt = app.label || 'App';
      const lbl = document.createElement('span');
      lbl.className = 'app-label';
      lbl.textContent = app.label || app.packageName;

      card.appendChild(img);
      card.appendChild(lbl);
      card.addEventListener('click', () => sendLaunch(app.packageName, app.label));
      appsGrid.appendChild(card);
    });
  }

  if (appsFilterInput) {
    appsFilterInput.addEventListener('input', () => renderApps(installedApps));
  }
  if (appsBtnRefresh) {
    appsBtnRefresh.addEventListener('click', () => {
      vibrate(15);
      loadApps();
    });
  }

  // --- Inputs Panel ---
  document.querySelectorAll('[data-input]').forEach((card) => {
    card.addEventListener('click', () => {
      sendInput(card.dataset.input);
    });
  });

  // --- Background State Polling ---
  const connDot = document.getElementById('conn-dot');
  const connText = document.getElementById('conn-text');
  const tvModeBadge = document.getElementById('tv-mode-badge');

  async function pollState() {
    try {
      const res = await fetch('/state', { cache: 'no-store', signal: AbortSignal.timeout(2000) });
      const data = await res.json();
      if (connDot) {
        connDot.className = 'pulse-dot';
      }
      if (connText) connText.textContent = 'TV ONLINE';
      if (tvModeBadge && data.mode) {
        tvModeBadge.textContent = data.mode;
      }
    } catch (e) {
      if (connDot) {
        connDot.className = 'pulse-dot offline';
      }
      if (connText) connText.textContent = 'CONNECTING...';
    }
  }

  setInterval(pollState, 1500);
  pollState();
})();

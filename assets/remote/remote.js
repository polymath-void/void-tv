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

  async function sendMouse(dx, dy, click, scroll, action) {
    const params = { dx: Math.round(dx || 0), dy: Math.round(dy || 0) };
    if (click) params.click = 'true';
    if (scroll) params.scroll = 'true';
    if (action) params.action = action;
    return await apiGet('/mouse', params);
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

    // Handle physical / desktop keyboard navigation keys
    liveCapturer.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        sendKey('ENTER');
      } else if (e.key === 'Backspace' && e.keyCode !== 229) {
        sendKey('BACKSPACE');
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        sendKey('UP');
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        sendKey('DOWN');
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        sendKey('LEFT');
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        sendKey('RIGHT');
      } else if (e.key && e.key.length === 1 && e.keyCode !== 229) {
        sendKey(e.key === ' ' ? 'SPACE' : e.key.toUpperCase());
      }
    });

    // Handle mobile soft keyboard (Android Gboard, iOS virtual keyboard IME)
    liveCapturer.addEventListener('input', (e) => {
      const inputType = e.inputType || '';
      if (inputType === 'deleteContentBackward') {
        sendKey('BACKSPACE');
      } else if (inputType === 'insertLineBreak') {
        sendKey('ENTER');
      } else if (e.data) {
        for (const ch of e.data) {
          sendKey(ch === ' ' ? 'SPACE' : ch.toUpperCase());
        }
      } else if (liveCapturer.value) {
        const val = liveCapturer.value;
        for (const ch of val) {
          sendKey(ch === ' ' ? 'SPACE' : ch.toUpperCase());
        }
      }
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
  let tpLastTapTime = 0;
  let isDoubleTapScroll = false;
  let isHolding = false;
  let holdTimer = null;
  let tpTwoFingerLastY = 0;
  let tpTwoFingerLastX = 0;

  if (touchpadSurface) {
    touchpadSurface.addEventListener('touchstart', (e) => {
      const now = Date.now();
      if (e.touches.length === 2) {
        clearTimeout(holdTimer);
        if (isHolding) {
          isHolding = false;
          sendMouse(0, 0, false, false, 'up');
        }
        tpTwoFingerLastY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
        tpTwoFingerLastX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        return;
      }

      if (e.touches.length === 1) {
        const t = e.touches[0];
        tpStartX = t.clientX;
        tpStartY = t.clientY;
        tpLastX = t.clientX;
        tpLastY = t.clientY;
        tpMoved = false;

        if (now - tpLastTapTime < 320) {
          isDoubleTapScroll = true;
          vibrate(20);
          showToast('Double-tap scroll active');
        } else {
          isDoubleTapScroll = false;
        }
        tpLastTapTime = now;

        clearTimeout(holdTimer);
        holdTimer = setTimeout(() => {
          if (!tpMoved && !isDoubleTapScroll) {
            isHolding = true;
            vibrate(35);
            showToast('Hold active (dragging)');
            sendMouse(0, 0, false, false, 'down');
          }
        }, 320);
      }
    }, { passive: false });

    touchpadSurface.addEventListener('touchmove', (e) => {
      e.preventDefault();
      const now = Date.now();

      // 1. Two-finger smooth scrolling
      if (e.touches.length === 2) {
        clearTimeout(holdTimer);
        const currentY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
        const currentX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const dy = (currentY - tpTwoFingerLastY) * 2.2;
        const dx = (currentX - tpTwoFingerLastX) * 2.2;
        tpTwoFingerLastY = currentY;
        tpTwoFingerLastX = currentX;

        if (now - tpLastSend > 25) {
          tpLastSend = now;
          sendMouse(-dx, -dy, false, true, 'scroll');
        }
        return;
      }

      // 2. Single finger movement / double-tap scroll / tap-and-hold drag
      if (e.touches.length === 1) {
        const t = e.touches[0];
        const dx = (t.clientX - tpLastX) * 1.5;
        const dy = (t.clientY - tpLastY) * 1.5;
        tpLastX = t.clientX;
        tpLastY = t.clientY;

        if (Math.abs(t.clientX - tpStartX) > 6 || Math.abs(t.clientY - tpStartY) > 6) {
          tpMoved = true;
          if (!isHolding && !isDoubleTapScroll) {
            clearTimeout(holdTimer);
          }
        }

        if (now - tpLastSend > 25) {
          tpLastSend = now;
          if (isDoubleTapScroll) {
            sendMouse(0, -dy * 2.2, false, true, 'scroll');
          } else if (isHolding) {
            sendMouse(dx, dy, false, false, 'move');
          } else {
            sendMouse(dx, dy, false, false, 'move');
          }
        }
      }
    }, { passive: false });

    touchpadSurface.addEventListener('touchend', (e) => {
      clearTimeout(holdTimer);
      if (isHolding) {
        isHolding = false;
        vibrate(20);
        sendMouse(0, 0, false, false, 'up');
        showToast('Drag released');
        return;
      }

      if (isDoubleTapScroll) {
        isDoubleTapScroll = false;
        return;
      }

      if (!tpMoved && e.touches.length === 0) {
        vibrate(20);
        sendMouse(0, 0, true, false, 'click');
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

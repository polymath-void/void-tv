package com.polymath.voidtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements LiveServer.Host {
    public static final String BROWSER_HOME_URL = "file:///android_asset/runtime/browser_home.html";
    private static final String PREFS = "voidtv_browser";
    private static final String PREF_RECENT = "recent_urls";

    private FrameLayout rootLayout;
    private TvBridge tvBridge;
    private WebView launcherWebView;
    private FrameLayout browserContainer;
    private LinearLayout toolbar;
    private WebView browserWebView;
    private TextView browserUrlText;
    private ProgressBar browserProgressBar;
    private Button btnBack;
    private Button btnForward;
    private Button btnHome;
    private Button btnSearch;
    private Button btnReload;
    private Button btnCursor;
    private Button btnShield;
    private Button btnClose;
    private FrameLayout webViewContainer;
    private View cursorView;
    private float cursorX = 640f;
    private float cursorY = 360f;
    private boolean isCursorActive = true;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FileObserver fileObserver;
    private AlertDialog searchDialog;
    private EditText searchInput;
    private VoidShield voidShield;
    private AlertDialog shieldDialog;
    private boolean isMouseDown = false;
    private boolean isResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive mode for 10-foot TV experience
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        File configDir = new File("/sdcard/voidtv");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        voidShield = new VoidShield(this);
        setupLauncherWebView();
        setupBrowserOverlay();

        rootLayout.addView(launcherWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(browserContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootLayout);

        // Initialize or retrieve control auth token
        SharedPreferences tokenPrefs = getSharedPreferences("voidtv_prefs", MODE_PRIVATE);
        String token = tokenPrefs.getString("control_token", null);
        if (token == null || token.isEmpty()) {
            token = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            tokenPrefs.edit().putString("control_token", token).apply();
        }
        try {
            File tokenFile = new File(configDir, "token");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tokenFile);
            fos.write(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) {}

        // Initialize 24/7 VoidControlHub and register foreground launcher
        VoidControlHub.getInstance().init(getApplicationContext());
        VoidControlHub.getInstance().registerLauncher(this, launcherWebView);

        // Watch /sdcard/voidtv for hot-reloading
        try {
            fileObserver = new FileObserver(configDir.getPath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, String path) {
                    launcherWebView.post(() -> {
                        launcherWebView.evaluateJavascript("window.HotReload && window.HotReload.reloadConfig();", null);
                    });
                }
            };
            fileObserver.startWatching();
        } catch (Exception ignored) {}
    }

    private void setupLauncherWebView() {
        launcherWebView = new WebView(this);
        launcherWebView.setFocusable(false);
        launcherWebView.setFocusableInTouchMode(false);
        WebSettings s = launcherWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        tvBridge = new TvBridge(this);
        launcherWebView.addJavascriptInterface(tvBridge, "Android");
        launcherWebView.loadUrl("file:///android_asset/runtime/index.html");
    }

    private void setupBrowserOverlay() {
        browserContainer = new FrameLayout(this);
        browserContainer.setBackgroundColor(Color.BLACK);
        browserContainer.setVisibility(View.GONE);

        // Top Toolbar
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Color.parseColor("#0C0C14"));
        toolbar.setPadding(16, 6, 16, 6);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        btnBack = createToolButton("◀ BACK", v -> {
            if (browserWebView.canGoBack()) browserWebView.goBack();
        });

        btnForward = createToolButton("▶ FWD", v -> {
            if (browserWebView.canGoForward()) browserWebView.goForward();
        });

        btnHome = createToolButton("🏠 HOME", v -> {
            browserWebView.loadUrl(BROWSER_HOME_URL);
        });

        btnSearch = createToolButton("🔍 SEARCH", v -> {
            showSearchDialog();
        });

        browserUrlText = new TextView(this);
        browserUrlText.setTextColor(Color.parseColor("#00E5FF"));
        browserUrlText.setTypeface(Typeface.MONOSPACE);
        browserUrlText.setTextSize(12);
        browserUrlText.setSingleLine(true);
        browserUrlText.setPadding(14, 6, 14, 6);
        browserUrlText.setBackgroundColor(Color.parseColor("#12121A"));
        browserUrlText.setFocusable(true);
        browserUrlText.setFocusableInTouchMode(true);
        browserUrlText.setOnClickListener(v -> showSearchDialog());
        browserUrlText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                browserUrlText.setBackgroundColor(Color.parseColor("#1F1F2E"));
            } else {
                browserUrlText.setBackgroundColor(Color.parseColor("#12121A"));
            }
        });
        browserUrlText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setCursorActive(true);
                cursorY = 50f;
                browserWebView.requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        urlParams.setMargins(8, 0, 8, 0);
        browserUrlText.setLayoutParams(urlParams);

        btnReload = createToolButton("⟳ RELOAD", v -> browserWebView.reload());
        btnCursor = createToolButton("🖱️ CURSOR: ON", v -> toggleCursorMode());
        btnShield = createToolButton("🛡️ SHIELD (0)", v -> showShieldSettingsDialog());
        btnShield.setTextColor(Color.parseColor("#00E5FF"));
        btnClose = createToolButton("✕ EXIT", v -> hideBrowser());

        toolbar.addView(btnBack);
        toolbar.addView(btnForward);
        toolbar.addView(btnHome);
        toolbar.addView(btnSearch);
        toolbar.addView(browserUrlText);
        toolbar.addView(btnReload);
        toolbar.addView(btnCursor);
        toolbar.addView(btnShield);
        toolbar.addView(btnClose);

        // Neon hairline progress bar
        browserProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        browserProgressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4));
        browserProgressBar.setProgress(0);
        browserProgressBar.setVisibility(View.GONE);

        // Browser WebView
        browserWebView = new WebView(this);
        browserWebView.setBackgroundColor(Color.BLACK);
        browserWebView.setFocusable(true);
        browserWebView.setFocusableInTouchMode(true);

        // Cookies support
        CookieManager cookieManager = CookieManager.getInstance();
        boolean cookiesOn = voidShield != null && voidShield.isCookiesEnabled();
        cookieManager.setAcceptCookie(cookiesOn);
        cookieManager.setAcceptThirdPartyCookies(browserWebView, cookiesOn);

        // WebSettings for modern JS, HTML5 video & DRM
        WebSettings bs = browserWebView.getSettings();
        bs.setJavaScriptEnabled(true);
        boolean popupsOn = voidShield == null || !voidShield.isPopupBlockEnabled();
        bs.setJavaScriptCanOpenWindowsAutomatically(popupsOn);
        bs.setSupportMultipleWindows(popupsOn);
        bs.setDomStorageEnabled(true);
        bs.setDatabaseEnabled(true);
        bs.setAllowFileAccess(true);
        bs.setAllowContentAccess(true);
        bs.setAllowFileAccessFromFileURLs(true);
        bs.setAllowUniversalAccessFromFileURLs(true);
        bs.setMediaPlaybackRequiresUserGesture(false); // Enable auto-play and streaming playback
        bs.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        bs.setCacheMode(WebSettings.LOAD_DEFAULT);
        bs.setUseWideViewPort(true);
        bs.setLoadWithOverviewMode(true);
        bs.setSupportZoom(true);
        bs.setBuiltInZoomControls(true);
        bs.setDisplayZoomControls(false);
        applyUserAgent();

        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String url = request.getUrl().toString();
                if (voidShield != null && voidShield.shouldBlockNavigation(url)) {
                    MainActivity.this.runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "🛡️ VoidShield: Blocked popup redirect", Toast.LENGTH_SHORT).show());
                    return true;
                }
                if (customView != null) {
                    return true;
                }
                return false; // Stay inside in-app browser
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (voidShield != null && voidShield.shouldBlockNavigation(url)) {
                    MainActivity.this.runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "🛡️ VoidShield: Blocked popup redirect", Toast.LENGTH_SHORT).show());
                    return true;
                }
                if (customView != null) {
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (voidShield != null && request != null) {
                    WebResourceResponse blocked = voidShield.interceptResource(request.getUrl());
                    if (blocked != null) return blocked;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                browserProgressBar.setVisibility(View.VISIBLE);
                browserUrlText.setText("Loading: " + prettyUrl(url) + "...");
                updateNavButtons();
                updateShieldButton();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                browserProgressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (url != null && !url.startsWith("file:///")) {
                    rememberUrl(url);
                }
                browserUrlText.setText(prettyUrl(url));
                updateNavButtons();
                updateShieldButton();
                if (voidShield != null && voidShield.isPopupBlockEnabled()) {
                    view.evaluateJavascript(VoidShield.getAntiPopupScript(), null);
                }
            }
        });

        browserWebView.setWebChromeClient(new WebChromeClient() {
            // Anti-Popup protection: intercept window.open / popups at the WebChromeClient level
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                if (voidShield != null && voidShield.isPopupBlockEnabled()) {
                    Log.d("VoidShield", "Blocked popup window request via onCreateWindow");
                    return false;
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
            }

            // Modern DRM Support: Grant Widevine EME (RESOURCE_PROTECTED_MEDIA_ID) & media capture
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    request.grant(request.getResources());
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                super.onPermissionRequestCanceled(request);
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    browserProgressBar.setVisibility(View.VISIBLE);
                    browserProgressBar.setProgress(newProgress);
                } else {
                    browserProgressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                browserContainer.setVisibility(View.GONE);
                rootLayout.addView(customView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                dismissCustomView();
            }
        });

        webViewContainer = new FrameLayout(this);
        webViewContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        webViewContainer.addView(browserWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout browserContent = new LinearLayout(this);
        browserContent.setOrientation(LinearLayout.VERTICAL);
        browserContent.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        browserContent.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52));
        browserContent.addView(browserProgressBar);
        browserContent.addView(webViewContainer);
        browserContainer.addView(browserContent);

        cursorView = new View(this);
        int cursorPx = (int) (22 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cursorPx, cursorPx);
        cursorView.setLayoutParams(clp);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(Color.parseColor("#CC00E5FF"));
        dot.setStroke(3, Color.WHITE);
        cursorView.setBackground(dot);
        cursorView.setElevation(50f);
        cursorView.setTranslationZ(50f);
        browserContainer.addView(cursorView);
    }

    private void dispatchBrowserClick(float x, float y) {
        if (cursorView != null) {
            cursorView.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80)
                .withEndAction(() -> cursorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start())
                .start();
        }

        if (y <= 52f && toolbar != null) {
            int count = toolbar.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = toolbar.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    int left = child.getLeft();
                    int right = child.getRight();
                    int top = child.getTop();
                    int bottom = child.getBottom();
                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        child.performClick();
                        return;
                    }
                }
            }
            showSearchDialog();
            return;
        }

        float webY = Math.max(0, y - 52f);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, webY, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, webY, 0);
        browserWebView.dispatchTouchEvent(down);
        browserWebView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    public void toggleCursorMode() {
        setCursorActive(!isCursorActive);
    }

    public void setCursorActive(boolean active) {
        isCursorActive = active;
        if (cursorView != null) {
            cursorView.setVisibility(active ? View.VISIBLE : View.GONE);
            if (active) updateCursorPosition();
        }
        if (btnCursor != null) {
            btnCursor.setText(active ? "🖱️ CURSOR: ON" : "🖱️ CURSOR: OFF");
            btnCursor.setTextColor(active ? Color.parseColor("#00E5FF") : Color.GRAY);
        }
    }

    private void updateCursorPosition() {
        if (cursorView != null) {
            float half = cursorView.getWidth() > 0 ? cursorView.getWidth() / 2f : 16f;
            cursorView.setX(cursorX - half);
            cursorView.setY(cursorY - half);
        }
    }

    private void applyUserAgent() {
        if (browserWebView != null && voidShield != null) {
            if (voidShield.isDesktopUaEnabled()) {
                browserWebView.getSettings().setUserAgentString(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            } else {
                browserWebView.getSettings().setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 14; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            }
        }
    }

    private void updateShieldButton() {
        if (btnShield != null && voidShield != null) {
            boolean on = voidShield.isAdBlockEnabled() || voidShield.isPopupBlockEnabled();
            int count = voidShield.getBlockedCount();
            btnShield.setText(on ? ("🛡️ SHIELD (" + count + ")") : "🛡️ SHIELD: OFF");
            btnShield.setTextColor(on ? Color.parseColor("#00E5FF") : Color.GRAY);
        }
    }

    public void showShieldSettingsDialog() {
        if (voidShield == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(32, 24, 32, 24);
        panel.setBackgroundColor(Color.parseColor("#0C0C16"));

        TextView title = new TextView(this);
        title.setText("🛡️ VOIDSHIELD · BROWSER SETTINGS");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(16);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);
        panel.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Anti-Malware, AdBlock & Popup Protection for TV Streaming");
        subtitle.setTextColor(Color.parseColor("#8E95AF"));
        subtitle.setTextSize(11);
        subtitle.setPadding(0, 0, 0, 16);
        panel.addView(subtitle);

        // 1. Ad & Malware Blocker Toggle Button
        Button btnAdblock = createToolButton(
            voidShield.isAdBlockEnabled() ? "🛡️ AD & MALWARE BLOCKER: [ ON ]" : "🛡️ AD & MALWARE BLOCKER: [ OFF ]", null);
        btnAdblock.setTextColor(voidShield.isAdBlockEnabled() ? Color.parseColor("#00E676") : Color.GRAY);
        btnAdblock.setOnClickListener(v -> {
            boolean next = !voidShield.isAdBlockEnabled();
            voidShield.setAdBlockEnabled(next);
            btnAdblock.setText(next ? "🛡️ AD & MALWARE BLOCKER: [ ON ]" : "🛡️ AD & MALWARE BLOCKER: [ OFF ]");
            btnAdblock.setTextColor(next ? Color.parseColor("#00E676") : Color.GRAY);
            updateShieldButton();
        });
        panel.addView(btnAdblock);

        // 2. Popup & Redirect Shield Toggle Button
        Button btnPopups = createToolButton(
            voidShield.isPopupBlockEnabled() ? "🚫 POPUP & REDIRECT SHIELD: [ ON ]" : "🚫 POPUP & REDIRECT SHIELD: [ OFF ]", null);
        btnPopups.setTextColor(voidShield.isPopupBlockEnabled() ? Color.parseColor("#00E676") : Color.GRAY);
        btnPopups.setOnClickListener(v -> {
            boolean next = !voidShield.isPopupBlockEnabled();
            voidShield.setPopupBlockEnabled(next);
            btnPopups.setText(next ? "🚫 POPUP & REDIRECT SHIELD: [ ON ]" : "🚫 POPUP & REDIRECT SHIELD: [ OFF ]");
            btnPopups.setTextColor(next ? Color.parseColor("#00E676") : Color.GRAY);
            if (browserWebView != null) {
                browserWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(!next);
                browserWebView.getSettings().setSupportMultipleWindows(!next);
            }
            updateShieldButton();
        });
        panel.addView(btnPopups);

        // 3. User-Agent Toggle Button
        Button btnUa = createToolButton(
            voidShield.isDesktopUaEnabled() ? "🖥️ USER-AGENT: [ DESKTOP ]" : "📺 USER-AGENT: [ SMART TV ]", null);
        btnUa.setTextColor(Color.parseColor("#FFB300"));
        btnUa.setOnClickListener(v -> {
            boolean next = !voidShield.isDesktopUaEnabled();
            voidShield.setDesktopUaEnabled(next);
            btnUa.setText(next ? "🖥️ USER-AGENT: [ DESKTOP ]" : "📺 USER-AGENT: [ SMART TV ]");
            applyUserAgent();
        });
        panel.addView(btnUa);

        // 4. Cookies Toggle Button
        Button btnCookies = createToolButton(
            voidShield.isCookiesEnabled() ? "🍪 ACCEPT COOKIES: [ ON ]" : "🍪 ACCEPT COOKIES: [ OFF ]", null);
        btnCookies.setTextColor(voidShield.isCookiesEnabled() ? Color.WHITE : Color.GRAY);
        btnCookies.setOnClickListener(v -> {
            boolean next = !voidShield.isCookiesEnabled();
            voidShield.setCookiesEnabled(next);
            btnCookies.setText(next ? "🍪 ACCEPT COOKIES: [ ON ]" : "🍪 ACCEPT COOKIES: [ OFF ]");
            btnCookies.setTextColor(next ? Color.WHITE : Color.GRAY);
            CookieManager.getInstance().setAcceptCookie(next);
        });
        panel.addView(btnCookies);

        // 5. Clear Browsing Data
        Button btnClear = createToolButton("🗑️ CLEAR CACHE & COOKIES", v -> {
            clearBrowserData();
        });
        btnClear.setTextColor(Color.parseColor("#FF3366"));
        panel.addView(btnClear);

        // Stats Footer
        TextView stats = new TextView(this);
        stats.setText("🛡️ Blocked this session: " + voidShield.getBlockedCount() + " ads & popups");
        stats.setTextColor(Color.parseColor("#00E5FF"));
        stats.setTypeface(Typeface.MONOSPACE);
        stats.setTextSize(11);
        stats.setPadding(0, 14, 0, 14);
        panel.addView(stats);

        // Close Button
        Button btnCloseDialog = createToolButton("✕ CLOSE", v -> {
            if (shieldDialog != null) shieldDialog.dismiss();
        });
        btnCloseDialog.setBackgroundColor(Color.parseColor("#25283D"));
        panel.addView(btnCloseDialog);

        builder.setView(panel);
        shieldDialog = builder.create();
        if (shieldDialog.getWindow() != null) {
            shieldDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        shieldDialog.show();
        btnAdblock.requestFocus();
    }

    private Button createToolButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#181824"));
        b.setPadding(14, 4, 14, 4);
        b.setOnClickListener(listener);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);

        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                b.setBackgroundColor(Color.parseColor("#00E5FF"));
                b.setTextColor(Color.BLACK);
                b.setTypeface(Typeface.DEFAULT_BOLD);
                b.setScaleX(1.08f);
                b.setScaleY(1.08f);
            } else {
                b.setBackgroundColor(Color.parseColor("#181824"));
                b.setTextColor(Color.WHITE);
                b.setTypeface(Typeface.DEFAULT);
                b.setScaleX(1.0f);
                b.setScaleY(1.0f);
            }
        });

        b.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setCursorActive(true);
                cursorY = 50f;
                browserWebView.requestFocus();
                return true;
            }
            return false;
        });
        return b;
    }

    private void updateNavButtons() {
        if (btnBack != null && browserWebView != null) {
            btnBack.setAlpha(browserWebView.canGoBack() ? 1.0f : 0.4f);
        }
        if (btnForward != null && browserWebView != null) {
            btnForward.setAlpha(browserWebView.canGoForward() ? 1.0f : 0.4f);
        }
    }

    private void dismissCustomView() {
        if (customView == null) return;
        rootLayout.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        browserContainer.setVisibility(View.VISIBLE);
        browserWebView.requestFocus();
    }

    public void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(28, 18, 28, 22);
        panel.setBackgroundColor(Color.parseColor("#0C0C14"));

        TextView title = new TextView(this);
        title.setText("VOID BROWSER  ·  URL OR SEARCH");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(13);
        title.setPadding(0, 0, 0, 12);
        panel.addView(title);

        final EditText input = new EditText(this);
        searchInput = input;
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setPadding(20, 14, 20, 14);
        input.setBackgroundColor(Color.parseColor("#12121A"));
        String currentUrl = browserWebView != null ? browserWebView.getUrl() : "";
        if (currentUrl != null && !currentUrl.startsWith("file:///") && !"about:blank".equals(currentUrl)) {
            input.setText(currentUrl);
            input.selectAll();
        } else {
            input.setHint("example.com, 192.168.0.10:8080, or a search");
        }
        panel.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.HORIZONTAL);
        suggestions.setPadding(0, 14, 0, 0);
        panel.addView(suggestions);

        final AlertDialog[] holder = new AlertDialog[1];

        Runnable submit = () -> {
            String val = input.getText().toString().trim();
            if (val.isEmpty()) return;
            String url = normalizeAddress(val);
            if (url == null) return;
            if (holder[0] != null) holder[0].dismiss();
            showBrowser(url);
        };

        input.setOnEditorActionListener((v, actionId, event) -> {
            submit.run();
            return true;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    && suggestions.getChildCount() > 0) {
                suggestions.getChildAt(0).requestFocus();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                buildSuggestions(suggestions, s.toString(), input, holder);
            }
        });

        builder.setView(panel);
        builder.setPositiveButton("GO", (dialog, which) -> submit.run());
        builder.setNeutralButton("HOME", (dialog, which) -> showBrowser(BROWSER_HOME_URL));
        builder.setNegativeButton("CLOSE", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        holder[0] = dialog;
        searchDialog = dialog;
        dialog.setOnDismissListener(d -> {
            holder[0] = null;
            if (searchDialog == dialog) searchDialog = null;
            searchInput = null;
        });
        dialog.setOnShowListener(d -> {
            buildSuggestions(suggestions, input.getText().toString(), input, holder);
            input.requestFocus();
        });
        dialog.show();
        dialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        input.requestFocus();
    }

    /** D-pad friendly suggestion strip under the address field. */
    private void buildSuggestions(LinearLayout container, String raw, EditText input, AlertDialog[] holder) {
        container.removeAllViews();
        List<String[]> items = new ArrayList<>();
        String q = raw == null ? "" : raw.trim();

        if (!q.isEmpty()) {
            String direct = normalizeAddress(q);
            boolean isSearch = direct != null && direct.startsWith("https://duckduckgo.com/?q=");
            if (direct != null && !isSearch) {
                items.add(new String[]{"OPEN   " + shorten(prettyUrl(direct), 30), direct});
                items.add(new String[]{"SEARCH " + shorten(q, 26), searchUrl(q)});
            } else if (direct != null) {
                items.add(new String[]{"SEARCH " + shorten(q, 26), direct});
            }
        }
        for (String u : recentUrls()) {
            if (items.size() >= 4) break;
            if (!q.isEmpty() && !u.toLowerCase().contains(q.toLowerCase())) continue;
            items.add(new String[]{"↺ " + shorten(prettyUrl(u), 32), u});
        }
        if (items.isEmpty()) {
            items.add(new String[]{"↺ VoidTV Home", BROWSER_HOME_URL});
        }

        for (int i = 0; i < items.size(); i++) {
            String[] it = items.get(i);
            Button b = createToolButton(it[0], v -> {
                if (holder[0] != null) holder[0].dismiss();
                showBrowser(it[1]);
            });
            b.setTextSize(11);
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (i == 0) {
                b.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        input.requestFocus();
                        return true;
                    }
                    return false;
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, 8, 0);
            container.addView(b, lp);
        }
    }

    /** Turn whatever was typed into a real URL, or a DuckDuckGo search. */
    private String normalizeAddress(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return null;

        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches("^(https?|file|about|content)://.*")) return s;
        if (lower.equals("home") || lower.equals("voidtv")) return BROWSER_HOME_URL;
        if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1")) return "http://" + s;
        if (s.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")) return "http://" + s;
        if (!s.contains(" ") && s.contains(".") && !s.endsWith(".")) return "https://" + s;
        return searchUrl(s);
    }

    private String searchUrl(String q) {
        try {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            return "https://duckduckgo.com/?q=" + q;
        }
    }

    private String prettyUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.endsWith("runtime/browser_home.html")) return "VoidTV Home";
        boolean secure = url.startsWith("https://");
        String s = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return (secure ? "🔒 " : "") + s;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private List<String> recentUrls() {
        List<String> out = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_RECENT, "");
        if (raw != null && !raw.isEmpty()) {
            for (String u : raw.split("\n")) {
                if (!u.trim().isEmpty()) out.add(u.trim());
            }
        }
        return out;
    }

    private void rememberUrl(String url) {
        if (url == null || url.startsWith("data:") || url.startsWith("about:") || url.startsWith("file:")) return;
        List<String> list = recentUrls();
        list.remove(url);
        list.add(0, url);
        while (list.size() > 6) list.remove(list.size() - 1);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(PREF_RECENT, String.join("\n", list)).apply();
    }

    // ---------------- LiveServer.Host: agent control surface ----------------

    /** JSON snapshot of what is on screen; see window.AgentAPI in app.js. */
    @Override
    public String state() {
        final String[] out = {"{\"mode\":\"UNKNOWN\"}"};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                if (browserContainer != null && browserContainer.getVisibility() == View.VISIBLE) {
                    JSONObject o = new JSONObject();
                    String url = browserWebView.getUrl();
                    o.put("mode", "BROWSER");
                    o.put("url", url == null ? "" : url);
                    o.put("pretty", prettyUrl(url));
                    o.put("canGoBack", browserWebView.canGoBack());
                    o.put("canGoForward", browserWebView.canGoForward());
                    o.put("cursor", new JSONObject()
                        .put("x", cursorX)
                        .put("y", cursorY)
                        .put("active", isCursorActive));
                    if (searchDialog != null && searchDialog.isShowing()) o.put("dialog", "address");
                    out[0] = o.toString();
                }
            } catch (Exception ignored) {}
            latch.countDown();
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        if (!"{\"mode\":\"UNKNOWN\"}".equals(out[0])) return out[0];
        String s = evalSync("JSON.stringify(window.AgentAPI.state())");
        return (s == null || s.isEmpty()) ? "{\"mode\":\"UNKNOWN\"}" : s;
    }

    @Override
    public boolean key(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        final String rawKey = name.trim();
        final int code = keyCodeFor(rawKey);

        runOnUiThread(() -> {
            boolean inBrowser = browserContainer != null && browserContainer.getVisibility() == View.VISIBLE;

            // Global HOME key - returns immediately to Launcher home
            if ("HOME".equalsIgnoreCase(rawKey) || code == KeyEvent.KEYCODE_HOME) {
                if (searchDialog != null && searchDialog.isShowing()) searchDialog.dismiss();
                if (inBrowser) hideBrowser();
                return;
            }

            // Case 1: Search / Address Input Dialog is showing
            if (searchDialog != null && searchDialog.isShowing() && searchInput != null) {
                if (code == KeyEvent.KEYCODE_DEL || "BACKSPACE".equalsIgnoreCase(rawKey)) {
                    int start = Math.max(searchInput.getSelectionStart(), 0);
                    int end = Math.max(searchInput.getSelectionEnd(), 0);
                    if (start > 0 || end > 0) {
                        if (start == end && start > 0) {
                            searchInput.getText().delete(start - 1, start);
                        } else {
                            searchInput.getText().delete(Math.min(start, end), Math.max(start, end));
                        }
                    }
                    return;
                }
                if (code == KeyEvent.KEYCODE_ENTER || "ENTER".equalsIgnoreCase(rawKey)) {
                    String val = searchInput.getText().toString().trim();
                    if (!val.isEmpty()) {
                        searchDialog.dismiss();
                        showBrowser(normalizeAddress(val));
                    }
                    return;
                }
                if (code == KeyEvent.KEYCODE_SPACE || "SPACE".equalsIgnoreCase(rawKey)) {
                    int start = Math.max(searchInput.getSelectionStart(), 0);
                    int end = Math.max(searchInput.getSelectionEnd(), 0);
                    searchInput.getText().replace(Math.min(start, end), Math.max(start, end), " ");
                    return;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN || "DOWN".equalsIgnoreCase(rawKey)) {
                    View next = searchInput.focusSearch(View.FOCUS_DOWN);
                    if (next != null) next.requestFocus();
                    return;
                }
                if (code == KeyEvent.KEYCODE_BACK || "BACK".equalsIgnoreCase(rawKey)) {
                    searchDialog.dismiss();
                    return;
                }
                String charToInsert = null;
                if (rawKey.length() == 1) {
                    charToInsert = rawKey;
                } else if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) {
                    charToInsert = String.valueOf((char) ('a' + (code - KeyEvent.KEYCODE_A)));
                } else if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                    charToInsert = String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0)));
                }
                if (charToInsert != null) {
                    int start = Math.max(searchInput.getSelectionStart(), 0);
                    int end = Math.max(searchInput.getSelectionEnd(), 0);
                    searchInput.getText().replace(Math.min(start, end), Math.max(start, end), charToInsert);
                    return;
                }
            }

            // Case 2: In Browser Mode
            if (inBrowser) {
                if ("BACK".equalsIgnoreCase(rawKey) || code == KeyEvent.KEYCODE_BACK) {
                    if (customView != null) {
                        dismissCustomView();
                    } else if (searchDialog != null && searchDialog.isShowing()) {
                        searchDialog.dismiss();
                    } else if (browserWebView.canGoBack()) {
                        browserWebView.goBack();
                    } else {
                        hideBrowser();
                    }
                    return;
                }

                String currentUrl = browserWebView != null ? browserWebView.getUrl() : "";
                if (currentUrl != null && currentUrl.contains("browser_home.html")) {
                    if (code == KeyEvent.KEYCODE_ENTER || "ENTER".equalsIgnoreCase(rawKey)) {
                        browserWebView.evaluateJavascript(
                            "(function(){ var el = document.getElementById('search-input'); if(el && el.value.trim()){ window.location.href = 'https://duckduckgo.com/?q=' + encodeURIComponent(el.value.trim()); } })()", null);
                        return;
                    } else if (code == KeyEvent.KEYCODE_DEL || "BACKSPACE".equalsIgnoreCase(rawKey)) {
                        browserWebView.evaluateJavascript(
                            "(function(){ var el = document.getElementById('search-input'); if(el){ el.value = el.value.slice(0, -1); el.focus(); } })()", null);
                        return;
                    } else if (code == KeyEvent.KEYCODE_SPACE || "SPACE".equalsIgnoreCase(rawKey)) {
                        browserWebView.evaluateJavascript(
                            "(function(){ var el = document.getElementById('search-input'); if(el){ el.value += ' '; el.focus(); } })()", null);
                        return;
                    } else if (rawKey.length() == 1 || (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) || (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)) {
                        String c = rawKey.length() == 1 ? rawKey : (code >= KeyEvent.KEYCODE_A ? String.valueOf((char) ('a' + (code - KeyEvent.KEYCODE_A))) : String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0))));
                        String escaped = c.replace("\\", "\\\\").replace("'", "\\'");
                        browserWebView.evaluateJavascript(
                            "(function(){ var el = document.getElementById('search-input'); if(el){ el.value += '" + escaped + "'; el.focus(); } })()", null);
                        return;
                    }
                }

                if (isCursorActive) {
                    KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, 0);
                    if (handleBrowserKeyDown(code, event)) {
                        return;
                    }
                }
            }

            // Case 3: Standard KeyEvent dispatch
            if (code >= 0) {
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
                KeyEvent up = new KeyEvent(now, now + 20, KeyEvent.ACTION_UP, code, 0);
                if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dispatchKeyEvent(down);
                    searchDialog.dispatchKeyEvent(up);
                } else if (inBrowser) {
                    if (browserWebView != null) {
                        browserWebView.dispatchKeyEvent(down);
                        browserWebView.dispatchKeyEvent(up);
                    }
                } else {
                    dispatchKeyEvent(down);
                    dispatchKeyEvent(up);
                }
            }
        });
        return true;
    }

    @Override
    public boolean tap(String label) {
        if (label == null) label = "";
        if (browserContainer != null && browserContainer.getVisibility() == View.VISIBLE) {
            String l = label.trim().toUpperCase(java.util.Locale.ROOT);
            if ("CLICK".equals(l) || "ENTER".equals(l) || "SELECT".equals(l)) {
                return key("SELECT");
            } else if ("BACK".equals(l)) {
                runOnUiThread(() -> {
                    if (customView != null) dismissCustomView();
                    else if (searchDialog != null && searchDialog.isShowing()) searchDialog.dismiss();
                    else if (browserWebView.canGoBack()) browserWebView.goBack();
                    else hideBrowser();
                });
                return true;
            } else if ("HOME".equals(l)) {
                runOnUiThread(this::hideBrowser);
                return true;
            } else if ("SEARCH".equals(l) || "ADDRESS".equals(l)) {
                runOnUiThread(this::showSearchDialog);
                return true;
            } else if ("RELOAD".equals(l) || "REFRESH".equals(l)) {
                runOnUiThread(() -> browserWebView.reload());
                return true;
            } else if ("EXIT".equals(l) || "CLOSE".equals(l)) {
                runOnUiThread(this::hideBrowser);
                return true;
            } else if ("CURSOR".equals(l)) {
                runOnUiThread(this::toggleCursorMode);
                return true;
            } else if ("SHIELD".equals(l) || "SETTINGS".equals(l) || "PREFERENCES".equals(l)) {
                runOnUiThread(this::showShieldSettingsDialog);
                return true;
            }
        }
        String r = evalSync("JSON.stringify(window.AgentAPI.tap(" + JSONObject.quote(label) + "))");
        return r != null && r.contains("\"ok\":true");
    }

    @Override
    public boolean launch(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return false;
        final boolean[] ok = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            ok[0] = tvBridge.launchApp(pkg.trim());
            latch.countDown();
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return ok[0];
    }

    @Override
    public boolean openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String target = normalizeAddress(url);
        if (target == null) return false;
        final String t = target;
        runOnUiThread(() -> showBrowser(t));
        return true;
    }

    @Override
    public boolean switchInput(String input) {
        final boolean[] ok = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            ok[0] = tvBridge.switchInput(input);
            latch.countDown();
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return ok[0];
    }

    @Override
    public String inputs() {
        final String[] result = {"[]"};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            result[0] = tvBridge.getTvInputs();
            latch.countDown();
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result[0];
    }

    @Override
    public String apps() {
        final String[] result = {"[]"};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                result[0] = tvBridge.getInstalledApps();
            } catch (Exception ignored) {}
            latch.countDown();
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result[0];
    }

    @Override
    public boolean sendText(String text) {
        if (text == null || text.isEmpty()) return false;
        runOnUiThread(() -> {
            try {
                if (searchDialog != null && searchDialog.isShowing() && searchInput != null) {
                    searchInput.setText(text);
                    searchInput.setSelection(text.length());
                    return;
                }
                if (browserContainer != null && browserContainer.getVisibility() == View.VISIBLE) {
                    String currentUrl = browserWebView != null ? browserWebView.getUrl() : "";
                    if (currentUrl != null && currentUrl.contains("browser_home.html")) {
                        String escaped = text.replace("\\", "\\\\").replace("'", "\\'");
                        browserWebView.evaluateJavascript(
                            "(function(){ var el = document.getElementById('search-input'); if(el){ el.value = '" + escaped + "'; el.focus(); } })()", null);
                        return;
                    }
                    showBrowser(normalizeAddress(text));
                    return;
                }
                showBrowser(normalizeAddress(text));
            } catch (Exception ignored) {}
        });
        return true;
    }

    @Override
    public boolean mouse(float dx, float dy, boolean click, boolean scroll, String action) {
        runOnUiThread(() -> {
            if (browserContainer != null && browserContainer.getVisibility() == View.VISIBLE) {
                int viewW = rootLayout.getWidth() > 0 ? rootLayout.getWidth() : 1280;
                int viewH = rootLayout.getHeight() > 0 ? rootLayout.getHeight() : 720;

                if (scroll || "scroll".equalsIgnoreCase(action)) {
                    browserWebView.scrollBy((int) dx, (int) dy);
                    return;
                }

                cursorX = Math.max(10f, Math.min(viewW - 10f, cursorX + dx));
                cursorY = Math.max(10f, Math.min(viewH - 10f, cursorY + dy));
                setCursorActive(true);
                updateCursorPosition();

                long now = SystemClock.uptimeMillis();
                if ("down".equalsIgnoreCase(action)) {
                    isMouseDown = true;
                    if (cursorView != null) cursorView.setScaleX(0.75f);
                    float webY = Math.max(0, cursorY - 52f);
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, webY, 0);
                    browserWebView.dispatchTouchEvent(down);
                    down.recycle();
                } else if ("move".equalsIgnoreCase(action) && isMouseDown) {
                    float webY = Math.max(0, cursorY - 52f);
                    MotionEvent move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, cursorX, webY, 0);
                    browserWebView.dispatchTouchEvent(move);
                    move.recycle();
                } else if ("up".equalsIgnoreCase(action)) {
                    if (isMouseDown) {
                        isMouseDown = false;
                        if (cursorView != null) cursorView.setScaleX(1.0f);
                        float webY = Math.max(0, cursorY - 52f);
                        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, cursorX, webY, 0);
                        browserWebView.dispatchTouchEvent(up);
                        up.recycle();
                    }
                } else if (click || "click".equalsIgnoreCase(action)) {
                    dispatchBrowserClick(cursorX, cursorY);
                }
            }
        });
        return true;
    }

    @Override
    public boolean volume(String action) {
        if (action == null) return false;
        String act = action.trim().toUpperCase(java.util.Locale.ROOT);
        runOnUiThread(() -> {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                if ("VOL_UP".equals(act) || "UP".equals(act) || "VOLUME_UP".equals(act)) {
                    am.adjustSuggestedStreamVolume(android.media.AudioManager.ADJUST_RAISE,
                        android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
                        android.media.AudioManager.FLAG_SHOW_UI);
                } else if ("VOL_DOWN".equals(act) || "DOWN".equals(act) || "VOLUME_DOWN".equals(act)) {
                    am.adjustSuggestedStreamVolume(android.media.AudioManager.ADJUST_LOWER,
                        android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
                        android.media.AudioManager.FLAG_SHOW_UI);
                } else if ("MUTE".equals(act) || "VOLUME_MUTE".equals(act)) {
                    am.adjustSuggestedStreamVolume(android.media.AudioManager.ADJUST_TOGGLE_MUTE,
                        android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
                        android.media.AudioManager.FLAG_SHOW_UI);
                }
            }
        });
        return true;
    }

    @Override
    public String browserConfig() {
        return voidShield != null ? voidShield.getStatusJson() : "{}";
    }

    @Override
    public boolean updateBrowserConfig(String adblock, String popups, String ua, String cookies) {
        if (voidShield == null) return false;
        if (adblock != null && !adblock.isEmpty()) {
            voidShield.setAdBlockEnabled("true".equalsIgnoreCase(adblock) || "1".equals(adblock));
        }
        if (popups != null && !popups.isEmpty()) {
            voidShield.setPopupBlockEnabled("true".equalsIgnoreCase(popups) || "1".equals(popups));
        }
        if (ua != null && !ua.isEmpty()) {
            voidShield.setDesktopUaEnabled("desktop".equalsIgnoreCase(ua) || "true".equalsIgnoreCase(ua));
        }
        if (cookies != null && !cookies.isEmpty()) {
            voidShield.setCookiesEnabled("true".equalsIgnoreCase(cookies) || "1".equals(cookies));
        }
        runOnUiThread(() -> {
            if (browserWebView != null) {
                browserWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(!voidShield.isPopupBlockEnabled());
                browserWebView.getSettings().setSupportMultipleWindows(!voidShield.isPopupBlockEnabled());
            }
            applyUserAgent();
            CookieManager.getInstance().setAcceptCookie(voidShield.isCookiesEnabled());
            updateShieldButton();
        });
        return true;
    }

    @Override
    public boolean clearBrowserData() {
        runOnUiThread(() -> {
            if (browserWebView != null) {
                browserWebView.clearCache(true);
                browserWebView.clearHistory();
                browserWebView.clearFormData();
            }
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            if (voidShield != null) voidShield.resetBlockedCount();
            updateShieldButton();
            Toast.makeText(this, "🧹 Browsing cache and cookies cleared", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    private static int keyCodeFor(String name) {
        if (name == null) return -1;
        String upper = name.trim().toUpperCase(java.util.Locale.ROOT);
        switch (upper) {
            case "UP": return KeyEvent.KEYCODE_DPAD_UP;
            case "DOWN": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "LEFT": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "RIGHT": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "SELECT":
            case "ENTER":
            case "OK": return KeyEvent.KEYCODE_DPAD_CENTER;
            case "BACK": return KeyEvent.KEYCODE_BACK;
            case "MENU": return KeyEvent.KEYCODE_MENU;
            case "HOME": return KeyEvent.KEYCODE_HOME;
            case "POWER": return KeyEvent.KEYCODE_POWER;
            case "VOL_UP":
            case "VOLUP":
            case "VOLUME_UP": return KeyEvent.KEYCODE_VOLUME_UP;
            case "VOL_DOWN":
            case "VOLDOWN":
            case "VOLUME_DOWN": return KeyEvent.KEYCODE_VOLUME_DOWN;
            case "MUTE":
            case "VOLUME_MUTE": return KeyEvent.KEYCODE_VOLUME_MUTE;
            case "PLAY": return KeyEvent.KEYCODE_MEDIA_PLAY;
            case "PAUSE": return KeyEvent.KEYCODE_MEDIA_PAUSE;
            case "PLAY_PAUSE": return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case "STOP": return KeyEvent.KEYCODE_MEDIA_STOP;
            case "NEXT": return KeyEvent.KEYCODE_MEDIA_NEXT;
            case "PREV": return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case "REWIND": return KeyEvent.KEYCODE_MEDIA_REWIND;
            case "FAST_FORWARD": return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
            case "PAGE_UP": return KeyEvent.KEYCODE_PAGE_UP;
            case "PAGE_DOWN": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "INFO": return KeyEvent.KEYCODE_INFO;
            case "SEARCH": return KeyEvent.KEYCODE_SEARCH;
            case "SPACE": return KeyEvent.KEYCODE_SPACE;
            case "BACKSPACE":
            case "DEL": return KeyEvent.KEYCODE_DEL;
            case "TAB": return KeyEvent.KEYCODE_TAB;
            case "ESCAPE":
            case "ESC": return KeyEvent.KEYCODE_ESCAPE;
            default:
                if (upper.length() == 1) {
                    char c = upper.charAt(0);
                    if (c >= 'A' && c <= 'Z') return KeyEvent.KEYCODE_A + (c - 'A');
                    if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
                }
                try {
                    int n = Integer.parseInt(upper);
                    if (n >= 0 && n <= 9) return KeyEvent.KEYCODE_0 + n;
                } catch (Exception ignored) {}
                return -1;
        }
    }

    /** Run JS and block the HTTP thread until WebView answers (2s cap). */
    private String evalSync(String js) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = new String[1];
        launcherWebView.post(() -> launcherWebView.evaluateJavascript(js, value -> {
            result[0] = value;
            latch.countDown();
        }));
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        String v = result[0];
        if (v == null || v.equals("null")) return null;
        if (v.length() >= 2 && v.charAt(0) == '"') {
            try { return new JSONArray("[" + v + "]").getString(0); }
            catch (Exception e) { return v; }
        }
        return v;
    }

    public void showBrowser(String url) {
        if (url == null || url.trim().isEmpty() || "about:blank".equals(url)) {
            url = BROWSER_HOME_URL;
        }
        browserUrlText.setText("Loading: " + prettyUrl(url) + "...");
        browserWebView.loadUrl(url);
        browserContainer.setVisibility(View.VISIBLE);

        int w = rootLayout.getWidth() > 0 ? rootLayout.getWidth() : 1280;
        int h = rootLayout.getHeight() > 0 ? rootLayout.getHeight() : 720;
        cursorX = w / 2f;
        cursorY = (h - 48) / 2f;
        setCursorActive(true);
        browserWebView.requestFocus();
    }

    public void hideBrowser() {
        dismissCustomView();
        setCursorActive(false);
        browserWebView.loadUrl("about:blank");
        browserContainer.setVisibility(View.GONE);
        launcherWebView.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (browserContainer != null && browserContainer.getVisibility() == View.VISIBLE) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && event.getAction() == KeyEvent.ACTION_DOWN) {
                hideBrowser();
                return true;
            }

            if (customView != null) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    dismissCustomView();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (shieldDialog != null && shieldDialog.isShowing()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    shieldDialog.dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (searchDialog != null && searchDialog.isShowing()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    searchDialog.dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (handleBrowserKeyDown(event.getKeyCode(), event)) {
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // --- LAUNCHER MODE ---
        // Intercept all navigation and action keys so WebView never receives raw DPAD_CENTER
        // or executes phantom clicks on background elements (e.g. HDMI / Live TV).
        if (shieldDialog != null && shieldDialog.isShowing()) {
            return super.dispatchKeyEvent(event);
        }

        if (searchDialog != null && searchDialog.isShowing()) {
            return super.dispatchKeyEvent(event);
        }

        if (isLauncherNavKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                handleLauncherKeyDown(event.getKeyCode(), event);
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean handleBrowserKeyDown(int keyCode, KeyEvent event) {
        if (!isCursorActive) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setCursorActive(true);
                cursorY = 50f;
                browserWebView.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (customView != null) {
                    dismissCustomView();
                } else if (shieldDialog != null && shieldDialog.isShowing()) {
                    shieldDialog.dismiss();
                } else if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dismiss();
                } else if (browserWebView.canGoBack()) {
                    browserWebView.goBack();
                } else {
                    hideBrowser();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                hideBrowser();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showShieldSettingsDialog();
                return true;
            }
            return false;
        }

        // --- VIRTUAL CURSOR ACTIVE ---
        int viewW = rootLayout.getWidth() > 0 ? rootLayout.getWidth() : 1280;
        int viewH = rootLayout.getHeight() > 0 ? rootLayout.getHeight() : 720;
        float step = Math.min(18f + event.getRepeatCount() * 8f, 55f);

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                cursorY -= step;
                if (cursorY < 15f) cursorY = 15f;
                if (cursorY < 90f) {
                    browserWebView.scrollBy(0, -60);
                }
                updateCursorPosition();
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursorY += step;
                if (cursorY > viewH - 15f) cursorY = viewH - 15f;
                if (cursorY > viewH - 100f) {
                    browserWebView.scrollBy(0, 60);
                }
                updateCursorPosition();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                cursorX -= step;
                if (cursorX < 15f) cursorX = 15f;
                updateCursorPosition();
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cursorX += step;
                if (cursorX > viewW - 15f) cursorX = viewW - 15f;
                updateCursorPosition();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                dispatchBrowserClick(cursorX, cursorY);
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (customView != null) {
                    dismissCustomView();
                } else if (shieldDialog != null && shieldDialog.isShowing()) {
                    shieldDialog.dismiss();
                } else if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dismiss();
                } else if (browserWebView.canGoBack()) {
                    browserWebView.goBack();
                } else {
                    hideBrowser();
                }
                return true;

            case KeyEvent.KEYCODE_HOME:
                hideBrowser();
                return true;

            case KeyEvent.KEYCODE_MENU:
                showShieldSettingsDialog();
                return true;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                browserWebView.pageDown(false);
                return true;

            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                browserWebView.pageUp(false);
                return true;
        }

        return false;
    }

    private boolean isLauncherNavKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
        }
    }

    private boolean handleLauncherKeyDown(int keyCode, KeyEvent event) {
        String key = null;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: key = "UP"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: key = "DOWN"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: key = "LEFT"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: key = "RIGHT"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: key = "SELECT"; break;
            case KeyEvent.KEYCODE_BACK: key = "BACK"; break;
            case KeyEvent.KEYCODE_MENU: key = "MENU"; break;
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                key = String.valueOf(keyCode - KeyEvent.KEYCODE_0);
                break;
        }

        if (key != null) {
            final String finalKey = key;
            launcherWebView.post(() -> {
                launcherWebView.evaluateJavascript("window.FocusEngine && window.FocusEngine.onKey('" + finalKey + "');", null);
            });
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
    }

    public boolean isForeground() {
        return isResumed;
    }

    public boolean isBrowserShowing() {
        return browserContainer != null && browserContainer.getVisibility() == View.VISIBLE;
    }

    public WebView getLauncherWebView() {
        return launcherWebView;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VoidControlHub.getInstance().unregisterLauncher(this);
        if (fileObserver != null) fileObserver.stopWatching();
    }
}

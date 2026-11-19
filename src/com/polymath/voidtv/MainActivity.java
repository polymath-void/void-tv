package com.polymath.voidtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    public static final String BROWSER_HOME_URL = "file:///android_asset/runtime/browser_home.html";

    private FrameLayout rootLayout;
    private WebView launcherWebView;
    private LinearLayout browserContainer;
    private WebView browserWebView;
    private TextView browserUrlText;
    private ProgressBar browserProgressBar;
    private Button btnBack;
    private Button btnForward;
    private Button btnHome;
    private Button btnSearch;
    private Button btnReload;
    private Button btnClose;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private LiveServer liveServer;
    private FileObserver fileObserver;

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

        setupLauncherWebView();
        setupBrowserOverlay();

        rootLayout.addView(launcherWebView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(browserContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootLayout);

        // Start live coding REPL server on 0.0.0.0:4444
        liveServer = new LiveServer(4444, launcherWebView);
        liveServer.start();

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
        WebSettings s = launcherWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        launcherWebView.addJavascriptInterface(new TvBridge(this), "Android");
        launcherWebView.loadUrl("file:///android_asset/runtime/index.html");
    }

    private void setupBrowserOverlay() {
        browserContainer = new LinearLayout(this);
        browserContainer.setOrientation(LinearLayout.VERTICAL);
        browserContainer.setBackgroundColor(Color.BLACK);
        browserContainer.setVisibility(View.GONE);

        // Top Toolbar
        LinearLayout toolbar = new LinearLayout(this);
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
        browserUrlText.setOnClickListener(v -> showSearchDialog());
        browserUrlText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                browserUrlText.setBackgroundColor(Color.parseColor("#1F1F2E"));
            } else {
                browserUrlText.setBackgroundColor(Color.parseColor("#12121A"));
            }
        });
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        urlParams.setMargins(8, 0, 8, 0);
        browserUrlText.setLayoutParams(urlParams);

        btnReload = createToolButton("⟳ RELOAD", v -> browserWebView.reload());
        btnClose = createToolButton("✕ EXIT", v -> hideBrowser());

        toolbar.addView(btnBack);
        toolbar.addView(btnForward);
        toolbar.addView(btnHome);
        toolbar.addView(btnSearch);
        toolbar.addView(browserUrlText);
        toolbar.addView(btnReload);
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

        // Cookies support (First & Third-party cookies for modern auth, sessions, media CDNs)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(browserWebView, true);

        // WebSettings for modern JS, HTML5 video & DRM
        WebSettings bs = browserWebView.getSettings();
        bs.setJavaScriptEnabled(true);
        bs.setJavaScriptCanOpenWindowsAutomatically(true);
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
        bs.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Stay inside in-app browser
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                browserProgressBar.setVisibility(View.VISIBLE);
                browserUrlText.setText("Loading: " + (url != null ? url : ""));
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                browserProgressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                String display = url != null ? url : "";
                if (display.startsWith("https://")) {
                    display = "🔒 " + display;
                }
                browserUrlText.setText(display);
                updateNavButtons();
            }
        });

        browserWebView.setWebChromeClient(new WebChromeClient() {
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
        });

        browserContainer.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46));
        browserContainer.addView(browserProgressBar);
        browserContainer.addView(browserWebView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                b.setBackgroundColor(Color.parseColor("#00E5FF"));
                b.setTextColor(Color.BLACK);
                b.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                b.setBackgroundColor(Color.parseColor("#181824"));
                b.setTextColor(Color.WHITE);
                b.setTypeface(Typeface.DEFAULT);
            }
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

    public void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("VoidTV Browser - URL or Search");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setPadding(24, 20, 24, 20);
        String currentUrl = browserWebView != null ? browserWebView.getUrl() : "";
        if (currentUrl != null && !currentUrl.startsWith("file:///")) {
            input.setText(currentUrl);
            input.selectAll();
        } else {
            input.setHint("Enter URL or search term...");
        }
        builder.setView(input);

        builder.setPositiveButton("GO / SEARCH", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (val.isEmpty()) return;

            if (val.startsWith("http://") || val.startsWith("https://")) {
                showBrowser(val);
            } else if (val.contains(".") && !val.contains(" ")) {
                showBrowser("https://" + val);
            } else {
                try {
                    showBrowser("https://duckduckgo.com/?q=" + URLEncoder.encode(val, "UTF-8"));
                } catch (Exception e) {
                    showBrowser("https://duckduckgo.com/?q=" + val);
                }
            }
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.cancel());
        builder.setNeutralButton("HOME", (dialog, which) -> showBrowser(BROWSER_HOME_URL));

        AlertDialog dialog = builder.create();
        dialog.show();
        input.requestFocus();
    }

    public void showBrowser(String url) {
        if (url == null || url.trim().isEmpty() || "about:blank".equals(url)) {
            url = BROWSER_HOME_URL;
        }
        browserUrlText.setText("Loading: " + url + "...");
        browserWebView.loadUrl(url);
        browserContainer.setVisibility(View.VISIBLE);
        browserWebView.requestFocus();
    }

    public void hideBrowser() {
        if (customView != null) {
            rootLayout.removeView(customView);
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        }
        browserWebView.loadUrl("about:blank");
        browserContainer.setVisibility(View.GONE);
        launcherWebView.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If in-app browser is active, handle browser navigation
        if (browserContainer.getVisibility() == View.VISIBLE) {
            if (customView != null) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    rootLayout.removeView(customView);
                    customView = null;
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                        customViewCallback = null;
                    }
                    browserContainer.setVisibility(View.VISIBLE);
                    browserWebView.requestFocus();
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (browserWebView.canGoBack()) {
                    browserWebView.goBack();
                } else {
                    hideBrowser();
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showSearchDialog();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                browserWebView.pageDown(false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                browserWebView.pageUp(false);
                return true;
            }

            return super.onKeyDown(keyCode, event);
        }

        // Home screen focus routing
        String key = null;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: key = "UP"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: key = "DOWN"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: key = "LEFT"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: key = "RIGHT"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: key = "SELECT"; break;
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
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveServer != null) liveServer.stop();
        if (fileObserver != null) fileObserver.stopWatching();
    }
}

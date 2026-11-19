package com.polymath.voidtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;

public class MainActivity extends Activity {
    private FrameLayout rootLayout;
    private WebView launcherWebView;
    private LinearLayout browserContainer;
    private WebView browserWebView;
    private TextView browserUrlText;
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
        toolbar.setBackgroundColor(Color.parseColor("#0C0C12"));
        toolbar.setPadding(24, 8, 24, 8);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnBack = createToolButton("◀ BACK", v -> {
            if (browserWebView.canGoBack()) browserWebView.goBack();
        });

        browserUrlText = new TextView(this);
        browserUrlText.setTextColor(Color.parseColor("#00E5FF"));
        browserUrlText.setTypeface(Typeface.MONOSPACE);
        browserUrlText.setTextSize(13);
        browserUrlText.setSingleLine(true);
        browserUrlText.setPadding(24, 0, 24, 0);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        browserUrlText.setLayoutParams(urlParams);

        Button btnReload = createToolButton("⟳ RELOAD", v -> browserWebView.reload());
        Button btnClose = createToolButton("✕ CLOSE", v -> hideBrowser());

        toolbar.addView(btnBack);
        toolbar.addView(browserUrlText);
        toolbar.addView(btnReload);
        toolbar.addView(btnClose);

        // In-App Browser WebView
        browserWebView = new WebView(this);
        WebSettings bs = browserWebView.getSettings();
        bs.setJavaScriptEnabled(true);
        bs.setDomStorageEnabled(true);
        bs.setBuiltInZoomControls(true);
        bs.setDisplayZoomControls(false);
        bs.setUseWideViewPort(true);
        bs.setLoadWithOverviewMode(true);
        bs.setUserAgentString("Mozilla/5.0 (Linux; Android 14; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Stay inside in-app browser
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                browserUrlText.setText(url != null ? url : "");
            }
        });

        browserWebView.setWebChromeClient(new WebChromeClient());

        browserContainer.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48));
        browserContainer.addView(browserWebView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private Button createToolButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#181824"));
        b.setPadding(16, 4, 16, 4);
        b.setOnClickListener(listener);
        b.setFocusable(true);
        return b;
    }

    public void showBrowser(String url) {
        if (url == null || url.trim().isEmpty()) {
            url = "https://duckduckgo.com";
        }
        browserUrlText.setText("Loading: " + url + "...");
        browserWebView.loadUrl(url);
        browserContainer.setVisibility(View.VISIBLE);
        browserWebView.requestFocus();
    }

    public void hideBrowser() {
        browserWebView.loadUrl("about:blank");
        browserContainer.setVisibility(View.GONE);
        launcherWebView.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If in-app browser is active, intercept browser navigation
        if (browserContainer.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (browserWebView.canGoBack()) {
                    browserWebView.goBack();
                } else {
                    hideBrowser();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                browserWebView.pageDown(false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
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

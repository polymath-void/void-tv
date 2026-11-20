package com.polymath.voidtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.FileObserver;
import android.text.Editable;
import android.text.TextWatcher;
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
    private AlertDialog searchDialog;

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
        liveServer = new LiveServer(4444, launcherWebView, this);
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

        tvBridge = new TvBridge(this);
        launcherWebView.addJavascriptInterface(tvBridge, "Android");
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
                browserUrlText.setText("Loading: " + prettyUrl(url) + "...");
                updateNavButtons();
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
        });
        dialog.setOnShowListener(d -> buildSuggestions(suggestions, input.getText().toString(), input, holder));
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
        final int code = keyCodeFor(name);
        if (code < 0) return false;
        runOnUiThread(() -> {
            long now = android.os.SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
            KeyEvent up = new KeyEvent(now, now + 20, KeyEvent.ACTION_UP, code, 0);
            if (searchDialog != null && searchDialog.isShowing()) {
                searchDialog.dispatchKeyEvent(down);
                searchDialog.dispatchKeyEvent(up);
            } else {
                dispatchKeyEvent(down);
                dispatchKeyEvent(up);
            }
        });
        return true;
    }

    @Override
    public boolean tap(String label) {
        if (label == null) label = "";
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

    private static int keyCodeFor(String name) {
        if (name == null) return -1;
        switch (name.trim().toUpperCase(java.util.Locale.ROOT)) {
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
            default:
                try {
                    int n = Integer.parseInt(name.trim());
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

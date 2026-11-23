package com.polymath.voidtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.Locale;

public class VoidAccessibilityService extends AccessibilityService {
    private static final String TAG = "VoidA11y";

    public static volatile VoidAccessibilityService instance;
    private String currentPackage = "com.polymath.voidtv";

    private WindowManager windowManager;
    private CursorOverlayView cursorView;
    private WindowManager.LayoutParams cursorParams;
    private float cursorX = 640f;
    private float cursorY = 360f;
    private boolean isCursorVisible = false;

    private int screenWidth = 1280;
    private int screenHeight = 720;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideCursorRunnable = () -> hideCursor();

    public static VoidAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public String getCurrentPackage() {
        return currentPackage != null ? currentPackage : "unknown";
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "VoidAccessibilityService bound and active");

        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (dm.widthPixels > 0) screenWidth = dm.widthPixels;
            if (dm.heightPixels > 0) screenHeight = dm.heightPixels;
            cursorX = screenWidth / 2f;
            cursorY = screenHeight / 2f;
        } catch (Exception ignored) {}

        initCursorOverlay();
        VoidControlHub.getInstance().init(getApplicationContext());
        VoidControlHub.getInstance().registerService(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && !pkg.toString().isEmpty()) {
                currentPackage = pkg.toString();
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "VoidAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeCursorOverlay();
        VoidControlHub.getInstance().unregisterService(this);
        if (instance == this) {
            instance = null;
        }
        Log.i(TAG, "VoidAccessibilityService destroyed");
    }

    // =========================================================================
    // Virtual Cursor Overlay
    // =========================================================================

    private void initCursorOverlay() {
        mainHandler.post(() -> {
            try {
                windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                if (windowManager == null) return;

                int sizePx = (int) (26f * getResources().getDisplayMetrics().density);
                cursorParams = new WindowManager.LayoutParams(
                    sizePx,
                    sizePx,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                );
                cursorParams.gravity = Gravity.TOP | Gravity.START;
                cursorParams.x = (int) cursorX;
                cursorParams.y = (int) cursorY;

                cursorView = new CursorOverlayView(this);
                cursorView.setVisibility(View.GONE);
                windowManager.addView(cursorView, cursorParams);
                isCursorVisible = false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize cursor overlay", e);
            }
        });
    }

    private void removeCursorOverlay() {
        mainHandler.post(() -> {
            try {
                if (windowManager != null && cursorView != null) {
                    windowManager.removeView(cursorView);
                    cursorView = null;
                }
            } catch (Exception ignored) {}
        });
    }

    private void showCursor() {
        mainHandler.removeCallbacks(hideCursorRunnable);
        if (cursorView != null && !isCursorVisible) {
            cursorView.setVisibility(View.VISIBLE);
            isCursorVisible = true;
        }
        mainHandler.postDelayed(hideCursorRunnable, 4000);
    }

    private void hideCursor() {
        if (cursorView != null && isCursorVisible) {
            cursorView.setVisibility(View.GONE);
            isCursorVisible = false;
        }
    }

    // =========================================================================
    // Remote Control Dispatchers
    // =========================================================================

    public boolean handleKey(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        final String rawKey = name.trim().toUpperCase(Locale.ROOT);

        // 1. Global Navigation Actions
        if ("BACK".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        }
        if ("HOME".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_HOME);
        }
        if ("RECENTS".equals(rawKey) || "APP_SWITCH".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_RECENTS);
        }
        if ("POWER".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
        if ("NOTIFICATIONS".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
        if ("QUICK_SETTINGS".equals(rawKey)) {
            return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        }

        // 2. Media Playback Control
        if ("PLAY".equals(rawKey) || "PAUSE".equals(rawKey) || "PLAY_PAUSE".equals(rawKey)
            || "STOP".equals(rawKey) || "NEXT".equals(rawKey) || "PREV".equals(rawKey)) {
            return dispatchMediaKey(rawKey);
        }

        // 3. Directional D-Pad Navigation
        if ("UP".equals(rawKey)) {
            return performDpad(GLOBAL_ACTION_DPAD_UP, View.FOCUS_UP, 0f, -220f);
        }
        if ("DOWN".equals(rawKey)) {
            return performDpad(GLOBAL_ACTION_DPAD_DOWN, View.FOCUS_DOWN, 0f, 220f);
        }
        if ("LEFT".equals(rawKey)) {
            return performDpad(GLOBAL_ACTION_DPAD_LEFT, View.FOCUS_LEFT, -260f, 0f);
        }
        if ("RIGHT".equals(rawKey)) {
            return performDpad(GLOBAL_ACTION_DPAD_RIGHT, View.FOCUS_RIGHT, 260f, 0f);
        }
        if ("SELECT".equals(rawKey) || "ENTER".equals(rawKey) || "OK".equals(rawKey)) {
            return performSelect();
        }

        // 4. Backspace / Delete
        if ("BACKSPACE".equals(rawKey) || "DEL".equals(rawKey)) {
            return deleteLastChar();
        }

        // 5. Space Key
        if ("SPACE".equals(rawKey) || " ".equals(rawKey)) {
            return appendChar(" ");
        }

        // 6. Alphanumeric / Character Input
        if (rawKey.length() == 1) {
            return appendChar(rawKey);
        }

        return false;
    }

    private boolean performDpad(int globalAction, int focusDirection, float swipeDx, float swipeDy) {
        // Fast Path: Android 12+ framework global DPAD key injection
        try {
            if (performGlobalAction(globalAction)) {
                return true;
            }
        } catch (Exception ignored) {}

        // Fallback 1: Node Focus Search
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focused != null) {
                    AccessibilityNodeInfo target = focused.focusSearch(focusDirection);
                    if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback 2: Swipe Gesture
        float cx = screenWidth / 2f;
        float cy = screenHeight / 2f;
        return dispatchSwipe(cx, cy, cx + swipeDx, cy + swipeDy, 120);
    }

    private boolean performSelect() {
        // Fast Path: Framework DPAD_CENTER
        try {
            if (performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) {
                return true;
            }
        } catch (Exception ignored) {}

        // Fallback 1: Try clicking on-screen Search / Done / Go button if present
        if (tapByLabel("Search") || tapByLabel("Done") || tapByLabel("Go")) {
            return true;
        }

        // Fallback 2: Focused Node Click
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // Fallback 3: Tap at cursor / center
        return dispatchTap(cursorX, cursorY);
    }

    private boolean deleteLastChar() {
        try {
            AccessibilityNodeInfo input = findTargetInputNode();
            if (input != null) {
                CharSequence current = input.getText();
                if (current != null && current.length() > 0) {
                    CharSequence deleted = current.subSequence(0, current.length() - 1);
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, deleted);
                    if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        return true;
                    }
                }
            }

            // Fallback 1: Tap Delete / Backspace button on on-screen keyboard
            if (tapByLabel("Delete") || tapByLabel("⌫") || tapByLabel("Backspace") || tapByLabel("Clear")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean appendChar(String ch) {
        try {
            AccessibilityNodeInfo input = findTargetInputNode();
            if (input != null) {
                CharSequence current = input.getText();
                String newVal = (current != null ? current.toString() : "") + ch;
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newVal);
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return true;
                }
            }

            // Fallback 1: Tap letter button on on-screen keyboard (Cobalt UI / YouTube TV)
            if (" ".equals(ch)) {
                if (tapByLabel("Space") || tapByLabel("SPACE")) return true;
            } else {
                if (tapByLabel(ch) || tapByLabel(ch.toUpperCase(Locale.ROOT)) || tapByLabel(ch.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean injectText(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        try {
            // 1. Try to find target input node across all windows
            AccessibilityNodeInfo input = findTargetInputNode();
            if (input != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    // Trigger search after setting text
                    try {
                        performGlobalAction(GLOBAL_ACTION_DPAD_CENTER);
                    } catch (Exception ignored) {}
                    return true;
                }

                // Fallback: Clipboard Paste
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("voidtv_input", text));
                    if (input.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                        return true;
                    }
                }
            }

            // 2. Fallback: If YouTube TV is foreground, launch native YouTube search intent!
            if (currentPackage != null && currentPackage.contains("youtube")) {
                try {
                    Intent searchIntent = new Intent(Intent.ACTION_SEARCH);
                    searchIntent.setPackage("com.google.android.youtube.tv");
                    searchIntent.putExtra("query", text);
                    searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(searchIntent);
                    return true;
                } catch (Exception e1) {
                    try {
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(text)));
                        viewIntent.setPackage("com.google.android.youtube.tv");
                        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(viewIntent);
                        return true;
                    } catch (Exception ignored) {}
                }
            }

            // 3. Fallback: Type characters one by one via on-screen buttons
            boolean anySuccess = false;
            for (int i = 0; i < text.length(); i++) {
                String c = String.valueOf(text.charAt(i));
                if (appendChar(c)) {
                    anySuccess = true;
                }
            }
            if (anySuccess) {
                tapByLabel("Search");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error injecting text", e);
        }
        return false;
    }

    public AccessibilityNodeInfo findTargetInputNode() {
        // 1. Try active window
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo node = findInputInHierarchy(root);
            if (node != null) return node;
        }

        // 2. Try all windows (application, input method, system dialogs)
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo win : windows) {
                    AccessibilityNodeInfo winRoot = win.getRoot();
                    if (winRoot != null) {
                        AccessibilityNodeInfo node = findInputInHierarchy(winRoot);
                        if (node != null) return node;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private AccessibilityNodeInfo findInputInHierarchy(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // A. Input focus
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null) return focused;
        } catch (Exception ignored) {}

        // B. Accessibility focus if editable
        try {
            AccessibilityNodeInfo a11yFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (a11yFocused != null && isEditableNode(a11yFocused)) {
                return a11yFocused;
            }
        } catch (Exception ignored) {}

        // C. Deep recursive search for any editable node
        return searchEditableRecursive(root, 0);
    }

    private AccessibilityNodeInfo searchEditableRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 15) return null;

        if (isEditableNode(node)) {
            return node;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = searchEditableRecursive(child, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }

    private boolean isEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isEditable()) return true;
        if (supportsAction(node, AccessibilityNodeInfo.ACTION_SET_TEXT)) return true;

        CharSequence className = node.getClassName();
        if (className != null) {
            String cls = className.toString();
            if (cls.contains("EditText")) return true;
            if (cls.contains("SearchView") || cls.contains("SearchAutoComplete")) return true;
        }
        return false;
    }

    private boolean supportsAction(AccessibilityNodeInfo node, int actionId) {
        if (node == null) return false;
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions != null) {
            for (AccessibilityNodeInfo.AccessibilityAction act : actions) {
                if (act.getId() == actionId) return true;
            }
        }
        return (node.getActions() & actionId) != 0;
    }

    public boolean handleMouse(float dx, float dy, boolean click, boolean scroll, String action) {
        // 1. Update coordinates
        cursorX = Math.max(10f, Math.min(screenWidth - 10f, cursorX + dx));
        cursorY = Math.max(10f, Math.min(screenHeight - 10f, cursorY + dy));

        // 2. Update Overlay View
        mainHandler.post(() -> {
            showCursor();
            if (cursorParams != null && cursorView != null && windowManager != null) {
                cursorParams.x = (int) cursorX;
                cursorParams.y = (int) cursorY;
                try {
                    windowManager.updateViewLayout(cursorView, cursorParams);
                } catch (Exception ignored) {}
            }
        });

        // 3. Dispatch Click
        if (click || "click".equalsIgnoreCase(action)) {
            return dispatchTap(cursorX, cursorY);
        }

        // 4. Dispatch Scroll
        if (scroll || "scroll".equalsIgnoreCase(action)) {
            return dispatchSwipe(cursorX, cursorY, cursorX - dx * 2f, cursorY - dy * 2f, 150);
        }

        return true;
    }

    public boolean tapByLabel(String label) {
        if (label == null || label.trim().isEmpty()) return false;
        String target = label.trim();
        try {
            // 1. Try active window
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && tapNodeByText(root, target)) {
                return true;
            }

            // 2. Try all windows (e.g. keyboard window)
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        AccessibilityNodeInfo winRoot = win.getRoot();
                        if (winRoot != null && tapNodeByText(winRoot, target)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "Failed to tap by label: " + label, e);
        }
        return false;
    }

    private boolean tapNodeByText(AccessibilityNodeInfo root, String target) {
        if (root == null) return false;

        // Try exact match first
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                boolean matches = (text != null && text.toString().equalsIgnoreCase(target))
                    || (desc != null && desc.toString().equalsIgnoreCase(target));
                if (matches) {
                    if (clickNodeOrParent(node)) return true;
                }
            }
            // If exact didn't match, try any returned node
            for (AccessibilityNodeInfo node : nodes) {
                if (clickNodeOrParent(node)) return true;
            }
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    // =========================================================================
    // Low-Level Gesture Primitives
    // =========================================================================

    public boolean dispatchTap(float x, float y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            path.lineTo(x + 0.5f, y + 0.5f); // Sub-pixel stroke for Android 8-14 compatibility
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 45);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            return dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch tap at (" + x + "," + y + ")", e);
            return false;
        }
    }

    public boolean dispatchSwipe(float startX, float startY, float endX, float endY, long duration) {
        try {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(duration, 50));
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            return dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch swipe", e);
            return false;
        }
    }

    private boolean dispatchMediaKey(String action) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        int code = -1;
        switch (action) {
            case "PLAY": code = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "PAUSE": code = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case "PLAY_PAUSE": code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; break;
            case "STOP": code = KeyEvent.KEYCODE_MEDIA_STOP; break;
            case "NEXT": code = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "PREV": code = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
        }
        if (code >= 0) {
            long now = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(now, now + 30, KeyEvent.ACTION_UP, code, 0));
            return true;
        }
        return false;
    }

    // =========================================================================
    // Antialiased Cursor View Overlay
    // =========================================================================

    private static class CursorOverlayView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CursorOverlayView(Context context) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;

            fillPaint.setColor(0xEEFFFFFF); // Brilliant core
            fillPaint.setStyle(Paint.Style.FILL);

            ringPaint.setColor(0xFF00E5FF); // Neon cyan ring
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(2.5f * density);

            outerGlowPaint.setColor(0x5500E5FF); // Ambient cyan glow
            outerGlowPaint.setStyle(Paint.Style.STROKE);
            outerGlowPaint.setStrokeWidth(5f * density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - ringPaint.getStrokeWidth() * 1.5f;

            canvas.drawCircle(cx, cy, r, outerGlowPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);
            canvas.drawCircle(cx, cy, r * 0.45f, fillPaint);
        }
    }
}

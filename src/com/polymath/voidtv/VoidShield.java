package com.polymath.voidtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VoidShield: Built-in, high-performance AdBlocker, Anti-Malware,
 * and Anti-Popup Redirect Protection Engine for VoidTV WebViews.
 */
public class VoidShield {
    private static final String PREF_NAME = "void_browser_prefs";
    private static final String KEY_ADBLOCK = "adblock_enabled";
    private static final String KEY_POPUPS = "popups_blocked";
    private static final String KEY_DESKTOP_UA = "desktop_ua";
    private static final String KEY_COOKIES = "cookies_enabled";

    private final SharedPreferences prefs;
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    // Pre-indexed set of known ad, malware, and popup redirect host suffixes
    private static final Set<String> BLOCKED_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        // Major Ad & Tracking Networks
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "adroll.com",
        "appnexus.com",
        "smartadserver.com",
        "inmobi.com",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "mgid.com",
        "zeroredirect.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "statcounter.com",

        // Video Player Popunder, Deceptive Click-Jacking & Redirect Hijackers
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "trafficjunky.com",
        "yllix.com",
        "adcash.com",
        "clickadu.com",
        "onclickbright.com",
        "exdynsrv.com",
        "tsyndicate.com",
        "ad-delivery.net",
        "adtrue.com",
        "bidgear.com",
        "histats.com",
        "realsrv.com",
        "adsterra.com",
        "monetag.com",
        "hilltopads.com",
        "richpush.com",
        "admaven.com",
        "viralup.com",
        "juicyads.com",
        "trafficfactory.biz",
        "clksite.com",
        "adskeeper.com",
        "adblade.com",
        "popmyads.com",
        "popunder.net",
        "popunderclick.com",
        "syndication.exoclick.com",
        "adsupply.com",
        "bet365.com",
        "1xbet.com",
        "mostbet.com",

        // In-Browser Crypto Miners & Malicious Coin Trackers
        "coinhive.com",
        "coin-hive.com",
        "crypto-loot.com",
        "webminepool.com",
        "monerominer.rocks",
        "minr.pw"
    )));

    // Empty 200 OK response for silently neutralizing blocked assets
    private static final WebResourceResponse EMPTY_RESPONSE =
        new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));

    public VoidShield(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAdBlockEnabled() {
        return prefs.getBoolean(KEY_ADBLOCK, true);
    }

    public void setAdBlockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ADBLOCK, enabled).apply();
    }

    public boolean isPopupBlockEnabled() {
        return prefs.getBoolean(KEY_POPUPS, true);
    }

    public void setPopupBlockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_POPUPS, enabled).apply();
    }

    public boolean isDesktopUaEnabled() {
        return prefs.getBoolean(KEY_DESKTOP_UA, false);
    }

    public void setDesktopUaEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DESKTOP_UA, enabled).apply();
    }

    public boolean isCookiesEnabled() {
        return prefs.getBoolean(KEY_COOKIES, true);
    }

    public void setCookiesEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COOKIES, enabled).apply();
    }

    public int getBlockedCount() {
        return blockedCount.get();
    }

    public void resetBlockedCount() {
        blockedCount.set(0);
    }

    /**
     * Inspects a resource request. If it belongs to an ad, tracking beacon,
     * or malware domain, returns an empty WebResourceResponse to block it.
     */
    public WebResourceResponse interceptResource(Uri uri) {
        if (!isAdBlockEnabled() || uri == null) return null;

        String host = uri.getHost();
        if (host != null) {
            String lowerHost = host.toLowerCase(Locale.ROOT);
            for (String blocked : BLOCKED_HOSTS) {
                if (lowerHost.equals(blocked) || lowerHost.endsWith("." + blocked)) {
                    blockedCount.incrementAndGet();
                    return EMPTY_RESPONSE;
                }
            }
        }

        String path = uri.getPath();
        if (path != null) {
            String p = path.toLowerCase(Locale.ROOT);
            if (p.contains("/popunder") || p.contains("/popup.js") || p.contains("/adbanner")
                || p.contains("/ads.js") || p.contains("/adservice") || p.contains("/adskeeper")
                || p.contains("/prebid") || p.contains("/pagead/")) {
                blockedCount.incrementAndGet();
                return EMPTY_RESPONSE;
            }
        }

        return null;
    }

    /**
     * Checks whether an attempted page navigation / redirect should be blocked.
     * Prevents rogue video player overlays from hijacking the tab.
     */
    public boolean shouldBlockNavigation(String url) {
        if (url == null || url.isEmpty()) return true;

        String lower = url.toLowerCase(Locale.ROOT).trim();
        // Block non-web rogue schemes that disrupt TV experience
        if (lower.startsWith("intent:") || lower.startsWith("market:")
            || lower.startsWith("tel:") || lower.startsWith("mailto:")
            || lower.startsWith("sms:") || lower.startsWith("viber:")
            || lower.startsWith("whatsapp:") || lower.startsWith("tg:")) {
            blockedCount.incrementAndGet();
            return true;
        }

        if (isPopupBlockEnabled() || isAdBlockEnabled()) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                String lowerHost = host.toLowerCase(Locale.ROOT);
                for (String blocked : BLOCKED_HOSTS) {
                    if (lowerHost.equals(blocked) || lowerHost.endsWith("." + blocked)) {
                        blockedCount.incrementAndGet();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Injects client-side anti-popup and anti-clickjack protection into the web page.
     * Defuses window.open and neutralizes deceptive invisible overlays placed over video players.
     */
    public static String getAntiPopupScript() {
        return "(function() {\n"
            + "  if (window.__voidShieldActive) return;\n"
            + "  window.__voidShieldActive = true;\n"
            + "  \n"
            + "  // 1. Defuse window.open rogue popups\n"
            + "  var origOpen = window.open;\n"
            + "  window.open = function(url, target, features) {\n"
            + "    console.log('[VoidShield] Blocked rogue window.open:', url);\n"
            + "    return null;\n"
            + "  };\n"
            + "  \n"
            + "  // 2. Neutralize transparent clickjack overlays\n"
            + "  function sanitizeOverlays() {\n"
            + "    try {\n"
            + "      var links = document.querySelectorAll('a[target=\"_blank\"]');\n"
            + "      for (var i = 0; i < links.length; i++) {\n"
            + "        var a = links[i];\n"
            + "        var href = (a.href || '').toLowerCase();\n"
            + "        if (href.indexOf('http') === 0 && href.indexOf(location.hostname) === -1) {\n"
            + "          a.removeAttribute('target');\n"
            + "          a.addEventListener('click', function(e) {\n"
            + "            e.stopPropagation();\n"
            + "          }, true);\n"
            + "        }\n"
            + "      }\n"
            + "    } catch(e) {}\n"
            + "  }\n"
            + "  \n"
            + "  if (document.readyState === 'loading') {\n"
            + "    document.addEventListener('DOMContentLoaded', sanitizeOverlays);\n"
            + "  } else {\n"
            + "    sanitizeOverlays();\n"
            + "  }\n"
            + "  setInterval(sanitizeOverlays, 1500);\n"
            + "})();";
    }

    /**
     * JSON snapshot of current configuration for LiveServer and AgentAPI.
     */
    public String getStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("adblock", isAdBlockEnabled());
            json.put("popups", isPopupBlockEnabled());
            json.put("desktopUa", isDesktopUaEnabled());
            json.put("cookies", isCookiesEnabled());
            json.put("blockedCount", getBlockedCount());
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}

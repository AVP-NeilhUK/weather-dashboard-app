package uk.avp.weather;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int LOCATION_REQUEST = 1;

    private WebView web;

    // Held while Android asks the user for location permission
    private String pendingOrigin;
    private GeolocationPermissions.Callback pendingCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Serves the bundled index.html from a proper https origin,
        // so fetch(), localStorage and location behave as they do on a website.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFF0A0E14);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (HOST.equals(request.getUrl().getHost())) return false;
                // Links elsewhere (such as the Met Office warning pages) open in the phone's browser
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingOrigin = origin;
                    pendingCallback = callback;
                    requestPermissions(new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQUEST);
                }
            }
        });

        web.addJavascriptInterface(new Bridge(), "AVPBridge");

        setContentView(web);
        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    // Lets the page read the Met Office warnings feed, which web pages can't fetch directly.
    // Only Met Office addresses are allowed through.
    private class Bridge {
        @JavascriptInterface
        public void fetchText(final String id, final String url) {
            if (url == null || !url.startsWith("https://weather.metoffice.gov.uk/")) {
                deliver(id, false, "address not allowed");
                return;
            }
            new Thread(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "WeatherDashboard/1.0 (Android)");
                    int code = c.getResponseCode();
                    InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        in.close();
                    }
                    String body = out.toString("UTF-8");
                    deliver(id, code < 400, code < 400 ? body : "HTTP " + code);
                } catch (Exception e) {
                    deliver(id, false, e.getClass().getSimpleName());
                } finally {
                    if (c != null) c.disconnect();
                }
            }).start();
        }
    }

    private void deliver(final String id, final boolean ok, final String text) {
        final String js = "window.__bridgeDone && window.__bridgeDone("
                + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(text) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == LOCATION_REQUEST && pendingCallback != null) {
            pendingCallback.invoke(pendingOrigin, hasLocationPermission(), false);
            pendingCallback = null;
            pendingOrigin = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }
}

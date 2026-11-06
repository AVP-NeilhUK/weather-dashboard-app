package uk.avp.weather;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

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
                // Stay inside the app; ignore any navigation elsewhere
                return !HOST.equals(request.getUrl().getHost());
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

        setContentView(web);
        web.loadUrl("https://" + HOST + "/assets/index.html");
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

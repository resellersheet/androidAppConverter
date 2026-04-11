package com.yourdomain.webviewapp;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences fcmPrefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fcmPrefs = getSharedPreferences("fcm_prefs", MODE_PRIVATE);

        progressBar        = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        webView            = findViewById(R.id.webview);

        // ✅ Enable cookies for PHP session
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ✅ Fetch and store FCM token in SharedPreferences
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "FCM token fetch failed", task.getException());
                    return;
                }
                String newToken   = task.getResult();
                String savedToken = fcmPrefs.getString("fcm_token", "");

                if (!newToken.equals(savedToken)) {
                    fcmPrefs.edit()
                        .putString("fcm_token", newToken)
                        .apply();
                    Log.d(TAG, "FCM token stored in SharedPreferences");
                }
            });

        // ✅ Inject Android JS interface so the login page can read the FCM token
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);

        String siteUrl = getString(R.string.site_url);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                // ✅ If this is the login page, inject FCM token into the form
                // so it gets submitted together with email + password
                if (url.contains("login")) {
                    String fcmToken = fcmPrefs.getString("fcm_token", "");
                    if (!fcmToken.isEmpty()) {
                        // Inject a hidden input field into the login form
                        String js = "javascript:(function() {" +
                            "var forms = document.getElementsByTagName('form');" +
                            "if (forms.length > 0) {" +
                            "  var input = document.createElement('input');" +
                            "  input.type  = 'hidden';" +
                            "  input.name  = 'fcm_token';" +
                            "  input.value = '" + fcmToken.replace("'", "\\'") + "';" +
                            "  forms[0].appendChild(input);" +
                            "  console.log('FCM token injected into login form');" +
                            "}" +
                            "})()";
                        view.loadUrl(js);
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.loadUrl(siteUrl);
    }

    /**
     * ✅ JS Bridge — lets the login page JS read the FCM token from Android
     * Usage in JS: var token = AndroidBridge.getFcmToken();
     */
    public class AndroidBridge {
        @JavascriptInterface
        public String getFcmToken() {
            return fcmPrefs.getString("fcm_token", "");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

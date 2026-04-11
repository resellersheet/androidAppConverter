package com.yourdomain.webviewapp;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Store FCM token to send after page loads (so session cookie exists)
    private String pendingFcmToken = null;
    private boolean tokenSent = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar    = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        webView        = findViewById(R.id.webview);

        // ─── Enable cookie persistence ───────────────────────────────────────
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // ─── Get FCM token early; send it once the page (session) is ready ───
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) return;
                pendingFcmToken = task.getResult();
                // If the page already finished loading before we got the token,
                // send it right now.
                if (tokenSent == false && pendingFcmToken != null) {
                    trySendToken();
                }
            });

        // ─── Swipe-refresh setup ─────────────────────────────────────────────
        swipeRefreshLayout.setOnChildScrollUpCallback(
            (parent, child) -> webView.getScrollY() > 0
        );

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

        // ─── WebView settings ────────────────────────────────────────────────
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // ─── WebViewClient ───────────────────────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                // Persist cookies to disk so subsequent HTTP calls can use them
                CookieManager.getInstance().flush();

                // Send FCM token now that the session cookie is established
                if (!tokenSent && pendingFcmToken != null) {
                    trySendToken();
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

        // ─── WebChromeClient ─────────────────────────────────────────────────
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

        // ─── Load site ───────────────────────────────────────────────────────
        webView.loadUrl(getString(R.string.site_url));
    }

    /**
     * Send FCM token to server WITH the session cookie so PHP knows who is
     * logged in. Uses the same cookie jar as the WebView.
     */
    private void trySendToken() {
        if (tokenSent || pendingFcmToken == null) return;
        tokenSent = true; // prevent duplicate calls

        final String token = pendingFcmToken;

        new Thread(() -> {
            try {
                // ── Grab cookies that WebView stored for your domain ──────────
                String cookieString = CookieManager.getInstance()
                    .getCookie("https://themchat.com");

                URL url = new URL("https://themchat.com/api/save_fcm_token.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");

                // ── Attach the session cookie ─────────────────────────────────
                if (cookieString != null && !cookieString.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookieString);
                }

                // ── POST body ─────────────────────────────────────────────────
                String postData = "token=" +
                    java.net.URLEncoder.encode(token, "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                android.util.Log.d("FCM", "Token save response: " + responseCode);
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                tokenSent = false; // allow retry on next page load
            }
        }).start();
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

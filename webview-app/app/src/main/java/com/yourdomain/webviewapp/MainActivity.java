package com.yourdomain.webviewapp;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

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

        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        webView = findViewById(R.id.webview);

        // ✅ Enable cookies (required for PHP session auth)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ✅ Fetch FCM token and store in SharedPreferences if changed
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "FCM token fetch failed", task.getException());
                    return;
                }
                String token = task.getResult();
                String savedToken = fcmPrefs.getString("pending_token", "");

                if (!token.equals(savedToken)) {
                    fcmPrefs.edit()
                        .putString("pending_token", token)
                        .putBoolean("token_sent", false)
                        .apply();
                    Log.d(TAG, "New FCM token stored, will send after login");
                }
            });

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

                // ✅ After every page load, attempt to send pending token
                boolean tokenSent = fcmPrefs.getBoolean("token_sent", false);
                String pendingToken = fcmPrefs.getString("pending_token", "");

                if (!tokenSent && !pendingToken.isEmpty()) {
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null && cookies.contains("PHPSESSID")) {
                        sendTokenToServer(pendingToken, cookies);
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
     * ✅ Send FCM token to PHP using WebView session cookies so PHP can identify the user
     */
    private void sendTokenToServer(String token, String cookies) {
        new Thread(() -> {
            try {
                URL url = new URL("https://themchat.com/api/save_fcm_token.php");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("Referer", "https://themchat.com/");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String postData = "token=" + URLEncoder.encode(token, "UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                conn.disconnect();

                String body = response.toString();
                Log.d(TAG, "Token save response (" + responseCode + "): " + body);

                if (body.contains("\"success\"")) {
                    fcmPrefs.edit().putBoolean("token_sent", true).apply();
                    Log.d(TAG, "FCM token saved to server successfully!");
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send FCM token: " + e.getMessage(), e);
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

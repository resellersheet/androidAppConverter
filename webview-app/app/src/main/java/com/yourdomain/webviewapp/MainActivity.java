package com.yourdomain.webviewapp;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    private String userId = null; // ✅ will be set from WebView

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        webView = findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // ✅ Add JS interface
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // ✅ Get FCM token
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;

                    String token = task.getResult();

                    // Wait until userId is available
                    new Thread(() -> {
                        try {
                            int retries = 0;

                            while (userId == null && retries < 10) {
                                Thread.sleep(1000);
                                retries++;
                            }

                            if (userId != null) {
                                sendTokenToServer(token, userId);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                });

        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);

        String url = getString(R.string.site_url);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                // 🔥 Inject JS to fetch user ID if needed
                webView.evaluateJavascript(
                        "javascript:(function() { " +
                                "if (window.Android && window.user_id) { Android.setUserId(window.user_id); }" +
                                "})()",
                        null
                );
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

        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl(url);
    }

    // ✅ JS Interface
    public class WebAppInterface {
        @JavascriptInterface
        public void setUserId(String id) {
            userId = id;
        }
    }

    // ✅ Send token + user_id
    private void sendTokenToServer(String token, String userId) {
        new Thread(() -> {
            try {
                URL url = new URL("https://themchat.com/save_fcm_token.php");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String postData = "token=" + token + "&user_id=" + userId;

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
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

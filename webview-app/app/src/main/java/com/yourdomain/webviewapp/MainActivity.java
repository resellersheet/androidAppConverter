package com.yourdomain.webviewapp;

import android.annotation.SuppressLint;
import android.content.Intent;
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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String TAG        = "MainActivity";
    private static final int    RC_SIGN_IN = 9001;

    // ✅ Replace with your Web Client ID from Google Cloud Console
    // (Not the Android client ID — the WEB one, found under OAuth 2.0 credentials)
    private static final String WEB_CLIENT_ID = "539210452254-2a29k7o3u675laqimqij032d6ln8vqq0.apps.googleusercontent.com";

    private WebView             webView;
    private ProgressBar         progressBar;
    private SwipeRefreshLayout  swipeRefreshLayout;
    private SharedPreferences   fcmPrefs;
    private GoogleSignInClient  mGoogleSignInClient;

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

        // ✅ Set up Google Sign-In SDK
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(WEB_CLIENT_ID)
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // ✅ Fetch and store FCM token
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "FCM token fetch failed", task.getException());
                    return;
                }
                String newToken   = task.getResult();
                String savedToken = fcmPrefs.getString("fcm_token", "");
                if (!newToken.equals(savedToken)) {
                    fcmPrefs.edit().putString("fcm_token", newToken).apply();
                    Log.d(TAG, "FCM token stored in SharedPreferences");
                }
            });

        // ✅ Attach the JS bridge (AndroidBridge) — now includes Google Sign-In trigger
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // ✅ Tag the user agent so the login page can detect WebView reliably
        String currentUA = webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(currentUA + " ThemchatWebView");

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

                // ✅ Inject FCM token into login form hidden field
                if (url.contains("login")) {
                    String fcmToken = fcmPrefs.getString("fcm_token", "");
                    if (!fcmToken.isEmpty()) {
                        String js = "javascript:(function() {" +
                            "var forms = document.getElementsByTagName('form');" +
                            "if (forms.length > 0) {" +
                            "  var input = document.createElement('input');" +
                            "  input.type  = 'hidden';" +
                            "  input.name  = 'fcm_token';" +
                            "  input.value = '" + fcmToken.replace("'", "\\'") + "';" +
                            "  forms[0].appendChild(input);" +
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

    // ─────────────────────────────────────────────────────────────
    // ✅ Called from JS via AndroidBridge.startGoogleLogin()
    // ─────────────────────────────────────────────────────────────
    public void signInWithGoogle() {
        // Sign out first so the account picker always shows
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ Handle Google Sign-In result, pass data back to WebView JS
    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);

                String email   = account.getEmail()          != null ? account.getEmail()          : "";
                String name    = account.getDisplayName()    != null ? account.getDisplayName()    : "";
                String idToken = account.getIdToken()        != null ? account.getIdToken()        : "";
                String photoUrl = account.getPhotoUrl()      != null ? account.getPhotoUrl().toString() : "";

                // Escape single quotes to avoid breaking the JS string
                String safeEmail   = email.replace("'", "\\'");
                String safeName    = name.replace("'", "\\'");
                String safeToken   = idToken.replace("'", "\\'");
                String safePhoto   = photoUrl.replace("'", "\\'");

                // ✅ Call the JS function defined in your login page
                String js = "javascript:receiveGoogleUser('" + safeEmail + "','" + safeName + "','" + safeToken + "','" + safePhoto + "')";
                webView.post(() -> webView.loadUrl(js));

                Log.d(TAG, "Google Sign-In success: " + email);

            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed, code: " + e.getStatusCode());
                // ✅ Notify the page that login failed
                webView.post(() -> webView.loadUrl("javascript:googleLoginFailed(" + e.getStatusCode() + ")"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ JS Bridge — exposes methods to your HTML/JS login page
    // ─────────────────────────────────────────────────────────────
    public class AndroidBridge {

        /** Returns the FCM push token to inject into the login form */
        @JavascriptInterface
        public String getFcmToken() {
            return fcmPrefs.getString("fcm_token", "");
        }

        /** Triggered by the Google button in WebView — launches native Google Sign-In */
        @JavascriptInterface
        public void startGoogleLogin() {
            runOnUiThread(() -> signInWithGoogle());
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

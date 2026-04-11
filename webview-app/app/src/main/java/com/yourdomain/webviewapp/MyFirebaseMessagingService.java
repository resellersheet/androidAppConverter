package com.yourdomain.webviewapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "themchat_notifications";

    /**
     * ✅ Called whenever Firebase generates a NEW token
     * (after reinstall, token expiry, or app data clear)
     * This ensures your DB always has a fresh token.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token generated: " + token);

        // ✅ Save new token to server automatically
        // NOTE: We can't use session cookies here (no WebView context)
        // So we save it to SharedPreferences and send it next time MainActivity loads
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_token", token)
            .putBoolean("token_sent", false)
            .apply();

        Log.d(TAG, "New token stored in SharedPreferences — will be sent on next app open");
    }

    /**
     * ✅ Called when a push notification arrives while app is in FOREGROUND
     * Without this, foreground notifications are silently ignored on Android
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // ✅ Get notification data
        String title = "New Notification";
        String body  = "Tap to view";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                ? remoteMessage.getNotification().getTitle() : title;
            body  = remoteMessage.getNotification().getBody() != null
                ? remoteMessage.getNotification().getBody() : body;
        }

        // ✅ Show notification manually when app is in foreground
        showNotification(title, body);
    }

    /**
     * ✅ Build and display the notification
     */
    private void showNotification(String title, String body) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ThemChat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Likes and comments on your posts");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        // Tap notification → open app
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // ✅ Make sure this icon exists in res/drawable
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}

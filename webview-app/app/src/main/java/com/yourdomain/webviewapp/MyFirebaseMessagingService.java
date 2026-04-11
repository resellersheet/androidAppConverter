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

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG        = "FCMService";
    private static final String CHANNEL_ID = "themchat_notifications";

    /**
     * ✅ Called by Firebase whenever a new token is generated
     * (reinstall, app data clear, token expiry)
     * Stores it in SharedPreferences so MainActivity injects it into the login form
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token generated");

        // Store token — MainActivity will inject it into login form on next login
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply();
    }

    /**
     * ✅ Called when notification arrives while app is in FOREGROUND
     * Android suppresses FCM notifications in foreground by default —
     * this method shows them manually
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received");

        String title = "New Notification";
        String body  = "Tap to view";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null)
                title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null)
                body = remoteMessage.getNotification().getBody();
        }

        showNotification(title, body);
    }

    /**
     * ✅ Build and display the notification on screen
     */
    private void showNotification(String title, String body) {
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Required for Android 8+
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

        // Tap notification → open MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, CHANNEL_ID)
                // ✅ FIXED: uses your existing app launcher icon — no missing resource
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}

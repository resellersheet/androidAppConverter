package com.yourdomain.webviewapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG        = "FCMService";
    private static final String CHANNEL_ID = "themchat_notifications";

    /**
     * ✅ Called whenever Firebase generates a new token
     * Stores in SharedPreferences — MainActivity sends it after login
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token generated");
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply();
    }

    /**
     * ✅ Called when notification arrives while app is in FOREGROUND
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received");

        String title    = "New Notification";
        String body     = "Tap to view";
        String imageUrl = null;

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null)
                title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null)
                body = remoteMessage.getNotification().getBody();
            // ✅ Get image URL from notification payload
            if (remoteMessage.getNotification().getImageUrl() != null)
                imageUrl = remoteMessage.getNotification().getImageUrl().toString();
        }

        // Also check data payload for image
        if (imageUrl == null && remoteMessage.getData().containsKey("image")) {
            imageUrl = remoteMessage.getData().get("image");
        }

        showNotification(title, body, imageUrl);
    }

    /**
     * ✅ Build and display the notification with:
     * - App logo as small icon in the status bar
     * - Sender profile picture as large icon (circular)
     */
    private void showNotification(String title, String body, String imageUrl) {
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ThemChat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Likes, comments and mentions");
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
                // ✅ App logo in status bar (small icon — must be white/transparent PNG)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        // ✅ Load sender's profile picture as large icon (circular crop)
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Bitmap profileBitmap = downloadBitmap(imageUrl);
            if (profileBitmap != null) {
                // Crop to circle so it looks like a profile picture
                Bitmap circularBitmap = toCircleBitmap(profileBitmap);
                builder.setLargeIcon(circularBitmap);
            }
        } else {
            // Fallback: use app icon as large icon
            builder.setLargeIcon(
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)
            );
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * ✅ Download image from URL on background thread
     */
    private Bitmap downloadBitmap(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            InputStream input = conn.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.e(TAG, "Failed to download profile image: " + e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Crop a bitmap into a circle (for profile pictures)
     */
    private Bitmap toCircleBitmap(Bitmap bitmap) {
        int size   = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Rect rect = new Rect(0, 0, size, size);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
}

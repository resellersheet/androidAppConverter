package com.yourdomain.webviewapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        // 1. Find the ImageView in your splash layout
        ImageView logoImageView = findViewById(R.id.logo);

        // 2. URL to the user-uploaded logo (replace with dynamic URL logic if needed)
        String logoUrl = "https://activespreadsheet.com/uploads/logo_user123.png";

        // 3. Load the image using Glide
        Glide.with(this)
            .load(logoUrl)
            .placeholder(R.drawable.ic_launcher_foreground) // Optional placeholder
            .error(R.drawable.ic_launcher_background) // Optional error fallback
            .into(logoImageView);

        // 4. Delay 2 seconds then launch MainActivity
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 2000);
    }
}

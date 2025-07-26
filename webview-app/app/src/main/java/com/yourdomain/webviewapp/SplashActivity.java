package com.yourdomain.webviewapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logoImageView);
        TextView appName = findViewById(R.id.appNameTextView);

        // Set app name from strings.xml (user input)
        appName.setText(getString(R.string.app_name));

        // Load logo from local drawable resource "your_logo.png"
        int logoResId = getResources().getIdentifier("your_logo", "drawable", getPackageName());
        Glide.with(this)
            .load(logoResId)
            .into(logo);

        // Delay 1.5 seconds then open MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1500);
    }
}

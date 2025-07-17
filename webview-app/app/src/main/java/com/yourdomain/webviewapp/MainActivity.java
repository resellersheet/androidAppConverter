package com.yourdomain.webviewapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import android.widget.ImageView;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logoImageView);
        Glide.with(this)
             .load(R.drawable.ic_launcher)  // your launcher icon here or downloaded logo
             .into(logo);

        // Delay and then start MainActivity (simplified)
        logo.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1500);
    }
}

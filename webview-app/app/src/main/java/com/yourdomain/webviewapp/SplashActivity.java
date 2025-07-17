package com.yourdomain.webviewapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class SplashActivity extends AppCompatActivity {
    private static final String LOGO_URL = "https://activespreadsheet.com/uploads/your_logo.png"; // Replace dynamically if needed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logoImageView);
        Glide.with(this)
             .load(LOGO_URL)
             .into(logo);

        // Delay then start MainActivity
        logo.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1500);
    }
}

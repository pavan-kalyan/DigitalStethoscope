package com.example.pavan.digitalstethoscope;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class settings extends AppCompatActivity {
    TextView iptext;
    TextView usertext;
    TextView password;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        iptext = findViewById(R.id.ip_id);
        usertext = findViewById(R.id.username_id);
        password = findViewById(R.id.password_id);

    }

    public void sendToMain(View view) {
        String getip = String.valueOf(iptext.getText());
        String getuser = String.valueOf(usertext.getText());
        String getpsswd = String.valueOf(password.getText());
        Constants.USER_NAME = getuser;
        Constants.PASSWORD = getpsswd;
        SharedPreferences preferences = getSharedPreferences("Hell",MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("Hi",getip);
        editor.commit();
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }
}
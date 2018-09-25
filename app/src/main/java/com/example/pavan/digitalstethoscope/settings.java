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
    TextView mSerber;
    TextView muser;
    TextView mpass;
    TextView mpost;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        iptext = findViewById(R.id.ip_id);
        usertext = findViewById(R.id.username_id);
        password = findViewById(R.id.password_id);
        mSerber = findViewById(R.id.server_id);
        muser = findViewById(R.id.user_m);
        mpass = findViewById(R.id.pass_m);
        mpost = findViewById(R.id.port_m);

    }

    public void sendToMain(View view) {
        String getip = String.valueOf(iptext.getText());
        String getuser = String.valueOf(usertext.getText());
        String getpsswd = String.valueOf(password.getText());
        String getServer = String.valueOf(mSerber.getText());
        String getuserm = String.valueOf(muser.getText());
        String getpsswdm = String.valueOf(mpass.getText());
        String getpotdm = String.valueOf(mpost.getText());
        Constants.USER_NAME = getuser;
        Constants.PASSWORD = getpsswd;
        SharedPreferences preferences = getSharedPreferences("Hell",MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("ftp_ip",getip);
        editor.putString("ftp_username",getuser);
        editor.putString("ftp_pass",getpsswd);
        editor.putString("mqtt_ip",getServer);
        editor.putString("mqtt_username",getuserm);
        editor.putString("mqtt_password",getpsswdm);
        editor.putString("mqtt_port",getpotdm);
        editor.commit();
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }
}

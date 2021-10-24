package com.example.pavan.digitalstethoscope;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Equalizer;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;


import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.net.InetAddress;
import java.text.DateFormat;
import java.util.*;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jibble.simpleftp.SimpleFTP;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

import com.adeel.library.easyFTP;
import com.musicg.wave.Wave;
import com.musicg.wave.extension.Spectrogram;
import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

public class MainActivity extends AppCompatActivity {
    Button pick;
    TextView filepath;
    TextView text;
    TextView resultTextView;
    ProgressBar recordPgBar;
    int recordPgBarStatus = 0;
    Button recordButton;
    Button submitButton;
    Handler pgHandler = new Handler();
    MediaPlayer player;

    private static final int RECORDER_BPP = 16;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private boolean permissionToRecordAccepted = false;

    Bitmap bp;
    


    private static final String TAG = "Main Activity";
    MqttAndroidClient mqtt;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }
    private String saveToInternalStorage(Bitmap bitmapImage){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        File mypath=new File(directory,"profile.jpg");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mypath);
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.d(TAG, "saveToInternalStorage: did it work ?");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return directory.getAbsolutePath();
    }
    public Bitmap spectrogramToImage(double[][] data) {
        Bitmap bmp = null;
        if (data != null) {
            int width = data.length;
            int height = data[0].length;

            int[] arrayCol = new int[width * height];
            int counter = 0;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int value;
                    int color;
                    value = 255 - (int) (data[j][i] * 255);
                    color = (value << 16 | value << 8 | value | 255 << 24);
                    arrayCol[counter] = color;
                    counter++;
                }
            }
            bmp = Bitmap.createBitmap(arrayCol, width, height, Bitmap.Config.ARGB_8888);


            FileOutputStream out = null;
            try {
                out = new FileOutputStream(getCacheDir() + "demo_spectrogram_image");
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                saveToInternalStorage(bmp);
                Log.d(TAG, "spectrogramToImage: success ????");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        } else {
            System.err.println("Data Corrupt");
        }
        return bmp;
    }

    private MqttConnectOptions getMqttConnectionOption() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setUserName(preferences.getString("mqtt_username","wlhagkju"));
        mqttConnectOptions.setPassword(preferences.getString("mqtt_password","_5NZoVmjTPjx").toCharArray());
        return mqttConnectOptions;
    }
    private DisconnectedBufferOptions getDisconnectedBufferOptions() {
        DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
        disconnectedBufferOptions.setBufferEnabled(true);
        disconnectedBufferOptions.setBufferSize(100);
        disconnectedBufferOptions.setPersistBuffer(true);
        disconnectedBufferOptions.setDeleteOldestMessages(false);
        return disconnectedBufferOptions;
    }
    public MqttAndroidClient getMqttClient(final Context context, String brokerUrl, String clientId) {
        final String TAG = "In getMqttClient()";

        final MqttAndroidClient mqttAndroidClient = new MqttAndroidClient(context, brokerUrl, clientId);
        try {
            IMqttToken token = mqttAndroidClient.connect(getMqttConnectionOption());
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    mqttAndroidClient.setBufferOpts(getDisconnectedBufferOptions());
                    Log.d(TAG, "Success");
                    mqttAndroidClient.setCallback(new MqttCallbackExtended() {
                        @Override
                        public void connectComplete(boolean b, String s) {

                        }
                        @Override
                        public void connectionLost(Throwable throwable) {

                        }
                        @Override
                        public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                            Log.d(TAG,new String(mqttMessage.getPayload()));
                            resultTextView.setText(new String(mqttMessage.getPayload()));
                        }
                        @Override
                        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                            Log.d(TAG, "deliveryComplete: ");

                        }
                    });


                    try
                    {
                        subscribe(mqtt, Constants.SUBSCRIBE_TOPIC, 0);
                    }
                    catch(Exception e)
                    {
                        Log.d(TAG," Failure "+e);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failure " + exception.toString());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
        return mqttAndroidClient;
    }
    public void publishMessage(@NonNull MqttAndroidClient client,
                               @NonNull String msg, int qos, @NonNull String topic)
            throws MqttException, UnsupportedEncodingException {
        byte[] encodedPayload = new byte[0];
        encodedPayload = msg.getBytes("UTF-8");
        MqttMessage message = new MqttMessage(encodedPayload);
        message.setId(5866);
        message.setRetained(false);
        message.setQos(qos);
        client.publish(topic, message);
    }
    public void subscribe(@NonNull MqttAndroidClient client,
                          @NonNull final String topic, int qos) throws MqttException {
        IMqttToken token = client.subscribe(topic, qos);
        token.setActionCallback(new IMqttActionListener() {

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                Log.d(TAG, "Subscribed Successfully " + topic);

            }

            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                Log.e(TAG, "Subscription Failed " + topic);
            }
        });
    }
    SharedPreferences preferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Cardi-AI");
        preferences = getSharedPreferences("Hell",MODE_PRIVATE);
        pick = findViewById(R.id.pick_id);
        filepath = findViewById(R.id.path_id);
        text = findViewById(R.id.path_id);
        resultTextView = findViewById(R.id.txt_result);
        submitButton = findViewById(R.id.btn_submit_hb);
        recordButton = findViewById(R.id.btn_record_hb);
        recordPgBar = findViewById(R.id.pgbar_record_hb);


        final ImageView imgView = (ImageView)findViewById(R.id.imageView);
        final ImageView imgView2 = findViewById(R.id.imageView1);
        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialFilePicker()
                        .withActivity(MainActivity.this)
                        .withRequestCode(1000)
                        .withPath("/storage/emulated/0/Audio")
                        .withHiddenFiles(true)
                        .start();
            }
        });
        mqtt = getMqttClient(this,preferences.getString("mqtt_ip","tcp://m11.cloudmqtt.com") + ":" + preferences.getString("mqtt_port","16138"),Constants.CLIENT_ID);

        try
        {
            publishMessage(mqtt,"Hello World" , 0, Constants.PUBLISH_TOPIC);
        }
        catch(Exception e)
        {
            Log.d(TAG," Failure "+e);
        }
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File demoFile =new File(getCacheDir()+"demo.wav");
                InputStream ins = getResources().openRawResource(R.raw.demo);
                Log.d(TAG, "INS" + ins);
                try {
                    FileUtils.copyInputStreamToFile(ins, demoFile);
                    Log.d(TAG, "COPY INS TO DEMO FILE");
                    ins.close();
                } catch (IOException e) {
                    Log.d(TAG, "COPY FAILED");
                    e.printStackTrace();
                }
                final String TAG="on submit";

                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onCreate: PERMISSION NOT GRANTED");
                    Log.d(TAG, "onCreate: after permissions");

                }

                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onCreate: PERMISSION NOT GRANTED");
                }
                @SuppressLint("StaticFieldLeak") AsyncTask asyncTask =new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {
                        try {
                            SimpleFTP ftp = new SimpleFTP();
                            Log.d(TAG, "onCreate: before connect");

                            ftp.connect(preferences.getString("ftp_ip","192.168.43.114"),21,
                                    preferences.getString("ftp_username","pavan"),
                                    preferences.getString("ftp_pass","glaedr491"));

                            Log.d(TAG, "onCreate: after connect");
                            ftp.bin();
                            Log.d(TAG, "doInBackground FTP: before changing directory");
                            Log.d(TAG, "doInBackground FTP: after changing dir");
                            Log.d(TAG, "doInBackground FTP: "+getFilesDir());
                            String path ="/storage/emulated/0/Audio";

                            File file = new File(filepath.getText().toString());
                            Log.d(TAG, "doInBackground FTP: "+file);
                            final int d = Log.d(TAG, "doInBackground FTP: before file upload");
                            Log.d(TAG, "doInBackgroundFTP: current ftp path"+ftp.pwd());
                            ftp.stor(file);

                            Log.d(TAG, "doInBackground: after upload");

                            ftp.disconnect();
                        } catch (IOException e) {
                            Log.d(TAG, "onCreate: failed");
                            e.printStackTrace();
                        }
                        return null;
                    }
                    public void onPostExecute(Object res) {
                        if (res instanceof Exception) {
                            int d = Toast.LENGTH_SHORT;
                            Toast toast = Toast.makeText(getApplicationContext(),"hello world"+res,d);
                            toast.show();
                        }
                    }

                };
                asyncTask.execute();

                Wave wave = new Wave(filepath.getText().toString());
                Spectrogram spectrogram = new Spectrogram(wave);


               
                bp = spectrogramToImage(spectrogram.getAbsoluteSpectrogramData());

                imgView.setImageBitmap(bp);
                imgView2.setImageBitmap(bp);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bp.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream .toByteArray();
                String[] list=filepath.getText().toString().split(Pattern.quote("/"));
                Log.e(TAG, "publish : "+ list[list.length-1]);
                String msg= "sent wav file"+list[list.length-1];
                try {
                    publishMessage(mqtt, list[list.length-1].substring(0,list[list.length-1].length()-4), 0, Constants.PUBLISH_TOPIC);
                    Log.d(TAG, "onClick: after publish" + list[list.length-1]);
                }
                catch(Exception e)
                {
                    Log.d(TAG," Failed to publish  "+e);
                }
            }
        });
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordPgBarStatus=0;
                String fileName = DateFormat.getDateTimeInstance().format(new Date());
                String path= "/storage/self/primary/Audio/test_real_test_man.wav";
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        WavRecorder wavRecorder = new WavRecorder("/storage/self/primary/Audio/test_real_test_man.wav");
                        wavRecorder.startRecording();
                        while(recordPgBarStatus<=100)
                        {

                            Log.d(TAG, "run: "+recordPgBarStatus);
                            android.os.SystemClock.sleep(1000);
                            pgHandler.post(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    recordPgBar.setProgress(recordPgBarStatus);
                                }
                            });
                            recordPgBarStatus+=10;
                        }
                        wavRecorder.stopRecording();
                    }
                }).start();
                filepath.setText(path);

            }

        });


    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1000 && resultCode == RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            filepath.setText(filePath);
        }
    }

    public void settingsActivity(View view) {
        Intent i = new Intent(this,settings.class);
        startActivity(i);
    }

    public void play(View view) {
        player = new MediaPlayer();
        String a = (String) text.getText();
        try {
            player.setDataSource(a);
            player.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });

    }
}

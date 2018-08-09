package com.example.pavan.digitalstethoscope;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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
//import org.apache.commons.net.ftp.*;
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

import com.adeel.library.easyFTP;
import com.musicg.wave.Wave;
import com.musicg.wave.extension.Spectrogram;

public class MainActivity extends AppCompatActivity {
    TextView resultTextView;
    ProgressBar recordPgBar;
    int recordPgBarStatus = 0;
    Button recordButton;
    Button submitButton;
    Handler pgHandler = new Handler();
    MediaPlayer mp;

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

    @Override //android permissions for recording sound
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }


    //saveToInternalStorage method not working properly, Ignore
    private String saveToInternalStorage(Bitmap bitmapImage){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        // path to /data/data/yourapp/app_data/imageDir
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        // Create imageDir
        File mypath=new File(directory,"profile.jpg");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mypath);
            // Use the compress method on the BitMap object to write image to the OutputStream
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

    //code to convert spectrogram data to image.
    public Bitmap spectrogramToImage(double[][] data) {
        Bitmap bmp = null;
        if (data != null) {
            //paint.setStrokeWidth(1);
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
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
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
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setAutomaticReconnect(true);
       mqttConnectOptions.setWill(Constants.PUBLISH_TOPIC, "I am going offline".getBytes(), 1, true);
        mqttConnectOptions.setUserName(Constants.USER_NAME);
        mqttConnectOptions.setPassword(Constants.PASSWORD.toCharArray());
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
                            //setMessageNotification(s, new String(mqttMessage.getPayload()));
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
        message.setRetained(true);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // initalising all UI elements
        resultTextView = findViewById(R.id.txt_result);
        submitButton = findViewById(R.id.btn_submit_hb);
        recordButton = findViewById(R.id.btn_record_hb);
        recordPgBar = findViewById(R.id.pgbar_record_hb);


        final ImageView imgView = (ImageView)findViewById(R.id.imageView);
        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        //ActivityCompat.requestPermissions(this,permissions,);





        //creating the client
        mqtt = getMqttClient(this,Constants.MQTT_BROKER_URL,Constants.CLIENT_ID);

        try
        {
            publishMessage(mqtt,"Hello World" , 0, Constants.PUBLISH_TOPIC);
            //subscribe(mqtt,Constants.SUBSCRIBE_TOPIC,0);
        }
        catch(Exception e)
        {
            Log.d(TAG," Failure "+e);
        }
        //events
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //spectrogram generation and base64 conversion here

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
                    // Permission is not granted
                    Log.d(TAG, "onCreate: PERMISSION NOT GRANTED");
                    // requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},42);
                    Log.d(TAG, "onCreate: after permissions");

                }

                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Permission is not granted
                    Log.d(TAG, "onCreate: PERMISSION NOT GRANTED");
                }
                @SuppressLint("StaticFieldLeak") AsyncTask asyncTask =new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {
                        try {
                            SimpleFTP ftp = new SimpleFTP();
                            //requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
                            // Connect to an FTP server on port 21.
                            Log.d(TAG, "onCreate: before connect");
                            ftp.connect("192.168.2.7", 21);
                            //ftp.connect("192.168.2.2", 2121,"ftp","ftp");
                            Log.d(TAG, "onCreate: after connect");
                            // Set binary mode.
                            ftp.bin();

                            // Change to a new working directory on the FTP server.
                            Log.d(TAG, "doInBackground: before changing directory");
                            //ftp.cwd("Download/");
                            //ftp.cwd("MATLAB/");
                            Log.d(TAG, "doInBackground: after changing dir");
                            // Upload some files.
                            Log.d(TAG, "doInBackground: "+getFilesDir());
                            String path ="/storage/emulated/0/Audio";
                            File file = new File(new File(path),"example.wav");
                            Log.d(TAG, "doInBackground: "+file);
                            final int d = Log.d(TAG, "doInBackground: before file upload");
                            Log.d(TAG, "doInBackground: current ftp path"+ftp.pwd());
                            ftp.stor(new File(path+"/example.wav"));
                            // ftp.stor(new File("comicbot-latest.png"));

                            // You can also upload from an InputStream, e.g.
                            // ftp.stor(new FileInputStream(new File("test.png")), "test.png");
                            // ftp.stor(someSocket.getInputStream(), "blah.dat");
                            Log.d(TAG, "doInBackground: after upload");

                            // Quit from the FTP server.
                            ftp.disconnect();
                        } catch (IOException e) {
                            // Jibble.
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

                Wave wave = new Wave(getCacheDir()+"demo.wav");
                Spectrogram spectrogram = new Spectrogram(wave);


               
                bp = spectrogramToImage(spectrogram.getAbsoluteSpectrogramData());

                imgView.setImageBitmap(bp);



                // Bitmap to base64 encoding
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bp.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream .toByteArray();
                //String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                String msg= Base64.encodeToString(byteArray, Base64.NO_WRAP);
                try {
                    publishMessage(mqtt, msg, 0, Constants.PUBLISH_TOPIC);
                    Log.d(TAG, "onClick: after publish" + msg);
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



                //pg bar thread runs for 10 sec.
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        WavRecorder wavRecorder = new WavRecorder("/storage/self/primary/Audio/example.wav");
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


            }

        });


    }
}

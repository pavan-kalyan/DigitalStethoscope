package com.example.pavan.digitalstethoscope;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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


    private static final String TAG = "Main Activity";
    MqttAndroidClient mqtt;

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
                Log.d(TAG, "Subscribe Successfully " + topic);

            }

            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                Log.e(TAG, "Subscribe Failed " + topic);
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
        mp = MediaPlayer.create(this,R.raw.demo);
        final ImageView imgView = (ImageView)findViewById(R.id.imageView);





        //creating the client
        mqtt = getMqttClient(this,Constants.MQTT_BROKER_URL,Constants.CLIENT_ID);

        try
        {
            publishMessage(mqtt,"Hello World" , 0, Constants.PUBLISH_TOPIC);
        }
        catch(Exception e)
        {
            Log.d(TAG," Failure "+e);
        }
        //events
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //TODO: spectrogram generation and base64 conversion here

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

                Wave wave = new Wave(getCacheDir()+"demo.wav");
                Spectrogram spectrogram = new Spectrogram(wave);

                imgView.setImageBitmap(spectrogramToImage(spectrogram.getAbsoluteSpectrogramData()) );


                /*
                try {
                    demoWav = WavFile.openWavFile(demoFile);
                    //demoWav.display();
                    Log.d(TAG, "onClick: buffer size "+demoWav.getNumChannels() * (int) demoWav.getNumFrames()));
                    int[] sampleBuffer = new int[demoWav.getNumChannels() * (int) demoWav.getNumFrames()];
                    demoWav.readFrames(sampleBuffer, (int) demoWav.getNumFrames());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (WavFileException e) {
                    e.printStackTrace();
                }

                */


                String msg="sample";
                try {
                    publishMessage(mqtt, msg, 0, "myTopic");
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

                // TODO: audio record code here


                //pg bar thread runs for 10 sec.
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
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
                    }
                }).start();
            }
        });


    }
}

package com.example.pavan.digitalstethoscope;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {
    TextView resultTextView;
    ProgressBar recordPgBar;
    int recordPgBarStatus = 0;
    Button recordButton;
    Button submitButton;
    Handler pgHandler = new Handler();

    private static final String TAG = "Main Activity";
    MqttAndroidClient mqtt;

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


                //pg bar thread
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

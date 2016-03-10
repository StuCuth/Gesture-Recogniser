package com.example.stuart.gesturerecogniser;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class WatchGestureSettings extends AppCompatActivity implements MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks {
    private GoogleApiClient googleApiClient;
    private Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_gesture_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initialiseGoogleAPI();
        init();
    }

    public void init() {
        registerMessageListener();
        Button changeGestureButton = (Button) findViewById(R.id.gestureButton);
        changeGestureButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                sendMessage("/SETGESTURE","");
            }
        });

        Button goBackButton = (Button) findViewById(R.id.backButton);
        goBackButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v){
                deregisterMessageListener();
                finish();
            }
        });

    }

    public void initialiseGoogleAPI() {
        googleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
        googleApiClient.connect();
    }

    public void registerMessageListener() {
        Wearable.MessageApi.addListener(googleApiClient, this);
        sendMessage("/STARTGESTURE","");
    }

    public void deregisterMessageListener() {
        sendMessage("/STOPGESTURE","");
        Wearable.MessageApi.removeListener(googleApiClient, this);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {
        super.onDestroy();
        googleApiClient.disconnect();
    }

    //NEED TO SAY WHERE THIS CAME FROM
    private void sendMessage(final String path, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Log.d("sendMessage", path);
                    Wearable.MessageApi.sendMessage(
                            googleApiClient, node.getId(), path, text.getBytes()).await();
                }
            }
        }).start();

    }

    //AND THIS
    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (messageEvent.getPath().equalsIgnoreCase("/START")) {
                    sendMessage("/GESTUREACK", "");
                } else if (messageEvent.getPath().equalsIgnoreCase("/GESTURESET")) {
                    Toast.makeText(context,"The gesture has been set.",Toast.LENGTH_SHORT).show();
                    //Do all the confirmationstuff
                }else if (messageEvent.getPath().equalsIgnoreCase("/NODATA")) {
                    Toast.makeText(context,"Oops, sensor data was not available. Please try again.",Toast.LENGTH_SHORT).show();
                }

            }
        });
    }
}

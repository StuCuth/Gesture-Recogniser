package com.example.stuart.gesturerecogniser;

import android.content.Context;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Stuart on 29/02/2016.
 */
public class GestureModel implements MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks {
    private MainActivity mainActivity;
    private GoogleApiClient googleApiClient;
    private boolean watchInputActive = false;
    private boolean watchConnected = false;
    private Random random = new Random();
    private int count = 0;
    private double totalTime =0;
    private double averageX = 0;
    private double averageY = 0;
    private double averageZ = 0;
    File path;
    File file;
    FileOutputStream fileOutputStream;

    public GestureModel(MainActivity mainActivity){
        this.mainActivity = mainActivity;
        initialiseGoogleAPI();
        path = mainActivity.getExternalFilesDir(null);
        file = new File(path,"GestureData.txt");
    }
    public void initialiseGoogleAPI() {
        googleApiClient = new GoogleApiClient.Builder(mainActivity).addApiIfAvailable(Wearable.API).build();
        googleApiClient.connect();
    }

    public void startGestures(){
        if(!watchInputActive){
            count = 0;
            totalTime = 0;
            averageX =0;
            averageY=0;
            averageZ=0;
            try {
                fileOutputStream = new FileOutputStream(file);
            }catch (Exception e){

            }
            Log.d("Log", "Logging data");
            watchInputActive = true;
            registerMessageListener();
            sendMessage("/START", "");
            start();

        }else{
            Log.d("Error","Cant run while running");
        }
    }

    private void stop(){
        watchInputActive = false;
        deregisterMessageListener();
        watchConnected = false;
        sendMessage("/STOP","");
        sendEmail();
    }
    public void sendEmail(){
        File file = new File(mainActivity.getExternalFilesDir(null),"GestureData.txt");
        Uri data = Uri.fromFile(file);
        Intent email = new Intent(Intent.ACTION_SEND);
        email.setType("vnd.android.cursor.dir/email");
        email.putExtra(Intent.EXTRA_STREAM, data);
        email.putExtra(Intent.EXTRA_SUBJECT,"Gesture data");
        email.putExtra(Intent.EXTRA_EMAIL, new String[]{"xmb12164@uni.strath.ac.uk"});
        email.putExtra(Intent.EXTRA_TEXT,"This is datalog [1/2], [my/their] gesture for _.");
        mainActivity.startActivity(Intent.createChooser(email, "Send email..."));
    }

    private void start(){
        int time = random.nextInt(6000) + 5000;
        count++;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage("/DOGESTURE","");
            }
        },time);

    }

    private void sendMessage(final String path, final String text){
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( googleApiClient ).await();
                for(Node node : nodes.getNodes()) {
                    Log.d("sendMessage",path);
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            googleApiClient, node.getId(), path, text.getBytes() ).await();
                }
            }
        }).start();

    }

    private void registerMessageListener(){
        Wearable.MessageApi.addListener(googleApiClient, this);
    }

    private void deregisterMessageListener(){
        Wearable.MessageApi.removeListener(googleApiClient, this);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        new Thread(new Runnable() {
            public void run() {
                if (watchInputActive && watchConnected) {
                    if (messageEvent.getPath().equalsIgnoreCase("/GESTUREACTION")) {
                        if(count<4) {
                            try {
                                fileOutputStream.write(messageEvent.getData());

                            }catch (Exception e){

                            }
                            totalTime = totalTime + Double.parseDouble(new String(messageEvent.getData()).split(",")[3].replaceAll("[a-zA-Z]","").replace(":", "").trim());
                            averageX = averageX + Double.parseDouble(new String(messageEvent.getData()).split(",")[0].replaceAll("[a-zA-Z]","").replace(":","").trim());
                            averageY = averageY + Double.parseDouble(new String(messageEvent.getData()).split(",")[1].replaceAll("[a-zA-Z]","").replace(":","").trim());
                            averageZ = averageZ + Double.parseDouble(new String(messageEvent.getData()).split(",")[2].replaceAll("[a-zA-Z]","").replace(":","").trim());
                            start();
                        }else{
                            try {
                                fileOutputStream.write(messageEvent.getData());

                                totalTime = totalTime + Double.parseDouble(new String(messageEvent.getData()).split(",")[3].replaceAll("[a-zA-Z]","").replace(":", "").trim());
                                averageX = averageX + Double.parseDouble(new String(messageEvent.getData()).split(",")[0].replaceAll("[a-zA-Z]","").replace(":","").trim());
                                averageY = averageY + Double.parseDouble(new String(messageEvent.getData()).split(",")[1].replaceAll("[a-zA-Z]","").replace(":","").trim());
                                averageZ = averageZ + Double.parseDouble(new String(messageEvent.getData()).split(",")[2].replaceAll("[a-zA-Z]","").replace(":","").trim());

                                String average = "\nAverage X value:"+averageX/4+", Average Y value:"+averageY/4+", Average Z value:"+averageZ/4+", Average time taken per gesture: "+totalTime/4+"ms \n";
                                fileOutputStream.write(average.getBytes());
                            }catch (Exception e){

                            }

                            stop();
                        }
                    }else if(messageEvent.getPath().equalsIgnoreCase("/SENSORSTART")){
                        try {
                            fileOutputStream.write(messageEvent.getData());
                        }catch (Exception e){

                        }
                    }
                } else if (watchInputActive && !watchConnected) {
                    if (messageEvent.getPath().equalsIgnoreCase("/START")) {
                        watchConnected = true;
                        sendMessage("/ACK", "");
                    }
                }
            }
        }).start();
    }
}

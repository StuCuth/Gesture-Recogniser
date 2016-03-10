package com.example.stuart.gesturerecogniser;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends WearableActivity implements SensorEventListener,
        MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks {

    private boolean gestureSetMode = false;
    private SQLiteDatabase sqLiteDatabase;
    private Vibrator vibrate;
    private GoogleApiClient googleApiClient;
    private SensorManager sensorManager;
    private Sensor sensor;
    private Sensor compass;
    private Sensor gyroscope;
    private Sensor rotation;
    private SensorManagement sensorEventListener = new SensorManagement();
    private float[] sensorValues;
    private float[] setCompassVals;
    private float[] setGyroVals;
    private float[] setRotationVals;
    private long lastUpdate;
    private boolean previousUpdate = false;

    private boolean DO_GESTURE = false;
    private long TIME_START = System.currentTimeMillis();

    private double TOLERANCE = 1.5;
    private double[] xyzMin = {7,-3,-6.5};
    private double[] xyzMax = {10, 0.35, 0.9};
    private int ifType = 0;
    private ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Means the watch will never switch off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Setting up the messages API
        initialiseGoogleAPI();


        init();

        sqLiteDatabase = dataBaseCreation();
        readSensorValues();
        ifType = gestureRecogniser();
    }

    private SQLiteDatabase dataBaseCreation(){
        return openOrCreateDatabase("WatchDatabase",MODE_PRIVATE,null);
    }

    private void init(){
        vibrate = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        compass = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        iv = (ImageView) findViewById(R.id.imageView);
    }

    private void initialiseGoogleAPI(){
        googleApiClient = new GoogleApiClient.Builder( this )
                .addApi( Wearable.API )
                .addConnectionCallbacks( this )
                .build();

        if( googleApiClient != null && !( googleApiClient.isConnected() || googleApiClient.isConnecting() ) )
            googleApiClient.connect();
    }

    //https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
    private void sendMessage(final String path, final String text){
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( googleApiClient ).await();
                for(Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            googleApiClient, node.getId(), path, text.getBytes() ).await();
                }
            }
        }).start();

    }


    @Override
    public void onConnected(Bundle bundle) {
        sendMessage("/START", "");
        Log.d("Connected", "Sent message.");
        Wearable.MessageApi.addListener(googleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        super.onDestroy();
        googleApiClient.disconnect();
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageEvent.getPath().equalsIgnoreCase("/ACK")) {
                    Log.d("Message", "ACK received");
                    //Start the sensor listener
                    Log.d("Values",""+setCompassVals[0]+" "+setCompassVals[1]+" "+setCompassVals[2]);
                    sendMessage("/SENSORSTART", "----ORIGINAL SENSOR VALUES----\nXYZmins x: " + xyzMin[0] + ",y: " + xyzMin[1] + ",z: " + xyzMin[2] +
                            " XYZmax x: " + xyzMax[0] + ", y: " + xyzMax[1] + ", z: " + xyzMax[2] + "\n" +
                            "GYRO x: "+setGyroVals[0]+", y: "+setGyroVals[1]+", z:"+setGyroVals[2]+"\n"+
                            "MAGNO X: "+setCompassVals[0]+" Y: "+setCompassVals[1]+" Z: " +setCompassVals[2]+ "\n"+
                            "ROTAT X: "+setRotationVals[0]+" Y: "+setRotationVals[1]+" Z: "+setRotationVals[2]+" cos: " +setRotationVals[3]+ " Accuracy: "+setRotationVals[4]+"\n");

                    sensorManager.registerListener(MainActivity.this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener, compass, SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener,rotation,SensorManager.SENSOR_DELAY_NORMAL);
                    //Change image
                    iv.setImageResource(R.drawable.common_ic_googleplayservices);
                    vibrate.vibrate(500);
                } else if (messageEvent.getPath().equalsIgnoreCase("/STOP")) {
                    //Close app
                    Log.d("Message", "Stop recieved");
                    sensorManager.unregisterListener(MainActivity.this);
                    sensorManager.unregisterListener(sensorEventListener);
                    iv.setImageResource(R.drawable.common_google_signin_btn_text_light_pressed);
                } else if (messageEvent.getPath().equalsIgnoreCase("/START") ||
                        messageEvent.getPath().equalsIgnoreCase("/STARTGESTURE")) {
                    sendMessage("/START", "");
                    Log.d("Message", "Start recieved");
                } else if (messageEvent.getPath().equalsIgnoreCase("/GESTUREACK")) {
                    Log.d("Starting", "Movement recorded");
                    sensorManager.registerListener(MainActivity.this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener,gyroscope,SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener,compass,SensorManager.SENSOR_DELAY_NORMAL);
                    sensorManager.registerListener(sensorEventListener,rotation,SensorManager.SENSOR_DELAY_NORMAL);
                    vibrate.vibrate(250);
                    gestureSetMode = true;
                    Log.d("Message", "Gesture Ack recieved");
                } else if (messageEvent.getPath().equalsIgnoreCase("/STOPGESTURE")) {
                    Log.d("Stopping", "No more movement recorded");
                    sensorManager.unregisterListener(MainActivity.this);
                    sensorManager.unregisterListener(sensorEventListener);
                    Log.d("Values", "" + setCompassVals[0] + " " + setCompassVals[1] + " " + setCompassVals[2]);
                    vibrate.vibrate(250);
                    gestureSetMode = false;
                    Log.d("Message", "Stop gesture recieved");
                } else if (messageEvent.getPath().equalsIgnoreCase("/SETGESTURE")) {
                    Log.d("Message", "Set gesture recieved");
                    if (sensorValues != null) {
                        vibrate.vibrate(250);
                        float[] savedValues = sensorValues;
                        setCompassVals = sensorEventListener.getMagno();
                        setGyroVals = sensorEventListener.getGyro();
                        setRotationVals = sensorEventListener.getRotation();
                        Log.d("Values",""+setCompassVals[0]+" "+setCompassVals[1]+" "+setCompassVals[2]);
                        insertValuesInTable(savedValues);
                        sendMessage("/GESTURESET", "");
                    } else {
                        sendMessage("/NODATA", "");
                    }

                } else if (messageEvent.getPath().equalsIgnoreCase("/DOGESTURE")) {
                    DO_GESTURE = true;
                    TIME_START = System.currentTimeMillis();
                    vibrate.vibrate(250);
                }
            }
        });
    }

    private void insertValuesInTable(float[] savedValues){
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS GestureTest(minXYZ TEXT,maxXYZ TEXT);");
        sqLiteDatabase.execSQL("DELETE FROM GestureTest");
        String gestureValues = getGestureValues(savedValues);
        sqLiteDatabase.execSQL("INSERT INTO GestureTest VALUES(" + gestureValues + ");");
        readSensorValues();
    }

    private String getGestureValues(float[] vals){
        String returnValue ="'";
        double[] xMinMax =  decideMinMax(vals[0]);
        double[] yMinMax =  decideMinMax(vals[1]);
        double[] zMinMax =  decideMinMax(vals[2]);
        returnValue = returnValue + xMinMax[0] + ","+yMinMax[0]+","+zMinMax[0]+"','";
        returnValue = returnValue + xMinMax[1] + ","+yMinMax[1]+","+zMinMax[1]+"'";
        Log.d("Query Insert", returnValue);
        return returnValue;
    }

    private double[] decideMinMax(double value){
        double[] returnValue = new double[2];
        double minValue = 0;
        double maxValue = 0;

        //Decides Max
        if(value + TOLERANCE > 10){
            maxValue = -10 + ((value + TOLERANCE) - 10);
        }else{
            maxValue = value + TOLERANCE;
        }

        //Decides Min
        if(value - TOLERANCE < -10){
            minValue = 10 + ((value - TOLERANCE) + 10);
        }else{
            minValue = value - TOLERANCE;
        }
        returnValue[0] = minValue;
        returnValue[1] = maxValue;

        return returnValue;
    }

    private void readSensorValues(){
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS GestureTest(minXYZ TEXT,maxXYZ TEXT);");
        Cursor emptyCheck = sqLiteDatabase.rawQuery("Select count(*) from GestureTest",null);
        emptyCheck.moveToFirst();
        if(emptyCheck.getInt(0) > 0 ) {
            Cursor resultSet = sqLiteDatabase.rawQuery("Select * from GestureTest", null);
            resultSet.moveToFirst();
            String[] min = resultSet.getString(0).split(",");
            String[] max = resultSet.getString(1).split(",");
            xyzMin[0] = Double.parseDouble(min[0]);
            xyzMin[1] = Double.parseDouble(min[1]);
            xyzMin[2] = Double.parseDouble(min[2]);
            xyzMax[0] = Double.parseDouble(max[0]);
            xyzMax[1] = Double.parseDouble(max[1]);
            xyzMax[2] = Double.parseDouble(max[2]);
            ifType = gestureRecogniser();
        }
    }

    protected int gestureRecogniser(){
        String code = "";

        if(xyzMin[0] > xyzMax[0]){
            code = "1";
        }else{
            code = "0";
        }

        if(xyzMin[1] > xyzMax[1]){
            code = code + "1";
        }else{
            code = code + "0";
        }

        if(xyzMin[2] > xyzMax[2]){
            code = code + "1";
        }else {
            code = code + "0";
        }
        return Integer.parseInt(code,2);
    }

    protected boolean gestureCheck(float x, float y, float z){
        switch (ifType){
            case 0:
                if(x > xyzMin[0] && x < xyzMax[0] &&
                        y > xyzMin[1] && y < xyzMax[1] &&
                        z > xyzMin[2] && z < xyzMax[2]){
                    return true;
                }
                return false;
            case 1:
                if (x > xyzMin[0] && x < xyzMax[0] &&
                        y > xyzMin[1] && y < xyzMax[1] &&
                        z > xyzMin[2] || z < xyzMax[2]){
                    return true;
                }
                return false;
            case 2:
                if(x > xyzMin[0] && x < xyzMax[0] &&
                        y > xyzMin[1] || y < xyzMax[1] &&
                        z > xyzMin[2] && z < xyzMax[2]){
                    return true;
                }
                return false;
            case 3:
                if (x > xyzMin[0] && x < xyzMax[0] &&
                        y > xyzMin[1] || y < xyzMax[1] &&
                        z > xyzMin[2] || z < xyzMax[2]){
                    return true;
                }
                return false;
            case 4:
                if(x > xyzMin[0] || x < xyzMax[0] &&
                        y > xyzMin[1] && y < xyzMax[1] &&
                        z > xyzMin[2] && z < xyzMax[2]){
                    return true;
                }
                return false;
            case 5:
                if (x > xyzMin[0] || x < xyzMax[0] &&
                        y > xyzMin[1] && y < xyzMax[1] &&
                        z > xyzMin[2] || z < xyzMax[2]){
                    return true;
                }
                return false;
            case 6:
                if(x > xyzMin[0] || x < xyzMax[0] &&
                        y > xyzMin[1] || y < xyzMax[1] &&
                        z > xyzMin[2] && z < xyzMax[2]){
                    return true;
                }
                return false;
            case 7:
                if(x > xyzMin[0] || x < xyzMax[0] &&
                        y > xyzMin[1] || y < xyzMax[1] &&
                        z > xyzMin[2] || z < xyzMax[2]){
                    return true;
                }
                return false;
        }
        return false;
    }

    protected float[] smoothData(float[] newVals,float[] oldVals){
        oldVals[0] = oldVals[0] + (newVals[0] - oldVals[0]);
        oldVals[1] = oldVals[1] + (newVals[1] - oldVals[1]);
        oldVals[2] = oldVals[2] + (newVals[2] - oldVals[2]);

        return oldVals;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        if ((curTime - lastUpdate) > 500 && !gestureSetMode) {
            lastUpdate = curTime;
            if(sensorValues != null) {
                sensorValues = smoothData(event.values, sensorValues);
            }else{
                sensorValues = event.values;
            }

            float x = sensorValues[0], y = sensorValues[1], z = sensorValues[2];
            if(gestureCheck(x,y,z)){

                if(!previousUpdate) {
                    previousUpdate = true;
                    vibrate.vibrate(250);
                    if(!DO_GESTURE) {
                        sendMessage("/LOG", "");
                    }else{
                        double totalTime = (System.currentTimeMillis() - TIME_START);
                        DO_GESTURE = false;
                        float[] comp = sensorEventListener.getMagno();
                        float[] rot = sensorEventListener.getRotation();
                        float[] gyro = sensorEventListener.getGyro();
                        sendMessage("/GESTUREACTION","\nX: "+x+", Y: "+y+", Z: "+z+", Time: "+totalTime+",\n" +
                                "GYRO x: "+gyro[0]+", y: "+gyro[1]+", z:"+gyro[2]+"\n"+
                                "MAG X: "+comp[0]+" Y: "+comp[1]+" Z: " +comp[2]+ "\n"+
                                "ROT X: "+rot[0]+" Y: "+rot[1]+" Z: "+rot[2]+" cos: " +rot[3]+ " Accuracy: "+rot[4]+"\n");
                    }
                }

            }else{
                previousUpdate = false;
            }

        }else if(gestureSetMode){

            if(sensorValues != null) {
                sensorValues = smoothData(event.values, sensorValues);
            }else{
                sensorValues = event.values;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onDestroy(){
        sensorManager.unregisterListener(MainActivity.this);
        sensorManager.unregisterListener(sensorEventListener);
        Wearable.MessageApi.removeListener(googleApiClient, MainActivity.this);
        sendMessage("/DESTROY","");
        super.onDestroy();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

     class SensorManagement implements SensorEventListener {
         private float[] gyro = {0, 0, 0};
         private float[] magno = {0, 0, 0};
         private float[] rotation = {0, 0, 0, 0, 0};

        @Override
        public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
                gyro[0] = event.values[0];
                gyro[1] = event.values[1];
                gyro[2] = event.values[2];
            }else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
                magno[0] = event.values[0];
                magno[1] = event.values[1];
                magno[2] = event.values[2];
            }else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
                rotation[0] = event.values[0];
                rotation[1] = event.values[1];
                rotation[2] = event.values[2];
                rotation[3] = event.values[3];
                rotation[4] = event.values[4];
            }
        }

         public float[] getGyro() {
             return new float[]{gyro[0],gyro[1],gyro[2]};
         }

         public float[] getMagno() {
             return new float[]{magno[0],magno[1],magno[2]};
         }

         public float[] getRotation() {
             return new float[]{rotation[0],rotation[1],rotation[2],rotation[3],rotation[4]};
         }

         @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}

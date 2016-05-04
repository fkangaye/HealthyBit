package com.mbientlab.activitytracker;

import android.app.AlertDialog;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.Highlight;
import com.mbientlab.activitytracker.GraphFragment.GraphCallback;
import com.mbientlab.activitytracker.MWDeviceConfirmFragment.DeviceConfirmCallback;
import com.mbientlab.activitytracker.MWScannerFragment.ScannerCallback;
import com.mbientlab.activitytracker.db.ActivitySampleDbHelper;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class MainActivity extends ActionBarActivity implements ScannerCallback, ServiceConnection, DeviceConfirmCallback, GraphCallback
{
    private static final float ACC_RANGE = 8.f, ACC_FREQ = 50.f;
    private static final String STREAM_KEY = "accel_stream";
    private final static int REQUEST_ENABLE_BT= 0;
    private final static String ACCELEROMETER_FRAGMENT_KEY= "com.mbientlab.activitytracker.AccelerometerFragment.ACCELEROMETER_FRAGMENT_KEY";
    private final static String GRAPH_FRAGMENT_KEY = "com.mbientlab.activitytracker.GraphFragment.GRAPH_FRAGMENT_KEY";
    private static final String TAG = "METAWEAR";
    public Accelerometer accelModule;
    private Switch accel_switch;
    private MetaWearBleService.LocalBinder serviceBinder;
    private GraphFragment mGraphFragment;
    private MetaWearBoard metaWearBoard = null;
    private MWScannerFragment mwScannerFragment = null;
    private SharedPreferences sharedPreferences;
    private Editor editor;
    private BluetoothDevice bluetoothDevice;
    private BluetoothAdapter btAdapter;
    private Menu menu;
    private SQLiteDatabase activitySampleDb;
    private boolean btDeviceSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getApplicationContext().getSharedPreferences("com.mbientlab.metatracker", 0); // 0 - for private mode
        editor = sharedPreferences.edit();
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            PlaceholderFragment mainFragment= new PlaceholderFragment();

            getFragmentManager().beginTransaction().add(R.id.container, mainFragment).commit();
            mGraphFragment = (GraphFragment) getFragmentManager().findFragmentById(R.id.graph);
        } else {
            mGraphFragment = (GraphFragment) getFragmentManager().getFragment(savedInstanceState, GRAPH_FRAGMENT_KEY);
        }

        btAdapter= ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(R.string.error_no_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);


    }

    public void open(View view){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("Time To Stand Up!!!!");

        alertDialogBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                Toast.makeText(MainActivity.this,"Good Job!!!",Toast.LENGTH_LONG).show();
            }
        });

        alertDialogBuilder.setNegativeButton("Snooze", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(MainActivity.this, "See you again next time!!", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    @Override
    protected void onResume(){
        super.onResume();

        String bleMacAddress = sharedPreferences.getString("ble_mac_address", null);
        if(bleMacAddress != null){
            TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
            connectionStatus.setText(getText(R.string.metawear_connected));
        }

    }

    @Override
    protected void onStart(){
        super.onStart();
        setupDatabase();
    }

    @Override
    protected void onStop(){
       super.onStop();
       activitySampleDb.close();
    }

    public void connectDevice(final MetaWearBoard metaWearBoard){

        metaWearBoard.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void connected() {
                Log.i("Metawear Controller", "Device Connected");
                Toast.makeText(getApplicationContext(), R.string.toast_connected, Toast
                        .LENGTH_SHORT).show();

                if (btDeviceSelected) {
                MWDeviceConfirmFragment mwDeviceConfirmFragment = new MWDeviceConfirmFragment();
                mwDeviceConfirmFragment.flashDeviceLight(metaWearBoard, getFragmentManager());
                btDeviceSelected = false;
                 }

                try {
                    accelModule = metaWearBoard.getModule(Accelerometer.class);
                } catch (UnsupportedModuleException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void disconnected() {
                Toast.makeText(getApplicationContext(), R.string.toast_disconnected, Toast
                        .LENGTH_SHORT).show();
            }
        });
        metaWearBoard.connect();



        if(menu != null) {
            MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
            connectMenuItem.setTitle(R.string.disconnect);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

            getMenuInflater().inflate(R.menu.main, menu);
            this.menu = menu;
            String bleMacAddress = sharedPreferences.getString("ble_mac_address", null);
            if(bleMacAddress != null){
                MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
                connectMenuItem.setTitle(R.string.disconnect);
            }
            return true;
    }

    @Override
    public void btDeviceSelected(BluetoothDevice device) {
        Log.i("Connected", "Connected"+device.getAddress());
        bluetoothDevice = device;
        btDeviceSelected = true;
        //Error Here
        //connectDevice(serviceBinder.getMetaWearBoard(bluetoothDevice));

    }

    public void pairDevice(){
        editor.putString("ble_mac_address", bluetoothDevice.getAddress());
        editor.commit();
        Log.i("pairDevice", "PairDevice");
    }

    public void dontPairDevice(){
        bluetoothDevice = null;
        metaWearBoard.disconnect();
        mwScannerFragment.show(getFragmentManager(), "metawear_scanner_fragment");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (MetaWearBleService.LocalBinder) service;
        //serviceBinder.executeOnUiThread();

        String bleMacAddress = sharedPreferences.getString("ble_mac_address", null);
        Log.i("Log : ble_mac_address", bleMacAddress);
        if(bleMacAddress != null){
        bluetoothDevice = btAdapter.getRemoteDevice(bleMacAddress);
        metaWearBoard = serviceBinder.getMetaWearBoard(bluetoothDevice);
        connectDevice(metaWearBoard);

        }
    }

    @Override
    public void setGraphFragment(GraphFragment graphFragment){
        mGraphFragment = graphFragment;
    }

    @Override
    public void updateCaloriesAndSteps(int calories, int steps){
        TextView activeCaloriesBurned = (TextView) findViewById(R.id.active_calories_burned);
        activeCaloriesBurned.setText(getString(R.string.active_calories_burned) + String.valueOf
                (calories));
        Log.i(String.valueOf(calories), String.valueOf(calories) + "TTT");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) { }
//////////////////////////////////
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_connect:
                if((metaWearBoard != null) && metaWearBoard.isConnected()){

                    MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
                    connectMenuItem.setTitle(R.string.connect);
                    editor.remove("ble_mac_address");
                    editor.commit();
                    TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
                    connectionStatus.setText(getText(R.string.metawear_connected));
                    metaWearBoard.disconnect();
                }else {
                    if(mwScannerFragment == null) {
                        mwScannerFragment = new MWScannerFragment();
                        mwScannerFragment.show(getFragmentManager(), "metawear_scanner_fragment");
                    } else {
                        mwScannerFragment.show(getFragmentManager(), "metawear_scanner_fragment");
                    }
                }
                break;
            case R.id.action_reset_device:

                MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
                connectMenuItem.setTitle(R.string.connect);
                editor.remove("ble_mac_address");
                editor.commit();
        }

        return super.onOptionsItemSelected(item);
    }

    ///////

    private void setupDatabase(){
        ActivitySampleDbHelper activitySampleDbHelper = new ActivitySampleDbHelper(this);
        activitySampleDb = activitySampleDbHelper.getWritableDatabase();
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements ServiceConnection, OnChartValueSelectedListener{

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);


            Switch demoSwitch = (Switch) rootView.findViewById(R.id.demo);
            demoSwitch.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean isChecked) {

                    GraphFragment graphFragment = (GraphFragment) getChildFragmentManager().findFragmentById(R.id.graph);
                    if(graphFragment == null){
                        graphFragment = (GraphFragment) getFragmentManager().findFragmentById(R.id.graph);
                    }
                    graphFragment.toggleDemoData(isChecked);
                }
            });

            //Accelerometer switch
//            Switch accel_switch = (Switch) rootView.findViewById(R.id.accel_switch);
//            accel_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                    Log.i("Switch State=", "" + isChecked);
//                    if (isChecked) {
//                        if (isChecked) {
//                            accelModule.setOutputDataRate(ACC_FREQ);
//                            accelModule.setAxisSamplingRange(ACC_RANGE);
//                            accelModule.routeData()
//                                    .fromAxes().stream(STREAM_KEY)
//                                    .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
//                                @Override
//                                public void success(RouteManager result) {
//                                    result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
//                                        @Override
//                                        public void process(Message message) {
//                                            CartesianFloat axes = message.getData(CartesianFloat.class);
//                                            Log.i(TAG, axes.toString());
//                                        }
//
//                                    });
//                                }
//
//                                @Override
//                                public void failure(Throwable error) {
//                                    Log.e(TAG, "Error committing route", error);
//                                }
//                            });
//                            accelModule.enableAxisSampling();
//                            accelModule.start();
//                        } else {
//                            accelModule.disableAxisSampling();
//                            accelModule.stop();
//                        }
//                    }
//                }
//            });
//
//
//            //
            GraphFragment graphFragment = getGraphFragment();
            graphFragment.getmChart().setOnChartValueSelectedListener(this);
            return rootView;
        }


        @Override
        public void onValueSelected(Entry e, int dataSetIndex, Highlight h){
            GraphFragment graphFragment = getGraphFragment();
            int steps = Float.valueOf(e.getVal()).intValue();
            TextView readingTime = (TextView) getActivity().findViewById(R.id.reading_time);
            String formattedDate;
            try {
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date date = dateFormat.parse(graphFragment.getActivitySample(e.getXIndex()).getDate());
                DateFormat outputDateFormat = new SimpleDateFormat("MMM dd, yyyy   HH:mm");
                formattedDate = outputDateFormat.format(date);
            } catch (ParseException pe){
                formattedDate = "";
            }

            readingTime.setText(formattedDate);
            TextView stepsView = (TextView) getActivity().findViewById(R.id.steps);
            stepsView.setText(String.valueOf(steps) + " " + getString(R.string.steps));
        }

        @Override
        public void onNothingSelected(){

        }

        private GraphFragment getGraphFragment(){
            GraphFragment graphFragment = (GraphFragment) getChildFragmentManager().findFragmentById(R.id.graph);
            if(graphFragment == null){
                graphFragment = (GraphFragment) getFragmentManager().findFragmentById(R.id.graph);
            }
            return graphFragment;
        }

    }

}

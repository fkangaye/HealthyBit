package com.mbientlab.activitytracker;

import android.app.AlertDialog;
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
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.Highlight;
import com.mbientlab.activitytracker.MWScannerFragment.ScannerCallback;
import com.mbientlab.activitytracker.MWDeviceConfirmFragment.DeviceConfirmCallback;
import com.mbientlab.activitytracker.db.ActivitySampleDbHelper;
import com.mbientlab.activitytracker.model.ActivitySampleContract;
import com.mbientlab.metawear.api.MetaWearBleService;
import com.mbientlab.metawear.api.MetaWearController;
import com.mbientlab.metawear.api.Module;
import com.mbientlab.metawear.api.controller.Debug;
import com.mbientlab.activitytracker.GraphFragment.GraphCallback;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class MainActivity extends ActionBarActivity implements ScannerCallback, ServiceConnection, DeviceConfirmCallback, GraphCallback, AccelerometerFragment.AccelerometerCallback
{

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private GraphFragment mGraphFragment;
    private final static int REQUEST_ENABLE_BT= 0;
    private final static String ACCELEROMETER_FRAGMENT_KEY= "com.mbientlab.activitytracker.AccelerometerFragment.ACCELEROMETER_FRAGMENT_KEY";
    private final static String GRAPH_FRAGMENT_KEY = "com.mbientlab.activitytracker.GraphFragment.GRAPH_FRAGMENT_KEY";
    private MetaWearBleService mwService= null;
    private MetaWearController mwController = null;
    private MWScannerFragment mwScannerFragment = null;
    private AccelerometerFragment accelerometerFragment = null;
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
            accelerometerFragment = new AccelerometerFragment();
            getFragmentManager().beginTransaction().add(R.id.container, mainFragment)
                    .add(R.id.container, accelerometerFragment, ACCELEROMETER_FRAGMENT_KEY).commit();
            mGraphFragment = (GraphFragment)
                    getFragmentManager().findFragmentById(R.id.graph);
        } else {
            accelerometerFragment = (AccelerometerFragment) getFragmentManager().getFragment(savedInstanceState, ACCELEROMETER_FRAGMENT_KEY);
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

    //Alert Dialog

    public void open(View view){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("Time To Stand Up!!!!");

        alertDialogBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                Toast.makeText(MainActivity.this,"Good Job!!!",Toast.LENGTH_LONG).show();
            }
        });

        alertDialogBuilder.setNegativeButton("Snooze",new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(MainActivity.this,"S",Toast.LENGTH_LONG).show();
                //finish();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }
    //

    @Override
    protected void onResume(){
        super.onResume();

        String bleMacAddress = sharedPreferences.getString("ble_mac_address", null);
        if(bleMacAddress != null){
            TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
            connectionStatus.setText(getText(R.string.metawear_connected));
        }


        accelerometerFragment = (AccelerometerFragment) getFragmentManager().findFragmentByTag(ACCELEROMETER_FRAGMENT_KEY);
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

    private MetaWearController.DeviceCallbacks dCallback= new MetaWearController.DeviceCallbacks() {
        @Override
        public void connected() {
            Log.i("Metawear Controller", "Device Connected");
            Toast.makeText(getApplicationContext(), R.string.toast_connected, Toast.LENGTH_SHORT).show();

            if(accelerometerFragment != null) {
                accelerometerFragment.restoreState(sharedPreferences);
            }

            if(btDeviceSelected) {
                MWDeviceConfirmFragment mwDeviceConfirmFragment = new MWDeviceConfirmFragment();
                mwDeviceConfirmFragment.flashDeviceLight(mwController, getFragmentManager());
                btDeviceSelected = false;
            }
        }

        @Override
        public void disconnected() {
            Log.i("Metawear Controler", "Device Disconnected");
            Toast.makeText(getApplicationContext(), R.string.toast_disconnected, Toast.LENGTH_SHORT).show();
        }
    };

    private void connectDevice(BluetoothDevice device){
        mwController = mwService.getMetaWearController(device);
        mwController.addDeviceCallback(dCallback);

        mwController.connect();


        if(menu != null) {
            MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
            connectMenuItem.setTitle(R.string.disconnect);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
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
        bluetoothDevice = device;
        btDeviceSelected = true;
        connectDevice(device);
    }

    public void pairDevice(){
        editor.putString("ble_mac_address", bluetoothDevice.getAddress());
        editor.commit();
        accelerometerFragment.addTriggers(mwController, editor);
    }

    public void dontPairDevice(){
        mwController.waitToClose(true);
        bluetoothDevice = null;
        mwController.close(true);
        mwScannerFragment.show(getFragmentManager(), "metawear_scanner_fragment");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mwService != null) {
            mwService.unregisterReceiver(MetaWearBleService.getMetaWearBroadcastReceiver());
        }
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Get a reference to the MetaWear service from the binder
        mwService= ((MetaWearBleService.LocalBinder) service).getService();
        mwService.registerReceiver(MetaWearBleService.getMetaWearBroadcastReceiver(),
                MetaWearBleService.getMetaWearIntentFilter());
        String bleMacAddress = sharedPreferences.getString("ble_mac_address", null);
        Log.i("Service Connected", "Stored mac address is " + bleMacAddress);
        if(bleMacAddress != null){
            bluetoothDevice = btAdapter.getRemoteDevice(bleMacAddress);
            connectDevice(bluetoothDevice);
        }
    }

    @Override
    public GraphFragment getGraphFragment(){
        return mGraphFragment;
    }

    @Override
    public void setGraphFragment(GraphFragment graphFragment){
        mGraphFragment = graphFragment;
    }

    @Override
    public void updateCaloriesAndSteps(int calories, int steps){
        TextView activeCaloriesBurned = (TextView) findViewById(R.id.active_calories_burned);
        activeCaloriesBurned.setText(getString(R.string.active_calories_burned) + String.valueOf(calories));
    }

    @Override
    public void startDownload(){
        TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
        connectionStatus.setText(getText(R.string.metawear_syncing));
    }

    @Override
    public void totalDownloadEntries(int entries){
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.download_progress);
        progressBar.setMax(entries);
    }

    @Override
    public void downloadProgress(int entriesDownloaded){
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.download_progress);
        progressBar.setProgress(entriesDownloaded);
    }

    @Override
    public void downloadFinished(){
        TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
        connectionStatus.setText(getText(R.string.metawear_connected));
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.download_progress);
        progressBar.setProgress(0);
    }
    ///< Don't need this callback method but we must implement it
    @Override
    public void onServiceDisconnected(ComponentName name) { }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_connect:
                if((mwController != null) && mwController.isConnected()){
                    accelerometerFragment.stopLog(mwController);
                    accelerometerFragment.removeTriggers(editor);
                    mwController.waitToClose(false);
                    MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
                    connectMenuItem.setTitle(R.string.connect);
                    editor.remove("ble_mac_address");
                    editor.commit();
                    TextView connectionStatus = (TextView) findViewById(R.id.connection_status);
                    connectionStatus.setText(getText(R.string.metawear_connected));
                    mwController.close(false);
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
                Debug debugController= (Debug) mwController.getModuleController(Module.DEBUG);
                accelerometerFragment.stopLog(mwController);
                accelerometerFragment.removeTriggers(editor);
                debugController.resetDevice();
                //mwController.waitToClose(false);
                MenuItem connectMenuItem = menu.findItem(R.id.action_connect);
                connectMenuItem.setTitle(R.string.connect);
                accelerometerFragment.removePersistedTriggers(editor);
                editor.remove("ble_mac_address");
                editor.commit();
                //mwController.close(false);
                break;
            case R.id.action_clear_log:
                activitySampleDb.execSQL("delete from " + ActivitySampleContract.ActivitySampleEntry.TABLE_NAME);
                break;
            case R.id.action_refresh:
                if(!mwController.isConnected()) {
                    mwController.connect();
                }
                if(accelerometerFragment == null){

                    accelerometerFragment.restoreState(sharedPreferences);
                }
                accelerometerFragment.startLogDownload(mwController, activitySampleDb);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

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
            //mwService= null;
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
                Log.i("MainActivity", "Date Parse Exception OnValueSelected " + pe.toString());
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

package com.mbientlab.activitytracker;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Led;

public class MWDeviceConfirmFragment extends DialogFragment {

    private static final float ACC_RANGE = 8.f, ACC_FREQ = 50.f;
    private static final String STREAM_KEY = "accel_stream";
    private Switch accel_switch;
    private Led ledModule = null;
    private Button yesButton = null;
    private Button noButton = null;
    private DeviceConfirmCallback callback = null;
    private String currentState = null;
    private Accelerometer accelModule;
    private static final String TAG = "METAWEAR";


    public void flashDeviceLight(MetaWearBoard metaWearBoard, FragmentManager fragmentManager) {
        try {
            ledModule = metaWearBoard.getModule(Led.class);
            ledModule.configureColorChannel(Led.ColorChannel.BLUE).setHighIntensity((byte) 31)
                    .setRiseTime((short) 750).setFallTime((short) 750)
                    .setHighTime((short) 500).setPulseDuration((short) 2000)
                    .setRepeatCount((byte) -1).commit();
            ledModule.play(false);

            accelModule = metaWearBoard.getModule(Accelerometer.class);


        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }

        show(fragmentManager, "device_confirm_callback");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.metawear_device_confirm, container);
    }

    @Override
    public void onAttach(Activity activity) {
        if (!(activity instanceof DeviceConfirmCallback)) {
            throw new RuntimeException("Acitivty does not implement DeviceConfirmationCallback interface");
        }

        callback= (DeviceConfirmCallback) activity;
        super.onAttach(activity);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        noButton = (Button) view.findViewById(R.id.confirm_no);
        noButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ledModule.stop(false);
                callback.dontPairDevice();
                dismiss();
            }
        });

        yesButton = (Button) view.findViewById(R.id.confirm_yes);
        yesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ledModule.stop(false);
                callback.pairDevice();
                dismiss();
            }
        });

        Switch accel_switch = (Switch) view.findViewById(R.id.accel_switch);
        accel_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i("Switch State=", "" + isChecked);
                if (isChecked) {
                    if (isChecked) {
                        accelModule.setOutputDataRate(ACC_FREQ);
                        accelModule.setAxisSamplingRange(ACC_RANGE);

                        accelModule.routeData()
                                .fromAxes().stream(STREAM_KEY)
                                .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                            @Override
                            public void success(RouteManager result) {
                                result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(Message message) {
                                        CartesianFloat axes = message.getData(CartesianFloat.class);
                                        Log.i(TAG, axes.toString());
                                    }

                                });
                            }

                            @Override
                            public void failure(Throwable error) {
                                Log.e(TAG, "Error committing route", error);
                            }
                        });
                        accelModule.enableAxisSampling(); //You must enable axis sampling before you can start
                        accelModule.start();
                    } else {
                        accelModule.disableAxisSampling(); //Likewise, you must first disable axis sampling before stopping
                        accelModule.stop();
                    }
                }
            }
        });
    }

    public interface DeviceConfirmCallback {
        public void pairDevice();
        public void dontPairDevice();
    }



}

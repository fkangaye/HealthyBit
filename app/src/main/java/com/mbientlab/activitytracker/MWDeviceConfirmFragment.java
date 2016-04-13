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

import com.mbientlab.metawear.api.MetaWearController;
import com.mbientlab.metawear.api.Module;
import com.mbientlab.metawear.api.controller.LED;

public class MWDeviceConfirmFragment extends DialogFragment {
    public interface DeviceConfirmCallback {
        public void pairDevice();
        public void dontPairDevice();
    }


    private LED ledCtrllr = null;
    private Button yesButton = null;
    private Button noButton = null;
    private DeviceConfirmCallback callback = null;
    private String currentState = null;


    public void flashDeviceLight(MetaWearController mwController, FragmentManager fragmentManager) {
        ledCtrllr = (LED) mwController.getModuleController(Module.LED);
        ledCtrllr.setColorChannel(LED.ColorChannel.BLUE).withHighIntensity((byte) 31)
                .withRiseTime((short) 750).withFallTime((short) 750)
                .withHighTime((short) 500).withPulseDuration((short) 2000)
                .withRepeatCount((byte) -1).commit();
        ledCtrllr.play(false);

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
                ledCtrllr.stop(false);
                callback.dontPairDevice();
                dismiss();
            }
        });

        yesButton = (Button) view.findViewById(R.id.confirm_yes);
        yesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ledCtrllr.stop(false);
                callback.pairDevice();
                dismiss();
            }
        });

    }
}

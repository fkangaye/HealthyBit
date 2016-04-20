package com.mbientlab.activitytracker;

import android.app.Fragment;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;

import com.mbientlab.activitytracker.model.ActivitySampleContract;
import com.mbientlab.metawear.api.MetaWearController;
import com.mbientlab.metawear.api.Module;
import com.mbientlab.metawear.api.controller.Accelerometer;
import com.mbientlab.metawear.api.controller.Accelerometer.SamplingConfig;
import com.mbientlab.metawear.api.controller.DataProcessor;
import com.mbientlab.metawear.api.controller.DataProcessor.FilterConfig;
import com.mbientlab.metawear.api.controller.Logging;
import com.mbientlab.metawear.api.controller.Logging.LogEntry;
import com.mbientlab.metawear.api.controller.Logging.ReferenceTick;
import com.mbientlab.metawear.api.controller.Logging.Trigger;
import com.mbientlab.metawear.api.util.FilterConfigBuilder;
import com.mbientlab.metawear.api.util.LoggingTrigger;
import com.mbientlab.metawear.api.util.TriggerBuilder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class AccelerometerFragment extends Fragment {
    String message = "HeathyBit Accelerometer";
    private Accelerometer accelCtrllr;
    private Logging loggingCtrllr;
    private DataProcessor dataProcessorController;
    private Editor editor;
    private MetaWearController mwController;
    private int totalEntryCount;
    private SQLiteDatabase activitySampleDb;
    private AccelerometerCallback accelerometerCallback;
    private final byte ACTIVITY_DATA_SIZE = 4;
    private final int TIME_DELAY_PERIOD = 60000;

    private byte rmsFilterId = -1, accumFilterId = -1, timeFilterId = -1, timeTriggerId = -1;

    public interface AccelerometerCallback {
        public void startDownload();
        public void totalDownloadEntries(int entries);
        public void downloadProgress(int entriesDownloaded);
        public void downloadFinished();
        public GraphFragment getGraphFragment();
    }

    @Override
    public void onResume(){
        super.onResume();
        accelerometerCallback = (AccelerometerCallback) getActivity();
        Log.d(message, "The onResume() event");
    }

    private final DataProcessor.Callbacks dpCallbacks = new DataProcessor.Callbacks() {
        @Override
        public void receivedFilterId(byte filterId) {
            byte filterArray[] = {filterId};
            if (rmsFilterId == -1) {
                rmsFilterId = filterId;
                editor.putString("rmsFilterId", Base64.encodeToString(filterArray, Base64.NO_WRAP));
                editor.commit();
                FilterConfig accumFilter= new FilterConfigBuilder.AccumulatorBuilder()
                        .withInputSize(LoggingTrigger.ACCELEROMETER_X_AXIS.length())
                        .withOutputSize(ACTIVITY_DATA_SIZE)
                        .build();

                dataProcessorController.chainFilters(rmsFilterId, ACTIVITY_DATA_SIZE, accumFilter);
            } else if (accumFilterId == -1) {
                accumFilterId= filterId;
                editor.putString("accumFilterId", Base64.encodeToString(filterArray, Base64.NO_WRAP));
                editor.commit();
                FilterConfig timeFilter = new FilterConfigBuilder.TimeDelayBuilder()
                        .withFilterMode(FilterConfigBuilder.TimeDelayBuilder.FilterMode.ABSOLUTE)
                        .withPeriod(TIME_DELAY_PERIOD)
                        .withDataSize(ACTIVITY_DATA_SIZE)
                        .build();
                dataProcessorController.chainFilters(accumFilterId, ACTIVITY_DATA_SIZE, timeFilter);
            } else {
                if(timeFilterId == -1) {
                    timeFilterId = filterId;
                    editor.putString("timeFilterId", Base64.encodeToString(filterArray, Base64.NO_WRAP));
                    editor.commit();
                    loggingCtrllr.addTrigger(TriggerBuilder.buildDataFilterTrigger(timeFilterId, ACTIVITY_DATA_SIZE));
                }
                mwController.removeModuleCallback(this);
            }

        }

    };

    private String getDateTime(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(date);
    }

    private final Logging.Callbacks logCallbacks = new Logging.Callbacks() {
        private final float notifyRatio = 0.01f;
        private boolean isDownloading;
        private ReferenceTick refTick;
        private LogEntry firstEntry = null;

        @Override
        public void receivedLogEntry(final LogEntry entry) {
            if (firstEntry == null) {
                firstEntry= entry;
            }

            int activityMilliG= ByteBuffer.wrap(entry.data())
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();



            byte tId = entry.triggerId();
            Date entryTime = entry.timestamp(refTick).getTime();

            if (tId == timeTriggerId) {
                Log.i("LoggingExample", "Time Trigger Id " + entryTime.toString() + String.valueOf(activityMilliG));//String.format(outputFormat, "Z-Axis", entryTime, Gs));
                Log.i("ActivityTracker", String.format(Locale.US, "%.3f,%.3f",
                        entry.offset(firstEntry) / 1000.0, activityMilliG / 1000.0));
                ContentValues contentValues = new ContentValues();
                contentValues.put(ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_MILLIG, activityMilliG);
                contentValues.put(ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_SAMPLE_TIME, getDateTime(entryTime));
                activitySampleDb.insert(ActivitySampleContract.ActivitySampleEntry.TABLE_NAME, null, contentValues);
            } else {
                Log.i("LoggingExample", String.format("Unkown Trigger ID, (%d, %s)",
                        tId, Arrays.toString(entry.data())));
            }
        }


        @Override
        public void receivedReferenceTick(ReferenceTick reference) {
            refTick = reference;

            Log.i("LoggingExample", String.format("Received the reference tick = %s, %d", reference, reference.tickCount()));
            // Got the reference tick, make lets get
            // the log entry count
            loggingCtrllr.readTotalEntryCount();
        }

        @Override
        public void receivedTriggerId(byte triggerId) {
            byte triggerArray[] = {triggerId};
            timeTriggerId = triggerId;
            editor.putString("timeTriggerId", Base64.encodeToString(triggerArray, Base64.NO_WRAP));
            editor.commit();
            startLog();
            Log.i("receivedTrigger", "Received trigger id " + String.valueOf(triggerId));
            Log.i("encoded trigger", Base64.encodeToString(triggerArray, Base64.NO_WRAP));
            Log.i("decoded trigger", String.valueOf(Base64.decode(Base64.encodeToString(triggerArray, Base64.NO_WRAP), Base64.NO_WRAP)[0]));
        }

        @Override
        public void receivedTotalEntryCount(int totalEntries) {
            if (!isDownloading && (totalEntries > 0)) {
                totalEntryCount = totalEntries;
                isDownloading = true;
                Log.i("LoggingExample", "Download begin");

                //Got the entry count, lets now download the log
                loggingCtrllr.downloadLog(totalEntries, (int) (totalEntries * notifyRatio));
                accelerometerCallback.totalDownloadEntries(totalEntries);
            }else{
                accelerometerCallback.downloadFinished();
                Log.i("LoggingExample", "Total Entries count " + String.valueOf(totalEntries));
            }
        }

        @Override
        public void receivedDownloadProgress(int nEntriesLeft) {
            Log.i("LoggingExample", String.format("Entries remaining= %d", nEntriesLeft));
            accelerometerCallback.downloadProgress(totalEntryCount - nEntriesLeft);
        }

        @Override
        public void downloadCompleted() {
            isDownloading = false;
            Log.i("removing ", String.valueOf((short)totalEntryCount) + " entries");
            loggingCtrllr.removeLogEntries((short) totalEntryCount);
            Log.i("LoggingExample", "Download completed");
            mwController.waitToClose(false);
            GraphFragment graphFragment = accelerometerCallback.getGraphFragment();
            graphFragment.updateGraph();
            accelerometerCallback.downloadFinished();
        }
    };

    public void restoreState(SharedPreferences sharedPreferences ){
        String rmsFilterString = sharedPreferences.getString("rmsFilterId", null);
        String accumFilterString = sharedPreferences.getString("accumFilterId", null);
        String timeFilterString = sharedPreferences.getString("timeFilterId", null);
        String timeTriggerString = sharedPreferences.getString("timeTriggerId", null);
        Log.i("Accelerometer", "Time Trigger Id is " + timeTriggerString);

        if((rmsFilterString != null) && (timeFilterString != null) && (timeTriggerString != null)) {
            rmsFilterId = Base64.decode(rmsFilterString, Base64.NO_WRAP)[0];
            accumFilterId = Base64.decode(accumFilterString, Base64.NO_WRAP)[0];
            timeFilterId = Base64.decode(timeFilterString, Base64.NO_WRAP)[0];
            timeTriggerId = Base64.decode(timeTriggerString, Base64.NO_WRAP)[0];
        }
        Log.i("Accelerometer", "Time Trigger Id is " + String.valueOf(timeTriggerId) );
    }

    public void removeTriggers(Editor editor) {
        loggingCtrllr.removeTrigger(timeTriggerId);
        removePersistedTriggers(editor);
    }

    public void removePersistedTriggers(Editor editor) {
        editor.remove("rmsFilterId");
        editor.remove("accumFilterId");
        editor.remove("timeFilterId");
        editor.remove("timeTriggerId");
        editor.commit();
        // Reset the IDs to -1
        rmsFilterId = -1;
        accumFilterId = -1;
        timeFilterId = -1;
        timeTriggerId = -1;
    }

    public void addTriggers(MetaWearController mwController, Editor editor) {
        /*
         * The board will start logging once all triggers have been registered.  This is done
         * by having the receivedTriggerId callback fn start the logger when the ID for the
         * Z axis has been received
         */
        this.editor = editor;
        this.mwController = mwController;
        accelCtrllr = (Accelerometer) mwController.getModuleController(Module.ACCELEROMETER);
        Trigger accelerometerTrigger = TriggerBuilder.buildAccelerometerTrigger();

        setupLogginController(mwController);

        FilterConfig rms= new FilterConfigBuilder.RMSBuilder().withInputCount((byte) 3)
                .withSignedInput().withOutputSize(LoggingTrigger.ACCELEROMETER_X_AXIS.length())
                .withInputSize(LoggingTrigger.ACCELEROMETER_X_AXIS.length())
                .build();

        dataProcessorController.addFilter(accelerometerTrigger, rms);

        final Accelerometer accelCtrllr= (Accelerometer) mwController.getModuleController(Module.ACCELEROMETER);
        accelCtrllr.enableXYZSampling().withFullScaleRange(SamplingConfig.FullScaleRange.FSR_8G)
                .withHighPassFilter((byte) 0).withOutputDataRate(SamplingConfig.OutputDataRate.ODR_100_HZ)
                .withSilentMode();
        accelCtrllr.startComponents();
    }

    private void startLog() {
        loggingCtrllr.startLogging();

        SamplingConfig samplingConfig = accelCtrllr.enableXYZSampling();
        samplingConfig.withFullScaleRange(SamplingConfig.FullScaleRange.FSR_8G)
                .withOutputDataRate(SamplingConfig.OutputDataRate.ODR_100_HZ)
                .withSilentMode();

        accelCtrllr.startComponents();
    }

    public void stopLog(MetaWearController mwController) {
        setupLogginController(mwController);
        loggingCtrllr.stopLogging();

        if(accelCtrllr == null){
            accelCtrllr = (Accelerometer) mwController.getModuleController(Module.ACCELEROMETER);
        }

        accelCtrllr.stopComponents();
    }

    private void setupLogginController(MetaWearController mwController){
        if(loggingCtrllr == null) {
            loggingCtrllr = (Logging) mwController.getModuleController(Module.LOGGING);
            mwController.addModuleCallback(logCallbacks);
        }
        if(dataProcessorController == null) {
            dataProcessorController = (DataProcessor) mwController.getModuleController(Module.DATA_PROCESSOR);
        }
        mwController.addModuleCallback(dpCallbacks);
    }

    public void startLogDownload(MetaWearController mwController, SQLiteDatabase activitySampleDb) {
        /*
           Before actually calling the downloadLog method, we will first gather the required
           data to compute the log timestamps and setup progress notifications.
           This means we will call downloadLog in one of the logging callback functions, and
           will start the callback chain here
         */
        this.activitySampleDb = activitySampleDb;
        this.mwController = mwController;
        setupLogginController(mwController);
        Log.i("LoggingExample", String.format("Starting Log Download"));
        loggingCtrllr.readReferenceTick();
        accelerometerCallback.startDownload();
    }
}
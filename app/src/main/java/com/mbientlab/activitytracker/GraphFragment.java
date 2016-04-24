package com.mbientlab.activitytracker;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.ChartData;
import com.mbientlab.activitytracker.db.ActivitySampleDbHelper;
import com.mbientlab.activitytracker.model.ActivitySample;
import com.mbientlab.activitytracker.model.ActivitySampleContract;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GraphFragment extends Fragment {

    public static Fragment newInstance() {
        return new GraphFragment();
    }

    final int colors[] = {Color.parseColor("#FF9500"), Color.parseColor("#FF4B30")};

    private BarChart mChart;
    private int totalSteps = 66;
    private int totalCalories = 200;
    private boolean demo = false;
    private SQLiteDatabase activitySampleDb;
    private ActivitySample[] activitySamples = new ActivitySample[61];
    private GraphCallback callback;

    public interface GraphCallback {
        public void updateCaloriesAndSteps(int totalCalories, int totalSteps);
        public void setGraphFragment(GraphFragment graphFragment);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        for (int i = 0; i < activitySamples.length; i++) {
            activitySamples[i] = new ActivitySample();
        }

        View v = inflater.inflate(R.layout.fragment_graph, container, false);
        mChart = (BarChart) v.findViewById(R.id.gragh_layout);
        mChart.setDescription("");
        mChart.setDrawGridBackground(false);
        mChart.setDrawBarShadow(false);
        mChart.setDrawBorders(false);
        mChart.setMaxVisibleValueCount(1);
        mChart.setBackgroundColor(Color.BLACK);

        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        if (!(activity instanceof GraphCallback)) {
            throw new RuntimeException("Acitivty does not implement DeviceConfirmationCallback interface");
        }
        callback = (GraphCallback) activity;
        super.onAttach(activity);
    }

    @Override
    public void onStart(){
        super.onStart();
        ActivitySampleDbHelper activitySampleDbHelper = new ActivitySampleDbHelper(getActivity());
        activitySampleDb = activitySampleDbHelper.getWritableDatabase();
    }

    @Override
    public void onStop(){
        super.onStop();
        activitySampleDb.close();
    }

    @Override
    public void onResume(){
        super.onResume();

        callback.setGraphFragment(this);
        updateGraph();
        Legend l = mChart.getLegend();
        l.setEnabled(false);

        YAxis leftAxis = mChart.getAxisLeft();

        mChart.getAxisRight().setEnabled(false);
        mChart.getXAxis().setEnabled(false);

        XAxis xAxis = mChart.getXAxis();
        xAxis.setEnabled(false);
        mChart.getAxisLeft().setEnabled(false);
    }

    public BarChart getmChart() {
        return mChart;
    }

    public void toggleDemoData(boolean isChecked) {
        demo = isChecked;
        updateGraph();
    }

    public void updateGraph() {
        if (demo) {
            generateBarData(1, 2000000, 60);
            mChart.setData(getCurrentReadings());
        } else {
            readPersistedValues();
            mChart.setData(getCurrentReadings());
        }
        mChart.invalidate();
        callback.updateCaloriesAndSteps(totalCalories, totalSteps);
    }

    private void readPersistedValues() {
        String activitySamplerQuery = "SELECT * FROM " + ActivitySampleContract.ActivitySampleEntry.TABLE_NAME + " ORDER BY " +
                ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_SAMPLE_TIME + " DESC LIMIT 61";

        if (activitySampleDb != null) {
            Cursor activitySampleCursor = activitySampleDb.rawQuery(activitySamplerQuery, null);

            int activitytSampleCount = activitySampleCursor.getCount();

            int dbStartIndex = 0;

            if (activitytSampleCount < 61) {
                dbStartIndex = 61 - activitytSampleCount;
                int zeroDbStartIndex = (dbStartIndex == 61) ? 60 : dbStartIndex;
                for (int i = 0; i <= zeroDbStartIndex; i++) {
                    activitySamples[i].setDate("");
                    activitySamples[i].setTotalMilliG(0L);
                }
            }

            for (int i = 60; i >= dbStartIndex; i--) {
                activitySampleCursor.moveToNext();
                String date = activitySampleCursor.getString(activitySampleCursor.getColumnIndex(ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_SAMPLE_TIME));
                Long milliG = activitySampleCursor.getLong(activitySampleCursor.getColumnIndex(ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_MILLIG));
                activitySamples[i].setDate(date);
                activitySamples[i].setTotalMilliG(milliG);
                //Log.i(date, "test");
                //Log.i("GraphFragment data time ",date);
                //Log.i("GraphFragment data value ", String.valueOf(milliG));
            }
            activitySampleCursor.close();
        }
    }

    public BarData getCurrentReadings() {
        ArrayList<BarDataSet> sets = new ArrayList<BarDataSet>();

        ArrayList<BarEntry> entries = new ArrayList<BarEntry>();

        totalCalories = 0;
        totalSteps = 0;

        for (int j = 1; j < 61; j++) {
            activitySamples[j].setIndividualMilliG(activitySamples[j].getTotalMilliG() - activitySamples[j - 1].getTotalMilliG());
            totalCalories += activitySamples[j].getCalories();
            totalSteps += activitySamples[j].getSteps();
            entries.add(new BarEntry(activitySamples[j].getSteps(), j));
//            Log.i("GraphFragment", "Total MilliG prev: " + activitySamples[j-1].getTotalMilliG());
//            Log.i("GraphFragment", "Total MilliG: " + activitySamples[j].getTotalMilliG());
//            Log.i("GraphFragment", "Individual MilliG: " + activitySamples[j].getIndividualMilliG());
//            Log.i("GraphFragment", "Steps: " + activitySamples[j].getSteps());
//            Log.i("GraphFragment", "Calories: " + activitySamples[j].getCalories());
        }

        BarDataSet ds = new BarDataSet(entries, getLabel(0));

        ds.setColors(colors);
        sets.add(ds);

        return new BarData(ChartData.generateXVals(0, 60), sets);
    }

    public ActivitySample getActivitySample(int index) {
        return activitySamples[index];
    }

    protected void generateBarData(int dataSets, float range, int count) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            Long runningTotal = 0L;
            for (int j = 0; j < count; j++) {
                activitySamples[j].setDate(dateFormat.format(new Date(System.currentTimeMillis() - (60000 * (count - j)))));
                runningTotal += (long) ((Math.random() * range) + range / 4);
                activitySamples[j].setTotalMilliG(runningTotal);
            }
    }

    private String[] mLabels = new String[]{"Activity A", "Activity B", "Activity C", "Activity D", "Activity E", "Activity F"};

    private String getLabel(int i) {
        return mLabels[i];
    }
}

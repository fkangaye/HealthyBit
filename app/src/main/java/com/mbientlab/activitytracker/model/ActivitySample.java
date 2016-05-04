package com.mbientlab.activitytracker.model;


import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public class ActivitySample {
    // Rough estimate of how many raw accelerometer counts are in a step
    public final static int ACTIVITY_PER_STEP = 20000;
    // Estimate of calories burned per step assuming casual walking speed @150 pounds
    public final static double CALORIES_PER_STEP = 0.045;
    private String date = "";
    private Long totalMilliG = 0L;
    private int steps = 0;
    private int calories = 0;
    private Long individualMilliG = 0L;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Long getTotalMilliG() {
        return totalMilliG;
    }

    public void setIndividualMilliG(Long milliG) {
        this.individualMilliG = milliG;
        if(milliG > 0) {
            steps = (int) (milliG / ACTIVITY_PER_STEP);
            calories = (int) (steps * CALORIES_PER_STEP);
        } else {
            steps = 0;
            calories = 0;
        }
    }

    public Long getIndividualMilliG() {
        return individualMilliG;
    }

    public void setTotalMilliG(Long milliG) {
        this.totalMilliG = milliG;
    }

    public int getSteps() {
        return steps;
    }

    public int getCalories() {
        return calories;
    }
}

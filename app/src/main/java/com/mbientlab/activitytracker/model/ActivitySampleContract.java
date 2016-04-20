package com.mbientlab.activitytracker.model;

import android.provider.BaseColumns;

public class ActivitySampleContract {
    public ActivitySampleContract(){}

    public static abstract class ActivitySampleEntry implements BaseColumns {
        public static final String TABLE_NAME = "activity_sample";
        public static final String COLUMN_NAME_SAMPLE_TIME = "sample_time";
        public static final String COLUMN_NAME_MILLIG = "millig";
    }
}

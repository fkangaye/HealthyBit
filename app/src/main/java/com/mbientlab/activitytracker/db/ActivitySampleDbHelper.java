package com.mbientlab.activitytracker.db;

import android.content.Context;
import android.util.Log;

import com.mbientlab.activitytracker.model.ActivitySampleContract;


public class ActivitySampleDbHelper extends DbHelper{

    private static final String[] SQL_CREATE_ENTRIES = {
            "CREATE TABLE " + ActivitySampleContract.ActivitySampleEntry.TABLE_NAME + " (" +
                    ActivitySampleContract.ActivitySampleEntry._ID + " INTEGER PRIMARY KEY," +
                    ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_SAMPLE_TIME + TEXT_TYPE + COMMA_SEP +
                    ActivitySampleContract.ActivitySampleEntry.COLUMN_NAME_MILLIG + INT_TYPE +
                    " )"};


    private static final String[] SQL_DELETE_ENTRIES = {
            "DROP TABLE IF EXISTS " + ActivitySampleContract.ActivitySampleEntry.TABLE_NAME
    };

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "MetaTracker.db";

    public ActivitySampleDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION, SQL_CREATE_ENTRIES, SQL_DELETE_ENTRIES);
    }


}

package com.mbientlab.activitytracker.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public abstract class DbHelper extends SQLiteOpenHelper {

    protected static final String TEXT_TYPE = " TEXT";
    protected static final String INT_TYPE = " INT";
    protected static final String REAL_TYPE = " REAL";
    protected static final String COMMA_SEP = ",";
    public static String[] CREATE_ENTRIES_SQL;
    public static String[] DELETE_ENTRIES_SQL;
    // probably need to remove this

    public DbHelper(Context context, String databaseName, SQLiteDatabase.CursorFactory cursorFactory, int dbVersion){
        super(context, databaseName, cursorFactory, dbVersion);
    }

    public DbHelper(Context context, String databaseName, SQLiteDatabase.CursorFactory cursorFactory, int dbVersion, String[] createEntriesSql, String[] deleteEntriesSql) {
        this(context, databaseName, cursorFactory, dbVersion);
        CREATE_ENTRIES_SQL = createEntriesSql;
        DELETE_ENTRIES_SQL = deleteEntriesSql;
    }

    public void onCreate(SQLiteDatabase db) {
        for(int i = 0; i < CREATE_ENTRIES_SQL.length; i++) {
            db.execSQL(CREATE_ENTRIES_SQL[i]);
        }
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        for(int i = 0; i < DELETE_ENTRIES_SQL.length; i++) {
            db.execSQL(DELETE_ENTRIES_SQL[i]);
        }
        onCreate(db);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

}


package com.harliestar.android.aliveandtexting;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ReminderDbAdapter {

    public static final String KEY_ROWID = "_id";
    public static final String KEY_CONTACT_ID = "contact_id";

    public static final String KEY_ROLE = "role";
    public static final String KEY_CREATED = "created";
    public static final String KEY_LAST_UPDATED = "last_updated";
    public static final String KEY_TIME = "time";
    public static final String KEY_PASSPHRASE = "passphrase";
    public static final String KEY_RINGTONE_URI = "ringtone_uri";
    public static final String KEY_NEXT_ALARM = "next_alarm";

    
    public static final Integer EXPECTING = 0;
    public static final Integer SENDING = 1;
    
    private static final String TAG = "ReminderDbAdapter";
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    /**
     * Database creation sql statement
     */
    private static final String DATABASE_CREATE =
        "create table reminders (_id integer primary key autoincrement, "
        + "contact_id integer not null, role integer not null, "
        + "time integer not null, passphrase text, ringtone_uri text not null, "
        + "created integer not null, last_updated integer not null, next_alarm integer  );";

    private static final String DATABASE_NAME = "data";
    private static final String DATABASE_TABLE = "reminders";
    private static final int DATABASE_VERSION = 1;
    
    private static final String DEBUG_TAG = "Reminder DB Adapter";

    private final Context mCtx;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS reminders");
            onCreate(db);
        }
    }

    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     * 
     * @param ctx the Context within which to work
     */
    public ReminderDbAdapter(Context ctx) {
        this.mCtx = ctx;
    }

    /**
     * Open the reminders database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     * 
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public ReminderDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        mDbHelper.close();
    }


    /**
     * Create a new reminder using the name  provided. If the reminder is
     * successfully created return the new rowId for that reminder, otherwise return
     * a -1 to indicate failure.
     * 
     * @param name the name of the contact
     * @return rowId or -1 if failed
     */
    public long createReminder( String contactId, Integer role, long time, String passphrase, String ringtoneURI) {
        ContentValues initialValues = new ContentValues();
        Calendar cal = new GregorianCalendar();
        long now = cal.getTimeInMillis();
        initialValues.put(KEY_CONTACT_ID, contactId);
        initialValues.put(KEY_ROLE, role);
        initialValues.put(KEY_TIME, time);
        initialValues.put(KEY_PASSPHRASE, passphrase);
        initialValues.put(KEY_RINGTONE_URI, ringtoneURI);

        initialValues.put(KEY_LAST_UPDATED, now);
        initialValues.put(KEY_CREATED, now);
        initialValues.put(KEY_NEXT_ALARM, time);

        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }

    /**
     * Delete the reminder with the given rowId
     * 
     * @param rowId id of reminder to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteReminder(long rowId) {

        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }

    /**
     * Return a Cursor over the list of all reminders in the database
     * 
     * @return Cursor over all reminders
     */
    public Cursor fetchAllReminders() {

        return mDb.query(DATABASE_TABLE, new String[] {KEY_ROWID, KEY_CONTACT_ID, KEY_ROLE,
        		KEY_TIME, KEY_PASSPHRASE, KEY_RINGTONE_URI, KEY_CREATED, KEY_LAST_UPDATED,
                }, null, null, null, null, KEY_TIME);
    }

    /**
     * Return a Cursor positioned at the reminder that matches the given rowId
     * 
     * @param rowId id of reminder to retrieve
     * @return Cursor positioned to matching reminder, if found
     * @throws SQLException if reminder could not be found/retrieved
     */
    public Cursor fetchReminder(long rowId) throws SQLException {

        Cursor mCursor =

            mDb.query(true, DATABASE_TABLE, new String[] {KEY_ROWID,
            		KEY_CONTACT_ID, KEY_ROLE, 
                    KEY_TIME,KEY_PASSPHRASE, KEY_RINGTONE_URI,
                    KEY_CREATED, KEY_LAST_UPDATED, KEY_NEXT_ALARM
                    }, KEY_ROWID + "=" + rowId, null,
                    null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }
    
    public Cursor fetchNextReminder() throws SQLException {

        Cursor mCursor =

	            mDb.query(true, DATABASE_TABLE, new String[] {KEY_ROWID,
                    KEY_CONTACT_ID, KEY_ROLE, 
                    KEY_TIME,KEY_PASSPHRASE, KEY_RINGTONE_URI,
                    KEY_CREATED, KEY_LAST_UPDATED, KEY_NEXT_ALARM
                    }, null, null, 
                    null, null, KEY_TIME, "1" );
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    /**
     * Update the reminder using the details provided. The reminder to be updated is
     * specified using the rowId, and it is altered to use the name 
     * values passed in
     * 
     * @param rowId id of reminder to update
     * @param name value to set reminder name to
     * @return true if the reminder was successfully updated, false otherwise
     */
    public boolean updateReminder(long rowId, String contactId, Integer role, long time, String passphrase, String ringtoneURI) {
        ContentValues args = new ContentValues();
        Calendar cal = new GregorianCalendar();
        long now = cal.getTimeInMillis();
        Log.v(DEBUG_TAG, "time: " + time);
        args.put(KEY_CONTACT_ID, contactId );
        args.put(KEY_ROLE, role);
        args.put(KEY_TIME, time);
        args.put(KEY_RINGTONE_URI, ringtoneURI);
        args.put(KEY_PASSPHRASE, passphrase);
        args.put(KEY_LAST_UPDATED, now);
        args.put(KEY_NEXT_ALARM, time);
        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }
    
    public boolean updateNextAlarm(long rowId, long time) {
        ContentValues args = new ContentValues();
          
        Log.v(DEBUG_TAG, "alarm: " + time);
        
        args.put(KEY_NEXT_ALARM, time);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }
}




package com.harliestar.android.aliveandtexting;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

public class ReminderManager extends BroadcastReceiver {

	private static Integer ALARM_REQUEST_CODE = 2001;
	private static String DEBUG_TAG = "ReminderManager";
	private ReminderDbAdapter mDbHelper;
	private static Integer SNOOZE = 1000 * 60 * 5; //five mins
	private static Uri INCOMING_SMS_URI = Uri.parse("content://sms/inbox");
	private static Uri OUTGOING_SMS_URI = Uri.parse("content://sms/sent"); 

	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Log.v(DEBUG_TAG, "received alarm");
		mDbHelper = new ReminderDbAdapter(context);
		mDbHelper.open();
		checkNextReminder(context);
		mDbHelper.close();

	}
	
	private static void setReminder(Context context, long time){
		Intent intent = new Intent(context, ReminderManager.class);
		PendingIntent sender = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		long now = System.currentTimeMillis();
		float diff = (float) (time - now) / 60000;
		Log.v(DEBUG_TAG, "setting reminder for time:" + time + " it is now " + System.currentTimeMillis() + " diff:" + diff);
		// Get the AlarmManager service
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, time, sender);
	}
	
	private static void clearReminders(Context context){
		Intent intent = new Intent(context, ReminderManager.class);
		PendingIntent sender = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		// Get the AlarmManager service
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		am.cancel(sender);
	}
	
	protected static void updateReminders(Context context, ReminderDbAdapter rdba){
		
		Cursor reminder = rdba.fetchNextReminder();
		Log.v(DEBUG_TAG, "updating reminder");
		long nextTime = 0;
		if (reminder.getCount()>0){
			nextTime =  (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_NEXT_ALARM)));
		}
		clearReminders(context);
		if (nextTime != 0 ){
			if (nextTime < System.currentTimeMillis()){
				nextTime += SNOOZE;
				long id =  (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROWID)));
				rdba.updateNextAlarm(id, nextTime);
			}
			setReminder(context, nextTime);
		}
		reminder.close();
	}
	
	private void checkNextReminder(Context context){
		Log.v(DEBUG_TAG, "checking reminder");

		Cursor reminder = mDbHelper.fetchNextReminder();
		if (reminder == null){
			return;
		}
		Log.v(DEBUG_TAG, "reminder not null");

		Calendar cal = Calendar.getInstance();
		long now = cal.getTimeInMillis();
		long reminderTime = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_TIME)));
		
		//if the next reminder is in the future, this alarm probably should have been cleared
		if (reminderTime >= now){
			return;
		}
		
		//get role
		int role = (reminder.getInt(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROLE)));
		String passPhrase = (reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_PASSPHRASE)));
		String contactId = (reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_CONTACT_ID)));
		Uri ringTone = Uri.parse((reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_RINGTONE_URI))));
		String name = contactId;
		Cursor contactsCursor = context.getContentResolver().query(Phone.CONTENT_URI, null, 
				Phone.CONTACT_ID + "=?",new String[]{contactId},null);
    	
    	if (contactsCursor.getCount() > 0){
    		contactsCursor.moveToFirst();
    		name = contactsCursor.getString(contactsCursor.getColumnIndex(Phone.DISPLAY_NAME));
    	}
    	contactsCursor.close();
    	
		long lastUpdated = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_LAST_UPDATED)));
		long rowId = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROWID)));
		//check to see if a message was received/sent
		if (checkReceipt(context, mDbHelper, rowId, role, contactId, passPhrase, lastUpdated )){
			updateReminders(context, mDbHelper);
		}
		else {
			Log.v(DEBUG_TAG, "not cleared");			

			long nextAlarm = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_NEXT_ALARM)));
			Log.v(DEBUG_TAG, "next alarm : " + nextAlarm);			
			if (nextAlarm < System.currentTimeMillis()){
				mDbHelper.updateNextAlarm(rowId, now+SNOOZE);
				triggerNotification(context, rowId, role, name, ringTone);
				clearReminders(context);
				setReminder(context, now+SNOOZE);
			}
		}
		
		reminder.close();
		
	
		
	}
	
	private static boolean checkReceipt(Context context, ReminderDbAdapter dbHelper, long rowId, int role, String contactId, String passPhrase, long lastUpdated){
		if (role == ReminderDbAdapter.EXPECTING){
			Log.v(DEBUG_TAG, "expecting");
			if (textFound( dbHelper, context, INCOMING_SMS_URI, contactId, passPhrase, lastUpdated)){
				Log.v(DEBUG_TAG, "text found");

				dbHelper.deleteReminder(rowId);
				return true;
			}
			else {
				Log.v(DEBUG_TAG, "incoming text not found");
				
			}
		}
		//check to see if a message was sent
		else {
			if (textFound( dbHelper, context, OUTGOING_SMS_URI, contactId, passPhrase, lastUpdated)){
				dbHelper.deleteReminder(rowId);
				return true;
			}
			else {
				Log.v(DEBUG_TAG, "incoming text not found");
				
			}
		}
		return false;
	}
	
	protected static boolean textFound (ReminderDbAdapter dbHelper, Context context, Uri uri, String contactId, String passPhrase, long lastUpdated ){
		String selection = "date > ?";
		
		ContentResolver cr = context.getContentResolver();
		Cursor cursor = cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + "=?",new String[]{contactId},null);
		Set<String> phoneNumbers = new HashSet<String>(cursor.getCount());
		if (cursor.getCount() > 0){
			cursor.moveToFirst();
			do {
				phoneNumbers.add(cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));
				Log.v(DEBUG_TAG, "number:" + cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));
			} while (cursor.moveToNext());
			
		}
		else {
			return false;
		}
		cursor = cr.query(uri, null, selection, new String[]{String.valueOf(lastUpdated)}, null);
		cursor.moveToFirst();
		if (cursor.getCount() > 0){
			Log.v(DEBUG_TAG, "yeah");

			Pattern pattern = null;			
			if (passPhrase != null && passPhrase.length() > 0){
				Log.v(DEBUG_TAG, "passPhrase: " + passPhrase);
				passPhrase = "\\Q" + passPhrase + "\\E";
				pattern = Pattern.compile(passPhrase, Pattern.CASE_INSENSITIVE);
			}
			do {
				//check phone number
				String address = cursor.getString(cursor.getColumnIndex("address"));
				boolean phoneMatches = false;
				
				for( String phoneNumber : phoneNumbers){
					Log.v(DEBUG_TAG, "comparing " + phoneNumber + " and " + address );
					if (PhoneNumberUtils.compare(phoneNumber, address)){
						phoneMatches = true;
						Log.v(DEBUG_TAG, "match!"); 
						break;
					}
				};
				
				
				if (phoneMatches){	
					Log.v(DEBUG_TAG, "checkingPattern");
					if (pattern != null){
						Log.v(DEBUG_TAG, "pattern not null");
						Log.v(DEBUG_TAG, cursor.getString(cursor.getColumnIndex("body")));
						Matcher bodyMatcher = pattern.matcher(cursor.getString(cursor.getColumnIndex("body")));
						if (bodyMatcher.find(0)){
							Log.v(DEBUG_TAG,"match!");
							cursor.close();
							return true;
						}
					}
					else {
						cursor.close();
						
						return true;
					}
				}		
				
			} while (cursor.moveToNext());
			
		}
		cursor.close();
		
		return false;
	}
	
	private void triggerNotification(Context context, long rowId, int role, String name, Uri ringTone){
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		String text =  context.getString(R.string.you_should_have_received); 
		if (role == ReminderDbAdapter.SENDING){
			text = " " + context.getString(R.string.you_should_have_sent);
		}
		Log.v(DEBUG_TAG, "setting up notification");
		
		text += " " + name + "!";
		int icon = android.R.drawable.ic_dialog_alert;
		Notification notification = new Notification(icon, text, System.currentTimeMillis());
		Intent notificationIntent = new Intent(context, ReminderEdit.class);
		notificationIntent.putExtra(ReminderDbAdapter.KEY_ROWID, rowId);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context,context.getString(R.string.app_name) , text, contentIntent);
		notification.sound = ringTone;
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		nm.notify(1, notification);

	
	}
	
	protected static void checkAllReminders(Context context, ReminderDbAdapter dbHelper ){
		Cursor reminder = dbHelper.fetchAllReminders();
		if (reminder.getCount() < 1) return;
		reminder.moveToFirst();
		
		do {
			long lastUpdated = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_LAST_UPDATED)));
			long rowId = (reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROWID)));
			int role = (reminder.getInt(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROLE)));
			String passPhrase = (reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_PASSPHRASE)));
			String contactId = (reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_CONTACT_ID)));
			Log.v(DEBUG_TAG, "checking:" + contactId);

			checkReceipt(context, dbHelper, rowId, role, contactId, passPhrase, lastUpdated);
		} while (reminder.moveToNext());
		updateReminders(context, dbHelper);
		reminder.close();
	}

}

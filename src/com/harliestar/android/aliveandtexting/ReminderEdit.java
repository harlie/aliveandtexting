package com.harliestar.android.aliveandtexting;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class ReminderEdit extends Activity {

    private ReminderDbAdapter mDbHelper;
    private TextView mContactNameText;
    private EditText mPassphraseText;
    private TimePicker mPickTime;
    private TextView mRingtoneText;
    private TextView mToFromText;
    
    
    
	private Long mRowId;
    private String mContactId;
    private Uri mRingtoneURI;
    private String mPhoneNum;
    private String mPhoneArr[];
    
    private static final int CONTACT_PICKER_RESULT = 1001; 
    private static final int SOUND_PICKER_RESULT = 1002; 
    
	private static final int DELETE_ID = Menu.FIRST;


    private static final String DEBUG_TAG = "ReminderEdit";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    mDbHelper = new ReminderDbAdapter(this);
	    mDbHelper.open();
		setContentView(R.layout.reminder_edit);
		setTitle(R.string.edit_reminder);
		mToFromText = (TextView) findViewById(R.id.fromLabel);
		mContactNameText = (TextView) findViewById(R.id.contactName);
		mPassphraseText = (EditText) findViewById(R.id.containsPhrase);
        mPickTime = (TimePicker) findViewById(R.id.timePicker);
        mRingtoneText = (TextView) findViewById(R.id.soundName);
		mRingtoneURI = null;
	
	
		
		Button confirmButton = (Button) findViewById(R.id.saveReminder);
        mRowId = (savedInstanceState == null) ? null :
            (Long) savedInstanceState.getSerializable(ReminderDbAdapter.KEY_ROWID);
        if (mRowId == null) {
            Bundle extras = getIntent().getExtras();
            mRowId = extras != null ? extras.getLong(ReminderDbAdapter.KEY_ROWID)
                                    : null;
        }
        populateFields();
        
		confirmButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View view) {
				if (saveState()){
					setResult(RESULT_OK);
					mDbHelper.close();
			    	finish();
			    }
				
			}
			

		});
		
		RadioGroup roleRadio = (RadioGroup) findViewById(R.id.radioGroupRole);
		roleRadio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				Log.v(DEBUG_TAG,"checked id: " + checkedId );
				setToFromText();
			    	
			}
		});
		
		

	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		mDbHelper.close();
		super.onSaveInstanceState(outState);
		outState.putSerializable(ReminderDbAdapter.KEY_ROWID, mRowId);
	}
	
	 @Override
	 protected void onPause() {
		 mDbHelper.close();
		 super.onPause();
	 }

	 @Override
	 protected void onResume() {
		 mDbHelper.open();
		 super.onResume();
	}

	 @Override
	 public boolean onCreateOptionsMenu(Menu menu) {
	     boolean result = super.onCreateOptionsMenu(menu);
	     menu.add(0, DELETE_ID, 0, R.string.menu_delete);
	     return result;
	 }

	 @Override
	 public boolean onOptionsItemSelected(MenuItem item) {
		 switch (item.getItemId()) {
		 case DELETE_ID:
			 deleteReminder();
			 return true;
		 }
	       
		 return super.onOptionsItemSelected(item);
	 }
	 
	
	 private void deleteReminder(){
		 if (mRowId != null){
			 mDbHelper.deleteReminder(mRowId);
		 }
		 mDbHelper.close();
		 finish();
	 }
	 private void populateFields() {
		 RadioButton selectedRoleButton = (RadioButton) findViewById(R.id.radioExpecting);
		 selectedRoleButton.setChecked(true);
		 if (mRowId != null) {
			 Cursor reminder = mDbHelper.fetchReminder(mRowId);
			 if (reminder.getCount()<1){
				 return;
			 }
			 startManagingCursor(reminder);
			 Integer role =  (reminder.getInt(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_ROLE)));
			 mContactId =  (reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_CONTACT_ID)));
			 Cursor contactCursor =  getContentResolver().query(Phone.CONTENT_URI, null, 
     				Phone.CONTACT_ID + "=?",new String[]{mContactId.toString()},null);
			 if (contactCursor.getCount() < 1){
				 return;
			 }
			 contactCursor.moveToFirst();
			 String contactName = contactCursor.getString(contactCursor.getColumnIndex(Phone.DISPLAY_NAME));
			 if (role == ReminderDbAdapter.SENDING) {
			 	selectedRoleButton = (RadioButton) findViewById(R.id.radioSending);
			 }
			 else {
			 	selectedRoleButton = (RadioButton) findViewById(R.id.radioExpecting);
			 }
			 selectedRoleButton.setChecked(true);
			 setToFromText();
			 mContactNameText.setText(contactName);
			 mPassphraseText.setText(reminder.getString(
					 reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_PASSPHRASE)));
			 long time = reminder.getLong(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_TIME));
			 Calendar cal = Calendar.getInstance();
			 cal.setTimeInMillis(time);

			 int hour = cal.get(Calendar.HOUR_OF_DAY);
			 int min = cal.get(Calendar.MINUTE);
			 mPickTime.setCurrentHour(hour);
			 mPickTime.setCurrentMinute(min);
			 String dbUri = reminder.getString(reminder.getColumnIndexOrThrow(ReminderDbAdapter.KEY_RINGTONE_URI));
			 if (dbUri.length() > 0){
				 mRingtoneURI = Uri.parse(dbUri);
				 
			 }
			 else {
				 mRingtoneURI = Settings.System.DEFAULT_RINGTONE_URI;
			 }
				 
			
			Ringtone ringtone = RingtoneManager.getRingtone(this, mRingtoneURI);
			
			String ringTitle = null;
			if (ringtone != null) {
			  ringTitle = ringtone.getTitle(this);
			}
			if (ringTitle != null){
				mRingtoneText.setText(ringTitle);
			}
	         if (time < System.currentTimeMillis()){
	        	 showAlert(role, contactName);
	         }

	    }
	 }
	 
	 private void showAlert(int role, String contactName){
    	 AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	 if (role == ReminderDbAdapter.SENDING) {
    		builder.setMessage(getString(R.string.shoulda_sent) + " " + contactName + " " + getString(R.string.but_didnt))
    			.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					mDbHelper.deleteReminder(mRowId);
    					ReminderEdit.this.finish();
    				}
    			})
    			.setNeutralButton(R.string.reschedule, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.dismiss();
    				}
    			})
    			.setPositiveButton(R.string.text_now, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
 						sendText();
    				
    				}
    			});
    	 }
    	 else {
    		 builder.setMessage(getString(R.string.shoulda_got) + " " + contactName + " " + getString(R.string.but_didnt))
 				.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
 					public void onClick(DialogInterface dialog, int id) {
 						mDbHelper.deleteReminder(mRowId);
    					ReminderEdit.this.finish();
 					}
 				})
 				.setNeutralButton(R.string.reschedule, new DialogInterface.OnClickListener() {
 					public void onClick(DialogInterface dialog, int id) {
 						dialog.dismiss();
 					}
 				});
 		
    	 }
				       
    	 AlertDialog alert = builder.create();
    	 alert.show();
			
	 }
	 
	 

     private boolean saveState() {
         Integer role = ReminderDbAdapter.EXPECTING;
         long time = getPickerTime();
         Calendar debugCal = Calendar.getInstance();
         debugCal.setTimeInMillis(time);
         
         String passphrase = mPassphraseText.getText().toString();
         String errors = "";
         
         if (mContactId == null){
        	 errors += getString(R.string.err_missing_contact) + "\n";
         }
         if (role == null) {
        	 errors += getString(R.string.err_missing_role) + "\n";
         }
         if (mRingtoneURI == null) {
        	 errors += getString(R.string.err_missing_sound) + "\n";
         }
         if (errors.length()>0){
        
			
        	 AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	 builder.setMessage(errors)
			        .setCancelable(false)
			        .setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
			        	public void onClick(DialogInterface dialog, int id) {
			        		dialog.dismiss();
			        	}
			        });
					       
        	 AlertDialog alert = builder.create();
        	 alert.show();
				
        	 return false;
         }
         if (((RadioButton)findViewById(R.id.radioSending)).isChecked()){
        	 role = ReminderDbAdapter.SENDING;
         } 
         
         
         Log.v(DEBUG_TAG, "role = " + Integer.toString(role));
         if (mRowId == null) {
             long id = mDbHelper.createReminder(mContactId, role, time, passphrase, mRingtoneURI.toString());
             if (id > 0) {
                 mRowId = id;
             }
         } else {
             mDbHelper.updateReminder(mRowId, mContactId, role, time, passphrase, mRingtoneURI.toString());
         }

         ReminderManager.updateReminders(this, mDbHelper);
         return true;
     }
     
     public void doLaunchContactPicker(View view) {  
    	    Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,  
    	            Contacts.CONTENT_URI);  
    	    startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);  
    	}  

     public void doLaunchSoundPicker(View view) {  
    	 Intent soundPickerIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    	 soundPickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
    	 soundPickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
    	 if (mRingtoneURI == null) {
    		 mRingtoneURI = Settings.System.DEFAULT_RINGTONE_URI;
    	 }
    	 soundPickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, mRingtoneURI);
 	   	 startActivityForResult(soundPickerIntent, SOUND_PICKER_RESULT);  
 	 }
     
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
    	    if (resultCode == RESULT_OK) {  
    	        switch (requestCode) {  
    	        case CONTACT_PICKER_RESULT:  
    	            // handle contact results  
    	        	Bundle extras = data.getExtras();    	        	
    	        	Uri result = data.getData();
    	        	String newContactId = result.getLastPathSegment();  
    	        	Cursor cursor = getContentResolver().query(Phone.CONTENT_URI, null, 
    	        				Phone.CONTACT_ID + "=?",new String[]{newContactId},null);
    	        	cursor.moveToFirst();  
    	        	if (cursor.getCount() > 0) {
    	        		String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
    	        		mContactNameText.setText(contactName);	
    	        		mContactId = newContactId;
    	        	}
    	        	else {
    	        		Log.w(DEBUG_TAG, "No phone numbers for this contact");
    	        		Toast.makeText(this, R.string.no_numbers, Toast.LENGTH_LONG).show();
    	        	}
    	        	break;  
    	        case SOUND_PICKER_RESULT:
    	        	extras = data.getExtras();
    	        	mRingtoneURI = (Uri) extras.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
    	        	Log.v(DEBUG_TAG, "uri: " + mRingtoneURI);
    	        	Ringtone ringtone = RingtoneManager.getRingtone(this, mRingtoneURI);
    	        	mRingtoneText.setText(ringtone.getTitle(this));
    	        	break;
    	    }  
    	 
    	 } else {  
    	   	// gracefully handle failure  
    	        Log.w(DEBUG_TAG, "Warning: activity result not ok");  
        }  
     } 
    
     private long getPickerTime() {
    	 int pickerHour = mPickTime.getCurrentHour();
         int pickerMin = mPickTime.getCurrentMinute();
         long time = 0;
         Calendar cal =  Calendar.getInstance();
         long currTime = cal.getTimeInMillis();
         cal.set(Calendar.HOUR_OF_DAY, pickerHour);
         cal.set(Calendar.MINUTE, pickerMin);
         time = cal.getTimeInMillis();
         long buffer = 1000 * 60 * 1;
         if (time < currTime + buffer ){
        	 cal.add(Calendar.DAY_OF_MONTH, 1);
        	 time = cal.getTimeInMillis(); 
         }
         return time;
     }
     
     private void sendText(){
    	 //pick number if needed
 		ContentResolver cr = this.getContentResolver();
 		Cursor cursor = cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + "=?",new String[]{mContactId},null);
 		Set<String> phoneNumbers = new HashSet<String>(cursor.getCount());
 		if (cursor.getCount() > 0){
 			cursor.moveToFirst();
 			
 			do {
 				phoneNumbers.add(cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));	
 			} while (cursor.moveToNext());
 				
 			mPhoneArr = phoneNumbers.toArray(new String[phoneNumbers.size()]);
 			if (phoneNumbers.size() == 1) {
 				mPhoneNum = mPhoneArr[0];
 				launchSMS(mPhoneNum);

 			}
 			else {

 				AlertDialog.Builder builder = new AlertDialog.Builder(this);
 				builder.setTitle(R.string.which_number);
 				builder.setItems(mPhoneArr, new DialogInterface.OnClickListener() {
 				    public void onClick(DialogInterface dialog, int item) {
 				    	mPhoneNum = mPhoneArr[item];
 				    	launchSMS(mPhoneNum);
 				    }
 				});
 				AlertDialog alert = builder.create();
 				alert.show();
 			}
 			
 		} 
 		
 			
   
    
     }

     private void launchSMS(String phone){
     	 Log.v(DEBUG_TAG, "should be sending to phone :" + phone);
    	 //launch intent
    	String body = mPassphraseText.getText().toString();
    	Intent sendIntent = new Intent(Intent.ACTION_VIEW);         
    	sendIntent.setData(Uri.parse("smsto:" + phone));
    	sendIntent.putExtra("sms_body", body); 
    	sendIntent.putExtra("address", phone); 
    	startActivity(sendIntent);
     }
     
     private void setToFromText(){
    	 
    	 if (((RadioButton)findViewById(R.id.radioSending)).isChecked()){
    		 mToFromText.setText(R.string.to);
         } 
    	 else {
    		 mToFromText.setText(R.string.from);
    		 
    	 }
     }
}

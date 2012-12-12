package com.harliestar.android.aliveandtexting;


import java.text.DateFormat;
import java.util.Date;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class AliveAndTexting extends ListActivity {

	private static final int ACTIVITY_CREATE=0;
    private static final int ACTIVITY_EDIT=1;

	private static final int INSERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final int EDIT_ID = Menu.FIRST + 2;

    private static final String DEBUG_TAG = "TextOnArrival";
	
	private ReminderDbAdapter mDbHelper;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reminder_list);
        mDbHelper = new ReminderDbAdapter(this);
        mDbHelper.open();
        fillData();
        registerForContextMenu(getListView());
        Button addReminder = (Button) findViewById(R.id.addReminder);
        addReminder.setOnClickListener(new View.OnClickListener() {

			public void onClick(View view) {
				createReminder();
			}
			

		});


    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, INSERT_ID, 0, R.string.menu_insert);
        return result;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case INSERT_ID:
            createReminder();
            return true;
        }
       
        return super.onOptionsItemSelected(item);
    }

    private void createReminder(){
    	Intent i = new Intent(this, ReminderEdit.class);
    	startActivityForResult(i, ACTIVITY_CREATE);
    	fillData();
    }
    
    private void fillData() {
    	Log.v(DEBUG_TAG,"filling data");
   	 	ReminderManager.checkAllReminders(this, mDbHelper);
    	
        // Get all of the notes from the database and create the item list
        Cursor remindersCursor = mDbHelper.fetchAllReminders();
        startManagingCursor(remindersCursor);
        
        
        // Now create an array adapter and set it to display using our row
        ReminderCursorAdapter reminders =
            new ReminderCursorAdapter(this,remindersCursor);
        setListAdapter(reminders);
    }

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_delete);
		menu.add(0, EDIT_ID, 0, R.string.menu_edit);	

	}
    
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch(item.getItemId()) {
        case DELETE_ID:
            mDbHelper.deleteReminder(info.id);
            fillData();
            return true;
        case EDIT_ID:
        	Intent i = new Intent(this, ReminderEdit.class);
            i.putExtra(ReminderDbAdapter.KEY_ROWID, info.id);
            startActivityForResult(i, ACTIVITY_EDIT);

        	
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, ReminderEdit.class);
        i.putExtra(ReminderDbAdapter.KEY_ROWID, id);
        startActivityForResult(i, ACTIVITY_EDIT);
        
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        fillData();
    }

    
    private class ReminderCursorAdapter extends CursorAdapter {
    	private Cursor _cursor;
     
        public ReminderCursorAdapter(Context context, Cursor c) {
            super(context, c);
            _cursor = c;
        }
     
        /**
         * {@inheritDoc}
         */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView nameView = (TextView) view.findViewById(R.id.contactName);
            TextView dateView = (TextView) view.findViewById((R.id.alarmTime));
            String id = _cursor.getString(_cursor.getColumnIndex(ReminderDbAdapter.KEY_CONTACT_ID));
            
        	Cursor contactsCursor = getContentResolver().query(Phone.CONTENT_URI, null, 
    				Phone.CONTACT_ID + "=?",new String[]{id},null);
        	String name = "";
        	if (contactsCursor.getCount() > 0){
        		contactsCursor.moveToFirst();
        		name = contactsCursor.getString(contactsCursor.getColumnIndex(Phone.DISPLAY_NAME));
        	}
        	else {
        		name = "Contact missing";
        	}
        	
    	    nameView.setText(name);
    	    long cursorTime = _cursor.getLong(_cursor.getColumnIndex(ReminderDbAdapter.KEY_TIME));
    	    Date alarmDate = new Date(cursorTime);
    	    String dateStr = DateFormat.getDateTimeInstance().format(alarmDate);
    	    dateView.setText(dateStr);
    	    if (cursorTime < System.currentTimeMillis()){
        	    nameView.setTextColor(Color.RED);
    	    	dateView.setTextColor(Color.RED);
    	    }
    	    else {
    	    	nameView.setTextColor(Color.WHITE);
    	    	dateView.setTextColor(Color.WHITE);
    	    }
        }
        
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
        	final View view = LayoutInflater.from(context).inflate(R.layout.reminders_row, parent, false);
        	return view;
        }
    }


}
package com.android.contacts.calllog;

import java.util.HashMap;

import android.database.Cursor;
import android.util.Log;

/**
 * 
 * @time 2012-9-11
 * @author shutao shutao@shendu.com 
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * @Function :To copy CallLogGroupBuilder packet Ann names Sort
 */
public class ShenduCallLogGroupBuilder {

	public interface GroupCreator {
		public void addGroup(int cursorPosition, int size, boolean expanded);
	}
	
	private static final String TAG = "ShenduCallLogGroupBuilder";
	
	private static final boolean DEBUG = false;

	/** The object on which the groups are created. */
	private final GroupCreator mGroupCreator;
	
	private HashMap<String, ShenduContactInfo> childCallLogName = new HashMap<String, ShenduContactInfo>();
	
	public HashMap<String, ShenduContactInfo> getChildCallLogName() {
		return childCallLogName;
	}

	public ShenduCallLogGroupBuilder(GroupCreator groupCreator){
		mGroupCreator = groupCreator;
	}
	
	/**
     * Finds all groups of adjacent entries in the call log which should be grouped together and
     * calls {@link GroupCreator#addGroup(int, int, boolean)} on {@link #mGroupCreator} for each of
     * them.
     * <p>
     * For entries that are not grouped with others, we do not need to create a group of size one.
     * <p>
     * It assumes that the cursor will not change during its execution.
     *
     * @see GroupingListAdapter#addGroups(Cursor)
     */
    public void addGroups(Cursor cursor) {
    	 long tiem1 = System.currentTimeMillis();
    	childCallLogName.clear();
    	
        int count = cursor.getCount();
        
        if (count == 0) {
            return;
        }

        int currentGroupSize = 1;
        cursor.moveToFirst();
        String firstName = cursor.getString(CallLogQuery.CACHED_NAME);
        if(firstName == null || firstName.equals("")){
        	  firstName = cursor.getString(CallLogQuery.NUMBER);
        }
        if(firstName == null || firstName.equals("")){

            while(cursor.moveToNext()){
            	 firstName = cursor.getString(CallLogQuery.CACHED_NAME);
                 if(firstName == null || firstName.equals("")){
                 	  firstName = cursor.getString(CallLogQuery.NUMBER);
                 }
//            		MyLog("firstName===null"+firstName);
            	if(firstName != null && !firstName.equals("")){
//            		MyLog("firstName===null"+firstName);
            		break;
            	}

            }
        }
    
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQuery.NUMBER);
        // This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
        firstName = cursor.getString(CallLogQuery.CACHED_NAME);
        if(firstName == null || firstName.equals("")){
        	  firstName = cursor.getString(CallLogQuery.NUMBER);
        }
        ShenduContactInfo info = new ShenduContactInfo();
        info.name = firstName;
        info.types.add(cursor.getInt(CallLogQuery.CALL_TYPE));
		 info.ids.add(cursor.getLong(CallLogQuery.ID));
//        MyLog("callType fist= "+firstCallType+"-----"+firstName+cursor.getCount());
    	 addGroup(cursor.getPosition(), 0);
        childCallLogName.put(firstName.toString(), info);

        
        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            final String currentNumber = cursor.getString(CallLogQuery.NUMBER);
            String currentName = cursor.getString(CallLogQuery.CACHED_NAME);
            if(currentName == null || currentName.equals("")){
            	currentName=currentNumber;
              }
            
            if(currentName == null || currentName.equals("")){
//            	 MyLog("currentName = "+currentName+"cursor"+cursor.getPosition());
            	continue;
            }
       /** shutao 2012-9-11  Discard this function Using the user name of the grouping */
//            MyLog("currentName = "+currentName+"cursor"+cursor.getPosition());
            equalName(currentName.toString() , cursor);       
        }

        MyLog("shenducallloggroup   time  = "+(System.currentTimeMillis()-tiem1));
    }

    /** shutao 2012-9-1 Whether this contact is not added to the data query data */
    boolean equalName(String name , Cursor cursor){
    	if(childCallLogName.containsKey(name)){
    		childCallLogName.get(name).types.add(cursor
					.getInt(CallLogQuery.CALL_TYPE));
    		childCallLogName.get(name).ids.add(cursor.getLong(CallLogQuery.ID));
    		return true;
    	}else{
    		MyLog("equalName"+name+"position= "+cursor.getPosition());
    		 ShenduContactInfo info = new ShenduContactInfo();
    	    info.name = name;
    	    info.types.add(cursor.getInt(CallLogQuery.CALL_TYPE));
    	    info.ids.add(cursor.getLong(CallLogQuery.ID));
    		childCallLogName.put(name, info);
    		addGroup(cursor.getPosition(), 0);
    		return false;
    	}

    }
    
    /**
     * Creates a group of items in the cursor.
     * <p>
     * The group is always unexpanded.
     *
     * @see CallLogAdapter#addGroup(int, int, boolean)
     */
    private void addGroup(int cursorPosition, int size) {
        mGroupCreator.addGroup(cursorPosition, size, false);
    }
    
	private void MyLog(String string) {
		// TODO Auto-generated method stub
		if(DEBUG){
			Log.d(TAG, string);
		}
	}

}

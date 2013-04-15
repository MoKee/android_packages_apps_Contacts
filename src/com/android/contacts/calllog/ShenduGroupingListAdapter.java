/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.calllog;

import java.util.ArrayList;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * 
 * @time 2012-9-11
 * @author shutao shutao@shendu.com 
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * @Function :Imitate GroupingListAdapter 
 * Maintains a list that groups adjacent items sharing the same value of
 * a "group-by" field.  The list has three types of elements: stand-alone, group header and group
 * child. Groups are collapsible and collapsed by default.
 */
public abstract class ShenduGroupingListAdapter extends BaseAdapter {

    public static final int ITEM_TYPE_IN_GROUP = 1;
    
    public ArrayList<Integer> cursorPosition_List = new ArrayList<Integer>();

    /**
     * Information about a specific list item: is it a group, if so is it expanded.
     * Otherwise, is it a stand-alone item or a group member.
     */
    protected static class PositionMetadata {
        int itemType;
        boolean isExpanded;
        int cursorPosition;
        int childCount;
        private int groupPosition;
        private int listPosition = -1;
    }

    private Context mContext;
    private Cursor mCursor;

    /**
     * Count of list items.
     */
    private int mCount;

    private int mRowIdColumnIndex;

    /**
     * Count of groups in the list.
     */
    private int mGroupCount;


    private SparseIntArray mPositionCache = new SparseIntArray();


    /**
     * A reusable temporary instance of PositionMetadata
     */
    private PositionMetadata mPositionMetadata = new PositionMetadata();

    protected ContentObserver mChangeObserver = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onContentChanged();
        }
    };

    protected DataSetObserver mDataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            notifyDataSetInvalidated();
        }
    };

    public ShenduGroupingListAdapter(Context context) {
        mContext = context;
        resetCache();
    }

    /**
     * Finds all groups of adjacent items in the cursor and calls {@link #addGroup} for
     * each of them.
     */
    protected abstract void addGroups(Cursor cursor);



    protected abstract View newGroupView(Context context, ViewGroup parent);
    protected abstract void bindGroupView(View view, Context context, Cursor cursor);



    /**
     * Cache should be reset whenever the cursor changes or groups are expanded or collapsed.
     */
    private void resetCache() {
        mCount = -1;
        mPositionMetadata.listPosition = -1;
        mPositionCache.clear();
    }

    protected void onContentChanged() {
    }

    public void changeCursor(Cursor cursor) {
    	cursorPosition_List.clear();
        if (cursor == mCursor) {
            return;
        }

        if (mCursor != null) {
            mCursor.unregisterContentObserver(mChangeObserver);
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mCursor.close();
        }
        mCursor = cursor;
        resetCache();
        findGroups();

        if (cursor != null) {
            cursor.registerContentObserver(mChangeObserver);
            cursor.registerDataSetObserver(mDataSetObserver);
            mRowIdColumnIndex = cursor.getColumnIndexOrThrow("_id");
            notifyDataSetChanged();
        } else {
            // notify the observers about the lack of a data set
            notifyDataSetInvalidated();
        }

    }


	public Cursor getCursor() {
        return mCursor;
    }

    /**
     * Scans over the entire cursor looking for duplicate phone numbers that need
     * to be collapsed.
     */
    private void findGroups() {
        mGroupCount = 0;

        if (mCursor == null) {
            return;
        }

        addGroups(mCursor);
    }

    /**
     * Records information about grouping in the list.  Should be called by the overridden
     * {@link #addGroups} method.
     */
    protected void addGroup(int cursorPosition, int size, boolean expanded) {
    	cursorPosition_List.add(cursorPosition);
        log("mGroupCount.size = "+mGroupCount);
    }




    public int getCount() {
        if (mCursor == null) {
            return 0;
        }
        if (mCount != -1) {
            return mCount;
        }
        mCount = cursorPosition_List.size();
        log("mGroupCount.size = "+mCount);
        return mCount;
    }




    @Override
    public int getViewTypeCount() {
        return 3;
    }



    public Object getItem(int position) {
        if (mCursor == null) {
            return null;
        }
        if (mCursor.moveToPosition(cursorPosition_List.get(position))) {
            return mCursor;
        } else {
            return null;
        }
    }

    public long getItemId(int position) {
        Object item = getItem(position);
        if (item != null) {
            return mCursor.getLong(mRowIdColumnIndex);
        } else {
            return -1;
        }
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;
        if (view == null) {
            view = newGroupView(mContext, parent);
        }

        mCursor.moveToPosition(cursorPosition_List.get(position));

        bindGroupView(view, mContext, mCursor);

        return view;
    }
    
    
    private static boolean debug = false;

   	private static void log(String msg) {
   		if (debug)
   			Log.i("Shendu_CallLogAdapter", msg);
   	}
}

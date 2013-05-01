
package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.android.contacts.activities.ShenDuContactSelectionActivity;
import com.android.contacts.activities.ShenDuContactSelectionActivity.Option;
import com.android.contacts.activities.ShenDuContactSelectionActivity.OptionChangedListener;
import com.android.contacts.group.GroupEditorFragment.Member;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ShenduPickAdapter extends ContactEntryListAdapter implements
        OptionChangedListener {

    protected String mExcludedContactIds;
    protected final CharSequence mUnknownNameText;
    protected ConcurrentHashMap<Long, Parcelable> mSelecteds = new ConcurrentHashMap<Long, Parcelable>();
    
    public ShenduPickAdapter(Context context) {
        super(context);
        mUnknownNameText = context.getText(android.R.string.unknownName);
        try {
            ShenDuContactSelectionActivity activity = (ShenDuContactSelectionActivity) context;
            activity.setOptionChangedListener(this);
        } catch (ClassCastException e) {
            log("ClassCastException !!!!");
            return ;
        }
    }

 
    /**
     * Select All Mode
     * @author Wang
     * @date 2013-1-5
     */
    protected abstract void onSelectAll();
    protected abstract View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) ;
    public abstract void configureLoader(CursorLoader loader, long directoryId);
    protected abstract void bindView(View itemView, int partition, Cursor cursor, int position) ;
    protected abstract void configureSelection(CursorLoader loader) ;
    
    /**
     * Deselect All Mode
     * @author Wang
     * @date 2013-1-5
     */
    protected void onDeselectAll(){
        mSelecteds.clear();
        notifyDataSetChanged();
        onUpdateCountView(null);
    }
    
    /**
     * Update CountView in Selection Activity.
     * @author Wang
     * @param state The Option state to set up. If null will not change option.
     * @date 2013-1-5
     */
    protected void onUpdateCountView(Option state){
        try {
            ShenDuContactSelectionActivity activity = (ShenDuContactSelectionActivity) mContext ;
            activity.updateSelectedCountInSpinner(mSelecteds.size());
            if(state != null)activity.changeOptionState(Option.Normal);
        } catch (ClassCastException e) {
            return;
        }
    }

    /**
     * Set the ContactId  those are in group.
     * @author Wang
     * @date 2013-1-5
     */
    public void setExcludedContactId(long[] ids) {
        if (ids == null)
            return;
        int end = ids.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            sb.append(ids[i]);
            if (i != end - 1) {
                sb.append(",");
            }
        }
        log(" =======IDs String =>" + sb.toString());
        setExcludedContactId(sb.toString());
    }

    /**
     * Set the ContactId  those are in group.
     * @author Wang
     * @date 2012-9-4
     */
    public void setExcludedContactId(String ids) {
        if (ids == null || TextUtils.isEmpty(ids) || ids.equals(mExcludedContactIds)) {
            return;
        }
        mExcludedContactIds = ids;
    }

    public Collection<Parcelable> getNewMembers(){
        return mSelecteds.values();
    }


    @Override
    public void onOptionChanged(Option op) {
        switch (op) {
            case Normal:
                onDeselectAll();
                break;

            case SelectAll:
                onSelectAll();
                break;
        }
    }

    private static final boolean debug = false;

    private static void log(String msg) {
        msg = "ShenduPickAdapter  --> " + msg;
        if (debug)
            Log.i("shenduGroup", msg);
    }

}

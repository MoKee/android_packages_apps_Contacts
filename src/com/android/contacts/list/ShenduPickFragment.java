package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.list.ShenduContactPickAdapter.MemberWithoutRawContactId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ShenduPickFragment extends ContactEntryListFragment<ContactEntryListAdapter> {
    protected long[] mExistedContactsIds;
    
    public ShenduPickFragment(){
        
    }

    /**
     * Called when  onCreateView method is callbacked in fragment.
     * */
    @Override
    protected abstract ContactEntryListAdapter createListAdapter() ;
    
    /**
     * Fetch the new Members Collection
     * @author Wang
     * @return new Members Collection
     * @date 2013-1-5
     * */
    public  Collection<Parcelable> getNewMembers(){
    	  try {
              ShenduPickAdapter adapter = (ShenduPickAdapter) getAdapter();
              return adapter.getNewMembers();
          } catch (ClassCastException e) {
              return null;
          }
    };

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }
    
    @Override
    protected void onItemClick(int position, long id) {
        return;
    }
    
    /**
     * Set up mExistedRawContactsIds values.
     * @author Wang
     * @param ids The contacts id has in group 
     * @return
     * @date 2012-9-4
     * */
    public void setupExistedContactsIds(long[] ids){
        mExistedContactsIds = ids;
    }

}

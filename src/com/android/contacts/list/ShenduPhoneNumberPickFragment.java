package com.android.contacts.list;

import com.android.contacts.list.ContactListItemView.PhotoPosition;

public class ShenduPhoneNumberPickFragment extends ShenduPickFragment {
    
    public ShenduPhoneNumberPickFragment(){
        setQuickContactEnabled(false);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }
    
    /**
     * Called when  onCreateView method is callbacked in fragment.
     * */
    @Override
    protected ContactEntryListAdapter createListAdapter() {
    	 ShenduPhoneNumberPickAdapter adapter = new ShenduPhoneNumberPickAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(PhotoPosition.LEFT);
        adapter.setExcludedContactId(mExistedContactsIds);
        return adapter;
    }
    

}

package com.android.contacts.list;


public class ShenduContactPickFragment extends ShenduPickFragment {
    
    public ShenduContactPickFragment(){
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
        ShenduContactPickAdapter adapter = new ShenduContactPickAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(false);
        adapter.setExcludedContactId(mExistedContactsIds);
        return adapter;
    }
   
}

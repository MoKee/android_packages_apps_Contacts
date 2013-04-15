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
package com.android.contacts.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.android.contacts.R;
import com.android.contacts.activities.ShenDuContactSelectionActivity;
import com.android.contacts.activities.ShenDuContactSelectionActivity.Option;
import com.android.contacts.activities.ShenDuContactSelectionActivity.OptionChangedListener;
import com.android.contacts.list.ShenduContactPickAdapter.ContactsQuery;
import com.android.contacts.list.ShenduContactPickAdapter.MemberWithoutRawContactId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cursor adapter for the {@link Phone#CONTENT_ITEM_TYPE} and
 * {@link SipAddress#CONTENT_ITEM_TYPE}.
 *
 * By default this adapter just handles phone numbers. When {@link #setUseCallableUri(boolean)} is
 * called with "true", this adapter starts handling SIP addresses too, by using {@link Callable}
 * API instead of {@link Phone}.
 */
public class ShenduPhoneNumberPickAdapter extends ShenduPickAdapter{
   

    protected static class PhoneQuery {
        private static final String[] PROJECTION_PRIMARY = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_URI,                     // 6
            Phone.DISPLAY_NAME_PRIMARY,         // 7
            Phone.PHOTO_ID,                           //8
        };

        public static final int PHONE_ID           = 0;
        public static final int PHONE_TYPE         = 1;
        public static final int PHONE_LABEL        = 2;
        public static final int PHONE_NUMBER       = 3;
        public static final int PHONE_CONTACT_ID   = 4;
        public static final int PHONE_LOOKUP_KEY   = 5;
        public static final int PHONE_PHOTO_URI     = 6;
        public static final int PHONE_DISPLAY_NAME = 7;
        public static final int PHONE_PHOTO_ID     = 8;
    }

    private ContactListItemView.PhotoPosition mPhotoPosition;
    private boolean mUseCallableUri;

    public ShenduPhoneNumberPickAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        final Builder builder;
        final Uri baseUri = Phone.CONTENT_URI;
       builder = baseUri.buildUpon().appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
       // Display Section Header
       builder.appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true");
       // Remove duplicates when it is possible.
       builder.appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");
       loader.setUri(builder.build());
       loader.setProjection(PhoneQuery.PROJECTION_PRIMARY);
       loader.setSortOrder(Phone.SORT_KEY_PRIMARY);
       configureSelection(loader);
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(PhoneQuery.PHONE_DISPLAY_NAME);
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the given cursor position.
     *
     * @return Uri for the data. may be null if the cursor is not ready.
     */
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            long id = cursor.getLong(PhoneQuery.PHONE_ID);
            return ContentUris.withAppendedId(Data.CONTENT_URI, id);
        } else {
           
            return null;
        }
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        view.setPhotoPosition(mPhotoPosition);
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;

        // Look at elements before and after this position, checking if contact IDs are same.
        // If they have one same contact ID, it means they can be grouped.
        //
        // In one group, only the first entry will show its photo and its name, and the other
        // entries in the group show just their data (e.g. phone number, email address).
        cursor.moveToPosition(position);
        boolean isFirstEntry = true;
        //boolean showBottomDivider = true;
        final long currentContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (currentContactId == previousContactId) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);
        /*if (cursor.moveToNext() && !cursor.isAfterLast()) {
            final long nextContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (currentContactId == nextContactId) {
                // The following entry should be in the same group, which means we don't want a
                // divider between them.
                // TODO: we want a different divider than the divider between groups. Just hiding
                // this divider won't be enough.
                showBottomDivider = false;
            }
        }
        
        cursor.moveToPosition(position);*/

        bindSectionHeaderAndDivider(view, position,cursor);
        if (isFirstEntry) {
            bindName(view, cursor);
            bindPhoto(view, cursor);
           
        } else {
            unbindName(view);

            view.removePhotoView(true, false);
        }
        bindPhoneNumber(view, cursor);
        //view.setDividerVisible(showBottomDivider);
        /*----------*/
        bindCheckBox(view, cursor, position);
    }
    
    private void bindCheckBox(ContactListItemView view, Cursor cursor, final int position) {
//         log(">>bindCheckBox  position = "+position+"<<");
        final long phoneId = cursor.getLong(PhoneQuery.PHONE_ID);
        final long contactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
        final String phoneNum = cursor.getString(PhoneQuery.PHONE_NUMBER);
        final String displayName = cursor.getString(PhoneQuery.PHONE_DISPLAY_NAME);
//         log("   displayName => "+displayName);
        final String lookupKey = cursor.getString(PhoneQuery.PHONE_LOOKUP_KEY);
        final String photoUri = cursor.getString(PhoneQuery.PHONE_PHOTO_URI);
        CheckBox box = view.getCheckBox();
        box.setChecked(mSelecteds.containsKey(phoneId) ? true : false);
        box.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    CompoundButton checkBox = (CompoundButton) v;
                    boolean isChecked = checkBox.isChecked();
                    if (isChecked) {
                        if (!mSelecteds.containsKey(phoneId)) {
                        	PhonePickMember member = new PhonePickMember(phoneId, contactId, phoneNum, lookupKey, displayName, photoUri);
                           mSelecteds.put(phoneId, member);
                        }
                    } else {
                        if (mSelecteds.containsKey(phoneId)) {
                            mSelecteds.remove(phoneId);
                        }
                    }
                    onUpdateCountView(Option.Normal);
                } catch (ClassCastException e) {
                    log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!CheckBox ClassCastException!");
                    return;
                }

            }
        });
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PhoneQuery.PHONE_TYPE)) {
            final int type = cursor.getInt(PhoneQuery.PHONE_TYPE);
            final String customLabel = cursor.getString(PhoneQuery.PHONE_LABEL);

            // TODO cache
            label = Phone.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, PhoneQuery.PHONE_NUMBER);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position, Cursor cursor) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);
            view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
            //view.setDividerVisible(!placement.lastInSection);
            boolean first = placement.firstInSection;
            boolean last = placement.lastInSection;
            if(position==cursor.getCount()-1){//add, for the last item do not display divider line
                view.setDividerVisible(false);
            	if(first){
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_overall);
            	}else{
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_bottom);
            	}
            }else{
                view.setDividerVisible(!placement.lastInSection);
                if(first && last){
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_overall);
                }else if(first && !last){
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_top);
                }else if(!first && last){
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_bottom);
                }else if(!first && !last){
                	view.setShenduRoundedBackground(R.drawable.shendu_listview_item_middle);
                }
            }
            long currentContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
            if (cursor.moveToNext() && !cursor.isAfterLast()) {
                final long nextContactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
                if (currentContactId == nextContactId) {
                    // The following entry should be in the same group, which means we don't want a
                    // divider between them.
                    // TODO: we want a different divider than the divider between groups. Just hiding
                    // this divider won't be enough.
                	view.setDividerVisible(false);
                }
            }
            cursor.moveToPosition(position);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PhoneQuery.PHONE_DISPLAY_NAME, getContactNameDisplayOrder());
        // Note: we don't show phonetic names any more (see issue 5265330)
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(PhoneQuery.PHONE_PHOTO_ID)) {
            photoId = cursor.getLong(PhoneQuery.PHONE_PHOTO_ID);
        }
        /*Wang: 2013-1-5*/
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId,false, view.getNameTextView().getText().toString(), -1);
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }
    
    protected void onSelectAll(){
        Cursor cursor = getCursor(0);
        if(cursor == null) return;
        cursor.moveToPosition(-1);
        while(cursor.moveToNext()){
        	  final long phoneId = cursor.getLong(PhoneQuery.PHONE_ID);
              final long contactId = cursor.getLong(PhoneQuery.PHONE_CONTACT_ID);
              final String phoneNum = cursor.getString(PhoneQuery.PHONE_NUMBER);
              final String displayName = cursor.getString(PhoneQuery.PHONE_DISPLAY_NAME);
              final String lookupKey = cursor.getString(PhoneQuery.PHONE_LOOKUP_KEY);
              final String photoUri = cursor.getString(PhoneQuery.PHONE_PHOTO_URI);
            PhonePickMember member = new PhonePickMember(phoneId, contactId, phoneNum, lookupKey, displayName, photoUri);
            mSelecteds.put(phoneId, member);
        }
         notifyDataSetChanged();
        /*Wang:should not change state because onItemClick method has changed state in activity*/
        onUpdateCountView(null);

    }
    
    protected void configureSelection(CursorLoader loader) {
        log(">>configureSelection<<");
        if (TextUtils.isEmpty(mExcludedContactIds)) {
            return;
        }
        String selection = Contacts._ID + " not in(" + mExcludedContactIds + ")";
        log(" configureSelection =>" + selection);
        loader.setSelection(selection);
    }
   
    public static class PhonePickMember  implements Parcelable{
        private static final PhonePickMember[] EMPTY_ARRAY = new PhonePickMember[0];
        
        private final long mPhoneId;
		private final long mContactId;
        private final String mLookupKey;
        private final String mDisplayName;
        private final String mPhotoUri;
        private final String mPhoneNum;
        

        public PhonePickMember(long phoneId,long contactId, String phoneNum,String lookupKey, String displayName,
                String photoUri) {
            this.mContactId = contactId;
            this.mLookupKey = lookupKey;
            this.mDisplayName = displayName;
            this.mPhotoUri = photoUri;
            this.mPhoneId = phoneId;
            this.mPhoneNum = phoneNum;
        }
  
		public long getmPhoneId() {
			return mPhoneId;
		}

		public String getmPhoneNum() {
			return mPhoneNum;
		}

        public long getContactId() {
            return mContactId;
        }

        public String getLookupKey() {
            return mLookupKey;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getPhotoUri() {
            return mPhotoUri;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mContactId);
            dest.writeLong(mPhoneId);
            dest.writeString(mPhoneNum);
            dest.writeString(mLookupKey);
            dest.writeString(mDisplayName);
            dest.writeString(mPhotoUri);
               
        }

        private PhonePickMember(Parcel in) {
            mContactId = in.readLong();
            mPhoneId = in.readLong();
            mPhoneNum = in.readString();
            mLookupKey = in.readString();
            mDisplayName = in.readString();
            mPhotoUri =in.readString();
        }
        public static final Parcelable.Creator<PhonePickMember> CREATOR = new Creator() {
            public PhonePickMember createFromParcel(Parcel source) {
                return new PhonePickMember(source);
            }
            public PhonePickMember[] newArray(int size) {
                return new PhonePickMember[size];
            }  
         
        };  
    }
    
    private static final boolean debug = true;

    private static void log(String msg) {
        msg = "ShenduPhonePickAdapter  --> " + msg;
        if (debug)
            Log.i("shenduPick", msg);
    }
}

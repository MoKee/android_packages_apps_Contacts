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

import com.android.contacts.ContactSaveService;
import com.android.contacts.preference.ContactsPreferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class DefaultContactListAdapter extends ContactListAdapter {

    public static final char SNIPPET_START_MATCH = '\u0001';
    public static final char SNIPPET_END_MATCH = '\u0001';
    public static final String SNIPPET_ELLIPSIS = "\u2026";
    public static final int SNIPPET_MAX_TOKENS = 5;

    public static final String SNIPPET_ARGS = SNIPPET_START_MATCH + "," + SNIPPET_END_MATCH + ","
            + SNIPPET_ELLIPSIS + "," + SNIPPET_MAX_TOKENS;

    public DefaultContactListAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        if (loader instanceof ProfileAndContactsLoader) {
            ((ProfileAndContactsLoader) loader).setLoadProfile(shouldIncludeProfile());
        }

        ContactListFilter filter = getFilter();
        if (isSearchMode()) {
            String query = getQueryString();
            if (query == null) {
                query = "";
            }
            query = query.trim();
            if (TextUtils.isEmpty(query)) {
                // Regardless of the directory, we don't want anything returned,
                // so let's just send a "nothing" query to the local directory.
                loader.setUri(Contacts.CONTENT_URI);
                loader.setProjection(getProjection(false));
                loader.setSelection("0");
            } else {
                Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendPath(query);      // Builder will encode the query
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
                if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                    builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(getDirectoryResultLimit()));
                }
                builder.appendQueryParameter(SearchSnippetColumns.SNIPPET_ARGS_PARAM_KEY,
                        SNIPPET_ARGS);
                builder.appendQueryParameter(SearchSnippetColumns.DEFERRED_SNIPPETING_KEY,"1");
                loader.setUri(builder.build());
                loader.setProjection(getProjection(true));
            }
        } else {
            configureUri(loader, directoryId, filter);
            loader.setProjection(getProjection(false));
            configureSelection(loader, directoryId, filter);
        }

        String sortOrder;
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortOrder = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
        }

        loader.setSortOrder(sortOrder);
    }

    protected void configureUri(CursorLoader loader, long directoryId, ContactListFilter filter) {
        Uri uri = Contacts.CONTENT_URI;
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            String lookupKey = getSelectedContactLookupKey();
            if (lookupKey != null) {
                uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            } else {
                uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, getSelectedContactId());
            }
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = buildSectionIndexerUri(uri);
        }

        // The "All accounts" filter is the same as the entire contents of Directory.DEFAULT
        if (filter != null
                && filter.filterType != ContactListFilter.FILTER_TYPE_CUSTOM
                && filter.filterType != ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            final Uri.Builder builder = uri.buildUpon();
            builder.appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                filter.addAccountQueryParameterToUrl(builder);
            }
            uri = builder.build();
        }

        loader.setUri(uri);
    }

    private void configureSelection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                // We have already added directory=0 to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                // We have already added the lookup key to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                selection.append(Contacts.STARRED + "!=0");
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                if (isCustomFilterForPhoneNumbersOnly()) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                // We use query parameters for account filter, so no selection to add here.
                break;
            }
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView,final int partition, Cursor cursor, final int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);

        if (isSelectionVisible()) {
            view.setActivated(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position, cursor);
        //Wang:
        bindName(view, cursor);
        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor, ContactQuery.CONTACT_PHOTO_ID,
                    ContactQuery.CONTACT_PHOTO_URI, ContactQuery.CONTACT_ID,
                    ContactQuery.CONTACT_LOOKUP_KEY);
        } else {
            if (getDisplayPhotos()) {
                bindPhoto(view, partition, cursor);
            }
        }
          //Wang:
//        bindName(view, cursor);
        bindPresenceAndStatusMessage(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        } else {
            view.setSnippet(null);
        }
        // Wang:
        /*boolean hasPhone = cursor.getInt(ContactQuery.CONTACT_HAS_PHONE_NUMBER) != 0;
        final long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
        if (!isSearchMode() && hasPhone) {
            view.setOnCallButtonClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = (Integer) v.getTag();
                    Cursor cursor = DefaultContactListAdapter.this.getCursor(partition);
                    if (cursor.moveToPosition(pos)) {
                        doCallAction(cursor);
                    }
                }
            });
            view.showCallButton(position, position);
        } else {
            view.hideCallButton();
        }*/
        view.hideCallButton();

    }

    /**
     * Display error information.
     * 
     * @author Wang
     * @date 2012-9-12
     */
    private void signalError() {
        Toast.makeText(mContext, "Number Invalid!", 200).show();

    }

    /**
     * Process Call Action.
     * 
     * @author Wang
     * @date 2012-9-12
     * @param cursor ContactQuery cursor
     */
    private void doCallAction(Cursor cursor) {
        boolean hasPhone = cursor.getInt(ContactQuery.CONTACT_HAS_PHONE_NUMBER) != 0;
        if (!hasPhone) {
            signalError();
            return;
        }
        long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
        log(">>>doCallAction => contactId =" + contactId);
        String phone = null;
        Cursor phonesCursor = null;
        phonesCursor = queryPhoneNumbers(contactId);
        if (phonesCursor == null || phonesCursor.getCount() == 0) {
            // No valid number
            signalError();
            return;
        } else if (phonesCursor.getCount() == 1) {
            // only one number, call it.
            phone = phonesCursor.getString(phonesCursor.getColumnIndex(Phone.NUMBER));
        } else {
            phonesCursor.moveToPosition(-1);
            ArrayList<Entry> entryList = new ArrayList<Entry>(phonesCursor.getCount());
            while (phonesCursor.moveToNext()) {
                if (phonesCursor.getInt(phonesCursor.
                        getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                    // Found super primary, call it.
                    phone = phonesCursor.
                            getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                    break;
                }
                String num = phonesCursor.
                        getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                long dataId = phonesCursor.getLong(phonesCursor
                        .getColumnIndex(Contacts.Data._ID));
                Entry entry = new Entry(dataId, num);
                entryList.add(entry);
            }
            //Wang:
            if(TextUtils.isEmpty(phone)){
                showDefaultContactSelectionDialog(entryList);
                if (phonesCursor != null) {
                    phonesCursor.close();
                }
                return ;
            }
        }
        if (!TextUtils.isEmpty(phone)) {
            makePhoneCall(phone);
        }
        if (phonesCursor != null) {
            phonesCursor.close();
        }
    }

    private void setDefaultContactMethod(long id) {
        Intent setIntent = ContactSaveService.createSetSuperPrimaryIntent(mContext, id);
        mContext.startService(setIntent);
    }

    /**
     * Query PhoneNumbers by contactId.
     * 
     * @author Wang
     * @date 2012-9-12
     * @param contactId
     */
    private Cursor queryPhoneNumbers(long contactId) {
        return queryPhoneNumbers(mContext.getContentResolver(), contactId);
    }

    /**
     * Query PhoneNumbers by contactId and pass param contentresolver.
     * 
     * @author Wang
     * @date 2012-9-12
     * @param resolver
     * @param contactId
     */
    private Cursor queryPhoneNumbers(ContentResolver resolver, long contactId) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);
        Cursor c = resolver.query(dataUri,
                new String[] {
                        Phone._ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE, Phone.TYPE, Phone.LABEL, Contacts.Data._ID
                },
                Data.MIMETYPE + "=?", new String[] {
                    Phone.CONTENT_ITEM_TYPE
                }, null);
        if (c != null) {
            if (c.moveToFirst()) {
                return c;
            }
            c.close();
        }
        return null;
    }

    /**
     * Make phone call
     * 
     * @author Wang
     * @date 2012-9-12
     * @param number the phone number to dial.
     */
    private void makePhoneCall(String number) {
        log("makePhoneCall =>" + number);
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts("tel", number, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mContext != null)
            mContext.startActivity(intent);
    }

    private void showDefaultContactSelectionDialog(ArrayList<Entry> list) {
        try {
            DefaultContactSelectionDialog dialog = new DefaultContactSelectionDialog(mContext,
                    list);
            Activity act = (Activity) mContext;
            dialog.show(act.getFragmentManager(), "default_contact");
        } catch (Exception e) {
            Toast.makeText(mContext, "Error", 200).show();
        }
    }

    /**
     * Default Contact Selection Dialog Fragment
     * 
     * @author Wang
     * @date 2012-9-14
     */
    private class DefaultContactSelectionDialog extends DialogFragment {
        private ArrayList<Entry> mList;
        private Context mContext;
        private String[] mPhoneNumbers;

        public DefaultContactSelectionDialog(Context ctx, ArrayList<Entry> list) {
            mList = list;
            mContext = ctx;
            mPhoneNumbers = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
               String phone =  list.get(i).phoneNumber;
               log("==>phone:"+phone);
                mPhoneNumbers[i] = phone;
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // TODO Auto-generated method stub
            return new AlertDialog.Builder(mContext)
//                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(com.android.contacts.R.string.message_using_number)
                    .setItems(mPhoneNumbers,  new
                            DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int
                                whichButton) {
                            /*
                             * User clicked on a radio button do some
                             * stuff
                             */
                            makePhoneCall(mPhoneNumbers[whichButton]);
                        }
                    })
                    .create();
        }

    }

    /**
     * Entry Class
     * 
     * @author Wang
     * @date 2012-9-14
     */
    private class Entry {
        public Entry(long dataId, String phoneNumber) {
            this.dataId = dataId;
            this.phoneNumber = phoneNumber;
        }

        long dataId;
        String phoneNumber;
    }

    private boolean isCustomFilterForPhoneNumbersOnly() {
        // TODO: this flag should not be stored in shared prefs.  It needs to be in the db.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
    }

    private static final boolean debug = false;

    private static void log(String msg) {
        if (debug)
            Log.i("Shendu", msg);
    }
}

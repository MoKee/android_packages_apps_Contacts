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
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailLayoutController;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.util.ImageViewDrawableSetter;
import com.android.contacts.util.NameAvatarUtils;
import com.android.contacts.util.PhoneCapabilityTester;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RadioGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;

public class ContactDetailActivity extends ContactsActivity {
    private static final String TAG = "ContactDetailActivity";

    /** Shows a toogle button for hiding/showing updates. Don't submit with true */
    private static final boolean DEBUG_TRANSITIONS = false;

    private ContactLoader.Result mContactData;
    private Uri mLookupUri;

    private ContactDetailLayoutController mContactDetailLayoutController;
    private ContactLoaderFragment mLoaderFragment;

    private Handler mHandler = new Handler();
    private Context mContext;
    
    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mContext = this;
        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());

            // If we are launched from the outside, we should create a new task, because the user
            // can freely navigate the app (this is different from phones, where only the UP button
            // kicks the user into the full app)
            if (shouldUpRecreateTask(intent)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            } else {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                        Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }

            intent.setClass(this, PeopleActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        getActionBar().hide();
        setContentView(R.layout.contact_detail_activity);

        mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                getFragmentManager(), null, findViewById(R.id.contact_detail_container),
                mContactDetailFragmentListener);
        

        // We want the UP affordance but no app icon.
        // Setting HOME_AS_UP, SHOW_TITLE and clearing SHOW_HOME does the trick.
       /* ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(
            		ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
            		ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setDisplayOptions(
            		ActionBar.DISPLAY_USE_LOGO,
                    ActionBar.DISPLAY_USE_LOGO
                    );

            actionBar.setTitle("");
        }*/

        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
         if (fragment instanceof ContactLoaderFragment) {
            mLoaderFragment = (ContactLoaderFragment) fragment;
            mLoaderFragment.setListener(mLoaderFragmentListener);
            mLoaderFragment.loadUri(getIntent().getData());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.star, menu);
        if (DEBUG_TRANSITIONS) {
            final MenuItem toggleSocial =
                    menu.add(mLoaderFragment.getLoadStreamItems() ? "less" : "more");
            toggleSocial.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            toggleSocial.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mLoaderFragment.toggleLoadStreamItems();
                    invalidateOptionsMenu();
                    return false;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
        starredMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mLookupUri != null) {
                    // Read the current starred value from the UI instead of using the last
                    // loaded state. This allows rapid tapping without writing the same
                    // value several times
                    final boolean isStarred = starredMenuItem.isChecked();

                    // To improve responsiveness, swap out the picture (and tag) in the UI already
                    ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                            mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                            !isStarred);

                    // Now perform the real save
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            ContactDetailActivity.this, mLookupUri, !isStarred);
                    ContactDetailActivity.this.startService(intent);
                }
                return true;
            }
        });
        // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    mContactData.getStarred());
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // First check if the {@link ContactLoaderFragment} can handle the key
        if (mLoaderFragment != null && mLoaderFragment.handleKeyDown(keyCode)) return true;

        // Otherwise find the correct fragment to handle the event
        FragmentKeyListener mCurrentFragment = mContactDetailLayoutController.getCurrentPage();
        if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return true;

        // In the last case, give the key event to the superclass.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }
    }

    private final ContactLoaderFragmentListener mLoaderFragmentListener =
            new ContactLoaderFragmentListener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onDetailsLoaded(final ContactLoader.Result result) {
            if (result == null) {
                return;
            }
            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the activity is destroyed (or will be destroyed soon), don't update the UI
                    if (isFinishing()) {
                        return;
                    }
                    mContactData = result;
                    mLookupUri = result.getLookupUri();
                    invalidateOptionsMenu();
                    setupTitle();
                    mContactDetailLayoutController.setContactData(mContactData);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(
                    ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            // Don't finish the detail activity after launching the editor because when the
            // editor is done, we will still want to show the updated contact details using
            // this activity.
            startActivity(intent);
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactDetailActivity.this, contactUri, true);
        }
    };

    /**
     * Setup the activity title and subtitle with contact name and company.
     */
    private void setupTitle() {
        CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(this, mContactData);
        String company =  ContactDetailDisplayUtils.getCompany(this, mContactData);

        byte[] photo = mContactData.getPhotoBinaryData(); //loading photo image
        Bitmap bitmap;
        if(photo==null){
        	//loading photo form name 
        	bitmap = NameAvatarUtils.makeNameAvatarBitmap(mContext, displayName.toString());
        	if(bitmap==null){
        		//loading photo form default 
            	bitmap = ContactBadgeUtil.loadDefaultAvatarPhoto(this, false, false);
        	}
        }else{
        	//loading photo image
        	bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.length);
        }
        
        View photoParent = LayoutInflater.from(mContext).inflate(
        		R.layout.contact_detail_activity_actionbar, null);
        ImageView photoView = (ImageView)photoParent.findViewById(R.id.contact_detail_activity_photo_id);
        photoView.setImageBitmap(bitmap);
        ActionBar.LayoutParams actionParams = new ActionBar.LayoutParams(
        		LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayOptions(
        		ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_TITLE,
        		ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(displayName);
        actionBar.setSubtitle(company);
        actionBar.setCustomView(photoParent,actionParams);
        actionBar.show();

        if (!TextUtils.isEmpty(displayName) &&
                AccessibilityManager.getInstance(this).isEnabled()) {
            View decorView = getWindow().getDecorView();
            decorView.setContentDescription(displayName);
            decorView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
    }

    private final ContactDetailFragment.Listener mContactDetailFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onItemClicked(Intent intent) {
            if (intent == null) {
                return;
            }
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, AccountWithDataSet account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };

    /**
     * This interface should be implemented by {@link Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }
}

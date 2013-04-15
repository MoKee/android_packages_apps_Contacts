/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.dialpad;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewSwitcher;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.SpecialCharSequenceMgr;
import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.calllog.CallLogFragment;
import com.android.contacts.dialpad.ShenduContactAdapter.SearchContactsListener;
import com.android.contacts.dialpad.ShenduContactAdapter.Shendu_ContactItem;
import com.android.contacts.dialpad.T9Search.ContactItem;
import com.android.contacts.dialpad.T9Search.T9Adapter;
import com.android.contacts.dialpad.T9Search.T9SearchResult;
import com.android.contacts.util.Constants;
import com.android.contacts.util.PhoneNumberFormatter;
import com.android.contacts.util.ShenduVibrate;
import com.android.contacts.util.StopWatch;
import com.android.internal.telephony.ITelephony;
import com.android.phone.CallLogAsync;
import com.android.phone.HapticFeedback;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends Fragment
        implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        AdapterView.OnItemClickListener, TextWatcher,
        PopupMenu.OnMenuItemClickListener,
        DialpadImageButton.OnPressedListener ,
        OnScrollListener ,ShenduContactAdapter.ContactsItemOnClickListener{
    private static final String TAG = DialpadFragment.class.getSimpleName();

    private static final boolean DEBUG = DialtactsActivity.DEBUG;

    private static final String EMPTY_NUMBER = "";

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_LENGTH_INFINITE = -1;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;
    
    /**shutao 2012-11-22*/
    private static final int DIAL_PHONE_OWNERSHI = 0;
    private static final int DIAL_NEW_CONTACT    = 1;
//    private static final int DIAL_RECENTCALLS_ADDTOCONTACT = 2;
    private static final int DIAL_SEND_MMS       = 2;
    
    /**
     * View (usually FrameLayout) containing mDigits field. This can be null, in which mDigits
     * isn't enclosed by the container.
     */
    private View mDigitsContainer;
    private EditText mDigits;

    /** Remembers if we need to clear digits field when the screen is completely gone. */
    private boolean mClearDigitsOnStop;

    private View mDelete;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private View mDialpad;
    /**
     * Remembers the number of dialpad buttons which are pressed at this moment.
     * If it becomes 0, meaning no buttons are pressed, we'll call
     * {@link ToneGenerator#stopTone()}; the method shouldn't be called unless the last key is
     * released.
     */
    private int mDialpadPressCount;

    private View mDialButtonContainer;
    private View mDialButton;
    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;

    private static T9Search sT9Search; // Static to avoid reloading when class is destroyed and recreated
    private ContactPhotoManager mPhotoLoader;
    private ToggleButton mT9Toggle;
    private ListView mT9List;
//    private ListView mT9ListTop;
    private T9Adapter mT9Adapter;
    private T9Adapter mT9AdapterTop;
    private ViewSwitcher mT9Flipper;
//    private LinearLayout mT9Top;
    private boolean mContactsUpdated;
    
    /**shutao  2012-10-17*/
    private CallLogFragment mShenduDialpadCallLogFragment;
    private View mShenduDialpadCallLogFragmentView;
    
    /** shutao 2012-10-23  */
    private  ShenduContactAdapter mShenduContactAdapter ;
    
    /**
     * shutao 2012-10-17 New T9 contacts list
     */
    private ListView mShenduNewContactsT9List;
    private ShenDuNewContactT9Adapter mShenduNewContactT9Adapter; 
    

    /**
     * Regular expression prohibiting manual phone call. Can be empty, which means "no rule".
     */
    private String mProhibitedPhoneNumberRegexp;


    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    private final CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private final HapticFeedback mHaptic = new HapticFeedback();
    /**shutao 2012-10-18*/
    private ShenduVibrate mVibrate;

    /** Identifier for the "Add Call" intent extra. */
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an ITelephony call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the ITelephony API.
     */
    private static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    private String mCurrentCountryIso;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Listen for phone state changes so that we can take down the
         * "dialpad chooser" if the phone becomes idle while the
         * chooser UI is visible.
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            // Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
            //       + state + ", '" + incomingNumber + "'");
            if ((state == TelephonyManager.CALL_STATE_IDLE) && dialpadChooserVisible()) {
                // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                // Note there's a race condition in the UI here: the
                // dialpad chooser could conceivably disappear (on its
                // own) at the exact moment the user was trying to select
                // one of the choices, which would be confusing.  (But at
                // least that's better than leaving the dialpad chooser
                // onscreen, but useless...)
                showDialpadChooser(false);
            }
        }
    };

    private boolean mWasEmptyBeforeTextChange;

    /**
     * This field is set to true while processing an incoming DIAL intent, in order to make sure
     * that SpecialCharSequenceMgr actions can be triggered by user input but *not* by a
     * tel: URI passed by some other app.  It will be set to false when all digits are cleared.
     */
    private boolean mDigitsFilledByIntent;
    
    /**shutao 2012-10-15*/
    private ViewFlipper mShenduCallBntFlipper;
    
    private Button mShenduCallShowButton;
    
    private View mShenduToContact;

    public static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

    // Add LOCALE_CHAGNED event receiver.
    private final BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "mIntentReceiver  onReceive  intent.getAction(): " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                if (isT9On()) {
//                    sT9Search = new T9Search(getActivity()); 
//                	MyLog("onReceive------++++++++++++++++ACTION_LOCALE_CHANGED");
                }
            } else if(intent.getAction().equals(REMOVER_CALLLOG)){

                Thread loadContacts = new Thread(new Runnable() {
                    public void run () {

                    	 if (mShenduContactAdapter == null) {
//                    		 MyLog("mShendu_ContactAdapter == null");
                    		 mShenduContactAdapter = new ShenduContactAdapter(getActivity());
                    		 mShenduContactAdapter.getAll();
                    	 }else{
//                    		 MyLog("mShendu_ContactAdapter != null");
                    	    mShenduContactAdapter.getAll();
                    	 }
                    }
                });
                loadContacts.start();
            }
        }
    };

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        mVibrate.Stop();
        getActivity().unregisterReceiver(mLocaleChangedReceiver);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    /**shutao 2012-10-25 The last number entered in the record*/
    public String mShenduHistoricalString = "";
    
    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
    	/**shutao 2012-10-25*/
    	if(!mShenduHistoricalString.equals(input.toString())){
    		
            if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.invalidateOptionsMenu();
                }
            }

    	}

        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }
    /** shutao 2012-10-15*/
    private boolean mShenduIsNull = true; 
    @Override
    public void afterTextChanged(Editable input) {
        // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequencMgr sequence,
        // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
        // behavior.
    	
    	if(!mShenduHistoricalString.equals(input.toString().replaceAll(" ", ""))){
//    		mShenduTimeHandler.removeCallbacks(mShenduRunnable);
    		try{
    		    if(input.toString().equals("")){
//    		    	MyLog("afterTextChanged == kong");
    				mDigitsContainer.setVisibility(View.GONE);
    		    	mShenduHistoricalThreadString = "";
    		    	mShenduIsNull = true;
//    		    	mShenduTimeHandler.removeCallbacks(mShenduRunnable);
    		    	if(mT9List.getVisibility() == View.VISIBLE 
    		    			|| mShenduNewContactsT9List.getVisibility() == View.VISIBLE){
    		    	mShenduNewContactsT9List.setVisibility(View.GONE);
					mT9List.setVisibility(View.GONE);
					mShenduDialpadCallLogFragmentView.setVisibility(View.VISIBLE);
					mShenduDialpadCallLogFragment.setMenuVisibility(true);
					getActivity().invalidateOptionsMenu();
    		    	}
    	         }else{
//    	        	 MyLog("afterTextChanged == bukong"+input.toString().replaceAll(" ", ""));
    	        	 searchContacts(false);
    	 			 mDigitsContainer.setVisibility(View.VISIBLE);
//    	        	 mIsSearch = true;
//    	        	 mShenduTimeHandler.postDelayed(mShenduRunnable, SEARCH_TIME_MILLIS);
//    	        	   	if(mShenduIsNull){
//        	            	mShenduTimeHandler.postDelayed(mShenduRunnable, SEARCH_TIME_MILLIS);
//        	            	mShenduIsNull = false;
//    	            	}
    	          }
    		}catch(Exception e){
    			
    		}
    	
    		mShenduHistoricalString = input.toString().replaceAll(" ", "");
    	}
        if (!mDigitsFilledByIntent &&
                SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        if (isDigitsEmpty()) {
            mDigitsFilledByIntent = false;
            mDigits.setCursorVisible(false);
        }

        updateDialAndDeleteButtonEnabledState();
    }
    
    /**shutao 2012-11-15*/
    public static final String REMOVER_CALLLOG = "shendu.action.REMOVER_CALLLOG";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mPhotoLoader = ContactPhotoManager.getInstance(getActivity());
        mPhotoLoader.preloadPhotosInBackground();
        mCurrentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());

        try {
        	mVibrate = new ShenduVibrate(getActivity());
      	    mVibrate.setOpen(getResources().getBoolean(R.bool.config_enable_dialer_key_vibration));
           mHaptic.init(getActivity(),
                         getResources().getBoolean(R.bool.config_enable_dialer_key_vibration));
        } catch (Resources.NotFoundException nfe) {
             Log.e(TAG, "Vibrate control bool missing.", nfe);
        }

        setHasOptionsMenu(true);

        mProhibitedPhoneNumberRegexp = getResources().getString(
                R.string.config_prohibited_phone_number_regexp);

        if (state != null) {
            mDigitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
        }

        // Add LOCALE_CHAGNED event receiver.
        IntentFilter localeChangedfilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        localeChangedfilter.addAction(REMOVER_CALLLOG);
        getActivity().registerReceiver(mLocaleChangedReceiver, localeChangedfilter);
        
    }

    private ContentObserver mContactObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mContactsUpdated = true;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);

        
        /** shutao 2012-10-16 */
       	mShenduCallBntFlipper = (ViewFlipper)fragmentView.findViewById(R.id.shendu_call_view_flipper);
       	mShenduCallShowButton = (Button)fragmentView.findViewById(R.id.shendu_call_show_button);
       	mShenduCallShowButton.setOnClickListener(this);
        /**
         * shutao 2012-10-17 Get call log fragment set the slide to hide the dial pad
         */
        mShenduDialpadCallLogFragment = (CallLogFragment)getFragmentManager().findFragmentById(R.id.dialpad_CallLogFragment);
        mShenduDialpadCallLogFragment.getListView().setOnScrollListener(this);
        mShenduDialpadCallLogFragmentView = fragmentView.findViewById(R.id.dialpad_CallLogFragment);
        mShenduDialpadCallLogFragmentView.setVisibility(View.VISIBLE);
        // Load up the resources for the text field.
        Resources r = getResources();

        mDigitsContainer = fragmentView.findViewById(R.id.digits_container);
        mDigits = (EditText) fragmentView.findViewById(R.id.digits);
        mDigits.setKeyListener(DialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(this);

        mT9List = (ListView) fragmentView.findViewById(R.id.t9list);
        mT9List.setVisibility(View.GONE);
        if (mT9List!= null) {
//            mT9List.setOnItemClickListener(this);
            mT9List.setOnScrollListener(this);
//            mT9List.setOnCreateContextMenuListener(this);
        }
        
        /**
         * shutao 2012-8-21
         */
        mShenduNewContactT9Adapter =new ShenDuNewContactT9Adapter(getActivity());
        mShenduNewContactsT9List = (ListView) fragmentView.findViewById(R.id.newContactList);
        if (mShenduNewContactsT9List!= null) {
        	mShenduNewContactsT9List.setOnItemClickListener(this);
        	mShenduNewContactsT9List.setOnScrollListener(this);
        	mShenduNewContactsT9List.setAdapter(mShenduNewContactT9Adapter);
        }
        
//        mT9ListTop = (ListView) fragmentView.findViewById(R.id.t9listtop);
//        if (mT9ListTop != null) {
//            mT9ListTop.setOnItemClickListener(this);
//            mT9ListTop.setTag(new ContactItem());
//        }
        mT9Toggle = (ToggleButton) fragmentView.findViewById(R.id.t9toggle);
        if (mT9Toggle != null) {
            mT9Toggle.setOnClickListener(this);
        }
        mT9Flipper = (ViewSwitcher) fragmentView.findViewById(R.id.t9flipper);
//        mT9Top = (LinearLayout) fragmentView.findViewById(R.id.t9topbar);
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits);
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            setupKeypad(fragmentView);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int minCellSize = (int) (56 * dm.density); // 56dip == minimum size of menu buttons
        int cellCount = dm.widthPixels / minCellSize;
        int fakeMenuItemWidth = dm.widthPixels / cellCount;
        mDialButtonContainer = fragmentView.findViewById(R.id.dialButtonContainer);
//        if (mDialButtonContainer != null) {
//            mDialButtonContainer.setPadding(
//                    fakeMenuItemWidth, mDialButtonContainer.getPaddingTop(),
//                    fakeMenuItemWidth, mDialButtonContainer.getPaddingBottom());
//        }
        /**shutao 2012-10-15*/
        mShenduToContact = fragmentView.findViewById(R.id.shendu_toContactsButton);
        if (mShenduToContact != null) {
        	mShenduToContact.setMinimumWidth(fakeMenuItemWidth);
        	mShenduToContact.setOnClickListener(this);
        }
        mDialButton = fragmentView.findViewById(R.id.dialButton);
        if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
            mDialButton.setOnClickListener(this);
            mDialButton.setOnLongClickListener(this);
        } else {
            mDialButton.setVisibility(View.GONE); // It's VISIBLE by default
            mDialButton = null;
        }

        mDelete = fragmentView.findViewById(R.id.deleteButton);
        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }

        mDialpad = fragmentView.findViewById(R.id.dialpad);  // This is null in landscape mode.

        // In landscape we put the keyboard in phone mode.
        if (null == mDialpad) {
            mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } else {
            mDigits.setCursorVisible(false);
        }

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        /**shutao 2012-11-2*/
//        mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
//        mDialpadChooser.setOnItemClickListener(this);

        configureScreenFromIntent(getActivity().getIntent());

        mShenduContactAdapter = new ShenduContactAdapter(getActivity());

		 mT9List.setAdapter(mShenduContactAdapter);
		 mShenduContactAdapter.setContactsItemOnClickListener(this);
        mShenduContactAdapter.setSearchContactsListener(new SearchContactsListener() {
			
			@Override
			public void notContacts() {
				// TODO Auto-generated method stub
				mShenduNewContactT9Adapter.setNewContactNumber(mDigits.getText().toString());
				if(mShenduNewContactsT9List.getVisibility() == View.GONE)
				mShenduNewContactsT9List.setVisibility(View.VISIBLE);
				
			}
			
			@Override
			public void Contacts() {
				// TODO Auto-generated method stub
				if(mShenduNewContactsT9List.getVisibility() == View.VISIBLE){
				mShenduNewContactsT9List.setVisibility(View.GONE);
				toggleT9();
				}
			}
		});
        
        return fragmentView;
    }

    private boolean isLayoutReady() {
        return mDigits != null;
    }

    public EditText getDigitsWidget() {
        return mDigits;
    }

    /**
     * @return true when {@link #mDigits} is actually filled by the Intent.
     */
    private boolean fillDigitsIfNecessary(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                if (Constants.SCHEME_TEL.equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    // Remember it is filled via Intent.
                    mDigitsFilledByIntent = true;
                    setFormattedDigits(data, null);
                    return true;
                } else {
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getActivity().getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                                null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    // Remember it is filled via Intent.
                                    mDigitsFilledByIntent = true;
                                    // Put the number into the input area
                                    setFormattedDigits(c.getString(0), c.getString(1));
                                    return true;
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * @see #showDialpadChooser(boolean)
     */
    private static boolean needToShowDialpadChooser(Intent intent, boolean isAddCallMode) {
        final String action = intent.getAction();

        boolean needToShowDialpadChooser = false;

        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) {
                // ACTION_DIAL or ACTION_VIEW with no data.
                // This behaves basically like ACTION_MAIN: If there's
                // already an active call, bring up an intermediate UI to
                // make the user confirm what they really want to do.
                // Be sure *not* to show the dialpad chooser if this is an
                // explicit "Add call" action, though.
                if (!isAddCallMode && phoneIsInUse()) {
                    needToShowDialpadChooser = true;
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            // The MAIN action means we're bringing up a blank dialer
            // (e.g. by selecting the Home shortcut, or tabbing over from
            // Contacts or Call log.)
            //
            // At this point, IF there's already an active call, there's a
            // good chance that the user got here accidentally (but really
            // wanted the in-call dialpad instead).  So we bring up an
            // intermediate UI to make the user confirm what they really
            // want to do.
            if (phoneIsInUse()) {
                // Log.i(TAG, "resolveIntent(): phone is in use; showing dialpad chooser!");
                needToShowDialpadChooser = true;
            }
        }

        return needToShowDialpadChooser;
    }

    private static boolean isAddCallMode(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
        } else {
            return false;
        }
    }

    /**
     * Checks the given Intent and changes dialpad's UI state. For example, if the Intent requires
     * the screen to enter "Add Call" mode, this method will show correct UI for the mode.
     */
    public void configureScreenFromIntent(Intent intent) {
        if (!isLayoutReady()) {
            // This happens typically when parent's Activity#onNewIntent() is called while
            // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
            // this point. onViewCreate() should call this method after preparing layouts, so
            // just ignore this call now.
            Log.i(TAG,
                    "Screen configuration is requested before onCreateView() is called. Ignored");
            return;
        }

        boolean needToShowDialpadChooser = false;

        final boolean isAddCallMode = isAddCallMode(intent);
        if (!isAddCallMode) {
            final boolean digitsFilled = fillDigitsIfNecessary(intent);
            if (!digitsFilled) {
                needToShowDialpadChooser = needToShowDialpadChooser(intent, isAddCallMode);
            }
        }
        showDialpadChooser(needToShowDialpadChooser);
    }

    /**
     * Sets formatted digits to digits field.
     */
    private void setFormattedDigits(String data, String normalizedNumber) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        dialString =
                PhoneNumberUtils.formatNumber(dialString, normalizedNumber, mCurrentCountryIso);
        if (!TextUtils.isEmpty(dialString)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), dialString);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
        }
    }

    private void setupKeypad(View fragmentView) {
        int[] buttonIds = new int[] { R.id.one, R.id.two, R.id.three, R.id.four, R.id.five,
                R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.zero, R.id.star, R.id.pound};
        for (int id : buttonIds) {
        	/**shutao 2012-10-25*/
//            ((DialpadImageButton) fragmentView.findViewById(id)).setOnPressedListener(this);
        	((DialpadImageButton) fragmentView.findViewById(id)).setOnClickListener(this);
        }

        // Long-pressing one button will initiate Voicemail.
//        fragmentView.findViewById(R.id.one).setOnLongClickListener(this);

        // Long-pressing zero button will enter '+' instead.
        fragmentView.findViewById(R.id.zero).setOnLongClickListener(this);

    }

    @Override
    public void onResume() {
        super.onResume();

        final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");

//        if ((sT9Search == null && isT9On()) || mContactsUpdated) {
            Thread loadContacts = new Thread(new Runnable() {
                public void run () {
                    mShenduDialpadCallLogFragment.refreshData();
//                    sT9Search = new T9Search(getActivity());
                	 if (mShenduContactAdapter == null) {
                		 MyLog("mShendu_ContactAdapter == null");
                		 mShenduContactAdapter = new ShenduContactAdapter(getActivity());
                		 mShenduContactAdapter.getAll();
                	 }else{
                		 MyLog("mShendu_ContactAdapter != null");
                	    mShenduContactAdapter.getAll();
                	 }
                }
            });
    	
            loadContacts.start();
            if (mContactsUpdated) {
                mContactsUpdated = false;
                onLongClick(mDelete);
                mT9Adapter = null;
                mT9AdapterTop = null;
                /**shutao  2012-10-15*/
//                mT9ListTop.setAdapter(mT9AdapterTop);
//                mT9List.setAdapter(mT9Adapter);
//            }
        }

        if (isT9On()) {
            getActivity().getContentResolver().unregisterContentObserver(mContactObserver);
        }

//        hideT9();

        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        stopWatch.lap("qloc");

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        stopWatch.lap("dtwd");

        // Retrieve the haptic feedback setting.
        mHaptic.checkSystemSetting();
        mVibrate.checkSystemSetting();
        stopWatch.lap("hptc");

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        stopWatch.lap("tg");
        // Prevent unnecessary confusion. Reset the press count anyway.
        mDialpadPressCount = 0;

        Activity parent = getActivity();
        if (parent instanceof DialtactsActivity) {
            // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
            // digits in the dialer field.
            fillDigitsIfNecessary(parent.getIntent());
        }

        stopWatch.lap("fdin");

        // While we're in the foreground, listen for phone state changes,
        // purely so that we can take down the "dialpad chooser" if the
        // phone becomes idle while the chooser UI is visible.
        TelephonyManager telephonyManager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        stopWatch.lap("tm");

        // Potentially show hint text in the mDigits field when the user
        // hasn't typed any digits yet.  (If there's already an active call,
        // this hint text will remind the user that he's about to add a new
        // call.)
        //
        // TODO: consider adding better UI for the case where *both* lines
        // are currently in use.  (Right now we let the user try to add
        // another call, but that call is guaranteed to fail.  Perhaps the
        // entire dialer UI should be disabled instead.)
        if (phoneIsInUse()) {
            final SpannableString hint = new SpannableString(
                    getActivity().getString(R.string.dialerDialpadHintText));
            /**shutao 2012-10-23*/
            mDigits.setHintTextColor(getActivity().getResources().getColor(R.color.background_primary));
            hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          
            mDigits.setHint(hint);
        } else {
            // Common case; no hint necessary.
            mDigits.setHint(null);

            // Also, a sanity-check: the "dialpad chooser" UI should NEVER
            // be visible if the phone is idle!
            showDialpadChooser(false);
        }

        stopWatch.lap("hnt");

        updateDialAndDeleteButtonEnabledState();

        stopWatch.lap("bes");

        stopWatch.stopAndLog(TAG, 50);
        /**shutao 2012-10-18*/
//        searchContacts();
    }

    @Override
    public void onPause() {
        super.onPause();

        /**shutao 2012-10-25*/
//        mShenduTimeHandler.removeCallbacks(mShenduRunnable);
//        mShenduIsNull = true;
        
        // Stop listening for phone state changes.
        TelephonyManager telephonyManager =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        // Make sure we don't leave this activity with a tone still playing.
        stopTone();
        // Just in case reset the counter too.
        mDialpadPressCount = 0;

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.
        if (isT9On()) {
            getActivity().getContentResolver().registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true, mContactObserver);
        }
        SpecialCharSequenceMgr.cleanup();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mClearDigitsOnStop) {
            mClearDigitsOnStop = false;
            mDigits.getText().clear();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if ((ViewConfiguration.get(getActivity()).hasPermanentMenuKey() || isLandscape) &&
                isLayoutReady() /*&& mDialpadChooser != null*/) {
            inflater.inflate(R.menu.dialpad_options, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        // Hardware menu key should be available and Views should already be ready.
        if ((ViewConfiguration.get(getActivity()).hasPermanentMenuKey() || isLandscape) &&
                isLayoutReady() /*&& mDialpadChooser != null*/) {
             setupMenuItems(menu);
        }
    }

    private void setupMenuItems(Menu menu) {
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings_dialpad);
        final MenuItem addToContactMenuItem = menu.findItem(R.id.menu_add_contacts);
        final MenuItem twoSecPauseMenuItem = menu.findItem(R.id.menu_2s_pause);
        final MenuItem waitMenuItem = menu.findItem(R.id.menu_add_wait);

        // Check if all the menu items are inflated correctly. As a shortcut, we assume all menu
        // items are ready if the first item is non-null.
        if (callSettingsMenuItem == null) {
            return;
        }

        final Activity activity = getActivity();
        if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
            // Call settings should be available via its parent Activity.
            callSettingsMenuItem.setVisible(false);
        } else {
            callSettingsMenuItem.setVisible(true);
            callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
        }

        // We show "add to contacts", "2sec pause", and "add wait" menus only when the user is
        // seeing usual dialpads and has typed at least one digit.
        // We never show a menu if the "choose dialpad" UI is up.
        if (dialpadChooserVisible() || isDigitsEmpty()) {
            addToContactMenuItem.setVisible(false);
            twoSecPauseMenuItem.setVisible(false);
            waitMenuItem.setVisible(false);
        } else {
            final CharSequence digits = mDigits.getText();

            // Put the current digits string into an intent
            addToContactMenuItem.setIntent(getAddToContactIntent(digits));
            addToContactMenuItem.setVisible(true);

            // Check out whether to show Pause & Wait option menu items
            int selectionStart;
            int selectionEnd;
            String strDigits = digits.toString();

            selectionStart = mDigits.getSelectionStart();
            selectionEnd = mDigits.getSelectionEnd();

            if (selectionStart != -1) {
                if (selectionStart > selectionEnd) {
                    // swap it as we want start to be less then end
                    int tmp = selectionStart;
                    selectionStart = selectionEnd;
                    selectionEnd = tmp;
                }

                if (selectionStart != 0) {
                    // Pause can be visible if cursor is not in the begining
                	/**shutao 2012-11-7*/
                    twoSecPauseMenuItem.setVisible(false);

                    // For Wait to be visible set of condition to meet
                    /**shutao 2012-11-7*/
//                    waitMenuItem.setVisible(showWait(selectionStart, selectionEnd, strDigits));
                    waitMenuItem.setVisible(false);
                } else {
                    // cursor in the beginning both pause and wait to be invisible
                    twoSecPauseMenuItem.setVisible(false);
                    waitMenuItem.setVisible(false);
                }
            } else {
            	/**shutao 2012-11-7*/
                twoSecPauseMenuItem.setVisible(false);

                // cursor is not selected so assume new digit is added to the end
                int strLength = strDigits.length();
            	/**shutao 2012-11-7*/                
//                waitMenuItem.setVisible(showWait(strLength, strLength, strDigits));
                waitMenuItem.setVisible(false);
            }
        }
    }

    private static Intent getAddToContactIntent(CharSequence digits) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Insert.PHONE, digits);
        intent.setType(People.CONTENT_ITEM_TYPE);
        return intent;
    }

    /**
     * Hides the topresult layout
     * Needed to reclaim the space when T9 is off.
     */
//    private void hideT9 () {
//        if (mDigitsContainer == null) {
//            if (!isT9On()) {
//                toggleT9();
////                mT9Top.setVisibility(View.GONE);
//            }else{
////                mT9Top.setVisibility(View.GONE);
//            }
//        } else {
//            LinearLayout.LayoutParams digitsLayout = (LayoutParams) mDigitsContainer.getLayoutParams();
//            if (!isT9On()) {
//                toggleT9();
//                digitsLayout.weight = 0.2f;
////                mT9Top.setVisibility(View.GONE);
//            } else {
//                digitsLayout.weight = 0.1f;
////                mT9Top.setVisibility(View.GONE);
//            }
//            mDigitsContainer.setLayoutParams(digitsLayout);
//        }
//        return;
//    }

    /**
     * Toggles between expanded list and dialpad
     */
    private void toggleT9() {
        if (mT9Flipper.getCurrentView() == mT9List) {
            mT9Toggle.setChecked(false);
            animateT9();
        }
    }

    /**
     * Initiates a search for the dialed digits
     * Toggles view visibility based on results
     * shutao 2012-10-15
     */
	private synchronized void searchContacts(boolean isAll) {
		if (!isT9On())
			return;
		final int length = mDigits.length();
		if (length > 0) {
			//shutao 2013-1-30   
//			if(mDigits.getText().toString().equals("1")){
//				if(	mT9List.getVisibility() == View.VISIBLE){
//					mShenduDialpadCallLogFragmentView.setVisibility(View.VISIBLE);
//					mShenduDialpadCallLogFragment.setMenuVisibility(true);
//					mShenduNewContactsT9List.setVisibility(View.GONE);
//					mT9List.setVisibility(View.GONE);
//					getActivity().invalidateOptionsMenu();
//				}
//				return;
//			}
			/** shutao 2012-9-21*/
//			mShenduContactAdapter.getFilter().filter(mDigits.getText().toString());
			mShenduContactAdapter.search(mDigits.getText().toString(),isAll);
			mT9List.setVisibility(View.VISIBLE);
			mShenduDialpadCallLogFragment.setMenuVisibility(false);
			mShenduDialpadCallLogFragmentView.setVisibility(View.GONE);
			getActivity().invalidateOptionsMenu();
//			if (sT9Search != null) {
//				mShenduDialpadCallLogFragmentView.setVisibility(View.GONE);
//				mShenduDialpadCallLogFragment.setMenuVisibility(false);
//				T9SearchResult result = sT9Search.search(mDigits.getText().toString());
//					mT9List.setVisibility(View.VISIBLE);
//					mShenduNewContactsT9List.setVisibility(View.GONE);
//					if (mT9Adapter == null) {
//						mT9Adapter = sT9Search.new T9Adapter(getActivity(), 0, 
//								result.getResults(),getActivity().getLayoutInflater(), mPhotoLoader);
//						mT9Adapter.setNotifyOnChange(true);
//					}else{
//						 mT9Adapter.clear();
//	                  mT9Adapter.addAll(result.getResults());
//					}
//					if (mT9List.getAdapter() == null) {
//						mT9List.setAdapter(mT9Adapter);
//					}
////					mT9Adapter.getFilter().filter(mDigits.getText().toString());
//					mT9Toggle.setTag(null);
//					mShenduDialpadCallLogFragment.setMenuVisibility(false);
//					mShenduDialpadCallLogFragmentView.setVisibility(View.GONE);
//			    	mT9List.setVisibility(View.VISIBLE);
//			}

		} else {
			mShenduNewContactsT9List.setVisibility(View.GONE);
			mShenduDialpadCallLogFragmentView.setVisibility(View.VISIBLE);
			mShenduDialpadCallLogFragment.setMenuVisibility(true);
			mT9List.setVisibility(View.GONE);
//			mT9ListTop.setVisibility(View.GONE);
		
			// mT9Toggle.setVisibility(View.INVISIBLE);
			toggleT9();
		}
	}

    /**
     * Returns preference value for T9Dialer
     */
    private boolean isT9On() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("t9_state", true);
    }

    /**
     * Returns preference for whether to dial
     * upon clicking contact in listview/topbar
     */
    private boolean dialOnTap() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("t9_dial_onclick", false);
    }

    /**
     * Animates the dialpad/listview
     */
    private void animateT9() {
        TranslateAnimation slidedown1 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        TranslateAnimation shendu_slidedown1 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        TranslateAnimation slidedown2 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, -1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        TranslateAnimation slideup1 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, -1.0f);
        TranslateAnimation slideup2 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        TranslateAnimation shendu_slideup2 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        slidedown2.setDuration(100);
        slidedown2.setInterpolator(new DecelerateInterpolator());
        slidedown1.setDuration(100);
        slidedown1.setInterpolator(new DecelerateInterpolator());
        shendu_slidedown1.setDuration(250);
        shendu_slidedown1.setInterpolator(new DecelerateInterpolator());
        shendu_slidedown1.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animatio){}
			@Override
			public void onAnimationRepeat(Animation animation) {}
			@Override
			public void onAnimationEnd(Animation animation) {
				// TODO Auto-generated method stub
				mT9Flipper.setVisibility(View.GONE);
				/**shutao 2012-10-10*/
//		       mShenduCallBntFlipper.showNext();
				mShenduCallBntFlipper.setDisplayedChild(1);
			}
		});
        slideup1.setDuration(50);
        slideup1.setInterpolator(new DecelerateInterpolator());
        slideup2.setDuration(50);
        slideup2.setInterpolator(new DecelerateInterpolator());
        shendu_slideup2.setDuration(250);
        shendu_slideup2.setInterpolator(new DecelerateInterpolator());
        shendu_slideup2.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animatio){
				// TODO Auto-generated method stub
				mT9Flipper.setVisibility(View.VISIBLE);
			}
			@Override
			public void onAnimationRepeat(Animation animation) {}
			@Override
			public void onAnimationEnd(Animation animation) {
				/**shutao 2012-10-10*/
//				mShenduCallBntFlipper.showNext();
				mShenduCallBntFlipper.setDisplayedChild(0);
			}
		});
        if (mT9Toggle.isChecked()) {
            mShenduCallBntFlipper.setOutAnimation(slidedown1);
            mShenduCallBntFlipper.setInAnimation(slidedown2);
            mT9Flipper.startAnimation(shendu_slidedown1);
        } else {
            mShenduCallBntFlipper.setOutAnimation(slideup1);
            mShenduCallBntFlipper.setInAnimation(slideup2);
      	     mT9Flipper.startAnimation(shendu_slideup2);
        }
    }

    private void keyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_MS);
                mVibrate.playVibrate(-1);
                break;
            default:
                break;
        }

//        mHaptic.vibrate();
    
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);

        // If the cursor is at the end of the text we hide it.
        final int length = mDigits.length();
//        searchContacts();
        if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
            mDigits.setCursorVisible(false);
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialButtonPressed();
                    return true;
                }
//                searchContacts();
                break;
        }
        return false;
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        if (DEBUG) Log.d(TAG, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
//            switch (view.getId()) {
//                case R.id.one: {
//                    keyPressed(KeyEvent.KEYCODE_1);
//                    break;
//                }
//                case R.id.two: {
//                    keyPressed(KeyEvent.KEYCODE_2);
//                    break;
//                }
//                case R.id.three: {
//                    keyPressed(KeyEvent.KEYCODE_3);
//                    break;
//                }
//                case R.id.four: {
//                    keyPressed(KeyEvent.KEYCODE_4);
//                    break;
//                }
//                case R.id.five: {
//                    keyPressed(KeyEvent.KEYCODE_5);
//                    break;
//                }
//                case R.id.six: {
//                    keyPressed(KeyEvent.KEYCODE_6);
//                    break;
//                }
//                case R.id.seven: {
//                    keyPressed(KeyEvent.KEYCODE_7);
//                    break;
//                }
//                case R.id.eight: {
//                    keyPressed(KeyEvent.KEYCODE_8);
//                    break;
//                }
//                case R.id.nine: {
//                    keyPressed(KeyEvent.KEYCODE_9);
//                    break;
//                }
//                case R.id.zero: {
//                    keyPressed(KeyEvent.KEYCODE_0);
//                    break;
//                }
//                case R.id.pound: {
//                    keyPressed(KeyEvent.KEYCODE_POUND);
//                    break;
//                }
//                case R.id.star: {
//                    keyPressed(KeyEvent.KEYCODE_STAR);
//                    break;
//                }
//                default: {
//                    Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
//                    break;
//                }
//            }
            mDialpadPressCount++;
        } else {
            view.jumpDrawablesToCurrentState();
            mDialpadPressCount--;
            if (mDialpadPressCount < 0) {
                // e.g.
                // - when the user action is detected as horizontal swipe, at which only
                //   "up" event is thrown.
                // - when the user long-press '0' button, at which dialpad will decrease this count
                //   while it still gets press-up event here.
                if (DEBUG) Log.d(TAG, "mKeyPressCount become negative.");
                stopTone();
                mDialpadPressCount = 0;
            } else if (mDialpadPressCount == 0) {
                stopTone();
            }
        }
    }

    @Override
    public void onClick(View view) {
		switch (view.getId()) {

		case R.id.one: {
			keyPressed(KeyEvent.KEYCODE_1);
			break;
		}
		case R.id.two: {
			keyPressed(KeyEvent.KEYCODE_2);
			break;
		}
		case R.id.three: {
			keyPressed(KeyEvent.KEYCODE_3);
			break;
		}
		case R.id.four: {
			keyPressed(KeyEvent.KEYCODE_4);
			break;
		}
		case R.id.five: {
			keyPressed(KeyEvent.KEYCODE_5);
			break;
		}
		case R.id.six: {
			keyPressed(KeyEvent.KEYCODE_6);
			break;
		}
		case R.id.seven: {
			keyPressed(KeyEvent.KEYCODE_7);
			break;
		}
		case R.id.eight: {
			keyPressed(KeyEvent.KEYCODE_8);
			break;
		}
		case R.id.nine: {
			keyPressed(KeyEvent.KEYCODE_9);
			break;
		}
		case R.id.zero: {
			keyPressed(KeyEvent.KEYCODE_0);
			break;
		}
		case R.id.pound: {
			keyPressed(KeyEvent.KEYCODE_POUND);
			break;
		}
		case R.id.star: {
			keyPressed(KeyEvent.KEYCODE_STAR);
			break;
		}
		case R.id.deleteButton: {
			keyPressed(KeyEvent.KEYCODE_DEL);
			return;
		}
		case R.id.dialButton: {
			// mHaptic.vibrate(); // Vibrate here too, just like we do for the
			// regular keys
			mVibrate.playVibrate(-1);
			dialButtonPressed();
			return;
		}
		case R.id.digits: {
			if (!isDigitsEmpty()) {
				mDigits.setCursorVisible(true);
			}
			return;
		}
		case R.id.shendu_call_show_button: {
			mT9Toggle.setChecked(false);
			animateT9();
			return;
		}
		/** shutao 2012-10-15 to contact */
		case R.id.shendu_toContactsButton: {
			Intent intent = new Intent(getActivity(), PeopleActivity.class);
			startActivity(intent);
			return;
		}
		case R.id.t9toggle: {
			/**shutao 2012-11-6*/
			searchContacts(true);
			
			animateT9();
			return;
		}
		default: {
			Log.wtf(TAG, "Unexpected onClick() event from: " + view);
			return;
		}
		
		}
    }

    public PopupMenu constructPopupMenu(View anchorView) {
        final Context context = getActivity();
        if (context == null) {
            return null;
        }
        final PopupMenu popupMenu = new PopupMenu(context, anchorView);
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        setupMenuItems(menu);
        return popupMenu;
    }

    /**shutao  2012-10-26*/
    public void clearDigits(){
        Editable digits = mDigits.getText();
    	 digits.clear();
    }
    
    @Override
    public boolean onLongClick(View view) {
//        final Editable digits = mDigits.getText();
        final int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
//                digits.clear();
//                searchContacts();
            	clearDigits();
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                return true;
            }
            /**shutao 2012-10-25*/
//            case R.id.one: {
//                // '1' may be already entered since we rely on onTouch() event for numeric buttons.
//                // Just for safety we also check if the digits field is empty or not.
//                if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
//                    // We'll try to initiate voicemail and thus we want to remove irrelevant string.
//                    removePreviousDigitIfPossible();
//
//                    if (isVoicemailAvailable()) {
//                        callVoicemail();
//                    } else if (getActivity() != null) {
//                        // Voicemail is unavailable maybe because Airplane mode is turned on.
//                        // Check the current status and show the most appropriate error message.
//                        final boolean isAirplaneModeOn =
//                                Settings.System.getInt(getActivity().getContentResolver(),
//                                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
//                        if (isAirplaneModeOn) {
//                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
//                                    R.string.dialog_voicemail_airplane_mode_message);
//                            dialogFragment.show(getFragmentManager(),
//                                    "voicemail_request_during_airplane_mode");
//                        } else {
//                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
//                                    R.string.dialog_voicemail_not_ready_message);
//                            dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
//                        }
//                    }
//                    return true;
//                }
//                return false;
//            }
            case R.id.zero: {
                // Remove tentative input ('0') done by onTouch().
                removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_PLUS);

                // Stop tone immediately and decrease the press count, so that possible subsequent
                // dial button presses won't honor the 0 click any more.
                // Note: this *will* make mDialpadPressCount negative when the 0 key is released,
                // which should be handled appropriately.
                stopTone();
                if (mDialpadPressCount > 0) mDialpadPressCount--;

                return true;
            }
            case R.id.digits: {
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDigits.setCursorVisible(true);
                return false;
            }
            case R.id.dialButton: {
                if (isDigitsEmpty()) {
                    handleDialButtonClickWithEmptyDigits();
                    // This event should be consumed so that onClick() won't do the exactly same
                    // thing.
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Remove the digit just before the current position. This can be used if we want to replace
     * the previous digit or cancel previously entered character.
     */
    private void removePreviousDigitIfPossible() {
        final Editable editable = mDigits.getText();
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    public void callVoicemail() {
        startActivity(ContactsUtils.getVoicemailIntent());
        mClearDigitsOnStop = true;
        getActivity().finish();
    }

    public static class ErrorDialogFragment extends DialogFragment {
        private int mTitleResId;
        private int mMessageResId;

        private static final String ARG_TITLE_RES_ID = "argTitleResId";
        private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

        public static ErrorDialogFragment newInstance(int messageResId) {
            return newInstance(0, messageResId);
        }

        public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
            final ErrorDialogFragment fragment = new ErrorDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_TITLE_RES_ID, titleResId);
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
            mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mTitleResId != 0) {
                builder.setTitle(mTitleResId);
            }
            if (mMessageResId != 0) {
                builder.setMessage(mMessageResId);
            }
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                    });
            return builder.create();
        }
    }

    /**
     * In most cases, when the dial button is pressed, there is a
     * number in digits area. Pack it in the intent, start the
     * outgoing call broadcast as a separate task and finish this
     * activity.
     *
     * When there is no digit and the phone is CDMA and off hook,
     * we're sending a blank flash for CDMA. CDMA networks use Flash
     * messages when special processing needs to be done, mainly for
     * 3-way or call waiting scenarios. Presumably, here we're in a
     * special 3-way scenario where the network needs a blank flash
     * before being able to add the new participant.  (This is not the
     * case with all 3-way calls, just certain CDMA infrastructures.)
     *
     * Otherwise, there is no digit, display the last dialed
     * number. Don't finish since the user may want to edit it. The
     * user needs to press the dial button again, to dial it (general
     * case described above).
     */
    public void dialButtonPressed() {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = ContactsUtils.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity)getActivity()).getCallOrigin() : null));
                startActivity(intent);
                /**shutao 2012-10-18*/
                mClearDigitsOnStop = true;
                mDigits.getText().clear();
//                getActivity().finish();
            }
        }
    }

    private void handleDialButtonClickWithEmptyDigits() {
        if (phoneIsCdma() && phoneIsOffhook()) {
            // This is really CDMA specific. On GSM is it possible
            // to be off hook and wanted to add a 3rd party using
            // the redial feature.
            startActivity(newFlashIntent());
        } else {
            if (!TextUtils.isEmpty(mLastNumberDialed)) {
                // Recall the last number dialed.
                mDigits.setText(mLastNumberDialed);

                // ...and move the cursor to the end of the digits string,
                // so you'll be able to delete digits using the Delete
                // button (just as if you had typed the number manually.)
                //
                // Note we use mDigits.getText().length() here, not
                // mLastNumberDialed.length(), since the EditText widget now
                // contains a *formatted* version of mLastNumberDialed (due to
                // mTextWatcher) and its length may have changed.
                mDigits.setSelection(mDigits.getText().length());
            } else {
                // There's no "last number dialed" or the
                // background query is still running. There's
                // nothing useful for the Dial button to do in
                // this case.  Note: with a soft dial button, this
                // can never happens since the dial button is
                // disabled under these conditons.
                playTone(ToneGenerator.TONE_PROP_NACK);
            }
        }
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager =
                (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        // Check if onCreateView() is already called by checking one of View objects.
        if (!isLayoutReady()) {
            return;
        }

        if (enabled) {
        	/** shutao 2012-10-15   */
            // Log.i(TAG, "Showing dialpad chooser!");
//            if (mDigitsContainer != null) {
//                mDigitsContainer.setVisibility(View.GONE);
//            } else {
//                // mDigits is not enclosed by the container. Make the digits field itself gone.
//                mDigits.setVisibility(View.GONE);
//            }
//            if (mDialpad != null) mDialpad.setVisibility(View.GONE);
//            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.GONE);
//
//            mDialpadChooser.setVisibility(View.VISIBLE);
//
//            // Instantiate the DialpadChooserAdapter and hook it up to the
//            // ListView.  We do this only once.
//            if (mDialpadChooserAdapter == null) {
//                mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
//            }
//            mDialpadChooser.setAdapter(mDialpadChooserAdapter);
        } else {
            if (isT9On()) {
                if (mT9Flipper.getCurrentView() != mT9List) {
                	/**shutao  2012-10-15*/ 
                	if(mT9Toggle.isChecked()){
                         mShenduCallBntFlipper.showPrevious();
                     }
                    mT9Toggle.setChecked(false);
                    mDialButton.setVisibility(View.VISIBLE);
                    mT9Flipper.setVisibility(View.VISIBLE);
//                    searchContacts();
                } else {
                    return;
                }
            }
            // Log.i(TAG, "Displaying normal Dialer UI.");
//            if (mDigitsContainer != null) {
////                mDigitsContainer.setVisibility(View.VISIBLE);
//            } else {
//                mDigits.setVisibility(View.VISIBLE);
//            }
            if (mDialpad != null) mDialpad.setVisibility(View.VISIBLE);
            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.VISIBLE);
        	/**shutao 2012-11-2*/
//            mDialpadChooser.setVisibility(View.GONE);
        }
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean dialpadChooserVisible() {
    	/**shutao 2012-11-2*/
//        return mDialpadChooser.getVisibility() == View.VISIBLE;
    	return false;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        @Override
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
//        if (parent == mT9List /*|| pmT9ListToparent == */) {
//            if (parent == mT9List) {
////                setFormattedDigits(mT9Adapter.getItem(position).number,null);
//            	   setFormattedDigits(((Shendu_ContactItem)mShenduContactAdapter.getItem(position)).number,null);
//                dialButtonPressed();
//            } else {
//                if (mT9Toggle.getTag() == null) {
//                	/**shutao 2012-10-18*/
////                    setFormattedDigits(mT9AdapterTop.getItem(position).number,null);
//                } else {
//                    startActivity(getAddToContactIntent(mDigits.getText()));
//                    return;
//                }
//            }
//            if (dialOnTap()) {
//                dialButtonPressed();
//            }
//            return;
//        }
        
        /**
         * shutao 2012-10-18 The unfamiliar Number menu option
         */
        if(parent == mShenduNewContactsT9List){
        	switch(position){
        	case DIAL_PHONE_OWNERSHI:
        		break;
        	case DIAL_NEW_CONTACT:
              Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
              intent.putExtra(Insert.PHONE, mDigits.getText().toString().replaceAll(" ", ""));
        		startActivity(intent);
        		break;
//        	case DIAL_RECENTCALLS_ADDTOCONTACT:
//        		startActivity(getAddToContactIntent(mDigits.getText().toString().replaceAll(" ", "")));
//        		break;
        	case DIAL_SEND_MMS:
        		Intent mIntent = new Intent(Intent.ACTION_SENDTO,Uri.fromParts("sms", mDigits.getText().toString(), null));
        		startActivity( mIntent );
        		break;
        	}
        	return;
        }
        
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        switch (itemId) {
            case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                // Fire off an intent to go back to the in-call UI
                // with the dialpad visible.
                returnToInCallScreen(true);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                // Fire off an intent to go back to the in-call UI
                // (with the dialpad hidden).
                returnToInCallScreen(false);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                // Ok, guess the user really did want to be here (in the
                // regular Dialer) after all.  Bring back the normal Dialer UI.
                showDialpadChooser(false);
                break;

            default:
                Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                break;
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phone.showCallScreenWithDialpad(showDialpad);
        } catch (RemoteException e) {
            Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
        }

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        getActivity().finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    public static boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneInUse = !phone.isIdle();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    /**
     * @return true if the phone state is OFFHOOK
     */
    private boolean phoneIsOffhook() {
        boolean phoneOffhook = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneOffhook = phone.isOffhook();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isOffhook() failed", e);
        }
        return phoneOffhook;
    }

    /**
     * Returns true whenever any one of the options from the menu is selected.
     * Code changes to support dialpad options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_2s_pause:
                updateDialString(",");
                return true;
            case R.id.menu_add_wait:
                updateDialString(";");
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(String newDigits) {
        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        Editable digits = mDigits.getText();
        if (selectionStart != -1) {
            if (selectionStart == selectionEnd) {
                // then there is no selection. So insert the pause at this
                // position and update the mDigits.
                digits.replace(selectionStart, selectionStart, newDigits);
            } else {
                digits.replace(selectionStart, selectionEnd, newDigits);
                // Unselect: back to a regular cursor, just pass the character inserted.
                mDigits.setSelection(selectionStart + 1);
            }
        } else {
            int len = mDigits.length();
            digits.replace(len, len, newDigits);
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonEnabledState() {
        final boolean digitsNotEmpty = !isDigitsEmpty();

        if (mDialButton != null) {
            // On CDMA phones, if we're already on a call, we *always*
            // enable the Dial button (since you can press it without
            // entering any digits to send an empty flash.)
            if (phoneIsCdma() && phoneIsOffhook()) {
                mDialButton.setEnabled(true);
            } else {
                // Common case: GSM, or CDMA but not on a call.
                // Enable the Dial button if some digits have
                // been entered, or if there is a last dialed number
                // that could be redialed.
                mDialButton.setEnabled(digitsNotEmpty ||
                        !TextUtils.isEmpty(mLastNumberDialed));
            }
        }
        mDelete.setEnabled(digitsNotEmpty);
    }

    /**
     * Check if voicemail is enabled/accessible.
     *
     * @return true if voicemail is enabled and accessibly. Note that this can be false
     * "temporarily" after the app boot.
     * @see TelephonyManager#getVoiceMailNumber()
     */
    private boolean isVoicemailAvailable() {
        try {
            return (TelephonyManager.getDefault().getVoiceMailNumber() != null);
        } catch (SecurityException se) {
            // Possibly no READ_PHONE_STATE privilege.
            Log.w(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
        }
        return false;
    }

    /**
     * This function return true if Wait menu item can be shown
     * otherwise returns false. Assumes the passed string is non-empty
     * and the 0th index check is not required.
     */
    private static boolean showWait(int start, int end, String digits) {
        if (start == end) {
            // visible false in this case
            if (start > digits.length()) return false;

            // preceding char is ';', so visible should be false
            if (digits.charAt(start - 1) == ';') return false;

            // next char is ';', so visible should be false
            if ((digits.length() > start) && (digits.charAt(start) == ';')) return false;
        } else {
            // visible false in this case
            if (start > digits.length() || end > digits.length()) return false;

            // In this case we need to just check for ';' preceding to start
            // or next to end
            if (digits.charAt(start - 1) == ';') return false;
        }
        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    getActivity(),
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        @Override
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            mLastNumberDialed = number;
                            updateDialAndDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    private Intent newFlashIntent() {
        final Intent intent = ContactsUtils.getCallIntent(EMPTY_NUMBER);
        intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
        return intent;
    }

    
    /**
     * shutao 2012-10-15 Listening contacts sliding and hide the keyboard
     */
	@Override
	public void onScroll(AbsListView arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
	}
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// TODO Auto-generated method stub
		 if (!mT9Toggle.isChecked()) {
           MyLog("down------------------------shutao");

           searchContacts(true);
			 mT9Toggle.setChecked(true);
			 animateT9();
		 }
	}
	
	
	
	/**
	 * shutao 2012-9-3  Loop search RUN method
	 */
    public void startThread(){
    	mT9List.setVisibility(View.VISIBLE);
    	new Thread(mShenduRunnable).start();
    }
    
    public boolean mIsSearch = false;
    public final long  SEARCH_TIME_MILLIS = 100;
    public String mShenduHistoricalThreadString = "";
	public Handler mShenduTimeHandler = new Handler();
	public boolean mRunnableOverlapp = false;
	public Runnable mShenduRunnable= new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
//			MyLog("timeHandler------------------------");
			while(true){
				if(mIsSearch){
//					searchContacts(false);
					mShenduContactAdapter.search(mDigits.getText().toString(),false);
					if(!mShenduHistoricalThreadString.equals(mDigits.getText().toString())){
						mShenduHistoricalThreadString = mDigits.getText().toString();
						
						mIsSearch = true;
					}else{
						mIsSearch = false;
					}
				}	
			}
		
//			mShenduTimeHandler.postDelayed(this, SEARCH_TIME_MILLIS);
		}
	};
	
	
	/** shutao 2012-10-29 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		// TODO Auto-generated method stub

		switch (item.getItemId()) {

		case SHENDU_SEND_SMS :
			Intent mIntent = new Intent(Intent.ACTION_SENDTO,Uri.fromParts("sms",mShenduMenuNumber, null));
    		startActivity( mIntent );
    		MyLog("DIAL_SEND_MMS");
			break;
		}
		return super.onContextItemSelected(item);
		
	}	
	
    /** shutao 2012-10-29 */
    private static final int SHENDU_ADD_BLEAKLIST = 0;
    private static final int SHENDU_SEND_SMS = 1 ;
	String mShenduMenuNumber = "";
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		// TODO Auto-generated method stub
    	   AdapterView.AdapterContextMenuInfo info;
           try {
                info = (AdapterView.AdapterContextMenuInfo) menuInfo;
           } catch (ClassCastException e) {
               Log.e(TAG, "bad menuInfo", e);
               return;
           }
//           Shendu_ContactItem 
        mShenduMenuNumber =((Shendu_ContactItem)mShenduContactAdapter.getItem(info.position)).number;
        String name = ((Shendu_ContactItem)mShenduContactAdapter.getItem(info.position)).name;
    	 menu.setHeaderTitle(name == null?mShenduMenuNumber:name);
        menu.add(0, SHENDU_SEND_SMS, 0, getResources().getString(R.string.shendu_send_sms));				
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	private void MyLog(String msg){
		if(DEBUG){
			Log.d(TAG, msg);
		}
	}

	
	//shutao 2013-1-21
	@Override
	public void onItemClick(int position) {
		// TODO Auto-generated method stub
		// setFormattedDigits(mT9Adapter.getItem(position).number,null);
		setFormattedDigits(((Shendu_ContactItem) mShenduContactAdapter.getItem(position)).number,
				null);
		dialButtonPressed();

		if (dialOnTap()) {
			dialButtonPressed();
		}
	}
}

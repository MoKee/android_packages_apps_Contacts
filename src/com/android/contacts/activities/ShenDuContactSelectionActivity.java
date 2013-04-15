package com.android.contacts.activities;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.ActionBar.LayoutParams;
import android.app.ActionBar.OnNavigationListener;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.list.ContactEntryListAdapter;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ShenduContactPickAdapter.MemberWithoutRawContactId;
import com.android.contacts.list.ShenduContactPickFragment;
import com.android.contacts.list.ShenduPhoneNumberPickAdapter.PhonePickMember;
import com.android.contacts.list.ShenduPhoneNumberPickFragment;
import com.android.contacts.list.ShenduPickFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * An activity for selection operating . For example add new members into
 * specified group.
 * 
 * @author Wang
 * @date 2012-9-2
 * @date 2013-1-7
 */
public class ShenDuContactSelectionActivity extends ContactsActivity
		implements
			OnNavigationListener,
			OnClickListener {
	public enum Option {
		Normal, SelectAll;
	}

	protected ContactEntryListFragment<?> mListFragment;
	private static final String EXCLUED_RAWCONTACTS_IDS_KEY = "exclued_ids";
	private View mSpinnerView;
	private TextView mSpinnerLine1View;
	private OptionsDropdownPopup mDropdown;
	private String[] mOptionsStringArray;
	private Option mOption = Option.Normal;
	private static final int SELECT_ALL_INDEX = 0;
	private static final int DESELECT_ALL_INDEX = 1;
	private OptionChangedListener mOptionChangedListener;
	private static final int SMS_PICK_PHONE_NUMBER_ACTION = 100;
	private static final int SMS_PICK_CONTACT_ACTION = 101;
	private int mPickMode = PICK_CONTACT_MODE;
	private static final int PICK_CONTACT_MODE = 1;
	private static final int PICK_PHONE_NUMBER_MODE = 2;
	private static final int PICK_GROUP_MEMBER_MODE = 3;

	@Override
	public void onAttachFragment(Fragment fragment) {
		if (fragment instanceof ContactEntryListFragment<?>) {
			mListFragment = (ContactEntryListFragment<?>) fragment;
			setupActionListener();
		}
	}

	public void setOptionChangedListener(OptionChangedListener listener) {
		this.mOptionChangedListener = listener;
	}

	private void setupActionListener() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setupOptionsStringArray();
		setContentView(R.layout.shendu_contact_pick);
		configureActionBar();
		resolveIntent();
		configureListFragment();
	}

	private void setupOptionsStringArray() {
		Resources res = getResources();
		mOptionsStringArray = new String[]{res.getString(R.string.select_all),
				res.getString(R.string.deselect_all)};

	}

	private void configureListFragment() {
		// Wang:
		ShenduPickFragment fragment = null;
		switch (mPickMode) {
			case PICK_PHONE_NUMBER_MODE :
				fragment = new ShenduPhoneNumberPickFragment();
				break;
            case PICK_GROUP_MEMBER_MODE :
            case PICK_CONTACT_MODE :
            	fragment = new ShenduContactPickFragment();
				break;
		}
		long[] ids = getIntent().getLongArrayExtra(EXCLUED_RAWCONTACTS_IDS_KEY);
		log(" ids ==>" + ids);
		fragment.setupExistedContactsIds(ids);
		mListFragment = fragment;

		getFragmentManager().beginTransaction()
				.replace(R.id.list_container, mListFragment)
				.commitAllowingStateLoss();
	}

	/**
	 * Resolve intent
	 * 
	 * @author Wang
	 * @date 2012-9-4
	 * @date 2013-1-7
	 */
	private void resolveIntent() {
		if (getIntent().getIntExtra("from", 0) == SMS_PICK_PHONE_NUMBER_ACTION) {// sms pick
			mPickMode = PICK_PHONE_NUMBER_MODE;
		}else if(getIntent().getIntExtra("from", 0) == SMS_PICK_CONTACT_ACTION){
			mPickMode = PICK_CONTACT_MODE;
		}else {
			mPickMode = PICK_GROUP_MEMBER_MODE;
		}
	}

	/**
	 * configure ActionBar with custom UI.
	 * 
	 * @author Wang
	 * @date 2012-9-3
	 */
	private void configureActionBar() {
		ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View customActionBarView = inflater.inflate(
					R.layout.shendu_contact_pick_actionbar, null);
			actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
					ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
							| ActionBar.DISPLAY_SHOW_TITLE);
			actionBar.setCustomView(customActionBarView);
			/* Spinner part */
			mSpinnerView = customActionBarView
					.findViewById(R.id.options_spinner);
			mSpinnerLine1View = (TextView) customActionBarView
					.findViewById(R.id.spinner_line_1);
			updateSelectedCountInSpinner(0);
			mDropdown = new OptionsDropdownPopup(this);
			mDropdown.setAdapter(new OptionAdapter(this));
			mSpinnerView.setOnClickListener(this);
			/* Done part */
			View doneItem = customActionBarView
					.findViewById(R.id.save_menu_item);
			doneItem.setOnClickListener(this);
			/* Cancel Part */
			View cancelItem = customActionBarView
					.findViewById(R.id.cancel_menu_item);
			cancelItem.setOnClickListener(this);
		}
	}

	/**
	 * Update text content of spinner
	 * 
	 * @author Wang
	 * @param selectedCount
	 *            Count of how many items has been selected.
	 * @date 2012-9-10
	 */
	public void updateSelectedCountInSpinner(int count) {
		if (mSpinnerLine1View == null) return;
		String text = String.format(
				getResources().getString(R.string.number_of_items_selected),
				count);
		mSpinnerLine1View.setText(text);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.options_spinner :
				mDropdown.show();
				break;
			case R.id.save_menu_item :
				Intent data = getIntent();
				switch (mPickMode) {
					case PICK_PHONE_NUMBER_MODE :
						/*-----phone num pick -----*/
						data.putExtra("data", getNewPhoneMembersNumbers());
						break;
					case PICK_CONTACT_MODE :
						/*-----contacts pick -----*/
						data.putExtra("data",  getNewMembersContactIdArray());
						break;
					case PICK_GROUP_MEMBER_MODE :
						/*-----group contact pick -----*/
						data.putExtra("data", getNewContactMembers());
						break;
				}
				setResult(0, data);
				this.finish();
				break;
			case R.id.cancel_menu_item :
				this.finish();
				break;

		}

	}

	/**
	 * Get New Contact Members
	 * 
	 * @author Wang
	 * @date 2013-1-5
	 */
	private ArrayList<MemberWithoutRawContactId> getNewContactMembers() {
		if (mListFragment != null
				&& mListFragment instanceof ShenduContactPickFragment) {
			ShenduContactPickFragment fragment = (ShenduContactPickFragment) mListFragment;
			Collection<Parcelable> clt = fragment.getNewMembers();
			ArrayList<MemberWithoutRawContactId> list = new ArrayList<MemberWithoutRawContactId>(
					clt.size());
			Iterator<Parcelable> it = clt.iterator();
			try {
				while (it.hasNext()) {
					list.add((MemberWithoutRawContactId) it.next());
				}
			} catch (ClassCastException e) {
				log("Err!!!ClassCastException");
				return null;
			}
			return list;
		}
		return null;
	}

	/**
	 * Get New Phone Members
	 * 
	 * @author Wang
	 * @date 2013-1-5
	 */
	private ArrayList<PhonePickMember> getNewPhoneMembers() {
		if (mListFragment != null
				&& mListFragment instanceof ShenduPhoneNumberPickFragment) {
			ShenduPhoneNumberPickFragment fragment = (ShenduPhoneNumberPickFragment) mListFragment;
			Collection<Parcelable> clt = fragment.getNewMembers();
			ArrayList<PhonePickMember> list = new ArrayList<PhonePickMember>(
					clt.size());
			Iterator<Parcelable> it = clt.iterator();
			try {
				while (it.hasNext()) {
					PhonePickMember member = (PhonePickMember) it.next();
					log("member=>" + member.getmPhoneNum() + " /"
							+ member.getDisplayName());
					list.add(member);
				}
			} catch (ClassCastException e) {
				log("Err!!!ClassCastException");
				return null;
			}
			return list;
		}
		return null;
	}
	
	/**
	 * Get Phone numbers Array of New  Members
	 * 
	 * @author Wang
	 * @date 2013-1-7
	 */
	private String[] getNewPhoneMembersNumbers() {
		if (mListFragment != null
				&& mListFragment instanceof ShenduPhoneNumberPickFragment) {
			ShenduPhoneNumberPickFragment fragment = (ShenduPhoneNumberPickFragment) mListFragment;
			Collection<Parcelable> clt = fragment.getNewMembers();
			String[] array  = new String[clt.size()];
			Iterator<Parcelable> it = clt.iterator();
			try {
				int i = 0;
				while (it.hasNext()) {
					PhonePickMember member = (PhonePickMember) it.next();
					log("member=>" + member.getmPhoneNum() + " /"
							+ member.getDisplayName());
					array[i] =  member.getmPhoneNum() ;
					i++;
				}
			} catch (ClassCastException e) {
				log("Err!!!ClassCastException");
				return null;
			}
			return array;
		}
		return null;
	}

	/**
	 * Get ContactId Array of New Members
	 * 
	 * @author Wang
	 * @date 2012-9-18
	 * @date 2012-1-7
	 */
	private long[] getNewMembersContactIdArray() {
		if (mListFragment != null
				&& mListFragment instanceof ShenduContactPickFragment) {
			ShenduContactPickFragment fragment = (ShenduContactPickFragment) mListFragment;
			Collection<Parcelable> clt = fragment.getNewMembers();
			long[] array = new long[clt.size()];
			Iterator<Parcelable> it = clt.iterator();
			int i = 0;
			try {
				while (it.hasNext()) {
					array[i] = ((MemberWithoutRawContactId) it.next())
							.getContactId();
					i++;
				}
			} catch (ClassCastException e) {
				log("Err!!!ClassCastException");
				return null;
			}
			return array;
		}
		return null;
	}

	/**
	 * Change Option State
	 * 
	 * @author Wang
	 * @param state
	 *            The state will be changed.
	 * @date 2012-9-10
	 */
	public void changeOptionState(Option state) {
		if (state == null) return;
		mOption = state;
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		// TODO Auto-generated method stub
		return false;
	}

	// Based on Spinner.DropdownPopup
	private class OptionsDropdownPopup extends ListPopupWindow {
		public OptionsDropdownPopup(Context context) {
			super(context);
			setAnchorView(mSpinnerView);
			setModal(true);
			setPromptPosition(POSITION_PROMPT_ABOVE);
			setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View v,
						int position, long id) {
					switch (mOption) {
						case Normal :
							mOption = Option.SelectAll;
							if (mOptionChangedListener != null)
								mOptionChangedListener.onOptionChanged(mOption);
							break;

						case SelectAll :
							mOption = Option.Normal;
							if (mOptionChangedListener != null)
								mOptionChangedListener.onOptionChanged(mOption);
							break;
					}
					dismiss();
				}
			});
		}

		@Override
		public void show() {
			setWidth(getResources().getDimensionPixelSize(
					R.dimen.shendu_custom_bar_spinner_width));
			setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
			super.show();
			// List view is instantiated in super.show(), so we need to do this
			// after...
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		}
	}

	private class OptionAdapter extends BaseAdapter {

		private Context mContext;

		public OptionAdapter(Context ctx) {
			mContext = ctx;
		}

		@Override
		public int getCount() {
			if (mOptionsStringArray == null) return 0;
			return 1;
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = LayoutInflater.from(mContext).inflate(
					R.layout.shendu_actionbar_drop_text, null);
			final TextView spinnerText = (TextView) v.findViewById(R.id.text1);
			switch (mOption) {
				case Normal :
					spinnerText.setText(mOptionsStringArray[SELECT_ALL_INDEX]);
					break;
				case SelectAll :
					spinnerText
							.setText(mOptionsStringArray[DESELECT_ALL_INDEX]);
					break;
			}
			return spinnerText;
		}

	}

	public static interface OptionChangedListener {
		public void onOptionChanged(Option op);
	}

	private static final boolean debug = false;

	private static void log(String msg) {
		msg = "ShenduSelectionAct  --> " + msg;
		if (debug) Log.i("shenduGroup", msg);
	}

}

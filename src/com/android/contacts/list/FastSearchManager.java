package com.android.contacts.list;

import android.R.integer;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.AvoidXfermode;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.renderscript.Program.TextureType;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.NumberKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.activities.ActionBarAdapter;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.activities.PeopleActivity.OnMenuItemVisibleChangedListener;
import com.android.contacts.list.ContactListAdapter;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DefaultContactListAdapter;
import com.android.contacts.list.ContactEntryListAdapter.onCursorChangedListener;
import com.android.contacts.util.NameAvatarUtils;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactSaveService.OnDeletedListener;
import com.android.contacts.R;

public class FastSearchManager
		implements
			View.OnClickListener,
			onCursorChangedListener, OnMenuItemVisibleChangedListener,OnLongClickListener, OnDeletedListener {
	private static final char[] ACCEPTED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			.toCharArray();
	private Context mContext;
	private static FastSearchManager mInstance;
	private ActionBarAdapter mActionBarAdapter;
	private DefaultContactBrowseListFragment mAllFragment;
	private ContactListAdapter mListAdapter;
	private static final String STATE_PREFERENCE = "padStatePreference";
	private static final String STATE_KEY = "padState";
	private boolean mOpenState = false;

	private static final int[] buttonIds = {R.id.fast_a, R.id.fast_b,
			R.id.fast_c, R.id.fast_d, R.id.fast_e, R.id.fast_f, R.id.fast_g,
			R.id.fast_h, R.id.fast_i, R.id.fast_j, R.id.fast_k, R.id.fast_l,
			R.id.fast_m, R.id.fast_n, R.id.fast_o, R.id.fast_p, R.id.fast_q,
			R.id.fast_r, R.id.fast_s, R.id.fast_t, R.id.fast_u, R.id.fast_v,
			R.id.fast_w, R.id.fast_x, R.id.fast_y, R.id.fast_z};
	private static final String[] FILTER_PROJECTION_PRIMARY = new String[]{
			Contacts._ID, // 0
			Contacts.DISPLAY_NAME_PRIMARY, // 1
			Contacts.CONTACT_PRESENCE, // 2
			Contacts.CONTACT_STATUS, // 3
			Contacts.PHOTO_ID, // 4
			Contacts.PHOTO_THUMBNAIL_URI, // 5
			Contacts.LOOKUP_KEY, // 6
			Contacts.IS_USER_PROFILE, // 7
			Contacts.HAS_PHONE_NUMBER, // 8 Wang
			SearchSnippetColumns.SNIPPET, // 9 Wang
			Contacts.SORT_KEY_ALTERNATIVE,// 10
	};

	private static final int INDEX_SORT_KEY = 10;
	private EditText mDisplay;
	private View mSearchPad;
	private QueryHandler mQueryHandler;
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private boolean[] exsits = new boolean[26];

	private FastSearchManager(Context ctx) {
		mContext = ctx;
		if (ctx != null) mQueryHandler = new QueryHandler(ctx);
		resetExsits();
	}

	public static FastSearchManager getInstance(Context ctx) {
		if (mInstance == null) {
			mInstance = new FastSearchManager(ctx);
		}
		try{
			PeopleActivity activity = (PeopleActivity)ctx;
			activity.setOnMenuItemVisibleChangedListener(mInstance);
		}catch(ClassCastException e){
			
		}
		return mInstance;
	}

	public void bindAdapter(ContactListAdapter adp) {
		// Log.i("1616", "FSM=>bindAdapter");
		mListAdapter = adp;
		if (mListAdapter != null) {
			mListAdapter.setOnCursorChangedListener(this);
		}
		if (mQueryHandler != null) {
			mQueryHandler.setAdapter(adp);
		}
	}

	public void bindActionBarAdapter(ActionBarAdapter adp) {
		mActionBarAdapter = adp;
	}

	public void bindFragment(DefaultContactBrowseListFragment fragment) {
		mAllFragment = fragment;
	}

	public void setSearchPad(View searchPad) {
		if (searchPad == mSearchPad) {
			return;
		}
		mSearchPad = searchPad;
		configSearchPad();
	}

	public void onStart() {
		boolean isOpened = detectPadSavedState(mContext);
		mOpenState = isOpened;
		mSearchPad.setVisibility(isOpened ? View.VISIBLE : View.GONE);
		if (mDisplay != null) {
			mDisplay.getText().clear();
			resetExsits();
		}
		ContactSaveService.setOnDeletedListener(this);
	}

	private void resetExsits() {
		int size = exsits.length;
		for (int i = 0; i < size; i++) {
			exsits[i] = false;
		}
	}

	private void configSearchPad() {
		mDisplay = (EditText) mSearchPad.findViewById(R.id.display);
		mDisplay.setInputType(InputType.TYPE_NULL);
		mDisplay.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				startQuery(s.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				log("beforeTextChanged:" + s + "/ count=" + count);
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (TextUtils.isEmpty(s)) {
					mAllFragment.setEnabled(true);
					mActionBarAdapter.changeSearchMode(false);
				}
			}
		});

		Button delete = (Button) mSearchPad.findViewById(R.id.delete);
		delete.setOnClickListener(this);
		delete.setOnLongClickListener(this);

		// set button click listener
		for (int id : buttonIds) {
			Button btn = (Button) mSearchPad.findViewById(id);
			btn.setOnClickListener(this);
		}
		//do not used,remove by hhl
		//ImageButton search_ex  = (ImageButton) mSearchPad.findViewById(R.id.search_ex);
		//search_ex.setOnClickListener(this);
	}

	public void displaySearchPad() {
		if(mSearchPad == null)  return;
		mSearchPad.setVisibility(View.VISIBLE);
	}
	
	public void hideSearchPad() {
		if(!isSearchPadVailable()) return;
		mSearchPad.setVisibility(View.GONE);
	}
	
	public void toggleSearchPad(){
		if(mSearchPad == null) return;
		if(mSearchPad.getVisibility() == View.VISIBLE){
			hideSearchPad();
			mOpenState = false;
		}else{
			displaySearchPad();
			mOpenState = true;
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.delete) {
			delete();
			return;
		}
		if (v instanceof Button) {
			String delta = ((Button) v).getText().toString();
			int cursor = mDisplay.getSelectionStart();
			if (mDisplay.getText() == null) {
				mDisplay.setText(delta);
			} else {
				mDisplay.getText().insert(cursor, delta);
			}
			return;
		}
		/*if(id == R.id.search_ex){
			mActionBarAdapter.changeSearchMode(true);
			return;
		}*/
	}

	private void delete() {
		mDisplay.dispatchKeyEvent(new KeyEvent(0, KeyEvent.KEYCODE_DEL));
	}

	public void startQuery(String delta) {
		if (!TextUtils.isEmpty(delta) && mQueryHandler != null) {
			mAllFragment.showEmptyUserProfile(false);
			mAllFragment.setEnabled(false);
			mQueryHandler.startQuery(delta);
		}
	}

	private boolean  isSearchPadVailable() {
		if(mSearchPad == null) return false;
		if(mSearchPad.getVisibility() == View.VISIBLE) return true;
		return false;
	}

	private class QueryHandler extends AsyncQueryHandler {
		private ContactListAdapter mAdapter;
		public static final char SNIPPET_START_MATCH = '\u0001';
		public static final char SNIPPET_END_MATCH = '\u0001';
		public static final String SNIPPET_ELLIPSIS = "\u2026";
		public static final int SNIPPET_MAX_TOKENS = 5;
		public static final String SNIPPET_ARGS = SNIPPET_START_MATCH + ","
				+ SNIPPET_END_MATCH + "," + SNIPPET_ELLIPSIS + ","
				+ SNIPPET_MAX_TOKENS;

		public QueryHandler(Context ctx) {
			super(ctx.getContentResolver());
		}

		public void setAdapter(ContactListAdapter adp) {
			if (mAdapter == adp) return;
			mAdapter = adp;
		}

		public ContactListAdapter getAdapter() {
			return mAdapter;
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
			super.onQueryComplete(token, cookie, cursor);
			if (mAdapter != null) {
				mAdapter.changeCursor(0, cursor);
			}
		}

		private Uri buildUri(String query) {
			long directoryId = Directory.DEFAULT;
			Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
			builder.appendPath(query); // Builder will encode the query
			builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
					String.valueOf(directoryId));
			builder.appendQueryParameter(
					SearchSnippetColumns.SNIPPET_ARGS_PARAM_KEY, SNIPPET_ARGS);
			builder.appendQueryParameter(
					SearchSnippetColumns.DEFERRED_SNIPPETING_KEY, "1");
			return builder.build();
		}

		protected final String[] getProjection() {
			return FILTER_PROJECTION_PRIMARY;
		}

		private String getDisplaySort() {
			return Contacts.SORT_KEY_PRIMARY;
		}

		public void startQuery(String delta) {
			startQuery(0, null, buildUri(delta), getProjection(), null, null,
					getDisplaySort());
		}

	}

	@Override
	public void onCurosrChanged(ContactEntryListAdapter adapter, Cursor cursor) {
		resetExsits();
		if (mDisplay != null && TextUtils.isEmpty(mDisplay.getText())) {
			String[] sections = (String[]) adapter.getSections();
			if (sections == null) return;
			int size = sections.length;
			for (int i = 0; i < size; i++) {
				String s = sections[i];
				int idx = ALPHABET.indexOf(s);
				if (idx >= 0) {
					exsits[idx] = true;
				}
			}
		} else {
			String display = mDisplay.getText().toString();
			try {
				int index = cursor
						.getColumnIndexOrThrow(Contacts.SORT_KEY_ALTERNATIVE);
				int i = 0;
				if (cursor.moveToFirst()) {
					do {
						String sortKey = cursor.getString(index);
						processSortkey(sortKey, display);
						i++;
					} while (cursor.moveToNext());
				}
			} catch (IllegalArgumentException e) {
				log( "===Err====>");
			}
		}
		updateSearchPad();
	}

	private void processSortkey(String sortKey, final String display) {
//		Log.i("1616", "display =" + display);
		String[] keys = sortKey.split(" ");
		StringBuilder fullName = new StringBuilder();
		StringBuilder fastKey = new StringBuilder();
		for (int i = 0; i < keys.length; i++) {
			String cn = NameAvatarUtils.containsChinese(keys[i]);
			if (!TextUtils.isEmpty(cn)) continue;
			fullName.append(keys[i]);
			fastKey.append(keys[i].charAt(0));
		}
		final int length = display.length();
		int index = fullName.toString().indexOf(display);
//		Log.i("1616", "fullName index=" + index);
		if (index != -1 && (index + length) < fullName.length()) {
			char c = fullName.charAt(index + length);
			int idx = ALPHABET.indexOf(c);
			if (idx >= 0) {
				exsits[idx] = true;
			}
		}
		index = fastKey.toString().indexOf(display);
		// Log.i("1616", "fastKey index=" + index);
		if (index != -1 && (index + length) < fastKey.length()) {
			char c = fastKey.charAt(index + length);
			int idx = ALPHABET.indexOf(c);
			if (idx >= 0) {
				exsits[idx] = true;
			}
		}
	}

	public void updateSearchPad() {
		if (mSearchPad == null) return;
		int size = exsits.length;
		for (int i = 0; i < size; i++) {
			boolean ex = exsits[i];
			int id = buttonIds[i];
			Button btn = (Button) mSearchPad.findViewById(id);
			btn.setEnabled(ex);
		}
	}

	@Override
	public void onMenuItemVisibleChanged(MenuItem item) {
		if(!mOpenState) return;
		boolean visible = item.isVisible();
		log("onMenuItemVisibleChanged =>"+visible);
		if(visible){
			displaySearchPad();
		}else{
			hideSearchPad();
		}
	}

	@Override
	public boolean onLongClick(View v) {
		switch (v.getId()) {
			case R.id.delete :
				if(mDisplay != null && mDisplay.getText() != null){
					mDisplay.getText().clear();
				}
				return true;

			default :
				break;
		}
		return false;
	}
	
	/**
	 * 
	 * @return Returns true when fast search pad is opened;
	 * */
	public boolean detectPadSavedState(Context ctx){
		return ctx.getSharedPreferences(STATE_PREFERENCE, Context.MODE_PRIVATE).getBoolean(STATE_KEY, true);
	}
	
	/**
	 * 
	 * @return Returns true if state save successfully;
	 * */
	private boolean updatePadSavedState(Context ctx, boolean isOpen){
		if(ctx == null) return false;
		Editor edit = ctx.getSharedPreferences(STATE_PREFERENCE, Context.MODE_PRIVATE).edit();
		edit.putBoolean(STATE_KEY, isOpen);
		return edit.commit();
	}
	
	public void saveSate(){
		updatePadSavedState(mContext,mOpenState);
	}

	@Override
	public void onDeleted() {
		// TODO update views
		if(isSearchPadVailable() && mDisplay != null && mDisplay.getText() != null){
			String delta = mDisplay.getText().toString();
			startQuery(delta);
		}
	}
	
	private static boolean debug = false;
	public static void log(String msg){
		if(debug) Log.i("1616", msg);
	}

}

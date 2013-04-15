/*
 * Copyright (C) 2011 The CyanogenMod Project
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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import android.mokee.location.PhoneLocation;

/**
 * @author shade, Danesh, pawitp
 */
class T9Search {

    // List sort modes
    private static final int NAME_FIRST = 1;
    private static final int NUMBER_FIRST = 2;

    // Phone number queries
    private static final String[] PHONE_PROJECTION = new String[] {Phone.NUMBER, Phone.CONTACT_ID, Phone.IS_SUPER_PRIMARY, Phone.TYPE, Phone.LABEL};
    private static final String PHONE_ID_SELECTION = Contacts.Data.MIMETYPE + " = ? ";
    private static final String[] PHONE_ID_SELECTION_ARGS = new String[] {Phone.CONTENT_ITEM_TYPE};
    private static final String PHONE_SORT = Phone.CONTACT_ID + " ASC";
    private static final String[] CONTACT_PROJECTION = new String[] {Contacts._ID, Contacts.DISPLAY_NAME, Contacts.TIMES_CONTACTED, Contacts.PHOTO_THUMBNAIL_URI ,Contacts.SORT_KEY_PRIMARY};
    private static final String CONTACT_QUERY = Contacts.HAS_PHONE_NUMBER + " > 0";
    private static final String CONTACT_SORT = Contacts._ID + " ASC";

    // Local variables
    private Context mContext;
    private int mSortMode;
    private ArrayList<ContactItem> mNameResults = new ArrayList<ContactItem>();
    private ArrayList<ContactItem> mNumberResults = new ArrayList<ContactItem>();
    private ArrayList<ContactItem> mPinyinResults = new ArrayList<ContactItem>();
    
    private Set<ContactItem> mAllResults = new LinkedHashSet<ContactItem>();
    private ArrayList<ContactItem> mContacts = new ArrayList<ContactItem>();
    private String mPrevInput;
    private static char[][] sT9Map;
    
    /** shutao 2012-10-12*/
    private static final String TAG = "T9Search";

    // Turn on this flag when we want to check internal data structure.
    private static final boolean DEBUG = true;
    

    public T9Search(Context context) {
        mContext = context;
        getAll();
    }

    private void getAll() {
    	long itime= System.currentTimeMillis();
    	if (sT9Map == null)
            initT9Map();

//        NameToNumber normalizer = NameToNumberFactory.create(mContext, sT9Chars, sT9Digits);

        Cursor contact = mContext.getContentResolver().query(Contacts.CONTENT_URI, CONTACT_PROJECTION, CONTACT_QUERY, null, CONTACT_SORT);
        Cursor phone = mContext.getContentResolver().query(Phone.CONTENT_URI, PHONE_PROJECTION, PHONE_ID_SELECTION, PHONE_ID_SELECTION_ARGS, PHONE_SORT);
        phone.moveToFirst();

        while (contact.moveToNext()) {
            long contactId = contact.getLong(0);
            if (phone.isAfterLast()) {
                break;
            }
            while (phone.getLong(1) == contactId) {
                String num = phone.getString(0);
                ContactItem contactInfo = new ContactItem();
                contactInfo.id = contactId;
                contactInfo.name = contact.getString(1);
                contactInfo.number = /*PhoneNumberUtils.formatNumber(*/num/*)*/;
                contactInfo.normalNumber = removeNonDigits(num);
                nameToPinYinAndNumber(contact.getString(4),contactInfo);
//                contactInfo.normalName = normalizer.convert(contact.getString(1));
//                MyLog(contactInfo.normalName);
                contactInfo.timesContacted = contact.getInt(2);
                contactInfo.isSuperPrimary = phone.getInt(2) > 0;
                contactInfo.groupType = Phone.getTypeLabel(mContext.getResources(), phone.getInt(3), phone.getString(4));
                if (!contact.isNull(3)) {
                    contactInfo.photo = Uri.parse(contact.getString(3));
                }
                mContacts.add(contactInfo);
                if (!phone.moveToNext()) {
                    break;
                }
            }
        }
        contact.close();
        phone.close();
        MyLog("system -- time=== " +(System.currentTimeMillis() - itime));
    }

	private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);
    private static final String FIRST_PINYIN_UNIHAN = "\u963F";
    private static final String LAST_PINYIN_UNIHAN = "\u84D9";
    private static final char FIRST_UNIHAN = '\u3400';
    /** shutao 2012-10-19*/
    public void nameToPinYinAndNumber(String name , ContactItem contactInfo){
    	 ArrayList<Integer> firstNumberIndexs = new ArrayList<Integer>();
     	 int nameLength = name.length();
    	 final StringBuilder sb = new StringBuilder();
    	 final StringBuilder sbNumber = new StringBuilder();
    	 final StringBuilder sbFirst = new StringBuilder();
    	 boolean [] isindex = new boolean[contactInfo.name.length()];
//    	 MyLog("contactInfo . name size = "+contactInfo.name.length());
    	 boolean isFirst = true;
    	 int cmp;
    	 int hanziNum = 0;
    	 for (int i = 0; i < nameLength; i++) {
    	
    		 
    		 final char character = name.charAt(i);
    		 final String letter = Character.toString(character);
    		 cmp = COLLATOR.compare(letter, FIRST_PINYIN_UNIHAN);
    		
    		 if(character == ' '){

    			 isFirst = true;
    		 }else if (character < 256) {
    			 
    			 if(character>=65 && character <=90 || character >= 97 && character <= 122){
//    				 log("char == "+character);
    				 char num = LetterToNumber(character);
    				 sbNumber.append(num);
    				 if(!isFirst){
    					 hanziNum++;
        				 sb.append((char)(character<97?(character+32):character));
    				 }else{
    					 hanziNum++;
//    					 isBoolean.add(true);
    					 firstNumberIndexs.add(hanziNum);
    					 sb.append(character);
    					 sbFirst.append(num);
    					 isFirst = false;
    				
    				 }
    				 
    			 }else if (character>=48 && character <=57){
    				 sbFirst.append(character);
    				 sb.append(character);
    				 sbNumber.append(character);
    				 hanziNum ++ ;
    			 }
    			 else{
    				 isFirst = false;
    			 }
    		 }else if(character<FIRST_UNIHAN){
    			 hanziNum ++ ;
    		 }else if(cmp < 0){
    			 hanziNum ++ ;
    		 }else{
    			 cmp = COLLATOR.compare(letter, LAST_PINYIN_UNIHAN);
    			 if(cmp >0){;
    				 hanziNum ++ ;
    			 }
    		 }
    		
    	 }

    	 contactInfo.pinYin = sb.toString();
    	 contactInfo.normalName = sbNumber.toString();
//    	 MyLog("sbNumber == " +sbNumber.toString());
    	 contactInfo.firstNumber = sbFirst.toString();
    	 contactInfo.firstNumberIndexs = firstNumberIndexs;
    	
    }
    /**shutao 2012-10-19*/
    private static char LetterToNumber(char letter) {
    	letter=Character.toLowerCase(letter);
        char num = letter;
            boolean matched = false;
            for (char[] row : sT9Map) {
                for (char a : row) {
                    if (letter == a) {
                        matched = true;
                        num=row[0];
                        break;
                    }
                }
                if (matched) {
                    break;
                }
            }
        return num;
    }


    
    public static class T9SearchResult {

        private final ArrayList<ContactItem> mResults;
        private ContactItem mTopContact = new ContactItem();

        public T9SearchResult (final ArrayList<ContactItem> results, final Context mContext) {
            mTopContact = results.get(0);
            mResults = results;
            mResults.remove(0);
        }

        public int getNumResults() {
        	/**shutao  2012-10-15*/
            return mResults.size() /*+ 1*/;
        }

        public ContactItem getTopContact() {
            return mTopContact;
        }

        public ArrayList<ContactItem> getResults() {
            return mResults;
        }
    }

    public static class ContactItem {
        Uri photo;
        String name;
        String number;
        String normalNumber;
        String normalName;
        String pinYin;
        String firstNumber;
        int timesContacted;
        int nameMatchId;
        int pinyinMatchId;
        int numberMatchId;
        CharSequence groupType;
        ArrayList<Integer> firstNumberIndexs;
        long id;
        boolean isSuperPrimary;
    }

    public T9SearchResult search(String number) {
    	long itime = System.currentTimeMillis();
        mNameResults.clear();
        mNumberResults.clear();
        number = removeNonDigits(number);
        int pos = 0;
        mSortMode = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(mContext).getString("t9_sort", "1"));
        boolean newQuery = mPrevInput == null || number.length() <= mPrevInput.length();
        // Go through each contact
        for (ContactItem item : (newQuery ? mContacts : mAllResults)) {
            item.numberMatchId = -1;
            item.nameMatchId = -1;
            pos = item.normalNumber.indexOf(number);
            if (pos != -1) {
                item.numberMatchId = pos;
                mNumberResults.add(item);
            }
            pos = item.normalName.indexOf(number);
            if (pos != -1) {
                int last_space = item.normalName.lastIndexOf("0", pos);
                if (last_space == -1) {
                    last_space = 0;
                }
                item.nameMatchId = pos - last_space;
                mNameResults.add(item);
            }
        }
        mAllResults.clear();
        mPrevInput = number;
        Collections.sort(mNumberResults, new NumberComparator());
        Collections.sort(mNameResults, new NameComparator());
//        Collections.sort(mPinyinResults, new PinyinComparator());
        MyLog("search -- time=== " +(System.currentTimeMillis() - itime));
        if (mNameResults.size() > 0 || mNumberResults.size() > 0 /*|| mPinyinResults.size() > 0*/) {
            switch (mSortMode) {
            case NAME_FIRST:
                mAllResults.addAll(mNameResults);
                mAllResults.addAll(mNumberResults);
                break;
            case NUMBER_FIRST:
                mAllResults.addAll(mNumberResults);
                mAllResults.addAll(mNameResults);
            }
            MyLog("search -- time=== " +mNameResults.size());
            return new T9SearchResult(new ArrayList<ContactItem>(mAllResults), mContext);
        }
        return null;
    }
    

    
    
    private void myLastIndexOf(String number , String inputNumber){
    	char[] numbers = number.toCharArray();
    	char[] inputs = inputNumber.toCharArray();
    	boolean isMatch = false;
    	ArrayList<Boolean> indexBoolean  = new ArrayList<Boolean>();
    	int inputIndex = 0;
    	for(int numberIndex = 0 ; numberIndex<numbers.length || inputIndex<inputNumber.length(); numberIndex++){
    		if(inputs[inputIndex] == numbers[numberIndex]){
    			indexBoolean.add(true);
    			inputIndex++;
    		}else{
    			indexBoolean.add(false);
    		}
    	}
    	if(inputIndex == inputNumber.length()){
    		isMatch = true;
    	}
    }

    public static class NameComparator implements Comparator<ContactItem> {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret = Integer.compare(lhs.nameMatchId, rhs.nameMatchId);
            if (ret == 0) ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret == 0) ret = Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
            return ret;
        }
    }

    public static class NumberComparator implements Comparator<ContactItem> {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret = Integer.compare(lhs.numberMatchId, rhs.numberMatchId);
            if (ret == 0) ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret == 0) ret = Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
            return ret;
        }
    }

    /**shutao 2012-10-19*/
	private synchronized void initT9Map() {
		String[] t9Array = mContext.getResources().getStringArray(
				R.array.t9_map);
		sT9Map = new char[t9Array.length][];
		int rc = 0;
		for (String item : t9Array) {
			int cc = 0;
			sT9Map[rc] = new char[item.length()];
			for (char ch : item.toCharArray()) {
				sT9Map[rc][cc] = ch;
				cc++;
			}
			rc++;
		}
	}

    public static String removeNonDigits(String number) {
        int len = number.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = number.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '*' || ch == '#' || ch == '+') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    protected class T9Adapter extends ArrayAdapter<ContactItem> {

        private ArrayList<ContactItem> mItems;
        private LayoutInflater mMenuInflate;
        private ContactPhotoManager mPhotoLoader;

        public T9Adapter(Context context, int textViewResourceId, ArrayList<ContactItem> items, LayoutInflater menuInflate, ContactPhotoManager photoLoader) {
            super(context, textViewResourceId, items);
            mItems = items;
            mMenuInflate = menuInflate;
            mPhotoLoader = photoLoader;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mMenuInflate.inflate(R.layout.shendu_row, null);
                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.rowName);
                holder.number = (TextView) convertView.findViewById(R.id.rowNumber);
                holder.icon = (QuickContactBadge) convertView.findViewById(R.id.rowBadge);
                holder.attribution = (TextView) convertView.findViewById(R.id.shendu_row_attribution);
                holder.pinYin = (TextView) convertView.findViewById(R.id.rowPinyin);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            ContactItem o = mItems.get(position);
            if (o.name == null) {
                holder.name.setText(mContext.getResources().getString(R.string.t9_add_to_contacts));
                holder.number.setVisibility(View.GONE);
                holder.icon.setImageResource(R.drawable.ic_menu_add_field_holo_light);
                holder.icon.assignContactFromPhone(o.number, true);
            } else {
                /**shutao 2012-10-19*/
                holder.pinYin.setText(o.pinYin ,TextView.BufferType.SPANNABLE);
                
                holder.name.setText(o.name, TextView.BufferType.SPANNABLE);
                holder.number.setText(o.normalNumber /*+ " (" + o.groupType + ")"*/, TextView.BufferType.SPANNABLE);
                holder.number.setVisibility(View.VISIBLE);
                if (o.nameMatchId != -1) {
                 	Spannable sPinyin = (Spannable) holder.pinYin.getText();
                  int nameStart = o.nameMatchId;
                  int sLeng = sPinyin.length();
             	    int send = (mPrevInput.length()+nameStart) >= sLeng ?sLeng:(mPrevInput.length()+nameStart);
                    for(int index = nameStart ; index< o.firstNumberIndexs.size();index++){
                    	if(index-nameStart == send){
                    		break;
                    	}
                 	   int num =o.firstNumberIndexs.get(index);
                 	  sPinyin.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
             				   num-1, num , Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                    holder.pinYin.setText(sPinyin);
                }else
                if (o.numberMatchId != -1) {
                    Spannable s = (Spannable) holder.number.getText();
                    int numberStart = o.numberMatchId;
                    s.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
                            numberStart, numberStart + mPrevInput.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    holder.number.setText(s);
                }else 
                	if(o.pinyinMatchId != -1){
                	Spannable sPinyin = (Spannable) holder.pinYin.getText();
                	int numberStart = o.pinyinMatchId;
                	sPinyin.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
                            numberStart, numberStart + mPrevInput.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                	holder.pinYin.setText(sPinyin);
                }
                if (o.photo != null)
                    mPhotoLoader.loadDirectoryPhoto(holder.icon, o.photo, true);
                else
                    holder.icon.setImageResource(ContactPhotoManager.getDefaultAvatarResId(false, true));
                holder.icon.assignContactFromPhone(o.number, true);
            }
        
         	String city = PhoneLocation.getCityFromPhone(o.number, mContext);
    		if(city != null){
    			holder.attribution.setText(city);
    		}else{
    			holder.attribution.setText("");
    		}
            return convertView;
        }

        class ViewHolder {
            TextView name;
            TextView number;
            QuickContactBadge icon;
            TextView attribution;
            TextView pinYin;
        }

    }
    
    
    /**shutao 2012-10-11*/
    public void MyLog(String msg){
    	if(DEBUG){
    		Log.d(TAG, msg);
    	}
    }

}

package com.android.contacts.dialpad;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.w3c.dom.Text;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.CallLog;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.dialpad.FirstNumberInfo.NodeShendu_ContactItem;
import android.mokee.location.PhoneLocation;

/**
 * @Time 2012-9-14 
 * @author shutao shutao@shendu.com
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * Filterable Asynchronous parse Search data function
 */
public class ShenduContactAdapter extends BaseAdapter implements Filterable {
	
	private LayoutInflater mInflater;
	//Save the current search contact
	private ArrayList<Shendu_ContactItem> mContactinfoList = new ArrayList<Shendu_ContactItem>();

	private ArrayList<Shendu_ContactItem> mInfoList = new ArrayList<ShenduContactAdapter.Shendu_ContactItem>();
	

    private ContactPhotoManager mPhotoLoader;
	
	private Context mContext;
	
    private static char[][] sT9Map;
    
    private SearchContactsListener mContactsListener;
    
    private Thread createDataThead;
    
    private ContactsItemOnClickListener mOnClickListener;
    
    /** shutao 2012-9-14  */
    public interface ContactsItemOnClickListener{
    	public void onItemClick(int position);
    }
    
    public void setContactsItemOnClickListener(ContactsItemOnClickListener clickListener){
    	this.mOnClickListener = clickListener;
    }
//    private NameSearchTree nameSearchTree;
    /**shutao 2012-10-30*/
    private FirstNumberInfo mFirstNumberInfo;
    
    /**shutao 2012-10-26*/
    private Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
//			super.handleMessage(msg);
			Results results = (Results)msg.obj;
			if(results.constraint.equals(mPrevInput)){
			
				/**shutao 2012-10-26*/
				mInfoList.clear();
				mInfoList = (ArrayList<Shendu_ContactItem>) results.values;
				if (mInfoList!=null && mInfoList.size() > 0) {
					notifyDataSetChanged();
					mContactsListener.Contacts();
				} else {
					mContactsListener.notContacts();
				}
			}
		
		}
    	
    };
    
    class Results {
    	ArrayList<Shendu_ContactItem> values;
    	String  constraint;
    	int count;
    }
    //shutao 2013-1-22
    private static final int SHENDU_SEND_SMS = 0 ;
    private final View.OnClickListener mClickListener = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			mOnClickListener.onItemClick(Integer.parseInt(v.getTag(R.id.shendu_tag_position).toString()));
		}
	};
    private final View.OnLongClickListener mPrimaryActionLongListener = new View.OnLongClickListener() {
    	@Override
		public boolean onLongClick(View v) {
			// TODO Auto-generated method stub
    		if(v.getTag(R.id.shendu_tag_second) == null){
				return false;
			}
			final String number = v.getTag(R.id.shendu_tag_second).toString();
			final String name = v.getTag(R.id.shendu_tag_name).toString();
		
			Builder sd=new AlertDialog .Builder(mContext).setTitle(name).
			setItems(R.array.shendu_onlongmenu_list, 
           new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// TODO Auto-generated method stub
					switch (which) {

					case SHENDU_SEND_SMS:
						Intent mIntent = new Intent(Intent.ACTION_SENDTO,Uri.fromParts("sms", number, null));
			    		mContext.startActivity( mIntent );
						break;

					}
				}
			});
			sd.create().show();
			return true;
    	}
    };
    
    
    /** shutao 2012-9-14  */
    public interface SearchContactsListener{
    	public void notContacts();
    	public void Contacts();
    }
	
    // Phone number queries
    private static final String[] PHONE_PROJECTION = new String[] {Phone.NUMBER, Phone.CONTACT_ID, Phone.IS_SUPER_PRIMARY, Phone.TYPE, Phone.LABEL};
    private static final String PHONE_ID_SELECTION = Contacts.Data.MIMETYPE + " = ? ";
    private static final String[] PHONE_ID_SELECTION_ARGS = new String[] {Phone.CONTENT_ITEM_TYPE};
    private static final String PHONE_SORT = Phone.CONTACT_ID + " ASC";
    private static final String[] CONTACT_PROJECTION = new String[] {Contacts._ID, Contacts.DISPLAY_NAME, Contacts.TIMES_CONTACTED, Contacts.PHOTO_THUMBNAIL_URI
    	,Contacts.SORT_KEY_PRIMARY};
    private static final String CONTACT_QUERY = Contacts.HAS_PHONE_NUMBER + " > 0";
    private static final String CONTACT_SORT = Contacts._ID + " ASC";
	
	public ShenduContactAdapter(Context context) {
		mInflater = LayoutInflater.from(context);
		mContext = context;
	    mPhotoLoader = ContactPhotoManager.getInstance(context);
       mPhotoLoader.preloadPhotosInBackground();
//		nameSearchTree = new NameSearchTree();
		mFirstNumberInfo = new FirstNumberInfo();
//		getAll();
//		initSomeList();
	
	}

	private final int MAXNUMS = 100000;
	
	/**SHUTAO 2012-10-16*/
	public  void search(final String s, final boolean isAll){
		
	   createDataThead = new Thread(new Runnable() {
		
		@Override
		public void run() {
//			// TODO Auto-generated method stub
			
//		Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY );
		long time1 = System.currentTimeMillis();
		// TODO Auto-generated method stub
		Results results = new Results();
		mPrevInput  = removeNonDigits(s.toString());
		if (mContactinfoList != null) {
			mContactinfoList.clear();
		}
	
		if(isAll){
			mContactinfoList = mFirstNumberInfo.searchNumber(mPrevInput,MAXNUMS);
		}else{
			mContactinfoList = mFirstNumberInfo.searchNumber(mPrevInput,4);
		}
		results.values = mContactinfoList;
		results.count = mContactinfoList.size();
		results.constraint = mPrevInput;
		Message msg = new Message();

//		msg.obj = mPrevInput;
		msg.obj = results;
		mHandler.sendMessage(msg);
		log("sech  ---   time "+ (System.currentTimeMillis()-time1));
		if(createDataThead!=null){
		createDataThead.interrupt();
		createDataThead = null;
	}
		}
	});
	   createDataThead.start();
	
	}
	
	public void setSearchContactsListener(SearchContactsListener contactsListener){
		this.mContactsListener = contactsListener;
	}
    
	public synchronized void getAll() {
		long time1 = System.currentTimeMillis();
		mFirstNumberInfo.clearAll();
				  if (sT9Map == null)
			            initT9Map();

			        Cursor contact = mContext.getContentResolver().query(Contacts.CONTENT_URI, CONTACT_PROJECTION, CONTACT_QUERY, null, CONTACT_SORT);
			        Cursor phone = mContext.getContentResolver().query(Phone.CONTENT_URI, PHONE_PROJECTION, PHONE_ID_SELECTION, PHONE_ID_SELECTION_ARGS, PHONE_SORT);
			        phone.moveToFirst();
//			        if(mContactinfoList != null){
//			        	mContactinfoList.clear();
//			        }else{
//			        	mContactinfoList = new ArrayList<ShenduContactAdapter.Shendu_ContactItem>();
//			        }

			        
			        while (contact.moveToNext()) {
			            long contactId = contact.getLong(0);
			            if (phone.isAfterLast()) {
			                break;
			            }
			            while (phone.getLong(1) == contactId) {
			                String num = phone.getString(0);
			                Shendu_ContactItem contactInfo = new Shendu_ContactItem();
			                contactInfo.id = contactId;
			                contactInfo.name = contact.getString(1);
			                contactInfo.number = removeNonDigits(num);
			                nameToPinYinAndNumber(contact.getString(4), contactInfo);
			                contactInfo.city = PhoneLocation.getCityFromPhone(contactInfo.number, mContext);
//			                contactInfo.timesContacted = contact.getInt(2);
//			                contactInfo.isSuperPrimary = phone.getInt(2) > 0;
//			                contactInfo.groupType = Phone.getTypeLabel(mContext.getResources(), phone.getInt(3), phone.getString(4));
			                if (!contact.isNull(3)) {
			                    contactInfo.photo = Uri.parse(contact.getString(3));
			                }
//			                mContactinfoList.add(contactInfo);
			                
//			                nameSearchTree.insert(contactInfo, contactInfo.hanziNums);
			                
			                if (!phone.moveToNext()) {
			                    break;
			                }
			            }
			        }
			        contact.close();
			        phone.close();
			       getStrangeCallLogs();
			       mFirstNumberInfo. comparatorArraylist();
				log("time1"+(System.currentTimeMillis() -time1 ));
    }
	
	
	private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);
    private static final String FIRST_PINYIN_UNIHAN = "\u963F";
    private static final String LAST_PINYIN_UNIHAN = "\u84D9";
    private static final char FIRST_UNIHAN = '\u3400';
    /** shutao 2012-10-19*/
    public void nameToPinYinAndNumber(String name , Shendu_ContactItem contactInfo){
//    	 ArrayList<Boolean> isBoolean = new ArrayList<Boolean>();
    	 ArrayList<Integer> firstNumberIndexs = new ArrayList<Integer>();
     	 int nameLength = name.length();
    	 final StringBuilder sb = new StringBuilder();
    	 final StringBuilder sbNumber = new StringBuilder();
    	 final StringBuilder sbFirst = new StringBuilder();
    	 final StringBuilder hanzis = new StringBuilder(); 
    	 ArrayList<String> numberNum =  new ArrayList<String>();
    	 boolean isFirst = true;
    	 int cmp;
    	 int hanziNum = 0;
    	 for (int i = 0; i < nameLength; i++) {
    		 final char character = name.charAt(i);
    		 final String letter = Character.toString(character);
    		 cmp = COLLATOR.compare(letter, FIRST_PINYIN_UNIHAN);
//    		 log("name = "+name);
    		 if(character == ' '){
    			 isFirst = true;
    			 if(hanzis.toString()!=null && !hanzis.toString().equals("")){
        			 numberNum.add(hanzis.toString());
    			 }
    			 hanzis.setLength(0);
    		 }else if (character < 256) {
    			 
    			 if(character>=65 && character <=90 || character >= 97 && character <= 122){
//    				 log("char == "+character);
    				 char num = LetterToNumber(character);
    				 sbNumber.append(num);
    				 hanzis.append(num);
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
    				 hanzis.append(character);
    				 hanziNum ++ ;
    				 firstNumberIndexs.add(hanziNum);
//    				 isBoolean.add(true);
    			 }
    			 else{
    				 isFirst = false;
//    				 isBoolean.add(false);
    			 }
    		 }else if(character<FIRST_UNIHAN){
//    			 isBoolean.add(false);
    		 }else if(cmp < 0){
//    			 isBoolean.add(false);
    		 }else{
    			 cmp = COLLATOR.compare(letter, LAST_PINYIN_UNIHAN);
    			 if(cmp >0){
//    				 isBoolean.add(false);
    			 }
    		 }
    		
    	 }

//    	 log("pinYin ====="+hanzis.length()+"===="+hanzis.toString());
    	 if(hanzis.length()>0){
    		 numberNum.add(hanzis.toString());
    	 }
    	 contactInfo.pinYin = sb.toString();
    	 sb.setLength(0);
//    	 contactInfo.normalName = sbNumber.toString();
//        mNameTONumberList.add(sbNumber.toString());
    	 contactInfo.pinyinNumber = sbNumber.toString();
        sbNumber.setLength(0);
//        mFirstNumberIndexs.add(sbFirst.toString());
        contactInfo.firstNumber = sbFirst.toString()+sbNumber.toString();
        sbFirst.setLength(0);
//    	 contactInfo.isHanzis = isBoolean;
        if(contactInfo.firstNumberIndexs!=null){
        	contactInfo.firstNumberIndexs.clear();
        }
     
        contactInfo.hanziNums = numberNum;
    	 contactInfo.firstNumberIndexs = firstNumberIndexs;
    	 try{
    	     setFirstNumberInfo(contactInfo);
    	 }catch(Exception e){
    	 
    	 }
  
    	
    }
    
    /**shutao 2012-10-31*/
	private void setFirstNumberInfo(Shendu_ContactItem contactInfo) throws Exception{

		if (contactInfo.name != null && !contactInfo.firstNumber.equals("")) {
			String first = contactInfo.firstNumber;
			for (int index = 0; index < contactInfo.firstNumber.length(); index++) {
				String firstNum = first.substring(index, first.length());
				NodeShendu_ContactItem contactItemFirst = new NodeShendu_ContactItem();
				contactItemFirst.number = firstNum;
				contactItemFirst.type = mFirstNumberInfo.FIRST;
				contactItemFirst.contactItem = contactInfo;
				char numFirst = contactItemFirst.number.toCharArray()[0];
				mFirstNumberInfo.setNumberContactsItem(numFirst,
						contactItemFirst);
				StringBuilder name = new StringBuilder();
				for (int j = index; j < contactInfo.hanziNums.size(); j++) {
					name = name.append(contactInfo.hanziNums.get(j));
				}
				NodeShendu_ContactItem contactItem = new NodeShendu_ContactItem();
				contactItem.number = name.toString();
				contactItem.type = mFirstNumberInfo.PINYIN;
				contactItem.contactItem = contactInfo;
				char num = contactItem.number.toCharArray()[0];
				mFirstNumberInfo.setNumberContactsItem(num, contactItem);
				name.setLength(0);
			}
		}
		String mNumber = contactInfo.number;
		for (int j = 0; j < mNumber.length(); j++) {

			NodeShendu_ContactItem contactItemNumber = new NodeShendu_ContactItem();
			String numb = mNumber.substring(j, mNumber.length());
			contactItemNumber.number = numb;
			contactItemNumber.type = mFirstNumberInfo.NUMBER;
			contactItemNumber.contactItem = contactInfo;
			char mFirst = contactItemNumber.number.toCharArray()[0];

			mFirstNumberInfo.setNumberContactsItem(mFirst, contactItemNumber);
			numb = null;
		}

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
	
    
    /**shutao 2012-10-25*/
    public  String removeNonDigits(String number) {
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
	private void getStrangeCallLogs() {
		/** shutao 2012-9-27*/
		String selection = "_id in (select _id  from calls where "+CallLog.Calls.CACHED_NAME + " is null or "+CallLog.Calls.CACHED_NAME +"= \"\"  group by number having count (number) > 0)";
		Cursor cursor = mContext.getContentResolver().query(
				CallLog.Calls.CONTENT_URI,
				new String[] { CallLog.Calls.NUMBER},
				selection, null, null);
		if (cursor == null) {
			return;
		}

		while (cursor.moveToNext()){
			Shendu_ContactItem r = new Shendu_ContactItem();
			r.number = removeNonDigits(cursor.getString(0));

//			log("getStrangeCallLogs == "+r.number);
			r.name = null;
			r.city = PhoneLocation.getCityFromPhone(r.number, mContext);
			try{
			setFirstNumberInfo(r);
			}catch(Exception e){
				
			}
		}
		cursor.close();

	}
	
	

    private void initT9Map() {
    	
        String[] t9Array = mContext.getResources().getStringArray(R.array.t9_map);
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


	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return mInfoList!=null?mInfoList.size():0;
	}

	@Override
	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return mInfoList.get(position);
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}

	class ViewHolder {
		QuickContactBadge imPhoto;
		TextView name;
		TextView number;
		TextView attribution;
		TextView pinYin;
		LinearLayout row_Layout;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.shendu_row, null);
            holder = new ViewHolder();
            holder.name = (TextView) convertView.findViewById(R.id.rowName);
            holder.number = (TextView) convertView.findViewById(R.id.rowNumber);
            holder.imPhoto = (QuickContactBadge) convertView.findViewById(R.id.rowBadge);
            holder.attribution = (TextView) convertView.findViewById(R.id.shendu_row_attribution);
            holder.pinYin = (TextView) convertView.findViewById(R.id.rowPinyin);
            holder.row_Layout = (LinearLayout)  convertView.findViewById(R.id.row_linear);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Shendu_ContactItem contactItem = mInfoList.get(position);
        holder.row_Layout.setTag(R.id.shendu_tag_second, contactItem.number);
        holder.row_Layout.setTag(R.id.shendu_tag_name , contactItem.name );
        holder.row_Layout.setTag(R.id.shendu_tag_position , position );
        holder.row_Layout.setOnLongClickListener(mPrimaryActionLongListener);
        holder.row_Layout.setOnClickListener(mClickListener);
        
        if (contactItem.name == null) {
            holder.name.setText(contactItem.number , TextView.BufferType.SPANNABLE);
            holder.number.setVisibility(View.GONE);
            holder.imPhoto.setImageResource(R.drawable.ic_contact_picture_holo_dark);
            holder.imPhoto.assignContactFromPhone(contactItem.number, true);
        	  holder.pinYin.setVisibility(View.GONE);
            if (contactItem.number.contains(mPrevInput)) {
                Spannable s = (Spannable) holder.name.getText();
                int numberStart = contactItem.number.indexOf(mPrevInput);
//           	  log("getview yy== "+numberStart+"num"+(numberStart + o.num)+"name"+o.number);
                s.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
                        numberStart, numberStart + mPrevInput.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                holder.name.setText(s);
            }
        } else {
            holder.name.setText(contactItem.name, TextView.BufferType.SPANNABLE);
            holder.number.setText(contactItem.number , TextView.BufferType.SPANNABLE);
            /**shutao 2012-10-19*/
            holder.pinYin.setVisibility(View.VISIBLE);
            holder.pinYin.setText(contactItem.pinYin ,TextView.BufferType.SPANNABLE);
            holder.number.setVisibility(View.VISIBLE);

			switch (contactItem.type) {
			case 0:{
				Spannable s = (Spannable) holder.number.getText();
				int numberStart = contactItem.number.indexOf(mPrevInput);
				s.setSpan(new ForegroundColorSpan(mContext.getResources()
						.getColor(R.color.shendu_high_light)), numberStart,
						numberStart + mPrevInput.length(),
						Spannable.SPAN_INCLUSIVE_INCLUSIVE);

				holder.number.setText(s);
				break;
			}
			case 1:{
				Spannable sFirstPinYin = (Spannable) holder.pinYin.getText();
				int num = mPrevInput.length();
				int nameStart = contactItem.firstNumber.indexOf(mPrevInput);
				for (int index = nameStart; index < contactItem.firstNumberIndexs.size(); index++) {
					if (index - nameStart == num) {
						break;
					}
					int nums = contactItem.firstNumberIndexs.get(index);

					sFirstPinYin.setSpan(
							new ForegroundColorSpan(mContext.getResources()
									.getColor(R.color.shendu_high_light)),
							nums - 1, nums, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
				}

				holder.pinYin.setText(sFirstPinYin);
			}
				break;
			case 2:{
				Spannable sPinYin = (Spannable) holder.pinYin.getText();
				int sLeng = sPinYin.length();
				int pinyinStart = contactItem.pinyinNumber.indexOf(mPrevInput);
				int send = (mPrevInput.length() + pinyinStart) >= sLeng ? sLeng
						: (mPrevInput.length() + pinyinStart);

				sPinYin.setSpan(new ForegroundColorSpan(mContext.getResources()
						.getColor(R.color.shendu_high_light)), pinyinStart, send,
						Spannable.SPAN_INCLUSIVE_INCLUSIVE);
				holder.pinYin.setText(sPinYin);
				break;
			}
			}
          	
//            if(contactItem.pinyinNumber.contains(mPrevInput)){
//      
//           	 Spannable sPinYin = (Spannable) holder.pinYin.getText();
//           	 int sLeng = sPinYin.length();
//           	 int nameStart = contactItem.pinyinNumber.indexOf(mPrevInput);
//           	 int send = (contactItem.num+nameStart) >= sLeng ?sLeng:(contactItem.num+nameStart);
////           	 log("nameStart"+nameStart+"send = "+send);
//       	     sPinYin.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
//       			 nameStart, send , Spannable.SPAN_INCLUSIVE_INCLUSIVE);
//       	     holder.pinYin.setText(sPinYin);
//           }else if(contactItem.firstNumber.contains(mPrevInput)){
//        	   Spannable s = (Spannable) holder.name.getText();
//             Spannable sPinYin = (Spannable) holder.pinYin.getText();
//             int num = mPrevInput.length();
//             int nameStart = contactItem.firstNumber.indexOf(mPrevInput);
//             for(int index = nameStart ; index< contactItem.firstNumberIndexs.size();index++){
//             	if(index-nameStart == num){
//             		break;
//             	}
//          	   int nums =contactItem.firstNumberIndexs.get(index);
////      		   log("o.firstNumberIndexs"+num+o.name);
//      		   sPinYin.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
//      				 nums-1, nums , Spannable.SPAN_INCLUSIVE_INCLUSIVE);
//             }
////             holder.name.setText(s);
//             holder.pinYin.setText(sPinYin);
//           }else if(contactItem.number.contains(mPrevInput)){
//    
//             Spannable s = (Spannable) holder.number.getText();
//             int numberStart = contactItem.number.indexOf(mPrevInput);
//             s.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.shendu_high_light)),
//                     numberStart, numberStart + mPrevInput.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
//             
//             holder.number.setText(s);
//           }
            if (contactItem.photo != null){
                mPhotoLoader.loadDirectoryPhoto(holder.imPhoto, contactItem.photo, true );
            }else{
            	if(TextUtils.isEmpty(contactItem.name)){
            		 holder.imPhoto.setImageResource(ContactPhotoManager.getDefaultAvatarResId(false, true)); 
            	}else{
            		 mPhotoLoader.loadDirectoryPhoto(holder.imPhoto, contactItem.photo, true ,contactItem.name ,contactItem.id );
            	}

              }
            holder.imPhoto.assignContactFromPhone(contactItem.number, true);
        }
        
		if(contactItem.city != null){
			holder.attribution.setText(contactItem.city);
		}
		   contactItem = null ;
//		log("getview  time = "+(System.currentTimeMillis() - time));
        return convertView;
    }

	
	private String mPrevInput = null;
	private boolean isSearch = false ;
	/***
	 * shutao 2012-9-14
	 * The following is added to the ListView filtering methods, 
	 * in accordance with the digital retrieval number, digital retrieve name.
	 */
	@Override
	public Filter getFilter() {
		Filter filter = new Filter() {


			@Override
			protected void publishResults(CharSequence constraint,
					FilterResults results) {
				// TODO Auto-generated method stub
				String number = removeNonDigits(constraint.toString());
				if(isSearch && number.equals(mPrevInput)){
		
					/**shutao 2012-10-26*/
					mInfoList = (ArrayList<Shendu_ContactItem>) results.values;
					if (results.count > 0) {
						notifyDataSetChanged();
						mContactsListener.Contacts();
					} else {
						notifyDataSetInvalidated();
						mContactsListener.notContacts();
					}
					isSearch = false;
			
				}
			}

			@Override
			protected FilterResults performFiltering(CharSequence s) {
				isSearch =false;
				long time1 = System.currentTimeMillis();
				// TODO Auto-generated method stub
				FilterResults results = new FilterResults();

				mPrevInput  = removeNonDigits(s.toString());
//				mContactinfoList = mFirstNumberInfo.searchNumber(mPrevInput,15);				
//				log("size"+mContactinfoList.size());
//
//				results.values = mContactinfoList/*new ArrayList<Shendu_ContactItem>(mOldInfoList)*/;
//				results.count =mContactinfoList /*mOldInfoList*/.size();

				log("sech  ---  00091 time "+ (System.currentTimeMillis()-time1));
				isSearch =true;
				return results;
			}
		};
		return filter;
	}
	
	public boolean numberMatch(String string, String s) {
		// TODO Auto-generated method stub
		if (null == string) return false;
		String dealStr = string.replace("-", "");
		dealStr = dealStr.replace(" ", "");
		if (dealStr.contains(s)) 
			return true;
		return false;
	}

	
	private static boolean debug = false;

	private static void log(String msg) {
		if (debug)
			Log.i("1716", msg);
	}
	
    public static class Shendu_ContactItem {
    	 long id;
        Uri photo;
        String name;
        String number;
        String pinYin;
        String firstNumber;
        String pinyinNumber;
        String city;
        int pinyinMatchId;
        int num;
        int type;
        ArrayList<String> hanziNums;
        ArrayList<Integer> firstNumberIndexs;
    }

}

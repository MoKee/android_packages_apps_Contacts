package com.android.contacts.dialpad;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.contacts.R;
import android.mokee.location.PhoneLocation;

 /**
 * @Time：2012-10-17
 * @author : shutao shutao@shendu.com
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * @Function : The T9 input not have this contact number, how can data display adapter (for example: add contacts, etc.)
 */
public class ShenDuNewContactT9Adapter extends BaseAdapter{
	
	private final int mMenuCount = 3; 
	private Context mContext;
	private String[] mShenduMenutValues;
	private String mNumber;

	
	public ShenDuNewContactT9Adapter(Context context){
	   mShenduMenutValues = context.getResources().getStringArray(R.array.new_contact_t9_menu_values);
		this.mContext = context;
	
	}
	
	public void setNewContactNumber(String number){;
		this.mNumber = number.replaceAll(" ", "");
		this.notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return mMenuCount;
	}

	@Override
	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}
	
	class Views{
		ImageView dialpad_menu_icon;
		TextView dialpad_menu_content;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
		Views views;
		if(convertView == null){
		convertView = LayoutInflater.from(mContext).inflate(
				R.layout.dialpad_shendu_new_contact_item, null);
		views = new Views();
		views.dialpad_menu_content = (TextView)convertView.findViewById(R.id.dialpad_menu_content);
		views.dialpad_menu_icon = (ImageView)convertView.findViewById(R.id.dialpad_menu_icon);
		convertView.setTag(views);
		}else{
			views=(Views)convertView.getTag();
		}
	    switch (position) {
		case 0:	
			views.dialpad_menu_icon.setImageResource(R.drawable.shendu_ic_location_place);
			String city = PhoneLocation.getCityFromPhone(mNumber != null ? mNumber : "", mContext);
			if(city!=null){
				views.dialpad_menu_content.setText(city);
			}else{
				views.dialpad_menu_content.setText(mShenduMenutValues[position]);
			}
			break;
		case 2:	
			if(position == 2){
				views.dialpad_menu_icon.setImageResource(R.drawable.shendu_ic_mms);
			}
			views.dialpad_menu_content.setText(mShenduMenutValues[position]);
			break;

		default:
			views.dialpad_menu_icon.setImageResource(R.drawable.shendu_ic_menu_add_contact_holo_light);
			views.dialpad_menu_content.setText(mShenduMenutValues[position]);
			break;
		}

		return convertView;
	}
	
}
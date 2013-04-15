package com.android.contacts.dialpad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.android.contacts.dialpad.ShenduContactAdapter.Shendu_ContactItem;
/**
 * @Time 2012-9-14 
 * @author shutao shutao@shendu.com
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * Linked list search logic data classes
 */
public class FirstNumberInfo {
	
	public  int NUMBER = 0;
	public  int FIRST = 1;
	public  int PINYIN = 2; 
	
	public static class NodeShendu_ContactItem{
		public Shendu_ContactItem contactItem;
		public String number ;
		public int type;
	}
	
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList0 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList1 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList2 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList3 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList4 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList5 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList6 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList7 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList8 = new ArrayList<NodeShendu_ContactItem>();
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayList9 = new ArrayList<NodeShendu_ContactItem>();
	
	private ArrayList<NodeShendu_ContactItem> phoneDataArrayListAdd = new ArrayList<NodeShendu_ContactItem>();
	
	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayListAdd() {
		return phoneDataArrayListAdd;
	}

	private HashMap<String, String> matchMap = new HashMap<String, String>();
	
	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList0() {
		return phoneDataArrayList0;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList1() {
		return phoneDataArrayList1;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList2() {
		return phoneDataArrayList2;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList3() {
		return phoneDataArrayList3;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList4() {
		return phoneDataArrayList4;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList5() {
		return phoneDataArrayList5;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList6() {
		return phoneDataArrayList6;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList7() {
		return phoneDataArrayList7;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList8() {
		return phoneDataArrayList8;
	}

	public ArrayList<NodeShendu_ContactItem> getPhoneDataArrayList9() {
		return phoneDataArrayList9;
	}

	
	public ArrayList<NodeShendu_ContactItem> getNumberContactsItem(char number){
		switch(number){
		case 43:
		  return getPhoneDataArrayListAdd();
		case 48:
		  return	getPhoneDataArrayList0();
		case 49:
			return	getPhoneDataArrayList1();
		case 50:
			return	getPhoneDataArrayList2();
		case 51:
			return	getPhoneDataArrayList3();
		case 52:
			return	getPhoneDataArrayList4();
		case 53:
			return	getPhoneDataArrayList5();
		case 54:
			return	getPhoneDataArrayList6();
		case 55:
			return	getPhoneDataArrayList7();
		case 56:
			return	getPhoneDataArrayList8();
		case 57:
			return	getPhoneDataArrayList9();
		}
		return null;
		
	}
	
	public void setNumberContactsItem(char number , NodeShendu_ContactItem contactItem){
		
		switch(number){
		case 43:
			phoneDataArrayListAdd.add(contactItem);
			break;
		case 48:
			phoneDataArrayList0.add(contactItem);
		   break;
		case 49:
			phoneDataArrayList1.add(contactItem);
			break;
		case 50:
			phoneDataArrayList2.add(contactItem);
			break;
		case 51:
			phoneDataArrayList3.add(contactItem);
			break;
		case 52:
			phoneDataArrayList4.add(contactItem);
			break;
		case 53:
			phoneDataArrayList5.add(contactItem);
			break;
		case 54:
			phoneDataArrayList6.add(contactItem);
			break;
		case 55:
			phoneDataArrayList7.add(contactItem);
			break;
		case 56:
			phoneDataArrayList8.add(contactItem);
			break;
		case 57:
			phoneDataArrayList9.add(contactItem);
			break;
		}
		
	}
	private final int MAXNUMS = 100000;
	public  synchronized ArrayList<Shendu_ContactItem>  searchNumber(String  input , int count){
		matchMap.clear();
		
		ArrayList< Shendu_ContactItem > numberList = new ArrayList<ShenduContactAdapter.Shendu_ContactItem>();
		ArrayList< Shendu_ContactItem > pinyinList = new ArrayList<ShenduContactAdapter.Shendu_ContactItem>();
		char num = input.toCharArray()[0];
		
		int inputCount = input.length();
		ArrayList<NodeShendu_ContactItem> data = getNumberContactsItem(num);
		/**shutao 2012-11-8*/
		if( data==null || data.size()<1){
			return numberList;
		}
		int start = binSearchMin(data , 0 , data.size()-1 , input)+1;
		if(start == data.size()){
			start = data.size()-1;
		}

		if(start == -1){

			if(data.get(0).number.contains(input)){
				start = 0;
				for(int  index = start ; index < data.size()  ; index++){
					NodeShendu_ContactItem itme = data.get(index);
					if(itme.number.contains(input) /*&& listSize < count*/ ){
						if(!matchMap.containsKey(itme.contactItem.number)){
							itme.contactItem.type = itme.type;
							if(itme.type == NUMBER){
								numberList.add(itme.contactItem);
							}else{
							   pinyinList.add(itme.contactItem);
							}
				        	itme.contactItem.num = inputCount;
				        	matchMap.put(itme.contactItem.number, "");
						}
			        }else{
			        	break;
			        }
				}
			}else{
				pinyinList.addAll( numberList );
				return pinyinList;
			}
		}else{
			for(int  index = start ; index < data.size()  ; index++){
				NodeShendu_ContactItem itme = data.get(index);
				if(itme.number.contains(input)/* && listSize < count*/){
					if(!matchMap.containsKey(itme.contactItem.number)){
						itme.contactItem.type = itme.type;
						if(itme.type == NUMBER){
							numberList.add(itme.contactItem);
						}else{
						   pinyinList.add(itme.contactItem);
						}
			        	itme.contactItem.num = inputCount;
			        	matchMap.put(itme.contactItem.number, "");
					}
		        }else{
		        	break;
		        }
			}
		}
		int pinyinSize = pinyinList.size();
		if(pinyinSize < count && MAXNUMS != count){
			for(int i = 0 ; i < numberList.size();i++){
				if( i >= (count-pinyinSize)){
					break;
				}
				pinyinList.add(numberList.get(i));
			}
			return pinyinList;
		}else if( pinyinSize > count  && MAXNUMS != count ){
			ArrayList< Shendu_ContactItem > arrayList = new ArrayList<ShenduContactAdapter.Shendu_ContactItem>();
			for(int i = 0 ; i < pinyinList.size();i++){
				if(i >= count){
					break;
				}
				arrayList.add(pinyinList.get(i));
			}
			return arrayList;
		}
		pinyinList.addAll( numberList );
		return pinyinList;
		
	}
	
	private boolean matchMin(ArrayList<NodeShendu_ContactItem>  data, int mid, String input){
		String before = data.get(mid).number;
		if (mid == data.size()-1){
			return before.indexOf(input) == 0;
		}
		String after = data.get(mid+1).number;
		return before.indexOf(input) != 0 && after.indexOf(input) ==0;
	}
	
	private int binSearchMin(ArrayList<NodeShendu_ContactItem>  data , int start , int end ,String input){
		
		int mid = (end - start) / 2 + start; 
		if (matchMin(data,mid,input)) {   
            return mid;   
        }   
		if (start >= end) {   
            return -1;   
        } else if(data.get(mid).number.compareTo(input) < 0){
        	 return binSearchMin(data, mid + 1, end, input);  
        }else if (data.get(mid).number.compareTo(input) >= 0) {   
            return binSearchMin(data, start, mid - 1, input);   
        }   
		return -1;
		
	}
	
//	private int binSearchMin(ArrayList<NodeShendu_ContactItem>  data,String input){
//		
//		int start = 0; 
//		int end = data.size()-1;
//		while( end > start+1 ){
//			int middle = ( start + end ) / 2;
//			if(matchMin(data,middle,input)){
//				return middle;
//			}
//			if(data.get( middle ).number.compareTo( input ) < 0){
//				start = middle;
//			}else if(data.get( middle ).number.compareTo( input ) >= 0){
//				end = middle;
//			}
//		}
//		return -1;		
//	}
	
	
	public  void  comparatorArraylist(){
		
		Comparator<NodeShendu_ContactItem> comparator = new Comparator<NodeShendu_ContactItem>(){
			@Override
			public int compare(NodeShendu_ContactItem lhs, NodeShendu_ContactItem rhs) {
				// TODO Auto-generated method stub
				return lhs.number.compareTo(rhs.number);
				//return 0;
			}};
		Collections.sort(phoneDataArrayListAdd,comparator);
		Collections.sort(phoneDataArrayList0,comparator);
		Collections.sort(phoneDataArrayList1,comparator);
		Collections.sort(phoneDataArrayList2,comparator);
		Collections.sort(phoneDataArrayList3,comparator);
		Collections.sort(phoneDataArrayList4,comparator);
		Collections.sort(phoneDataArrayList5,comparator);
		Collections.sort(phoneDataArrayList6,comparator);
		Collections.sort(phoneDataArrayList7,comparator);
		Collections.sort(phoneDataArrayList8,comparator);
		Collections.sort(phoneDataArrayList9,comparator);

	}

	
	public void clearAll(){
		
		phoneDataArrayList0.clear();
		phoneDataArrayList1.clear();
		phoneDataArrayList2.clear();
		phoneDataArrayList3.clear();
		phoneDataArrayList4.clear();
		phoneDataArrayList5.clear();
		phoneDataArrayList6.clear();
		phoneDataArrayList7.clear();
		phoneDataArrayList8.clear();
		phoneDataArrayList9.clear();
		phoneDataArrayListAdd.clear();
		
	}

}

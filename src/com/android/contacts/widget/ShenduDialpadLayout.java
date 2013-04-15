package com.android.contacts.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TableLayout;


/**shutao 2012-10-25*/
public class ShenduDialpadLayout extends TableLayout {

	public ShenduDialpadLayout(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public ShenduDialpadLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		// TODO Auto-generated method stub
		int pointerCount = ev.getPointerCount();
		/** shutao 2012-9-27 Open Multi-point*/
		if(pointerCount > 1){
			return true;
		}
		return super.onInterceptTouchEvent(ev);
	}
	
	

}

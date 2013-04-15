package com.android.contacts.util;

import android.content.Context;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.System;
import android.content.ContentResolver;
/**
 * @time 2012-9-4
 * @author shutao shutao@shendu.com
 * @module : Contacts
 * @Project: ShenDu OS 2.0
 * Custom vibration touch class
 */
public class ShenduVibrate {

	Vibrator vibrator;

	private Settings.System mSystemSettings;
	private ContentResolver mContentResolver;
	private boolean isOpen = true;

	public boolean isOpen() {
		return isOpen;
	}

	public void setOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}

	public ShenduVibrate(Context context) {
		vibrator = (Vibrator) context
				.getSystemService(context.VIBRATOR_SERVICE);
		mSystemSettings = new Settings.System();
		mContentResolver = context.getContentResolver();
	}

	long[] pattern = { 0, 25 };

	public void playVibrate(int type) {
		if (isOpen) {
			vibrator.vibrate(pattern, type);
		}
	}

	public void Stop() {
		vibrator.cancel();
	}

	/**
	 * Reload the system settings to check if the user enabled the haptic
	 * feedback.
	 */
	public void checkSystemSetting() {
		try {
			int val = mSystemSettings.getInt(mContentResolver,
					System.HAPTIC_FEEDBACK_ENABLED, 0);
			isOpen = val != 0;
		} catch (Exception e) {
			isOpen = false;
		}
	}

}

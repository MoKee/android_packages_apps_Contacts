package com.android.contacts.util;

import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * Util class for sim contacts
 * 
 * @author WangGang
 * @date 2012-11-9
 * */
public class ADNUtil {

	public static final int USIM = 0;
	public static final int SIM = 1;
	public static final int UIM = 2;
	public static final int UNKNOWN = -1;
	private static int mSimCardType = UNKNOWN;

	public static boolean isIccCardSupported(Context ctx) {
		return getSimCardType(ctx) == SIM;
	}

	private static int getSimCardType(Context ctx) {
		TelephonyManager tm = (TelephonyManager) ctx
				.getSystemService(Context.TELEPHONY_SERVICE);
		int type = tm.getNetworkType();
		if (type == TelephonyManager.NETWORK_TYPE_UMTS) {
			return USIM;
		} else if (type == TelephonyManager.NETWORK_TYPE_GPRS) {
			return SIM;
		} else if (type == TelephonyManager.NETWORK_TYPE_EDGE) {
			return SIM;
		} else {
			return UIM;
		}
	}
}

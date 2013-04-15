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
 * limitations under the License
 */

package com.android.contacts.calllog;

import com.android.contacts.R;
import com.android.contacts.dialpad.DialpadFragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;

/**
 * Dialog that clears the call log after confirming with the user
 */
public class ClearCallLogDialog extends DialogFragment {
    /** Preferred way to show this dialog */
	private static Context mContext; 
    public static void show(FragmentManager fragmentManager , Context context) {
        ClearCallLogDialog dialog = new ClearCallLogDialog();
        mContext = context;
        dialog.show(fragmentManager, "deleteCallLog");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ContentResolver resolver = getActivity().getContentResolver();
        final OnClickListener okListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final ProgressDialog progressDialog = ProgressDialog.show(getActivity(),
                        getString(R.string.clearCallLogProgress_title),
                        "", true, false);
                final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        resolver.delete(Calls.CONTENT_URI, null, null);
                        return null;
                    }
                    @Override
                    protected void onPostExecute(Void result) {
                    	Intent intent = new Intent(DialpadFragment.REMOVER_CALLLOG);
                    	mContext.sendBroadcast(intent);
                     progressDialog.dismiss();
                    }
                };
                // TODO: Once we have the API, we should configure this ProgressDialog
                // to only show up after a certain time (e.g. 150ms)
                progressDialog.show();
                task.execute();
            }
        };
        return new AlertDialog.Builder(getActivity())
            .setTitle(R.string.clearCallLogConfirmation_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(R.string.clearCallLogConfirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, okListener)
            .setCancelable(true)
            .create();
    }
}

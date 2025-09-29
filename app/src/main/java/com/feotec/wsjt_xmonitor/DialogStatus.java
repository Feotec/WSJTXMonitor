package com.feotec.wsjt_xmonitor;
/*
 * Copyright (C) 2019-2025 Feotec Thomas Reynolds
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation GNU APGLv3 or later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * A copy of GNU APGLv3, the GNU General Public license is in a file
 * named COPYING located in the root directory.
 */


import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogStatus extends DialogFragment {
    private static final String TAG = DialogStatus.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogStatus;

    static private UDPService mService;     //  See notes immediately below, where mService is assigned, about why this is a static variable.

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        SpannableStringBuilder statusString = new SpannableStringBuilder( "" );

        int curPos;

        //  This is ugly code but it seemed the easiest way to solve the problem of screen rotation.  The problem stems from the fact that when the screen is rotated
        //      BaseActivity object has not had onServiceConnection called when this code is executed.  So BaseActivity.mService is still null.  So I made the local
        //      mService variable a static variable and only loaded it if it is null.  I set it to null when the user dismisses the dialog.
        if ((BaseActivity)getActivity() != null) {
            if (mService == null) {
                mService = ((BaseActivity) getActivity()).mService;
            }
        }

        if ( mService != null) {             //  should alays be non-null
            String alertString = getString(R.string.action_alerts);
            statusString.append(alertString);
            statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, alertString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusString.append(": ");
            if (mService.alerts()) {
                statusString.append(mService.alertObject.describeAlert(getContext()));
            } else {
                statusString.append(getString(R.string.alerts_toast_off));
            }

            statusString.append("\n\n");
            curPos = statusString.length();
            String filterString = getString(R.string.action_filters);
            statusString.append(filterString);
            statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), curPos, curPos + filterString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusString.append(": ");
            if (mService.filters()) {
                statusString.append(mService.filterObject.describeFilter(getContext()));
            } else {
                statusString.append(getString(R.string.filter_toast_off));
            }

            statusString.append("\n\n");
            curPos = statusString.length();
            String sortingString = getString(R.string.action_sorting);
            statusString.append(sortingString);
            statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), curPos, curPos + sortingString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusString.append(": ");
            statusString.append(mService.describeSorting());

            statusString.append("\n\n");
            curPos = statusString.length();
            String myCallGridString = getString(R.string.dialog_status_mycallgrid);
            statusString.append(myCallGridString);
            statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), curPos, curPos + myCallGridString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusString.append(": ");
            statusString.append(mService.getMyCallAndGrid());

            statusString.append("\n\n");
            curPos = statusString.length();
            String dxCallGridString = getString(R.string.dialog_status_dxcallgrid);
            statusString.append(dxCallGridString);
            statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), curPos, curPos + dxCallGridString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusString.append(": ");
            statusString.append(mService.getDXCallAndGrid());

            //  if current WSJT-X frequency or mode are unknown then don't print the last line.  This can happen if in MSK-144 since msg1 is not used.  I could print "unknown" but then the user will ask why.
            if ( (!mService.getWsjtxFreq().equals("")) && (!mService.getWsjtxMode().equals("")) ) {
                statusString.append("\n\n");
                curPos = statusString.length();
                String freqModeString = getString(R.string.dialog_status_freamode);
                statusString.append(freqModeString);
                statusString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), curPos, curPos + freqModeString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                statusString.append(": ");
                statusString.append(mService.getWsjtxFreq()).append("/").append(mService.getWsjtxMode());
            }
        }

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(statusString)
                .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mService = null;
                        logInfo("DialogClearScreenHelp OK *****");
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

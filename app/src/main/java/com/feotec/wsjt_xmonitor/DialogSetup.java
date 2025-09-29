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
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;

public class DialogSetup extends DialogFragment {
    private static final String TAG = DialogSetup.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSetup;

    private static final String CALLED_AT_STARTUP = "called_at_startup";
    private static final String IP_ADDRESS_STRING = "ip_address_string";

    private boolean parameterCalledAtStartup;
    private String parameterIPAddressString;

    //
    //  Parameters
    //      parameter1Startup - boolean, loaded into parameterCalledAtStartup.  Set true if called at startup, false if called from menu
    //      parameter2IP - string, loaded into parameterIPAddressString.  Only used if parameterCalledAtStartup is false.
    //
    public static DialogSetup newInstance( boolean parameter1Startup, String parameter2IP ) {
        if (DO_LOGGING) {
            Log.i(TAG, "DialogSetup newInstance() called *****");       // cannot call logInfo() because in static method.
        }
        DialogSetup fragment = new DialogSetup();
        Bundle args = new Bundle();
        args.putBoolean(CALLED_AT_STARTUP, parameter1Startup);
        args.putString(IP_ADDRESS_STRING,parameter2IP);
        fragment.setArguments(args);
        return fragment;
    }

    //
    //  Only one listener, for the help button.  That listener is used to bring up SetupDetails
    //
    public interface dialogSetupListener {
        void dialogSetupAskForDetails(DialogFragment dialog);
    }

    DialogSetup.dialogSetupListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSetup.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSetup.dialogSetupListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogSetupListener");
        }
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            parameterCalledAtStartup = bundle.getBoolean(CALLED_AT_STARTUP);
            parameterIPAddressString = bundle.getString(IP_ADDRESS_STRING);
            logInfo("DialogSetup readBundle() called ***** ");
            return true;
        }
        else {
            logInfo("DialogSetup readBundle() called but bundle null ***** ");
            return false;       //  should only happen if default constructor called.
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (readBundle(getArguments())) {
            logInfo("DialogSetupDetails.onCreateDialog readBundle() ok  *****"+parameterCalledAtStartup+" "+parameterIPAddressString);
        }

        //  Build string with bold/italics.
        SpannableStringBuilder ssb;
        if (parameterCalledAtStartup) {
            //  When called at startup I want to make it as brief as possible, so as not to scare away people.
            String sss = getResources().getString(R.string.ip_port_text);
            String sss2 = sss + getResources().getString(R.string.ip_port_text8);
            String sss3 = sss2 + getResources().getString(R.string.ip_port_text9);
            ssb = new SpannableStringBuilder(sss3);
            ssb.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), sss.length(), sss2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        }
        else {
            //  Provide a little more detail if called from menu, primarily IP and port values.
            String ipAddressHeader = getResources().getString(R.string.ip_port_IP_text);        // allocate these as strings so can use .length() method
            String portValueHeader = getResources().getString(R.string.ip_port_port_text);
            String lfSnippet = getResources().getString(R.string.lf);
            String sss = ipAddressHeader + " " + parameterIPAddressString + lfSnippet +
                    portValueHeader + " " + Integer.toString(WSJTXUtils.getDatagramPort(getContext())) +
                    getResources().getString(R.string.lflf) +
                    getResources().getString(R.string.ip_port_text) +
                    getResources().getString(R.string.ip_port_text2);
            String sss2 = sss + getResources().getString(R.string.ip_port_text9);
            ssb = new SpannableStringBuilder(sss2);
            ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, ipAddressHeader.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC),
                    ipAddressHeader.length() + parameterIPAddressString.length() + lfSnippet.length(),
                    ipAddressHeader.length() + parameterIPAddressString.length() + lfSnippet.length() + portValueHeader.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        builder.setTitle(R.string.ip_port_title)
                .setMessage(ssb)
                .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        logInfo("DialogSetup Dismiss *****");
                    }
                })
                .setNeutralButton(R.string.detail_help, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        logInfo("DialogIPPort Dismiss *****");
                        mListener.dialogSetupAskForDetails( DialogSetup.this );
                    }
                })
        ;
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

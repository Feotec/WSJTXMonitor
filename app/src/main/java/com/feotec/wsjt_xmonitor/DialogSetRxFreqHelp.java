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
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogSetRxFreqHelp extends DialogFragment {
    private static final String TAG = DialogSetRxFreqHelp.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSetRxFreqHelp;
    private static final String MESSAGE_INT = "message_int";

    private int messageInt;

    public static DialogSetRxFreqHelp newInstance(int messageIntParam ) {
        DialogSetRxFreqHelp fragment = new DialogSetRxFreqHelp();
        Bundle args = new Bundle();
        args.putInt(MESSAGE_INT, messageIntParam);
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            messageInt = bundle.getInt(MESSAGE_INT);
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogSetRxFreqHelpListener {
        void dialogSetRxFreqHelpSetupListener(DialogFragment dialog );
        void dialogSetRxFreqHelpDismissListener(DialogFragment dialog );
    }

    DialogSetRxFreqHelp.dialogSetRxFreqHelpListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSetRxFreqHelp.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSetRxFreqHelp.dialogSetRxFreqHelpListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogSetRxFreqHelp.dialogSetRxFreqHelpListener");
        }
    }

    //  This allows a link to be inserted in the text.  Remove this when it is not needed.s
    @Override
    public void onStart() {
        super.onStart();
        ((TextView) getDialog().findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (readBundle(getArguments())) {
            logInfo("DialogSetRxFreqHelp.onCreateDialog readBundle() ok  *****"); // cannot call logInfo() because in static class.
        }

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        CharSequence message;
        SpannableStringBuilder ssb = new SpannableStringBuilder("");
        if (messageInt == 0) {
            //  Normal help dialog
            message = getResources().getText(R.string.dialog_set_rx_freq_texta);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        } else if (messageInt == 1) {
            //  Dialog for no decodes yet.  It also must handle the introduction business.
            message = getResources().getText(R.string.dialog_set_rx_freq_text0);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_texta);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_text1);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        } else if (messageInt == 2) {
            //  Dialog for when WSJT-X did not respond to the command to change frequencies.
            message = getResources().getText(R.string.dialog_set_rx_freq_text2);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        } else if (messageInt == 3) {
            //  Dialog for decoding WSPR
            message = getResources().getText(R.string.dialog_set_rx_freq_text3);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        } else if (messageInt == 4) {
            //  Introduction, first time the icon has been pressed.
            message = getResources().getText(R.string.dialog_set_rx_freq_text0);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_texta);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        } else {
            //  default, normal help
            message = getResources().getText(R.string.dialog_set_rx_freq_texta);
            ssb.append(message);
            message = getResources().getText(R.string.dialog_set_rx_freq_textb);
            ssb.append(message);
        }
        builder.setMessage(ssb)
                .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        logInfo("DialogSetRxFreqHelp Dismiss *****");
                        mListener.dialogSetRxFreqHelpDismissListener( DialogSetRxFreqHelp.this );
                    }
                })
                .setNeutralButton(R.string.setup, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        logInfo( "DialogSetRxFreqHelp SETUP *****");
                        mListener.dialogSetRxFreqHelpSetupListener( DialogSetRxFreqHelp.this );
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }
}

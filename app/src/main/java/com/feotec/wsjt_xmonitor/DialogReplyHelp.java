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
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogReplyHelp extends DialogFragment {

    private static final String TAG = DialogReplyHelp.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogReplyHelp;

    public interface dialogReplyHelpListener {
        void dialogReplyHelpSetupListener(DialogReplyHelp dialog );
    }

    DialogReplyHelp.dialogReplyHelpListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogReplyHelp.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogReplyHelp.dialogReplyHelpListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogReplyHelp.dialogReplyHelpListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View replyView = inflater.inflate(R.layout.dialog_reply_help, null);

        builder.setTitle(R.string.dialog_reply_title);

        builder.setView( replyView );

        //  I have to put this in here instead of the layout file or else the link will not be clickable.
        TextView notesTextView = replyView.findViewById(R.id.reply_text2);
        CharSequence message = getResources().getText(R.string.dialog_reply_text2);
        notesTextView.setText(message);
        notesTextView.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogReplyHelp Dismiss *****");
            }
        });

        builder.setNeutralButton(R.string.setup, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogReplyHelp Setup *****");
                mListener.dialogReplyHelpSetupListener( DialogReplyHelp.this );
            }
        });

        return builder.create();
    }

    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

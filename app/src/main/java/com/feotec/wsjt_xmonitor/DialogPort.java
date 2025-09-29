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
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class DialogPort extends DialogFragment {

    private static final String TAG = DialogPort.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogPort;

    public interface dialogPortListener {
        void dialogPortDoneClick(DialogFragment dialog, String portInput);
        void dialogPortHelpClick(DialogFragment dialog);
    }

    DialogPort.dialogPortListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogPort.onAttach  *****");
        super.onAttach(activity);

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogPort.dialogPortListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogPortListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogPort.onCreateDialog  *****");
        AlertDialog.Builder builder;

        builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.port_message_title);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View portView = inflater.inflate(R.layout.dialog_port, null);

        final EditText portEditText = portView.findViewById(R.id.port_number);
        String portUsedStr = Integer.toString( WSJTXUtils.getDatagramPort( getContext() ) );
        portEditText.setText( portUsedStr, TextView.BufferType.EDITABLE );

        builder.setView( portView );

        builder.setPositiveButton( getString(R.string.done), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogPort Done  *****");
                mListener.dialogPortDoneClick( DialogPort.this, portEditText.getText().toString() );
            }
        });
        builder.setNegativeButton( getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogPort Cancel  *****");
            }
        });
        builder.setNeutralButton( getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogPort Help  *****");
                mListener.dialogPortHelpClick( DialogPort.this );
            }
        });


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

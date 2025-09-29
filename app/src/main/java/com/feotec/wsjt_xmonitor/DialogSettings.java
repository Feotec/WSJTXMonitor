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
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

public class DialogSettings extends DialogFragment {
    private static final String TAG = DialogSettings.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSettings;
    private CharSequence[] charSeq = new CharSequence[6];

    public interface dialogSettingsListener {
        void dialogSettingsSelectionListener(DialogFragment dialog, int which );
        void dialogSettingsHelpListener(DialogFragment dialog );
    }

    DialogSettings.dialogSettingsListener mListener;

    @Override
    public void onAttach(@NonNull Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSettings.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSettings.dialogSettingsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogSettings.dialogSettingsListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        try {
            Window window = Objects.requireNonNull(getDialog()).getWindow();
            assert window != null;
            window.setBackgroundDrawableResource(R.drawable.rect_box_gradient);
        } catch (ClassCastException e) {
            logInfo("DialogSettings.onStart() unable to load drawable.");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogSettings.onCreateDialog  *****");

        //boolean milesNotKm = WSJTXUtils.getMilesNotKm( getContext() );
        charSeq[0] = getText(R.string.dialog_settings_shutdown);
        charSeq[1] = getText(R.string.dialog_settings_clear_screen);
        charSeq[2] = getText(R.string.dialog_settings_km_miles);
        charSeq[3] = getText(R.string.dialog_settings_status);
        charSeq[4] = getText(R.string.dialog_settings_port);
        charSeq[5] = getText(R.string.dialog_settings_setup_WSJTX);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_settings_title);
        builder.setItems( charSeq, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        logInfo("DialogSettings choice: "+which+" *****");
                        mListener.dialogSettingsSelectionListener( DialogSettings.this, which );
                    }   // end of onClick()
                } // end of onClickListener()
        );  // end of setItems()
        builder.setPositiveButton(getResources().getString(R.string.dismiss), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSettings Done *****");
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSettings Help  *****");
                //mListener.dialogFilterAlertsHelpListener( DialogFilterAlerts.this, doingFilters );
            }
        });

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        final AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog != null) {
            //  This code prevents the neutral button (which I'm using for HELP) from closing the dialog.  Extensive notes are in several other dialogs.
            Button neutralButton = dialog.getButton( Dialog.BUTTON_NEUTRAL );
            neutralButton.setOnClickListener( new View.OnClickListener() {
                                                  @Override
                                                  public void onClick( View v) {
                                                      logInfo("DialogSettings Neutral View.OnClickListener() *****");
                                                      mListener.dialogSettingsHelpListener( DialogSettings.this );
                                                      //dialog.dismiss();       //  remove this line to avoid dismissing dialog.
                                                  }
                                              }
            );
        }
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

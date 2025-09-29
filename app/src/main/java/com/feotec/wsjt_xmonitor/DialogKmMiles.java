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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogKmMiles extends DialogFragment {

    private static final String TAG = DialogKmMiles.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogKmMiles;

    private static int checkedItem = -1;

    public interface dialogKmMilesListener {
        void dialogKmMilesDoneListener(DialogFragment dialog, boolean milesNotKm, boolean showDontMessage );
    }

    DialogKmMiles.dialogKmMilesListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogKmMiles.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogKmMiles.dialogKmMilesListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogKmMiles.dialogKmMilesListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogKmMiles.onCreateDialog  *****");

        //  When the screen is rotated this method is called again which means that builder.setSingleChoiceItems() is called again but what is passed as the second parameter
        //      (checkedItem) is ignored.  For this reason checkedItem is made static and initialized to an invalid value (-1).  It is only initialized to a valid number
        //      if it is found to be set to -1, indicating that this is the first time this method has been run.  If this method is run and checkedItem != -1 then it means
        //      that the screen has been rotated.  Since checkedItem is static it is preserved.  If Cancel button pressed then checkedItem is set back to -1.  If Done button
        //      pressed then the code checks to see if the selection has changed and, if so, rewrites setting to SharedPref and then calls listener.  Finally it sets
        //      checkedItem back to -1 in anticipation of the next time.
        if (checkedItem == -1) {
            boolean milesNotKm1 = WSJTXUtils.getMilesNotKm(getContext());
            checkedItem = 0;
            if (milesNotKm1) {
                checkedItem = 1;
            }
        }

        CharSequence[] charSeq = new CharSequence[2];
        charSeq[0] = getText(R.string.dialog_distance_km);
        charSeq[1] = getText(R.string.dialog_distance_miles);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle( getText(R.string.dialog_distance_title));
        builder.setSingleChoiceItems(charSeq, checkedItem, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        checkedItem = which;
                        logInfo("DialogKmMiles choice " + checkedItem + " *****");
                    }   // end of onClick()
                } // end of onClickListener()
        );  // end of setItems()
        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogKmMiles Cancel *****");
                checkedItem = -1;
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogKmMiles Done *****");
                boolean showDontMessage = false;
                boolean milesNotKm1 = WSJTXUtils.getMilesNotKm(getContext());   //  get current setting in milesNotKm1
                boolean milesNotKm2 = false;
                if (checkedItem == 1) {
                    milesNotKm2 = true;
                }                                                               //  get new setting in milesNotKm2
                if (milesNotKm1 != milesNotKm2) {
                    WSJTXUtils.setMilesNotKm(getContext(), milesNotKm2);        //  if different then change setting in SharedPref
                    showDontMessage = true;                                     //  ... and tell listener to show dont-Show-again message
                }
                mListener.dialogKmMilesDoneListener( DialogKmMiles.this, milesNotKm2, showDontMessage );
                checkedItem = -1;                                               //  set static checkedItem back to default value.
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

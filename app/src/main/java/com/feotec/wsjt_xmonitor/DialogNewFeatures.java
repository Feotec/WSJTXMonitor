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
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

//  This was a static class in MainActivity (v1.29, versionCode 9).  However, I needed to add a listener and I had to make
//      it a separate class in order to do that.
public class DialogNewFeatures extends DialogFragment {

    private static final String TAG = DialogNewFeatures.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogNewFeatures;

    public interface dialogNewFeaturesListener {
        void dialogNewFeaturesDismiss(DialogFragment dialog);
    }

    DialogNewFeatures.dialogNewFeaturesListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        super.onAttach(activity);
        logInfo("DialogEULA.onAttach  *****");

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogNewFeatures.dialogNewFeaturesListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogEULAListener");
        }
    }

    //  This allows a link to be inserted in the text.  Remove this when it is not needed.
    @Override
    public void onStart() {
        super.onStart();
        ((TextView) getDialog().findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        /*

        //    Version where I use a custom layout.  This is because I wanted to include a graphic of the red arrow.  Note that
        //      when I did this I also had two strings, new_features_text and new_features_text2.  They were enumerated in the
        //      layout file.  THe second is referred to here because I needed to call setMovementMethod(0 in order to provide
        //      a link.

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View newFeaturesView = inflater.inflate(R.layout.dialog_new_features, null);

        builder.setTitle( getResources().getString(R.string.new_features_title)+" "+ WSJTXUtils.getHardcodedVersionString(getContext()) );
        builder.setView( newFeaturesView );

        //  Did this so I can put a link in the text.  Since I'm using a custom layout the code in onStart() above won't work.
        TextView newFeaturesText2 = newFeaturesView.findViewById(R.id.dialog_new_features_text2);
        newFeaturesText2.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogNewFeatures Dismiss *****");
                mListener.dialogNewFeaturesDismiss( DialogNewFeatures.this );
            }
        });

        return builder.create();
        */

        //    Version where I didn't use a custom layout.

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle( getResources().getString(R.string.new_features_title)+" "+ WSJTXUtils.getHardcodedVersionString(getContext()) );
        builder.setMessage(R.string.new_features_text);
        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogNewFeatures Dismiss *****");
                mListener.dialogNewFeaturesDismiss( DialogNewFeatures.this );
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


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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class DialogSetupDetails extends DialogFragment {
    private static final String TAG = DialogSetupDetails.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSetupDetails;

    private static final String TITLE_TEXT = "title_text";
    private static final String HEADER_TEXT = "header_text";
    private static final String IMAGE_REF = "image_ref";
    private static final String BUTTON_TEXT = "button_text";
    private static final String COUNTER = "counter";
    private static final String SPECIAL_STRING = "special_string";
    private static final String DO_CANCEL = "do_cancel";

    private int parameterTitle;
    private int paramaterHeader;
    private int parameterImage;
    private int parameterButton;
    private int parameterCounter;
    private String parameterSpecialString;
    private boolean parameterDoCancel;

    //
    //  Parameters
    //      param1Title - integer (for string resource), loaded into parameterTitle
    //      param2Header - integer (for string resource, loaded into paramaterHeader
    //      param3Image - integer (for image resource), loaded into parameterImage
    //      param5Button - integer (for string resource), loaded into parameterButton.  This loads either "Next" or "Finish"
    //      param6Counter - integer, loaded into parameterCounter.  Same value as BaseActivity.dialogSetupDetailsStepNumber.  Has value from 0-3.  If 0 then Back
    //          button is not displayed.  If 3 then a different layout file is inflated for JTAlert.  Its value is passed to the Next (parameterCounter+1) and
    //          Back (parameterCounter-1) button listeners.  In BaseActivity this passed value is loaded into dialogSetupDetailsStepNumber.
    //      param7Special - String, loaded into parameterSpecialString.  Null except when param6Counter is 2.  In that case it has the IP address and port string.
    //          It is used in place pf parameterHeader (sort of a kludge, explained again in readBundle() below).
    //      paramDoCancel - boolean, loaded int parameterDoCancel.  If set a Cancel button will be available.  Used to force the user to go through the four step setup
    //          upon a new install.
    //
    public static DialogSetupDetails newInstance(int param1Title,
                                                 int param2Header, int param3Image, int param5Button,
                                                 int param6Counter, String param7Special, boolean paramDoCancel ) {
        if (DO_LOGGING) {
            Log.i(TAG, "newInstance() called *****");   // cannot call logInfo() because in static method.
        }
        DialogSetupDetails fragment = new DialogSetupDetails();
        Bundle args = new Bundle();
        args.putInt(TITLE_TEXT, param1Title);
        args.putInt(HEADER_TEXT, param2Header);
        args.putInt(IMAGE_REF, param3Image);
        args.putInt(BUTTON_TEXT,param5Button);
        args.putInt(COUNTER,param6Counter);
        args.putString(SPECIAL_STRING,param7Special);
        args.putBoolean(DO_CANCEL,paramDoCancel);
        fragment.setArguments(args);
        return fragment;
    }

    public interface dialogSetupDetailsListener {
        void dialogSetupDetailsNext(DialogFragment dialog, int newCounter);
        void dialogSetupDetailsBack(DialogFragment dialog, int newCounter);
        void dialogSetupDetailsCancel(DialogFragment dialog);
    }

    DialogSetupDetails.dialogSetupDetailsListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSetupDetails.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSetupDetails.dialogSetupDetailsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogSetupDetailsListener");
        }
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            //  Note that the first four parameters are int but they refer to a string or (in the case of parameterImage) an image.
            //      parameterCounter is the BaseActivity.dialogSetupDetailsStepNumber (used when back or next button is pressed).
            //      parameterSpecialString is a string.  This is because on the third page parameterHeader needed to be a string which
            //      included the IP and port so it couldn't be passed as a simple integer.
            parameterTitle = bundle.getInt(TITLE_TEXT);
            paramaterHeader = bundle.getInt(HEADER_TEXT);
            parameterImage = bundle.getInt(IMAGE_REF);
            parameterButton = bundle.getInt(BUTTON_TEXT);
            parameterCounter = bundle.getInt(COUNTER);
            parameterSpecialString = bundle.getString(SPECIAL_STRING);
            parameterDoCancel = bundle.getBoolean(DO_CANCEL);
            logInfo("readBundle() called ***** ");
            return true;
        }
        else {
            logInfo("readBundle() called but bundle null ***** ");
            return false;       //  should only happen if default constructor called.
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogSetupDetails.onCreateDialog  *****");
        AlertDialog.Builder builder;

        readBundle(getArguments());
        /*
        if (readBundle(getArguments())) {
            logInfo("DialogSetupDetails.onCreateDialog readBundle() ok  *****"+
                    getResources().getString(parameterTitle)+" "+
                    getResources().getString(paramaterHeader) );
        }
        */

        builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogSetupDetailsView;
        if ( parameterCounter == 3 ) {
            //  The last page uses a different layout which only contains a single TextView.
            dialogSetupDetailsView = inflater.inflate(R.layout.dialog_setup_details_jtalert, null);
            TextView headerView = dialogSetupDetailsView.findViewById(R.id.jtalert_header_text);
            headerView.setMovementMethod(LinkMovementMethod.getInstance());
            headerView.setText(paramaterHeader);
        }
        else {
            dialogSetupDetailsView = inflater.inflate(R.layout.dialog_setup_details, null);
            //  Do text view
            TextView headerView = dialogSetupDetailsView.findViewById(R.id.scrollHeaderText);
            if (parameterSpecialString.length() == 0) {     //  See comment in readBundle() above about parameterSpecialString.
                headerView.setText(paramaterHeader);
            }
            else {
                headerView.setText(parameterSpecialString);
            }
            //  Do image
            ImageView imageView = dialogSetupDetailsView.findViewById(R.id.scrollImageView);
            imageView.setImageResource(parameterImage);
        }

        builder.setTitle( getResources().getString(parameterTitle) );
        builder.setView( dialogSetupDetailsView );

        builder.setPositiveButton( getResources().getString(parameterButton), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSetupDetails Positive  *****");
                mListener.dialogSetupDetailsNext( DialogSetupDetails.this, parameterCounter+1 );
            }
        });
        if (parameterCounter > 0) {
            builder.setNegativeButton(getResources().getString(R.string.back), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    logInfo("DialogSetupDetails Negative  *****");
                    mListener.dialogSetupDetailsBack(DialogSetupDetails.this, parameterCounter - 1);
                }
            });
        }
        if (parameterDoCancel) {
            builder.setNeutralButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    logInfo("DialogSetupDetails Neutral  *****");
                    mListener.dialogSetupDetailsCancel(DialogSetupDetails.this);
                }
            });
        }

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        final AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog != null) {
            if (parameterCounter > 0) {
                // This code captures the back button.  It calls the same listeners as the back button above.  Except here it is necessary to explicitly dismiss the dialog.
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                            logInfo("DialogSetupDetails setOnKeyListener() BACK button pressed *****");
                            mListener.dialogSetupDetailsBack(DialogSetupDetails.this, parameterCounter - 1);
                            dialog.dismiss();
                        }
                        return false;
                    }
                });
            }
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

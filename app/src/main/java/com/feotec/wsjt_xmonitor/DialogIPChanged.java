package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;

public class DialogIPChanged extends DialogFragment {
    private static final String TAG = DialogIPChanged.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogIPChanged;
    private static final String IP_ADDRESS_STRING = "ip_address_string";

    private String newIpAddressString;

    //
    //  Parameters
    //      parameter2IP - string, loaded into newIpAddressString.  Only used if parameterCalledAtStartup is false.
    //
    public static DialogIPChanged newInstance( String parameterIP ) {
        if (DO_LOGGING) {
            Log.i(TAG, "DialogIPChanged newInstance() called *****");
        }
        DialogIPChanged fragment = new DialogIPChanged();
        Bundle args = new Bundle();
        args.putString(IP_ADDRESS_STRING,parameterIP);
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            newIpAddressString = bundle.getString(IP_ADDRESS_STRING);
            logInfo("DialogIPChanged readBundle() called ***** ");
            return true;
        }
        else {
            logInfo("DialogIPChanged readBundle() called but bundle null ***** ");
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogIPChangedListener {
        void dialogIPChangedHelp(DialogFragment dialog);
    }

    DialogIPChanged.dialogIPChangedListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogIPChanged.onAttach  *****");
        super.onAttach(activity);

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogIPChanged.dialogIPChangedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogIPChangedListener");
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

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (readBundle(getArguments())) {
            logInfo("DialogIPChanged.onCreateDialog readBundle() ok  ***** "+newIpAddressString);
        }

        builder.setTitle( getResources().getString(R.string.dialog_ip_changed_title) );

        int oldIPHash = WSJTXUtils.getIpHashCode(getContext());
        String oldIpAddressString = String.format(Locale.US,"%d.%d.%d.%d",
                ( (oldIPHash >> 24) & 0x000000ff ),     //  Since there is no unsigned int in Java have to shift right before masking.  Otherwise will get a negative number.
                ( (oldIPHash & 0x00ff0000) >> 16 ),
                ( (oldIPHash & 0x0000ff00) >> 8 ),
                ( (oldIPHash & 0x000000ff) )
        );
        //  Using a SpannableStringBuilder so that I can embed HTML bold and italics in the text from strings.xml.  I tried making this a CharSequence
        //      but appending one String class (oldIpAddressString) will make the CharSequence a String.
        SpannableStringBuilder message = new SpannableStringBuilder(getResources().getText(R.string.dialog_ip_changed_text) + oldIpAddressString );
        message.append(getResources().getText(R.string.dialog_ip_changed_text2)).append(newIpAddressString).append(getResources().getText(R.string.dialog_ip_changed_text3));

        builder.setMessage(message);

        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogIPChanged Dismiss *****");
                //DialogFragment testFrag = new DialogIPChangedWarn();
                //testFrag.show(getActivity().getSupportFragmentManager(), "DialogIPChangedWarn");
                //testFrag.setCancelable(false);
            }
        });
        builder.setNeutralButton(R.string.help, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogIPChanged Help *****");
                mListener.dialogIPChangedHelp( DialogIPChanged.this );
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

    /*
        This was left here just to show that it is possible to embed a dialog within a dialog

    //  Static help class for DialogSettings help.
    public static class DialogIPChangedWarn extends DialogFragment {
        private static final String TAG = DialogIPChangedWarn.class.getSimpleName();

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            CharSequence message = getResources().getText(R.string.dialog_ip_changed_warn);
            builder.setMessage(message)
                    .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (DO_LOGGING) {
                                Log.i(TAG, "DialogMsgOnlyNoTitle OK *****");
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
    */

}

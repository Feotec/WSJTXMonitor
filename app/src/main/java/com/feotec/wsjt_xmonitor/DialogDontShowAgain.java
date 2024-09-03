package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogDontShowAgain extends DialogFragment {

    private static final String TAG = DialogDontShowAgain.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogDontShowAgain;

    private static final String KEY_TEXT1 = "key_text1";
    private static final String KEY_TEXT2 = "key_text2";
    private static final String KEY_WHICH_DONT_SHOW = "key_which_dont_show";

    private int text1;
    private int text2;
    private int whichDontShow;

    //  The parameters:
    //      text1Param - the title text - set to 0 for no title
    //      text2Param - the text of the message body
    //      whichDontShowParam - an integer indicating which function to call when user checks the don't-show-this-message-again box (0 for clear screen)
    public static DialogDontShowAgain newInstance( int text1Param, int text2Param, int whichDontShowParam ) {
        DialogDontShowAgain fragment = new DialogDontShowAgain();
        Bundle args = new Bundle();
        args.putInt( KEY_TEXT1, text1Param );
        args.putInt( KEY_TEXT2, text2Param );
        args.putInt( KEY_WHICH_DONT_SHOW, whichDontShowParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            text1 = bundle.getInt( KEY_TEXT1 );
            text2 = bundle.getInt( KEY_TEXT2 );
            whichDontShow = bundle.getInt( KEY_WHICH_DONT_SHOW );
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        if (readBundle(getArguments())) {
            logInfo("DialogDontShowAgain.onCreateDialog readBundle() ok  *****");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dontShowAgainView = inflater.inflate(R.layout.dialog_dont_show_again, null);

        if (text1 != 0) {
            builder.setTitle(text1);
        }

        builder.setView( dontShowAgainView );

        TextView dontTextView = dontShowAgainView.findViewById(R.id.dont_show_again_text);
        dontTextView.setText( getText( text2 ) );
        dontTextView.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogDontShowAgain Dismiss *****");
            }
        });

        CheckBox neverShowDialog = (CheckBox) dontShowAgainView.findViewById(R.id.dont_show_again_checkBox);
        neverShowDialog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                // Save the preference
                logInfo( "DialogDontShowAgain Checkbox "+isChecked+" *****");
                if (whichDontShow == 0) {
                    WSJTXUtils.setClearScreenDontShowAgain(getContext(), isChecked);
                } else if (whichDontShow == 1) {
                    WSJTXUtils.setKmMilesDontShowAgain( getContext(), isChecked );
                } else if (whichDontShow == 2) {
                    WSJTXUtils.setBothAppsDontShowAgain( getContext(), isChecked );
                } else if (whichDontShow == 3) {
                    WSJTXUtils.setSortingDontShowAgain( getContext(), isChecked );
                }
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

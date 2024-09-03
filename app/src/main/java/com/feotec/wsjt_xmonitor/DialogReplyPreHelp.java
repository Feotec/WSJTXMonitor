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

public class DialogReplyPreHelp extends DialogFragment {

    private static final String TAG = DialogReplyPreHelp.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogReplyPreHelp;

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View replyView = inflater.inflate(R.layout.dialog_reply_prehelp, null);

        builder.setTitle(R.string.dialog_reply_title);

        builder.setView( replyView );

        builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogReplyPreHelp Dismiss *****");
            }
        });

        CheckBox neverShowDialog = (CheckBox) replyView.findViewById(R.id.reply_pre_checkBox);
        neverShowDialog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                // Save the preference
                logInfo( "DialogReplyPreHelp Checkbox "+isChecked+" *****");
                WSJTXUtils.setReplyHelpDontShowAgain( getContext(), isChecked );
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

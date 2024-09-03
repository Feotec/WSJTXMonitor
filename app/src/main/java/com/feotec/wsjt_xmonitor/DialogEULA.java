package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Objects;


public class DialogEULA extends DialogFragment {

    private static final String TAG = DialogEULA.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogEULA;

    public interface dialogEULAListener {
        void dialogEULAOkClick(DialogFragment dialog);
    }

    private DialogEULA.dialogEULAListener mListener;

    @Override
    public void onAttach(@NonNull Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogEULA.onAttach  *****");
        super.onAttach(activity);

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogEULA.dialogEULAListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogEULAListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((TextView) Objects.requireNonNull(getDialog()).findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setMessage(R.string.eula_message)
                .setTitle(getString(R.string.eula_title))
                .setPositiveButton( getString(R.string.okay), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        logInfo("DialogEULA Done  *****");
                        mListener.dialogEULAOkClick( DialogEULA.this );
                    }})
        ;

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

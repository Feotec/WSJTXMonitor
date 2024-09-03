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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class DialogAbout extends DialogFragment {
    private static final String TAG = DialogAbout.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogAbout;

    public interface dialogAboutListener {
        void dialogAboutNegativeClick(DialogFragment dialog);
        void dialogAboutPositiveClick(DialogFragment dialog);
    }

    DialogAbout.dialogAboutListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogAbout.onAttach  *****");
        super.onAttach(activity);

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogAbout.dialogAboutListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement dialogAboutListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogAbout.onCreateDialog  *****");
        AlertDialog.Builder builder;

        builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View aboutView = inflater.inflate(R.layout.dialog_about, null);

        builder.setView( aboutView );

        TextView versionTextView = aboutView.findViewById(R.id.version_view);
        String versionString = getString(R.string.about_version)+" "+ WSJTXUtils.getHardcodedVersionString( getContext() );
        versionTextView.setText(versionString);

        TextView notesTextView = aboutView.findViewById(R.id.notes_view);
        CharSequence message = getResources().getText(R.string.about_notes);
        notesTextView.setText(message);
        notesTextView.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setPositiveButton( getString(R.string.dismiss), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogAbout Done  *****");
                mListener.dialogAboutPositiveClick( DialogAbout.this );
            }
        });
        builder.setNegativeButton( getString(R.string.about_view_eula), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogAbout Done  *****");
                mListener.dialogAboutNegativeClick( DialogAbout.this );
            }
        });


        return builder.create();
    }

    @Override
    public void onStop() {
        super.onStop();
        logInfo("DialogAbout onStop()  *****");
    }



    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

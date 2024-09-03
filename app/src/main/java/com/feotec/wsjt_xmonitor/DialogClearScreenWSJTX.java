package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class DialogClearScreenWSJTX extends DialogFragment {

    private static final String TAG = DialogClearScreenWSJTX.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogClearScreenWSJTX;

    private static int checkedItem = -1;
    private static boolean didntViewHelp = true;    //  a static variable to handle screen rotation.  The user may view help, then rotate the screen, then hit dismiss.
                                                    //      Under these conditions the dont-show-again message would still appear.

    public interface dialogClearScreenListener {
        void dialogClearScreenWSJTXDoneListener(DialogFragment dialog, boolean clearScreen, boolean didntViewHelp );
        void dialogClearScreenWSJTXHelpListener(DialogFragment dialog);
    }

    DialogClearScreenWSJTX.dialogClearScreenListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogClearScreenWSJTX.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogClearScreenWSJTX.dialogClearScreenListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogClearScreenWSJTX.dialogClearScreenListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogClearScreenWSJTX.onCreateDialog  *****");

        if (checkedItem == -1) {
            boolean clearScreen = WSJTXUtils.getClearScreenFromWSJTX(getContext());
            checkedItem = 0;
            if (clearScreen) {
                checkedItem = 1;
            }
        }
        CharSequence[] charSeq = new CharSequence[2];
        charSeq[0] = getText(R.string.disable);
        charSeq[1] = getText(R.string.enable);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle( getText(R.string.dialog_clear_screen_WSJTX_title));
        builder.setSingleChoiceItems(charSeq, checkedItem, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        checkedItem = which;
                        logInfo("DialogClearScreenWSJTX choice " + checkedItem + " *****");
                    }   // end of onClick()
                } // end of onClickListener()
        );  // end of setItems()
        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogClearScreenWSJTX Cancel *****");
                checkedItem = -1;
                didntViewHelp = true;
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogClearScreenWSJTX Done *****");
                boolean clearScreen1 = WSJTXUtils.getClearScreenFromWSJTX(getContext());
                boolean clearScreen2 = false;
                if (checkedItem == 1) {
                    clearScreen2 = true;
                }
                if (clearScreen1 != clearScreen2) {
                    WSJTXUtils.setClearScreenFromWSJTX( getContext(), clearScreen2 );
                }
                mListener.dialogClearScreenWSJTXDoneListener( DialogClearScreenWSJTX.this, clearScreen2, didntViewHelp );
                checkedItem = -1;
                didntViewHelp = true;
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogClearScreenWSJTX Help  *****");
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
            //  This code prevents the neutral button (which I'm using for HELP) from closing the dialog.  It overrides the neutral button OnClickListener() above.
            //      The AlertDialog's onCreate() is called after the .show() (below).  So this change has to be done later in the lifecycle.  Note that onResume()
            //      will be called on a screen rotation, another reason to place it in onResume().
            //  It seems that .setButton(), setPositiveButton(), .setNeutralButton() etc actually schedule two OnClickListeners().  The first is the
            //      DialogInterface.OnClickListener() above.  The second is the View.OnClickListener(), which dismisses the dialog.  I'm overriding the second.
            //      However, after doing this, DialogInterface.OnClickListener() (the normal help button callback) won't be called.
            //  See SO https://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked.
            Button neutralButton = dialog.getButton( Dialog.BUTTON_NEUTRAL );
            neutralButton.setOnClickListener( new View.OnClickListener() {
                                                  @Override
                                                  public void onClick( View v) {
                                                      logInfo("DialogClearScreenWSJTX Neutral View.OnClickListener() *****");
                                                      didntViewHelp = false;
                                                      mListener.dialogClearScreenWSJTXHelpListener( DialogClearScreenWSJTX.this );
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

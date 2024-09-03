package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class DialogSorting extends DialogFragment {
    private static final String TAG = DialogSorting.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSorting;

    private boolean[] checkedItems = { false, false, false, false };
    protected CharSequence[] charSeq = new CharSequence[4];
    protected int selection = -1;

    public interface dialogSortingListener {
        void dialogSortingNextListener(DialogSorting dialog );
        void dialogSortingCancelListener(DialogSorting dialog );
        void dialogSortingHelpListener(DialogSorting dialog );
    }

    DialogSorting.dialogSortingListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSorting.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSorting.dialogSortingListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogSorting.dialogSortingListener");
        }
    }


    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        logInfo( "DialogSorting.onCreateDialog  *****");

        //  Prepare title
        SpannableStringBuilder ssb = new SpannableStringBuilder(getString(R.string.dialog_sorting_title));
        int endOfLargeFont = getResources().getInteger(R.integer.sorting_end_of_large_font);
        ssb.setSpan(new RelativeSizeSpan(1.2f), 0, endOfLargeFont, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        charSeq[0] = getText(R.string.dialog_sorting_snr);
        charSeq[1] = getText(R.string.dialog_sorting_freq);
        charSeq[2] = getText(R.string.dialog_sorting_distance);
        charSeq[3] = getText(R.string.dialog_sorting_azimuth);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(ssb);
        builder.setMultiChoiceItems( charSeq, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedItems[which] = isChecked;

                        if (atLeastOneItemChecked()) {
                            //  If at least one item checked then enable positive (Finish) button ....
                            ((AlertDialog)dialog).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(true);
                            //  ... and clear any other button that might be checked.
                            for (int iii = 0; iii < 4; iii++) {
                                if (iii == which) {
                                    continue;
                                }
                                checkedItems[iii] = false;
                                ((AlertDialog)dialog).getListView().setItemChecked(iii, false);
                            }
                            selection = which;
                        }
                        else {
                            //  If no items are checked then disable positive (Finish) button.
                            ((AlertDialog)dialog).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(false);
                        }

                        String chosenString = charSeq[which].toString();
                        logInfo( "DialogSorting chose: "+chosenString+" "+which+" *****");
                    }   // end of onClick()s
                } // end of onClickListener()
        );  // end of setItems()

        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //System.arraycopy( previousCheckedItems, 0, checkedItems, 0, 4);  // from previousCheckedItems to CheckedItems
                logInfo( "DialogSorting Negative *****");
                mListener.dialogSortingCancelListener( DialogSorting.this );
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.finish), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //System.arraycopy( checkedItems, 0, previousCheckedItems, 0 , 4);  // from checkedItems to previousCheckedItems
                logInfo( "DialogSorting Positive *****");
                mListener.dialogSortingNextListener( DialogSorting.this );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSorting Help  *****");
                mListener.dialogSortingHelpListener( DialogSorting.this );
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
            //      However, after doing this, DialogInterface.OnClickListener() won't be called.
            //  See SO https://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked.
            Button neutralButton = dialog.getButton( Dialog.BUTTON_NEUTRAL );
            neutralButton.setOnClickListener( new View.OnClickListener() {
                                                  @Override
                                                  public void onClick( View v) {
                                                      logInfo("DialogSorting Neutral View.OnClickListener() *****");
                                                      mListener.dialogSortingHelpListener( DialogSorting.this );
                                                      //dialog.dismiss();       //  remove this line to avoid dismissing dialog.
                                                  }
                                              }
            );

            if (!atLeastOneItemChecked()) {
                Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
                positiveButton.setEnabled(false);
            }
        }
    }

    private boolean atLeastOneItemChecked() {
        boolean returnValue = false;
        for (int iii = 0; iii < 4; iii++) {
            if (checkedItems[iii]) {
                returnValue = true;
                break;
            }
        }
        return returnValue;
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

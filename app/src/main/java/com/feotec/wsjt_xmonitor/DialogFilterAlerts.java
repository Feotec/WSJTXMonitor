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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

//  This class was meant to be a generic dialog for Filters and Alerts.  It handles the select-from-list type of dialog.
public class DialogFilterAlerts extends DialogFragment {
    private static final String TAG = DialogFilterAlerts.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogFilterAlerts;

    private static final String SELECT_ALL = "select_all";
    private static final String CHAR_SEQ = "character_sequence";
    private static final String CHECKED_ITEMS = "checked_items";
    private static final String TITLE_TEXT = "title_text";
    private static final String LARGE_FONT = "large_font";
    private static final String MIDDLE_TEXT = "middle_text";
    private static final String RIGHT_TEXT = "right_text";
    private static final String DOING_FILTERS = "doing_filters";

    private boolean selectAll;
    protected int sizeOfArrays;
    private CharSequence[] charSeq;
    protected boolean[] checkedItems;
    private String titleText;
    private int endOfLargeFont = -1;  //  if the title should have smaller font subtext below it then this is the beginning of the subtext.
    private int middleButtonText;
    private int rightButtonText;
    private boolean doingFilters;

    private int arraySizeUsed;
    protected boolean[] checkedItemsUsed;
    private CharSequence[] charSeqUsed;

    //  The parameters:
    //      charSeqParam[] - the text displayed for each selection stored in a CharSequence[] array.
    //      checkedItemsParam[] - a boolean array indicating which item has been selected.
    //      titleParameter - A string for the title.  This is typically more than one line.  The first is in large bold font.  The remaining text is not.
    //      endOfLargeFontParameter - The string index of titleParameter where the large bold font ends.
    //      titleParameter - an integer representing the string resource of the middle button (often "Clear All")
    //      rightButtonTextParam - an integer representing the string resource of the right button ("Next", "Finish", "OK")
    //          NOTE that the left button is always help.
    //      doingFiltersParam - set to true if doing filters, false if doing alerts.  Passed to listeners.
    public static DialogFilterAlerts newInstance(boolean selectAllParam, CharSequence[] charSeqParam, boolean[] checkedItemsParam,
                                                 String titleParameter, int endOfLargeFontParameter,
                                                 int middleButtonTextParam, int rightButtonTextParam,
                                                 boolean doingFilterParam ) {
        DialogFilterAlerts fragment = new DialogFilterAlerts();
        Bundle args = new Bundle();
        args.putBoolean( SELECT_ALL, selectAllParam );
        args.putCharSequenceArray( CHAR_SEQ, charSeqParam );
        args.putBooleanArray( CHECKED_ITEMS, checkedItemsParam );
        args.putString( TITLE_TEXT, titleParameter );
        args.putInt( LARGE_FONT, endOfLargeFontParameter );
        args.putInt( MIDDLE_TEXT, middleButtonTextParam );
        args.putInt( RIGHT_TEXT, rightButtonTextParam );
        args.putBoolean( DOING_FILTERS, doingFilterParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            selectAll = bundle.getBoolean(SELECT_ALL);
            charSeq = bundle.getCharSequenceArray(CHAR_SEQ);
            checkedItems = bundle.getBooleanArray(CHECKED_ITEMS);
            titleText = bundle.getString( TITLE_TEXT );
            endOfLargeFont = bundle.getInt( LARGE_FONT );
            middleButtonText = bundle.getInt( MIDDLE_TEXT );
            rightButtonText = bundle.getInt( RIGHT_TEXT );
            doingFilters = bundle.getBoolean( DOING_FILTERS );
            sizeOfArrays = checkedItems.length;
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogFilterAlertsListener {
        void dialogFilterAlertsNextListener(DialogFragment dialog, boolean doingFilters );
        void dialogFilterAlertsCancelListener(DialogFragment dialog, boolean doingFilters );
        void dialogFilterAlertsHelpListener(DialogFragment dialog, boolean doingFilters );
        void dialogFilterAlertsSelectionListener(DialogFragment dialog, int which, boolean doingFilters );
        void dialogFilterAlertsBackButtonListener(DialogFragment dialog, boolean doingFilters );
    }

    DialogFilterAlerts.dialogFilterAlertsListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogFilterAlerts.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogFilterAlerts.dialogFilterAlertsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogFilterAlerts.dialogFilterAlertsListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo( "DialogFilterAlerts.onCreateDialog  *****");

        if (readBundle(getArguments())) {
            logInfo("DialogFilterAlerts.onCreateDialog readBundle() ok  *****");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(titleText);
        if ( (endOfLargeFont != -1) && (endOfLargeFont < titleText.length()) ) {
            ssb.setSpan(new RelativeSizeSpan(1.2f), 0, endOfLargeFont, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        //  This block of code was added to allow a "Select All" or "Deselect All" option at the top of the countries menus.  How it works is that I created a
        //      arraySizeUsed, charSeqUsed[], and checkedItemsUsed[].  If selectAll == false then arraySizeUsed = arraySize, charSeqUsed[] = charSeq[], and
        //      checkedItemsUsed[] = checkedItems[].  If selectAll == true then arraySizeUsed = arraySize+1 and the two arrays have an extra element (the "Select All")
        //      element) added to the front.
        arraySizeUsed = sizeOfArrays;
        if (selectAll) {
            arraySizeUsed++;
        }
        charSeqUsed = new CharSequence[arraySizeUsed];
        checkedItemsUsed = new boolean[arraySizeUsed];
        int iii = 0;
        int jjj = 0;
        if (selectAll) {
            iii++;
        }
        while (iii < arraySizeUsed) {                   // copy charSeq[] to charSeqUsed[] and checkedItems[] to checkedItemsUsed[].
            charSeqUsed[iii] = charSeq[jjj];            //      If selectAll == true then index 0 will not be populated in this loop.
            checkedItemsUsed[iii] = checkedItems[jjj];
            iii++; jjj++;
        }
        if (selectAll) {
            if (allItemsChecked()) {            //  if selectAll == true then populate index 0 of checkedItemsUsed[] and charSeqUsed[].
                checkedItemsUsed[0] = true;
                charSeqUsed[0] = getResources().getText(R.string.unselect_all);
            } else {
                checkedItemsUsed[0] = false;
                charSeqUsed[0] = getResources().getText(R.string.select_all);
            }
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(ssb);
        builder.setMultiChoiceItems( charSeqUsed, checkedItemsUsed, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (selectAll) {
                            //  If selectAll enabled ....

                            //  Get the ListView used in this dialog so checkboxes can be set and cleared programmatically.
                            Dialog dialog3 = getDialog();                   //  invoke DialogFragment.getDialog() to return a Dialog class
                            AlertDialog dialog4 = (AlertDialog) dialog3;    //  cast as AlertDialog.  AlertDialog is a descendent of Dialog class.
                            ListView listView = dialog4.getListView();

                            if (which == 0) {
                                //  if user selected "Select All" then check or uncheck all the remaining items based on the status of "Select All"
                                for (int iii = 1; iii < arraySizeUsed; iii++) {
                                    checkedItems[iii-1] = checkedItemsUsed[iii] = isChecked;
                                    listView.setItemChecked(iii,isChecked);
                                }
                                if (isChecked) {
                                    charSeqUsed[0] = getResources().getText(R.string.unselect_all);
                                } else {
                                    charSeqUsed[0] = getResources().getText(R.string.select_all);
                                }
                            } else {
                                //  if user chose some other item besides "Select All" ....
                                checkedItems[which-1] = checkedItemsUsed[which] = isChecked;
                                if (!isChecked) {
                                    // .... and user unselected item then clear the "Select All" item.
                                    checkedItemsUsed[0] = false;
                                    listView.setItemChecked(0,false);
                                    charSeqUsed[0] = getResources().getText(R.string.select_all);
                                } else {
                                    // .... but if user selected an item see if all items are now checked
                                    if (allItemsChecked()) {
                                        // .... and if so then check the "Select All" item.
                                        checkedItemsUsed[0] = true;
                                        listView.setItemChecked(0,true);
                                        charSeqUsed[0] = getResources().getText(R.string.unselect_all);
                                    }
                                }
                            }
                        } else {
                            //  if selectAll is false ....
                            checkedItems[which] = checkedItemsUsed[which] = isChecked;
                        }

                        //  If no items are checked then disable positive (Next) button.  If at least one item checked then enable it.
                        if (atLeastOneItemChecked()) {
                            ((AlertDialog)dialog).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(true);
                        }
                        else {
                            ((AlertDialog)dialog).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(false);
                        }

                        String chosenString = charSeqUsed[which].toString();
                        logInfo( "DialogFilterAlerts chose: "+chosenString+" "+which+" *****");

                        mListener.dialogFilterAlertsSelectionListener( DialogFilterAlerts.this, which, doingFilters );

                    }   // end of onClick()s
                } // end of onClickListener()
        );  // end of setItems()

        builder.setNegativeButton(getResources().getString(middleButtonText), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //System.arraycopy( previousCheckedItems, 0, checkedItems, 0, 4);  // from previousCheckedItems to CheckedItems
                logInfo( "DialogFilterAlerts Negative *****");
                mListener.dialogFilterAlertsCancelListener( DialogFilterAlerts.this, doingFilters );
            }
        });
        builder.setPositiveButton(getResources().getString(rightButtonText), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //System.arraycopy( checkedItems, 0, previousCheckedItems, 0 , 4);  // from checkedItems to previousCheckedItems
                logInfo( "DialogFilterAlerts Positive *****");
                mListener.dialogFilterAlertsNextListener( DialogFilterAlerts.this, doingFilters );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogFilterAlerts Help  *****");
                mListener.dialogFilterAlertsHelpListener( DialogFilterAlerts.this, doingFilters );
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
                                                      logInfo("DialogFilterAlerts Neutral View.OnClickListener() *****");
                                                      mListener.dialogFilterAlertsHelpListener( DialogFilterAlerts.this, doingFilters );
                                                      //dialog.dismiss();       //  remove this line to avoid dismissing dialog.
                                                  }
                                              }
            );
            // This code captures the back button.  It calls a listener which, in turn, calls FiltersInterface.doBackButton().  This can be removed by removing this
            //      code and the similar code in DialogPrefixCallsign.  Then remove the listerners for both dialogs and FiltersInterface.doBackButton().
            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode,
                                     KeyEvent event) {
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                        logInfo("DialogFilterAlerts setOnKeyListener() BACK button pressed *****");
                        mListener.dialogFilterAlertsBackButtonListener( DialogFilterAlerts.this, doingFilters );
                        dialog.dismiss();
                    }
                    return false;
                }
            });

            if (!atLeastOneItemChecked()) {
                Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
                positiveButton.setEnabled(false);
            }
        }
    }

    private boolean atLeastOneItemChecked() {
        boolean returnValue = false;
        for (int iii = 0; iii < sizeOfArrays; iii++) {
            if (checkedItems[iii]) {
                returnValue = true;
                break;
            }
        }
        return returnValue;
    }

    private boolean allItemsChecked() {
        //  This method is only called when selectAll is true.  It returns false if any checkedItemsUsed[] is false.
        //      Note that it always starts with the second item since the first item is "Select All".
        for (int iii = 1; iii < arraySizeUsed; iii++) {
            if (!checkedItemsUsed[iii]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDismiss( DialogInterface dialog ) {
        super.onDismiss(dialog);
        logInfo("DialogFilterAlerts onDismiss() *****");
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }
}

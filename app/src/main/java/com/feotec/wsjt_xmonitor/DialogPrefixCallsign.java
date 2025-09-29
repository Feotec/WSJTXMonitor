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
import androidx.appcompat.widget.AppCompatCheckedTextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

//
//  This class handles the text input of prefix or callsign (it can also handle grid squares provided each grid is verified to be valid).
//      Besides the normal button listeners, this dialog contains three additional listeners (internal to this class).
//          1) editText.addTextChangedListener - when user types anything.  It is used to change the instruction text.
//          2) editText.setOnEditorActionListener - used when the user completes an entry by pressing the Done key.  It will add an item to the
//             list adapter and also chenge the instruction text.
//          3) listView.setOnItemClickListener - used when the user checks or unchecks an item in the list.
//  In Menus2 development, I have a DialogPrefixCallsignTestbed class.  I think I used that for development but I don't actually recall.  In that
//      I overrode the Adapter class to limit the size.  When comparing the two files, there didn't seem to be any important differences.  The
//      testbed version didn't seem complete.
//
public class DialogPrefixCallsign extends DialogFragment {
    private static final String TAG = DialogPrefixCallsign.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogPrefixCallsign;

    private static final String STRING_LIST = "string_list";
    private static final String DOING_FILTERS = "doing_filters";

    ArrayList<String> listItems;
    protected boolean doingFilters;

    public static DialogPrefixCallsign newInstance( ArrayList<String> listItemsParam, boolean doingFilterParam ) {
        DialogPrefixCallsign fragment = new DialogPrefixCallsign();
        Bundle args = new Bundle();
        args.putStringArrayList( STRING_LIST, listItemsParam );
        args.putBoolean( DOING_FILTERS, doingFilterParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            listItems = bundle.getStringArrayList( STRING_LIST );
            doingFilters = bundle.getBoolean( DOING_FILTERS );
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogPrefixCallsignListener {
        void dialogPrefixCallsignFinishListener(DialogFragment dialog, boolean doingFilters );
        void dialogPrefixCallsignCancelListener(DialogFragment dialog, boolean doingFilters );
        void dialogPrefixCallsignHelpListener(DialogFragment dialog, boolean doingFilters );
        void dialogPrefixCallsignBackButtonListener(DialogFragment dialog, boolean doingFilters );
    }

    DialogPrefixCallsign.dialogPrefixCallsignListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogPrefixCallsign.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogPrefixCallsign.dialogPrefixCallsignListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogPrefixCallsign.dialogPrefixCallsignListener");
        }
    }

    private View dialogPrefixCallsignView;

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo( "DialogPrefixCallsign.onCreateDialog  *****");

        if (readBundle(getArguments())) {
            logInfo("DialogPrefixCallsign.onCreateDialog readBundle() ok  *****");
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        dialogPrefixCallsignView = inflater.inflate(R.layout.dialog_prefix_callsign, null);

        final TextView instructionsText = dialogPrefixCallsignView.findViewById(R.id.textViewPrefixEntry);
        if (listItems.size() == 0) {
            instructionsText.setText( getResources().getText(R.string.text_prefix_callsign) );
        }
        else {
            instructionsText.setText( getResources().getText(R.string.text2_prefix_callsign) );
        }

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>( getContext(), android.R.layout.simple_list_item_multiple_choice, listItems );
        final ListView listView = dialogPrefixCallsignView.findViewById(R.id.listViewPrefixEntry);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter( arrayAdapter );
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //
                //  This listener is for the user unchecking entries
                //
                String selectedItem = (String)adapterView.getItemAtPosition(i);
                AppCompatCheckedTextView ctv = (AppCompatCheckedTextView)view;
                logInfo( "DialogPrefixCallsign.listView.onItemClick() selected: "+selectedItem+" index:"+l+" "+i+" "+ctv.isChecked()+" *****");
                if (listItems.size() > i) {
                    listItems.remove(i);
                    arrayAdapter.notifyDataSetChanged();
                    if (listItems.size() == 0) {
                        //  If list is empty then clear Finish button
                        Dialog dialogX = DialogPrefixCallsign.this.getDialog();
                        ((AlertDialog)dialogX).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(false);
                        //  change instructions.
                        instructionsText.setText( getResources().getText(R.string.text_prefix_callsign) );
                    }
                    else {
                        //  For some reason, after the item is removed and the list collapsed the next item in list, if any, gets its checkbox cleared.
                        if (listItems.size() > i) {
                            listView.setItemChecked(i, true);
                        }
                    }
                }
            }
        });
        for (int iii = 0; iii < listItems.size(); iii++) {    //  set all checkboxes.  This has to be done AFTER calling .setAdapter().
            listView.setItemChecked(iii, true);
        }
        listView.setSelection( arrayAdapter.getCount()-1 );

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle( getResources().getString(R.string.title_prefix_callsign) );
        builder.setView( dialogPrefixCallsignView );

        final EditText editText = dialogPrefixCallsignView.findViewById(R.id.editTextPrefixEntry);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                //
                //  This listener is for the user pressing Done on the keyboard
                //
                boolean handled = false;
                logInfo("DialogPrefixCallsign.onEditorAction *****");
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    CharSequence newEntry = editText.getText();
                    editText.setText("",TextView.BufferType.EDITABLE);
                    logInfo( "DialogPrefixCallsign Action DONE, new entry: "+newEntry.toString()+" *****");

                    if (newEntry.length() > 0) {
                        //  Dynamically add the new item to the ListView and listItems
                        listItems.add( newEntry.toString() );
                        arrayAdapter.notifyDataSetChanged();
                        //  ... and set the checkmark
                        listView.setItemChecked(arrayAdapter.getCount()-1, true);
                        //  ... and scroll to bottom of list.
                        listView.setSelection( arrayAdapter.getCount()-1 );
                        //  ... and enable Finish button, since there is at least one item in listItems
                        Dialog dialogX = DialogPrefixCallsign.this.getDialog();
                        ((AlertDialog)dialogX).getButton( Dialog.BUTTON_POSITIVE ).setEnabled(true);
                        //  ... and change instructions
                        instructionsText.setText( getResources().getText(R.string.text2_prefix_callsign) );
                    }

                    //  These two lines remove the soft keyboard.
                    InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(dialogPrefixCallsignView.getWindowToken(), 0);
                    handled = true;
                }
                return handled;
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            //
            //  This listener is for the user typing in any character into EditText
            //
            public void afterTextChanged(Editable s) {
                logInfo("DialogPrefixCallsign.afterTextChanged() s: "+s+" *****");
                //  Now enable or disable Finish button if there are any characters typed in (which will be picked up by setPositiveButton.onClickListener()
                //      below) or if there is already a prefix in listItems.  Doing this adds a more natural feel to the interface.
                Dialog dialogX = DialogPrefixCallsign.this.getDialog();
                if ((s.length() > 0) || (listItems.size() > 0)) {
                    ((AlertDialog) dialogX).getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);
                }
                else {
                    ((AlertDialog) dialogX).getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                logInfo("DialogPrefixCallsign.beforeTextChanged() s: "+s+" start: "+start+" count: "+count+" after: "+after+" *****");
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                logInfo("DialogPrefixCallsign.onTextChanged() s: "+s+" start: "+start+" before: "+before+" count: "+count+" *****");
            }
        });


        builder.setNegativeButton(getResources().getString(R.string.clearall), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //System.arraycopy( previousCheckedItems, 0, checkedItems, 0, 4);  // from previousCheckedItems to CheckedItems
                logInfo( "DialogPrefixCallsign Cancel *****");
                mListener.dialogPrefixCallsignCancelListener( DialogPrefixCallsign.this, doingFilters );
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.okay), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogPrefixCallsign Finish *****");
                //  Get last entry, if not null then add it to listItems
                CharSequence newEntry = editText.getText();
                if (newEntry.length() != 0) {
                    listItems.add( newEntry.toString() );
                }
                mListener.dialogPrefixCallsignFinishListener( DialogPrefixCallsign.this, doingFilters );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogPrefixCallsign Help  *****");
                mListener.dialogPrefixCallsignHelpListener( DialogPrefixCallsign.this, doingFilters );
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
                                                      logInfo("DialogPrefixCallsign Neutral View.OnClickListener() *****");
                                                      mListener.dialogPrefixCallsignHelpListener( DialogPrefixCallsign.this, doingFilters );
                                                      //dialog.dismiss();       //  remove this line to avoid dismissing dialog.
                                                  }
                                              }
            );
            // This code captures the back button.  It calls a listener which, in turn, calls FiltersInterface.doBackButton().  This can be removed by removing this
            //     code and the similar code in DialogFilterAlerts.  Then remove the listerners for both dialogs and FiltersInterface.doBackButton().
            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode,
                                     KeyEvent event) {
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                        logInfo("DialogFilterAlerts setOnKeyListener() BACK button pressed *****");
                        mListener.dialogPrefixCallsignBackButtonListener( DialogPrefixCallsign.this, doingFilters );
                        dialog.dismiss();
                    }
                    return false;
                }
            });

            if (listItems.size() == 0) {
                Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
                positiveButton.setEnabled(false);
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

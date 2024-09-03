package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.Arrays;

public class DialogSetRxFreq extends DialogFragment {

    private static final String TAG = DialogSetRxFreq.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogSetRxFreq;

    private static final String KEY_CHAR_SEQ = "key_char_seq";

    private CharSequence[] charSeq = null;

    //  The parameters:
    //      charSeqParam - The list of lines to diaplay
    public static DialogSetRxFreq newInstance( CharSequence[] charSeqParam) {
        DialogSetRxFreq fragment = new DialogSetRxFreq();
        Bundle args = new Bundle();
        args.putCharSequenceArray( KEY_CHAR_SEQ, charSeqParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            charSeq = bundle.getCharSequenceArray( KEY_CHAR_SEQ );
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogSetRxFreqListener {
        void dialogSetRxFreqSelectionListener(DialogFragment dialog, int which );
        void dialogSetRxFreqHelpListener(DialogFragment dialog );
        void dialogSetRxFreqDismissListener(DialogFragment dialog );
    }

    DialogSetRxFreq.dialogSetRxFreqListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogSetRxFreq.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogSetRxFreq.dialogSetRxFreqListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogSetRxFreq.dialogSetRxFreqListener");
        }
    }

    /*
        This is some playing around I did, thinking I could modify the ListView, such as make it single line or different heights, etc) without a custom view.
    @Override
    public void onStart() {
        super.onStart();
        //((TextView) getDialog().findViewById(android.R.id.message)).setHorizontallyScrolling(true);
        AlertDialog thisDialog = (AlertDialog)getDialog();
        ListView thisList = thisDialog.getListView();
        logInfo("Made it *****");
    }
    */

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo("DialogSetRxFreq.onCreateDialog  *****");

        if (readBundle(getArguments())) {
            logInfo("DialogSetRxFreq.onCreateDialog readBundle() ok  *****");
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogSetRxFreqView = inflater.inflate(R.layout.dialog_set_rx_freq, null);

        //  Create ArrayList<> containing the contents of charSeq[]
        ArrayList<CharSequence> listItems = new ArrayList<>( Arrays.asList(charSeq) );      // the Arrays.asList() business was because I had to initialize with a collection.
        //  Create ArrayAdapter<> for placing contents into ListView
        final ArrayAdapter<CharSequence> arrayAdapter = new ArrayAdapter<>( getContext(), R.layout.simple_list_item_1, listItems );
        //  Get ListView defined in layout file
        final ListView listView = dialogSetRxFreqView.findViewById(R.id.listViewSetRxFreq);
        //  Attach adapter to ListView, placing contents into ListView
        listView.setAdapter( arrayAdapter );
        //  Create listener for when user clicks on an item.
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //String selectedItem = (String)adapterView.getItemAtPosition(i);
                logInfo("DialogSetRxFreq choice: "+i+" *****");
                mListener.dialogSetRxFreqSelectionListener( DialogSetRxFreq.this, i );
                final AlertDialog dialog = (AlertDialog)getDialog();
                dialog.dismiss();               //  This listener doesn't automatically dismiss.
            }
        });

        //  Prepare the title line.
        SpannableStringBuilder ssb = new SpannableStringBuilder( getResources().getString(R.string.dialog_set_rx_freq_title) );
        ssb.setSpan(new RelativeSizeSpan(1.2f), 0, getResources().getInteger(R.integer.set_rx_freq_end_of_large_font), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(ssb);
        builder.setView( dialogSetRxFreqView );
       /*
            Old stuff, using the built-in setItems() call.
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        //builder.setTitle(R.string.dialog_settings_title);
        builder.setItems( charSeq, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        logInfo("DialogSetRxFreq choice: "+which+" *****");
                        mListener.dialogSetRxFreqSelectionListener( DialogSetRxFreq.this, which );
                    }   // end of onClick()
                } // end of onClickListener()
        );  // end of setItems()
        */
        builder.setPositiveButton(getResources().getString(R.string.dismiss), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSetRxFreq Done *****");
                mListener.dialogSetRxFreqDismissListener( DialogSetRxFreq.this );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogSetRxFreq Help  *****");
                mListener.dialogSetRxFreqHelpListener( DialogSetRxFreq.this );
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



}

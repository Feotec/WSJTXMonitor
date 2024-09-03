package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

public class DialogMinSignal extends DialogFragment {
    private static final String TAG = DialogMinSignal.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogMinSignal;

    private static final String MIN_SIGNAL = "min_signal";

    protected int minSignal;

    public static DialogMinSignal newInstance( int minSignalParam ) {
        DialogMinSignal fragment = new DialogMinSignal();
        Bundle args = new Bundle();
        //  arguments
        args.putInt( MIN_SIGNAL, minSignalParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            //  arguments
            minSignal = bundle.getInt( MIN_SIGNAL );
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogMinSignalListener {
        void dialogMinSignalFinishListener(DialogFragment dialog );
        void dialogMinSignalCancelListener(DialogFragment dialog );
        void dialogMinSignalHelpListener(DialogFragment dialog );
        void dialogMinSignalBackButtonListener(DialogFragment dialog );
    }

    DialogMinSignal.dialogMinSignalListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogAlertSettings.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogMinSignal.dialogMinSignalListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogAlertSettings.dialogMinSignalListener");
        }
    }

    private NumberPicker numberPicker;

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo( "DialogMinSignal.onCreateDialog  *****");

        if (readBundle(getArguments())) {
            logInfo("DialogMinSignal.onCreateDialog readBundle() ok  *****");
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogMinSignalView = inflater.inflate(R.layout.dialog_min_signal, null);

        TextView text1 = dialogMinSignalView.findViewById( R.id.min_signal_text1 );
        CharSequence charSeqText1 = getText( R.string.min_signal_text );
        text1.setText( charSeqText1 );

        numberPicker = (NumberPicker) dialogMinSignalView.findViewById(R.id.min_signal_number);
        String noMinString = getString(R.string.min_signal_no_min);
        final String[] values = {noMinString,  "-25 dB", "-24 dB", "-23 dB", "-22 dB", "-21 dB", "-20 dB", "-19 dB", "-18 dB", "-17 dB", "-16 dB",
                "-15 dB", "-14 dB", "-13 dB", "-12 dB", "-11 dB", "-10 dB", " -9 dB", " -8 dB", " -7 dB", " -6 dB",
                " -5 dB", " -4 dB", " -3 dB", " -2 dB", " -1 dB", "  0 dB", " +1 dB", " +2 dB", " +3 dB", " +4 dB",
                " +5 dB", " +6 dB", " +7 dB", " +8 dB", " +9 dB", "+10 dB"
        };
        final int[] valuesInt = {Alerts.NO_MIN_SIGNAL,  -25, -24, -23, -22, -21, -20, -19, -18, -17, -16, -15, -14, -13, -12,
                -11, -10,  -9,  -8,  -7,  -6,  -5,  -4,  -3,  -2,  -1,   0,  +1,  +2,  +3,  +4,  +5,  +6,  +7,  +8,  +9, +10
        };
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(values.length-1);
        numberPicker.setDisplayedValues(values);
        numberPicker.setWrapSelectorWheel(false);
        int iii;
        for (iii = 0; iii < valuesInt.length; iii++) {
            if (minSignal == valuesInt[iii]) {
                numberPicker.setValue(iii);
                break;
            }
        }
        if (iii == valuesInt.length) {          //  should never happen.  Just in case.
            minSignal = Alerts.NO_MIN_SIGNAL;
            numberPicker.setValue(0);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle( getResources().getString(R.string.title_min_signal) );
        builder.setView( dialogMinSignalView );

        builder.setNegativeButton(getResources().getString(R.string.clearall), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogMinSignal Cancel *****");
                mListener.dialogMinSignalCancelListener( DialogMinSignal.this );
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.okay), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                int result = numberPicker.getValue();
                minSignal = valuesInt[ result ];
                logInfo( "DialogMinSignal Finish - value is "+result+" "+values[result]+" *****");
                mListener.dialogMinSignalFinishListener( DialogMinSignal.this );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogMinSignal Help  *****");
               // mListener.dialogMinSignalHelpListener( DialogMinSignal.this );
            }
        });

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        final AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog != null) {
            //  This code prevents the neutral button (which I'm using for HELP) from closing the dialog.  See further notes in DialogFilterAlerts.onResume().
            Button neutralButton = dialog.getButton( Dialog.BUTTON_NEUTRAL );
            neutralButton.setOnClickListener( new View.OnClickListener() {
                                                  @Override
                                                  public void onClick( View v) {
                                                      logInfo("DialogAlertSettings Neutral View.OnClickListener() *****");
                                                      mListener.dialogMinSignalHelpListener( DialogMinSignal.this );
                                                      //dialog.dismiss();       //  remove this line to avoid dismissing dialog.
                                                  }
                                              }
            );
            // This code captures the back button.  It calls a listener which, in turn, calls AlertsInterface.doBackButton().
            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode,
                                     KeyEvent event) {
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                        logInfo("DialogAlertSettings setOnKeyListener() BACK button pressed *****");
                        mListener.dialogMinSignalBackButtonListener( DialogMinSignal.this );
                        dialog.dismiss();
                    }
                    return false;
                }
            });
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

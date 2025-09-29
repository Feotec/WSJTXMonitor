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
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class DialogAlertSettings extends DialogFragment {
    private static final String TAG = DialogAlertSettings.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDialogAlertSettings;

    private static final String CHECKED_ITEMS = "checked_items";
    private static final String MIN_SIGNAL = "min_signal";
    private static final String REMEMBER_STATION = "remember_station";
    private static final String DEVICE_HAS_VIBRATOR = "device_has_vibrator";

    protected boolean[] checkedItemsSoundVibration;
    protected int minSignal;
    protected boolean rememberStation;
    private boolean deviceHasVibrator;

    public static DialogAlertSettings newInstance( boolean[] checkedItemsSoundVibrationParam, int minSignalParam,
                                                            boolean rememberStationParam, boolean deviceHasVibratorParam ) {
        DialogAlertSettings fragment = new DialogAlertSettings();
        Bundle args = new Bundle();
        //  arguments
        args.putBooleanArray( CHECKED_ITEMS, checkedItemsSoundVibrationParam );
        args.putInt( MIN_SIGNAL, minSignalParam );
        args.putBoolean( REMEMBER_STATION, rememberStationParam );
        args.putBoolean( DEVICE_HAS_VIBRATOR, deviceHasVibratorParam );
        fragment.setArguments(args);
        return fragment;
    }

    private boolean readBundle(Bundle bundle) {
        if (bundle != null) {
            //  arguments
            checkedItemsSoundVibration = bundle.getBooleanArray( CHECKED_ITEMS );
            minSignal = bundle.getInt( MIN_SIGNAL );
            rememberStation = bundle.getBoolean( REMEMBER_STATION );
            deviceHasVibrator = bundle.getBoolean( DEVICE_HAS_VIBRATOR );
            return true;
        }
        else {
            return false;       //  should only happen if default constructor called.
        }
    }

    public interface dialogAlertSettingsListener {
        void dialogAlertSettingsFinishListener(DialogFragment dialog );
        void dialogAlertSettingsCancelListener(DialogFragment dialog );
        void dialogAlertSettingsHelpListener(DialogFragment dialog );
        void dialogAlertSettingsBackButtonListener(DialogFragment dialog );
        void dialogAlertSettingsMinSignalListener(DialogFragment dialog );
        void dialogAlertSettingsTestSoundListener(DialogAlertSettings dialog, int soundVibrateTrueIndex );
    }

    DialogAlertSettings.dialogAlertSettingsListener mListener;

    @Override
    public void onAttach(Context activity) {    // onAttach(Activity) is deprecated in Fragment class.  Use onAttach(Context)
        logInfo("DialogAlertSettings.onAttach  *****");
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the customLayoutDialogListener so we can send events to the host
            mListener = (DialogAlertSettings.dialogAlertSettingsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DialogAlertSettings.dialogAlertSettingsListener");
        }
    }

    private TextView text1,text3;
    private int soundVibrateTrueIndex;

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logInfo( "DialogAlertSettings.onCreateDialog  *****");

        if (readBundle(getArguments())) {
            logInfo("DialogAlertSettings.onCreateDialog readBundle() ok  *****");
        }

        soundVibrateTrueIndex = 0;
        for (int iii = 0; iii < checkedItemsSoundVibration.length; iii++) {
            if (checkedItemsSoundVibration[iii]) {
                soundVibrateTrueIndex = iii;
                break;
            }
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogAlertSettingsView = inflater.inflate(R.layout.dialog_alert_settings, null);

        text1 = dialogAlertSettingsView.findViewById( R.id.alert_settings_text1 );
        TextView text2 = dialogAlertSettingsView.findViewById( R.id.alert_settings_text2 );
        text3 = dialogAlertSettingsView.findViewById( R.id.alert_settings_text3 );

        //ssbText1Pre = textHelp( R.string.alert_settings_sound_vibrate_prefix );
        //SpannableStringBuilder ssbTemp = text1Help(soundVibrateTrueIndex);
        //text1.setText( ssbText1Pre.append(ssbTemp) );
        text1.setText( textHelp( R.string.alert_settings_sound_vibrate_prefix ).append( text1Help(soundVibrateTrueIndex) ));

        text2.setText( textHelp( R.string.alert_settings_min_signal_prefix ).append( text2Help() ) );

        text3.setText( textHelp( R.string.alert_settings_stations_prefix ).append( text3Help( rememberStation ) ));

        TextView text4 = dialogAlertSettingsView.findViewById( R.id.alert_settings_text4 );
        CharSequence charSeqText4 = getResources().getText( R.string.alert_settings_test_sound );
        //text4.setMovementMethod(LinkMovementMethod.getInstance());
        text4.setText( charSeqText4 );
        text4.setOnTouchListener(new TextView.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_UP:
                        logInfo( "DialogAlertSettings Text4 onTouch UP() *****");
                        v.performClick();
                        //  The call to MainActivity.alertSettingsButtonSoundHelp() is accomplished via onClick in dialog_alert_settings.xml for this TextView.
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        /*
            IMPORTANT NOTE - when a listener is placed here, the call to the onClick() setting in XML is not done.
         */

        Button button1 = dialogAlertSettingsView.findViewById( R.id.alert_settings_button1 );
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //int number = Integer.parseInt(v.getTag().tostring());
                logInfo( "DialogAlertSettings Button1 onClick *****"+soundVibrateTrueIndex);
                checkedItemsSoundVibration[ soundVibrateTrueIndex ] = false;
                if (deviceHasVibrator) {
                    soundVibrateTrueIndex++;
                } else {
                    soundVibrateTrueIndex+=2;
                }
                soundVibrateTrueIndex &= 0x00000003;
                checkedItemsSoundVibration[ soundVibrateTrueIndex ] = true;
                text1.setText( textHelp( R.string.alert_settings_sound_vibrate_prefix ).append( text1Help(soundVibrateTrueIndex) ));
            }
        });

        Button button2 = dialogAlertSettingsView.findViewById( R.id.alert_settings_button2 );
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //int number = Integer.parseInt(v.getTag().tostring());
                logInfo( "DialogAlertSettings Button2 onClick *****");
                mListener.dialogAlertSettingsMinSignalListener( DialogAlertSettings.this );
            }
        });

        Button button3 = dialogAlertSettingsView.findViewById( R.id.alert_settings_button3 );
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //int number = Integer.parseInt(v.getTag().tostring());
                logInfo( "DialogAlertSettings Button3 onClick *****");
                if (rememberStation) { rememberStation = false; } else { rememberStation = true; }
                text3.setText( textHelp( R.string.alert_settings_stations_prefix ).append( text3Help( rememberStation ) ));
            }
        });

        Button button4 = dialogAlertSettingsView.findViewById( R.id.alert_settings_button4 );
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //int number = Integer.parseInt(v.getTag().tostring());
                logInfo( "DialogAlertSettings Button4 onClick *****");
                mListener.dialogAlertSettingsTestSoundListener( DialogAlertSettings.this, soundVibrateTrueIndex );
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle( getResources().getString(R.string.title_alert_settings) );
        builder.setView( dialogAlertSettingsView );

        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogAlertSettings Cancel *****");
                mListener.dialogAlertSettingsCancelListener( DialogAlertSettings.this );
            }
        });
        builder.setPositiveButton(getResources().getString(R.string.finish), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo( "DialogAlertSettings Finish *****");
                mListener.dialogAlertSettingsFinishListener( DialogAlertSettings.this );
            }
        });
        builder.setNeutralButton( getResources().getString(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                logInfo("DialogAlertSettings Help  *****");     // this gets the button installed.  However, listener is called from onResume() below.
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
                                                      mListener.dialogAlertSettingsHelpListener( DialogAlertSettings.this );
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
                        mListener.dialogAlertSettingsBackButtonListener( DialogAlertSettings.this );
                        dialog.dismiss();
                    }
                    return false;
                }
            });
        }
    }

    //  Returns the prefix, the text for each TextView up to the end of the "Currently" statement
    private SpannableStringBuilder textHelp( int prefix ) {
        SpannableStringBuilder returnValue;

        String textPre = getResources().getString( prefix );
        String textCur = textPre+"\n"+getResources().getString( R.string.alert_settings_currently );
        returnValue = new SpannableStringBuilder(textCur);
        returnValue.setSpan(new StyleSpan( Typeface.BOLD ), 0, textPre.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        returnValue.append(" ");

        return returnValue;
    }

    //  This returns one of four text values for sound/vibrate.  It is to be appended to what is returned by textHelp() above.
    private SpannableStringBuilder text1Help( int selection ) {
        String text1Choice;

        switch (selection) {
            case 0:
                text1Choice = getResources().getString( R.string.alert_settings_sound_vibrate );
                break;
            case 1:
                text1Choice = getResources().getString( R.string.alert_settings_sound_only );
                break;
            case 2:
                text1Choice = getResources().getString( R.string.alert_settings_vibrate_only );
                break;
            case 3:
                text1Choice = getResources().getString( R.string.alert_settings_silent );
                break;
            default:
                text1Choice = getResources().getString( R.string.alert_settings_sound_vibrate );
        }
        SpannableStringBuilder ssbChoice = new SpannableStringBuilder(text1Choice);;
        ssbChoice.setSpan(new BackgroundColorSpan( Color.GREEN ), 0, text1Choice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        ssbChoice.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), 0, text1Choice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        return ssbChoice;
    }

    private SpannableStringBuilder text2Help() {
        String textChoice;

        if (minSignal == Alerts.NO_MIN_SIGNAL) {
            textChoice = getResources().getString( R.string.alert_settings_min_signal_none );
        } else {
            textChoice = String.format(Locale.US, "%d dB", minSignal);
        }

        SpannableStringBuilder ssbChoice = new SpannableStringBuilder(textChoice);;
        ssbChoice.setSpan(new BackgroundColorSpan( Color.GREEN ), 0, textChoice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        ssbChoice.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), 0, textChoice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        return ssbChoice;
    }

    private SpannableStringBuilder text3Help( boolean selection ) {
        String textChoice;

        if (selection) {
            textChoice = getResources().getString( R.string.alert_settings_on );
        } else {
            textChoice = getResources().getString( R.string.alert_settings_off );
        }
        SpannableStringBuilder ssbChoice = new SpannableStringBuilder(textChoice);;
        ssbChoice.setSpan(new BackgroundColorSpan( Color.GREEN ), 0, textChoice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        ssbChoice.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), 0, textChoice.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
        return ssbChoice;
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

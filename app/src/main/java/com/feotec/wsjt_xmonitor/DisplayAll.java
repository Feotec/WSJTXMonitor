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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.ActionBar;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import static java.lang.String.format;

public class DisplayAll extends BaseActivity {

    private static final String PACKAGE_NAME = "com.feotec.wsjt_xmonitor";
    private static final String TAG = DisplayAll.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingDisplayAll;

    private TextView scrollerText;

    protected DisplayAll.MyReceiver myReceiver;     // The BroadcastReceiver used to listen from broadcasts from the service.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_all);

        logInfo("onCreate(): " + format(Locale.getDefault(),"Thread %d", Thread.currentThread().getId() ) + " *********************");

        Toolbar myChildToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myChildToolbar);    //  set my_child_toolbar as this Activity's toolbar.  Its actually the same toolbar as MainActivity.
        ActionBar ab = getSupportActionBar();   // Get an ActionBar object for this toolbar
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);     // Enable the Up button
        }

        isDisplayAll = true;

        scrollView = findViewById(R.id.terminalScroller);
        horizontalScrollView = findViewById(R.id.horizScrollView);
        scrollerText = findViewById(R.id.terminalOutput);

        scrollerText.setMovementMethod(new ScrollingMovementMethod());  //  This allows scrolling up/down by touch.
        scrollerText.setVerticalScrollbarPosition(ScrollView.SCROLLBAR_POSITION_LEFT);
        scrollerText.setTypeface(Typeface.MONOSPACE);
        scrollerText.setText(getResources().getString(R.string.waiting_short));
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        horizontalScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT);

        lastScrollX = lastScrollY = 0;
        scrollerText.setOnTouchListener(new View.OnTouchListener() {
            //  Further notes on detecting that the user has touched and scrolled the screen are in ScrollText project MainActivity.onCreate().
            public boolean onTouch(View v, MotionEvent event) {
                lastScrollX = horizontalScrollView.getScrollX();
                lastScrollY = scrollView.getScrollY();
                if (screenTimerCounter == 0) {
                    screenTimerHandler.postDelayed(screenTimerRunnable, SCREEN_TIMER_TIMEOUT_MS);
                }
                screenTimerCounter = SCREEN_TIMER_COUNTER_INIT;
                logInfo("DisplayAll.onTouch() ****** screenTimerHandler set ("+lastScrollX+" "+lastScrollY+")");
                return true;
            }
        });

        myReceiver = new MyReceiver();

        //  Create the Handler and define and create the Runnable here.  This will allow the base class to update the user interface
        //      in this descendant class.
        uiHandler = new Handler();
        uiRunnable = new Runnable() {
            @Override
            public void run() {
                if (mService.dataIsFlowing()) {
                    updateScrollView(mService.getDisplaySpanBuffer());
                }
            }
        };
    }

    protected class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SpannableStringBuilder buf = mService.getDisplaySpanBuffer();
            logInfo("MyReceiver.onReceive ********************* "+buf.length()+" "+screenTimerCounter);
            if (screenTimerCounter == 0) {
                if (buf.length() > 1) {     //  see notes on buf.length() in MainActivity.manageDisplay().
                    updateScrollView(buf); //mService.getDisplaySpanBufferMain() );
                }
            }
        }
    }

    private void updateScrollView( SpannableStringBuilder displaySpanBuffer ) {
        try {
            // An occasional exception here.  It said SpannableStringBuffer has end before start.  It always happened during screen rotation.  I think it
            //      was because I was getting displaySpanBuffer while UPD thread was manipulating it.  That's been fixed but I left the try{} block.
            scrollerText.setText(displaySpanBuffer);
        }
        catch (Exception ex){
            logInfo("updateScrollView() Exception " + ex.getMessage());
        }
        scrollerText.append("\n");      //  an extra LF seems to help the last line get displayed.
        scrollView.post(new Runnable() {        //  Seems that these need to be posted to runnables to work correctly.
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
        horizontalScrollView.post(new Runnable() {
            @Override
            public void run() {
                horizontalScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT);
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        logInfo("onStart() *********************");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logInfo("onResume() *********************");

        //  Register the broadcast receiver using LocalBroadcastManager.
        LocalBroadcastManager mLocalBroadcast = LocalBroadcastManager.getInstance(this);
        IntentFilter mBroadcastIntent = new IntentFilter(UDPService.ACTION_BROADCAST);
        mLocalBroadcast.registerReceiver(myReceiver, mBroadcastIntent);

        if (initialStartup) {
            initialStartup = false;
        }

        //  If DisplayAll or UDPService invoked a shutdown ...
        if (WSJTXUtils.getIsFinishInvoked( this )) {
            doFinish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        logInfo("onPause() *********************");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        logInfo("onStop() *********************");
    }

    @Override
    protected void onDestroy() {
        logInfo("onDestroy() ********************* "+upOrBackButtonPressed);
        super.onDestroy();
        upOrBackButtonPressed = false;      //  MUST BE AFTER super.onDestroy() because it is used there.
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_help:
                logInfo("onOptionsItemSelected() help *********************");
                boolean dataFlowing = false;
                if (mService != null) {
                    dataFlowing = mService.dataIsFlowing();
                }
                DialogFragment displayAllHelp = DisplayAllHelp.newInstance(dataFlowing);
                displayAllHelp.show(getSupportFragmentManager(), "DisplayAllHelp");
                return true;
            case android.R.id.home:
                logInfo("onOptionsItemSelected() HOME *****");
                upOrBackButtonPressed = true;
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    public static class DisplayAllHelp extends DialogFragment {

        private static final String NO_CONNECTIVITY = "no_connectivity";

        private boolean parameterConnectivity = false;

        public static DisplayAllHelp newInstance( boolean parameter1Conn ) {
            DisplayAllHelp fragment = new DisplayAllHelp();
            Bundle args = new Bundle();
            args.putBoolean(NO_CONNECTIVITY, parameter1Conn);
            fragment.setArguments(args);
            return fragment;
        }

        private boolean readBundle(Bundle bundle) {
            if (bundle != null) {
                parameterConnectivity = bundle.getBoolean(NO_CONNECTIVITY);
                return true;
            }
            else {
                return false;       //  should only happen if default constructor called.
            }
        }

        @Override
        public void onStart() {         //   This is done so that the dialog can have a link in it.
            super.onStart();
            ((TextView) getDialog().findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            if (readBundle(getArguments())) {
                if (DO_LOGGING) {
                    Log.i(TAG,"DisplayAllHelp.onCreateDialog readBundle() ok  *****" + parameterConnectivity);   // cannot call logInfo() because in static class.
                }
            }

            //  In order to add the link to the text I had to do everything with a SpannableStringBuilder.  That way I could load the Strings with
            //      getText() instead of getString().
            SpannableStringBuilder ssb1;
            if (!parameterConnectivity) {
                ssb1 = new SpannableStringBuilder(getResources().getText(R.string.display_no_conn_text));
                ssb1.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), 0, ssb1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
            }
            else {
                ssb1 = new SpannableStringBuilder("");
            }
            SpannableStringBuilder ssb2 = new SpannableStringBuilder(getResources().getText(R.string.display_all_text));
            ssb1.append(ssb2);

            builder.setTitle(R.string.display_all_title)
                    .setMessage(ssb1)
                    .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (DO_LOGGING) {
                                Log.i(TAG, "DialogPortSuccess OK *****");       // cannot call logInfo() because in static class.
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }

    }

    @Override
    public void onBackPressed() {
        logInfo("onBackPressed() HOME *****");
        upOrBackButtonPressed = true;
        super.onBackPressed();
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

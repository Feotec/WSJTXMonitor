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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Objects;

import static java.lang.String.format;


public class MainActivity extends BaseActivity implements
        DialogFilterAlerts.dialogFilterAlertsListener,
        DialogPrefixCallsign.dialogPrefixCallsignListener,
        DialogAlertSettings.dialogAlertSettingsListener,
        DialogMinSignal.dialogMinSignalListener,
        DialogSorting.dialogSortingListener,
        DialogReplyHelp.dialogReplyHelpListener,
        DialogSetRxFreq.dialogSetRxFreqListener,
        DialogSetRxFreqHelp.dialogSetRxFreqHelpListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingMainActivity;

    private TextView scrollerText;

    protected MainActivity.MyReceiver myReceiver;     // The BroadcastReceiver used to listen from broadcasts from the service.

    private FiltersInterface filtersInterface = null;
    private AlertsInterface alertsInterface = null;
    private static final String TAG_RETAINED_FRAGMENT_FILTER = "RetainedFragmentFilter";
    private RetainedFragmentFilter mRetainedFragmentFilter;
    private static final String TAG_RETAINED_FRAGMENT_ALERT = "RetainedFragmentAlert";
    private RetainedFragmentAlert mRetainedFragmentAlert;

    public Menu mainMenu;

    private boolean appExpired = false;

    private boolean messageSetRxFreqSet = false;
    private boolean dialogSetRxFreqAfterDismissHelp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logInfo("onCreate(): " + format(Locale.getDefault(),"Thread %d", Thread.currentThread().getId() ) + " "+getPackageName()+" *********************");

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        appExpired = DebugUtils.expiration();

        horizontalScrollView = findViewById(R.id.horizScrollViewMain);
        scrollView = findViewById(R.id.terminalScrollerMain);
        scrollerText = findViewById(R.id.terminalOutputMain);

        scrollerText.setMovementMethod(new ScrollingMovementMethod());  //  This allows scrolling up/down by touch.
        scrollerText.setVerticalScrollbarPosition(ScrollView.SCROLLBAR_POSITION_LEFT);
        scrollerText.setTypeface(Typeface.MONOSPACE);
        if ((dataNeverFlowed) || (ipAddressChanged)) {
            scrollerText.setText(getResources().getString(R.string.waiting));
        } else {
            scrollerText.setText(getResources().getString(R.string.waiting_short));
        }
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        horizontalScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT);

        //  WRITELOGSTOANDROIDFILE
        DebugUtils.doStoragePermissions( this, this );

        lastScrollX = lastScrollY = 0;
        lastScrollX = horizontalScrollView.getScrollX();
        lastScrollY = scrollView.getScrollY();
        scrollerText.setOnTouchListener(new View.OnTouchListener() {
            //  Further notes on detecting that the user has touched and scrolled the screen are in ScrollText project MainActivity.onCreate().
            public boolean onTouch(View v, MotionEvent event) {
                lastScrollX = horizontalScrollView.getScrollX();
                lastScrollY = scrollView.getScrollY();
                if (screenTimerCounter == 0) {
                    screenTimerHandler.postDelayed(screenTimerRunnable, SCREEN_TIMER_TIMEOUT_MS);
                }
                screenTimerCounter = SCREEN_TIMER_COUNTER_INIT;
                logInfo("MainActivity.onTouch() ****** screenTimerHandler set ("+lastScrollX+" "+lastScrollY+")");
                return true;
            }
        });

        myReceiver = new MainActivity.MyReceiver();

        //  Create the Handler and define and create the Runnable here.  This will allow the base class to update the user interface
        //      in this descendant class.  It does this in ServiceConnection.onServiceConnected().
        uiHandler = new Handler();
        uiRunnable = new Runnable() {
            @Override
            public void run() {
                if (mService.dataIsFlowing()) {
                    manageDisplay();
                }
            }
        };
    }

    protected class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            manageDisplay();
        }
    }

    //  Called from MyReceiver.onReceive() and from uiRunnable.run() (defined in onCreate() above) and from onResume() below.  There is no need to check if
    //      (mService != null) because neither MyReceiver or the runnable would be invoked until the service is running.  Further, this function will not
    //      be called unless a connection has been established.  This is because UDPService will not send out a broadcast unless a valid WSJT packet has been
    //      received.  If called from the runnable above, it checks mService.dataIsFlowing() before calling this method.
    protected void manageDisplay() {
        SpannableStringBuilder buf = mService.getDisplaySpanBufferMain();
        logInfo("manageDisplay() ********************* " + buf.length());

        //  If DialogSetup up then dismiss it since the user obviously has things set up.  Note - when DialogSetup is called from the menu it has a
        //      different tag so it won't be dismissed here.
        FragmentManager fragMan = getSupportFragmentManager();
        DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("DialogSetup");
        if (testFrag != null) {
            testFrag.dismiss();
        }

        if (dataNeverFlowed) {
            dataNeverFlowed = false;
            WSJTXUtils.setDataNeverFlowed( this, false );
        }
        if (ipAddressChanged) {
            ipAddressChanged = false;
        }

        //  If connection is established but no decodes have occurred then buf.length() == 0.
        //  If connection is established but buffer is being manipulated then buf.length() == 1.
        if (buf.length() > 1) {
            if (screenTimerCounter == 0) {
                updateScrollView(buf); //mService.getDisplaySpanBufferMain() );
            }
        }
        else {
            //  If no data to display but connection established then either the buffer is being manipulated or no decodes have yet been received.
            //      If buffer is being manipulated then buf.length() == 1 ....
            if (buf.length() == 0) {
                //  ... else let the user know that connection has been established but haven't yet received data.
                String str1 = getResources().getString(R.string.ip_port_supplement_line1);
                SpannableStringBuilder buf2 = new SpannableStringBuilder(str1 +
                        getResources().getString(R.string.ip_port_supplement_line2));
                buf2.setSpan(new ForegroundColorSpan(Color.BLACK), 0, buf2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                buf2.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, buf2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                updateScrollView(buf2);
            }
        }

        //  One last thing.  If a UDP message was sent to WSJT-X check to see if it has failed.  This is a kludge but it is the only timely way to bring up this dialog.
        //      Note that messageSetRxFreqSet is set whenever the user makes a set-rx-freq selection.  However, a special local broadcast will be sent by UDPService only
        //      if the message failed.  If the message succeeds then messageSetRxFreqSet won't be cleared until the next burst.
        if (messageSetRxFreqSet) {
            int setRxFreqResult = mService.getSetRxStatus();    //  returns 0 if unknown, 1 on success, and -1 on failure.
            if (setRxFreqResult != 0) {                         //  there is a remote possibility that a set-rx-freq message (msg4) is sent but the normal burst comes in before
                messageSetRxFreqSet = false;                    //      WSJT-X changed DX call of freq.  So don't clear this flag until a result is known.
                logInfo("manageDisplay() - setRxFreq complete, result "+setRxFreqResult+" *****1");
            }
            if (mService.getSetRxStatus() == -1) {
                DialogFragment testFrag2 = DialogSetRxFreqHelp.newInstance(2);
                testFrag2.show( getSupportFragmentManager(), "DialogSetRxFreqHelp");
                testFrag2.setCancelable(false);
            }
        }
    }

    private void updateScrollView( SpannableStringBuilder displaySpanBuffer ) {
        try {
            // An occasional exception here.  It said SpannableStringBuffer has end before start.  It always happened during screen rotation.  I think it
            //      was because I was getting displaySpanBuffer while UDP thread was manipulating it.  That's been fixed but I left the try{} block.
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
    public boolean onCreateOptionsMenu(Menu menu) {
        logInfo("onCreateOptionsMenu(): *********************");

        boolean returnValue = super.onCreateOptionsMenu(menu);

        //  Here add menu items that are only in MainActivity.  They are all in menu_main.xml.  The items added here are commented out in menu_main.xml.
        //      But the order (third parameter of Menu.add()) is easier to display there.

        //  Add a menu item.  The parameters are ( group == none, id, order, and title ).
        menu.add(Menu.NONE, R.id.action_display_all, 100, getResources().getString(R.string.action_display_all));

        menu.add(Menu.NONE, R.id.action_cq_only, 151, getResources().getString(R.string.action_cq_only));
        MenuItem cqItem = menu.findItem(R.id.action_cq_only);
        cqItem.setCheckable(true);

        menu.add(Menu.NONE, R.id.action_alerts, 152, getResources().getString(R.string.action_alerts));
        MenuItem alertsItem = menu.findItem(R.id.action_alerts);
        alertsItem.setCheckable(true);

        menu.add(Menu.NONE, R.id.action_filters, 153, getResources().getString(R.string.action_filters));
        MenuItem filtersItem = menu.findItem(R.id.action_filters);
        filtersItem.setCheckable(true);

        menu.add(Menu.NONE, R.id.action_sorting, 154, getResources().getString(R.string.action_sorting));
        MenuItem sortingItem = menu.findItem(R.id.action_sorting);
        sortingItem.setCheckable(true);

        menu.add(Menu.NONE, R.id.action_mapping, 155, getResources().getString(R.string.action_mapping));

        MenuItem setRxIcon = menu.add(Menu.NONE, R.id.action_set_rx, 70, getResources().getString(R.string.action_set_rx));
        setRxIcon.setIcon(R.drawable.baseline_reply_red_36);
        setRxIcon.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        //  Special case of the Reply icon and its need for a long press.
        MenuItem newIcon = menu.add( Menu.NONE, R.id.action_reply, 50, getResources().getString(R.string.action_reply));
        newIcon.setIcon(R.drawable.baseline_reply_black_36);
        newIcon.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        ImageView imageButton = new ImageView(this);    //  kind of bugs me that I have to create a bogus View just for this association.
        imageButton.setImageResource(R.drawable.baseline_reply_black_36);
        newIcon.setActionView(imageButton);
        ImageView newIconView = (ImageView)newIcon.getActionView(); //findViewById(R.id.action_reply);
        if (newIconView != null ) {
            logInfo("onCreateOptionsMenu() NOT null *********************");
            newIconView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View vvv) {
                    logInfo("setOnLongClickListener().onLongClick() *********************");
                    DialogFragment replyHelp;
                    replyHelp = (DialogFragment)getSupportFragmentManager().findFragmentByTag("Reply Help Tag");
                    if (replyHelp == null) {
                        replyHelp = new DialogReplyHelp();
                        replyHelp.show( getSupportFragmentManager(), "Reply Help Tag");
                        replyHelp.setCancelable(false);
                     }
                    return true;
                }
            });
            newIconView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    logInfo("setOnClickListener().onClick() *********************");
                    if (mService != null) {
                        MenuItem replyIcon = mainMenu.findItem( R.id.action_reply );
                        ImageView replyIconView = (ImageView)replyIcon.getActionView();
                        if (mService.doingReply()) {
                            mService.setDoingReply( false );
                            replyIconView.setImageResource(R.drawable.baseline_reply_black_36);
                            Toast.makeText(getApplicationContext(), getString(R.string.reply_toast_off), Toast.LENGTH_LONG).show();
                        } else {
                            mService.setDoingReply( true );
                            replyIconView.setImageResource(R.drawable.baseline_no_reply_black_36);
                            Toast.makeText(getApplicationContext(), getString(R.string.reply_toast_on), Toast.LENGTH_LONG).show();
                        }
                    }

                }
            });
        }


        return returnValue;
    }

    //  Called every time the menu is opened.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        logInfo("onPrepareOptionsMenu() called *****");

        mainMenu = menu;
        if (alertsInterface != null) {
            alertsInterface.setMainMenu(mainMenu);
        }

        MenuItem displayAllItem = menu.findItem(R.id.action_display_all);
        if ((dataNeverFlowed) || (ipAddressChanged)) {
            //  If data has never flowed then we've renamed the Display All menu option to Setup WSJT-X or JTDX.
            displayAllItem.setTitle(R.string.dialog_settings_setup_WSJTX);
        } else {
            displayAllItem.setTitle(R.string.action_display_all);
        }

        if (mService != null) {             //  should alays be non-null
            MenuItem cqItem = menu.findItem(R.id.action_cq_only);
            if (mService.cqOnly()) {
                cqItem.setChecked(true);
            } else {
                cqItem.setChecked(false);
            }

            MenuItem alertsItem = menu.findItem(R.id.action_alerts);
            if (mService.alerts()) {
                alertsItem.setChecked(true);
            } else {
                alertsItem.setChecked(false);
            }

            MenuItem filtersItem = menu.findItem(R.id.action_filters);
            if (mService.filters()) {
                filtersItem.setChecked(true);
            } else {
                filtersItem.setChecked(false);
            }

            MenuItem sortingItem = menu.findItem(R.id.action_sorting);
            if (mService.doingSorting()) {
                sortingItem.setChecked(true);
            } else {
                sortingItem.setChecked(false);
            }

            //  Again, special case for the Reply icon.
            MenuItem replyItem = menu.findItem(R.id.action_reply);
            if (mService.alerts()) {
                replyItem.setVisible(true);
                replyItem.setEnabled(true);
                ImageView replyIconView = (ImageView)replyItem.getActionView();
                if (mService.doingReply()) {
                    replyIconView.setImageResource(R.drawable.baseline_no_reply_black_36);
                } else {
                    replyIconView.setImageResource(R.drawable.baseline_reply_black_36);
                }
            } else {
                replyItem.setVisible(false);
                replyItem.setEnabled(false);
            }

        }

        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // used to be a switch statement until Google made the R.id.* non-final
        int itemId = item.getItemId();
        if (itemId == R.id.action_set_rx) {
            logInfo("onOptionsItemSelected() set rx freq *********************");
            if (mService != null) {
                if (!dataNeverFlowed) {     //  if data never flowed then don't do anything.
                    if (mService.isCurrentModeWSPR()) {
                        DialogFragment testFrag = DialogSetRxFreqHelp.newInstance(3);
                        testFrag.show(getSupportFragmentManager(), "DialogSetRxFreqHelp");
                        testFrag.setCancelable(false);
                    } else {
                        int numSetRxUses = WSJTXUtils.getAndBumpNumberOfSetRxFreq(this);
                        CharSequence[] setRxFreqCharSeq = mService.getSetRxCharSeq();
                        if (numSetRxUses == 1) {
                            //  If first time .....
                            DialogFragment testFrag;
                            if ((setRxFreqCharSeq != null) && (setRxFreqCharSeq.length > 0)) {
                                //  .... and already data then DialogSetRxFreqHelp introduction version
                                testFrag = DialogSetRxFreqHelp.newInstance(4);
                            } else {
                                //  .... and no data yet then DialogSetRxFreqHelp no decodes version
                                testFrag = DialogSetRxFreqHelp.newInstance(1);
                            }
                            testFrag.show(getSupportFragmentManager(), "DialogSetRxFreqHelp");
                            testFrag.setCancelable(false);
                            dialogSetRxFreqAfterDismissHelp = true;
                            //  Set variable to check again on dismiss of DialogSetRxFreqHelp.
                        } else {
                            //  If not first time .....
                            if ((setRxFreqCharSeq != null) && (setRxFreqCharSeq.length > 0)) {
                                //  .... and data then do normal display DialogSetRxFreq
                                DialogFragment dialogSetRxFreq = DialogSetRxFreq.newInstance(setRxFreqCharSeq);
                                dialogSetRxFreq.show(getSupportFragmentManager(), "DialogSetRxFreq");
                                dialogSetRxFreq.setCancelable(false);
                            } else {
                                //  .... and no data then DialogSetRxFreqHelp no decodes version
                                DialogFragment testFrag = DialogSetRxFreqHelp.newInstance(1);
                                testFrag.show(getSupportFragmentManager(), "DialogSetRxFreqHelp");
                                testFrag.setCancelable(false);
                            }
                        }
                    }
                }
            }
            return true;
        } else if (itemId == R.id.action_help) {
            logInfo("onOptionsItemSelected() help *********************");
            boolean dataFlowing = false;
            if (mService != null) {
                dataFlowing = mService.dataIsFlowing();
            }
            DialogFragment mainDisplayHelp = MainDisplayHelp.newInstance(dataFlowing);
            mainDisplayHelp.show(getSupportFragmentManager(), "MainDisplayHelp");
            return true;
        } else if (itemId == R.id.action_display_all) {//  Create the intent and start the activity
            if ((dataNeverFlowed) || (ipAddressChanged)) {
                //  If data has never flowed then we've renamed the Settings menu option to Setup WSJT-X or JTDX.
                DialogFragment testFrag = DialogSetup.newInstance(false, ipAddressString);
                testFrag.show(getSupportFragmentManager(), "DialogSetupSecondTag");    //  different tag so MainActivity.manageDisplay() doesn't dismiss it.
                testFrag.setCancelable(false);
            } else {
                Intent intent = new Intent(this, DisplayAll.class);
                startActivity(intent);
            }
            return true;
        } else if (itemId == R.id.action_cq_only) {
            logInfo("onOptionsItemSelected() CQ Only *********************");
            if (mService != null) {             //  should alays be non-null
                if (mService.cqOnly()) {
                    mService.switchCQOnly(false);
                } else {
                    mService.switchCQOnly(true);
                }
            }
            return true;
        } else if (itemId == R.id.action_alerts) {
            logInfo("onOptionsItemSelected() alerts *********************");
            if (mService != null) {             //  should alays be non-null
                if (mService.alerts()) {
                    mService.alertObject = null;
                    MenuItem replyIcon = mainMenu.findItem(R.id.action_reply);
                    replyIcon.setEnabled(false);
                    replyIcon.setVisible(false);
                    Toast.makeText(getApplicationContext(), getString(R.string.alerts_toast_off), Toast.LENGTH_LONG).show();
                } else {
                    if (alertsInterface == null) {
                        logInfo("alertsInterface instance is null *****");
                        alertsInterface = new AlertsInterface(this, mService, mainMenu);
                    } else {
                        logInfo("alertsInterface instance is NOT null *****");
                        alertsInterface.startUI();
                    }
                }
            }
            return true;
        } else if (itemId == R.id.action_filters) {
            logInfo("onOptionsItemSelected() filters *********************");
            if (mService != null) {             //  should alays be non-null
                //  No need to call setChecked().  The filters on/off is checked in onPrepareOptionsMenu() above and the checkbox set/cleared.
                if (mService.filters()) {
                    mService.filterObject = null;   //  if filters currently on then turn them off.
                    Toast.makeText(getApplicationContext(), getString(R.string.filter_toast_off), Toast.LENGTH_LONG).show();
                } else {
                    if (filtersInterface == null) {
                        logInfo("filterAlertsInterface instance is null *****");
                        filtersInterface = new FiltersInterface(this, mService);
                    } else {
                        logInfo("filterAlertsInterface instance is NOT null *****");
                        filtersInterface.startUI();
                    }
                }
            }
            return true;
        } else if (itemId == R.id.action_sorting) {
            logInfo("onOptionsItemSelected() Sorting *********************");
            if (mService != null) {             //  should alays be non-null
                if (mService.doingSorting()) {
                    mService.setSorting(false, -1);
                    Toast.makeText(getApplicationContext(), getString(R.string.dialog_sorting_toast_off), Toast.LENGTH_LONG).show();
                } else {
                    DialogFragment dialogSorting = new DialogSorting();
                    dialogSorting.show(getSupportFragmentManager(), "Dialog Sorting");
                    dialogSorting.setCancelable(false);
                }
            }
            return true;
        } else if (itemId == R.id.action_mapping) {
            if (getResources().getBoolean(R.bool.includeMapping)) {
                logInfo("onOptionsItemSelected() Mapping *********************");
                if (!isGoogleMapsInstalled()) {         // if Google Maps are not installed then inform the user that he's missing out.
                    DialogFragment dialogNoGMaps = new DialogNoGMaps();
                    dialogNoGMaps.show(getSupportFragmentManager(), "DialogNoGMaps");
                    dialogNoGMaps.setCancelable(false);

                } else {
                    if (mService != null) {                     //  should never happen
                        HelperStationList helperStationList = mService.getHelperStationList();
                        if (helperStationList != null) {        //  also should never happen
                            String myGrid = helperStationList.getMyGrid();
                            String[] rxGrids = helperStationList.getRxGridsForMapping();
                            Intent intentMap = new Intent(this, MapsActivity.class);
                            intentMap.putExtra(MapsActivity.MAP_EXTRA_MY_GRID, myGrid);
                            intentMap.putExtra(MapsActivity.MAP_EXTRA_RX_GRIDS, rxGrids);
                            intentMap.putExtra(MapsActivity.MAP_SCREEN_ALWAYS_ON, screenAlwaysOnForDisplay);
                            startActivity(intentMap);
                        }
                    }
                }
            } else {
                Intent intentMap = new Intent(this, MapsActivity.class);
                intentMap.putExtra(MapsActivity.MAP_SCREEN_ALWAYS_ON, screenAlwaysOnForDisplay);
                startActivity(intentMap);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        logInfo("onStart() *********************");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logInfo("onResume() ********************* initStartup:"+initialStartup);

        //  Register the broadcast receiver using LocalBroadcastManager.
        LocalBroadcastManager mLocalBroadcast = LocalBroadcastManager.getInstance(this);
        IntentFilter mBroadcastIntent = new IntentFilter(UDPService.ACTION_BROADCAST_MAIN);
        mLocalBroadcast.registerReceiver(myReceiver, mBroadcastIntent);

        if (initialStartup) {
            if (!wifiConnected) {
                DialogFragment dialogNoWifi = new DialogNoWifi();
                dialogNoWifi.show( getSupportFragmentManager(), "DialogNoWifi");
                dialogNoWifi.setCancelable(false);
            }
            else {
                if (installedAndJustUpgraded) {
                    installedAndJustUpgraded = false;
                    DialogFragment newFeaturesDialog = new DialogNewFeatures();
                    newFeaturesDialog.show( getSupportFragmentManager(), "DialogNewFeatures");
                }
                else {
                    bringupAboutMenu();
                }
            }
            if (appExpired) {
                DialogFragment dialogExpired = new DialogExpiration();
                dialogExpired.show( getSupportFragmentManager(), "DialogExpiration");
                dialogExpired.setCancelable(false);
            }
            initialStartup = false;
        } else {
            if (mService != null) {
                logInfo("onResume() ****** updating screen");
                if (mService.dataIsFlowing()) {
                    manageDisplay();        //  If just coming back from screen rotation or app in background then get the latest screen display.
                }
            }
        }

        //  If DisplayAll or UDPService invoked a shutdown ...
        if (WSJTXUtils.getIsFinishInvoked( this )) {
            WSJTXUtils.setIsFinishInvoked( this, false );    //  clear the variable or else it will always shut down.s
            doFinish();                                                     //  kill this Activity
        }

    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        logInfo( "onResumeFragments() called *****");

        FragmentManager fm = getSupportFragmentManager();
        mRetainedFragmentFilter = (RetainedFragmentFilter) fm.findFragmentByTag(TAG_RETAINED_FRAGMENT_FILTER);
        if (mRetainedFragmentFilter == null) {    //  Tom - if this has never been created.
            // add the fragment
            mRetainedFragmentFilter = new RetainedFragmentFilter();
            fm.beginTransaction().add(mRetainedFragmentFilter, TAG_RETAINED_FRAGMENT_FILTER).commit();
            logInfo("onResumeFragments() new RetainedFragmentFilter was created *****");
        }
        else {
            filtersInterface = mRetainedFragmentFilter.getData();
            if (filtersInterface != null) {
                filtersInterface.setContext(this);
                logInfo("onResumeFragments() RetainedFragmentFilter.getData() called.  filterAlertsInterface object NOT null *****");
            }
            else {
                logInfo("onResumeFragments() RetainedFragmentFilter.getData() called but filterAlertsInterface object NULL *****");
            }
        }
        mRetainedFragmentAlert = (RetainedFragmentAlert) fm.findFragmentByTag(TAG_RETAINED_FRAGMENT_ALERT);
        if (mRetainedFragmentAlert == null) {    //  Tom - if this has never been created.
            // add the fragment
            mRetainedFragmentAlert = new RetainedFragmentAlert();
            fm.beginTransaction().add(mRetainedFragmentAlert, TAG_RETAINED_FRAGMENT_ALERT).commit();
            logInfo("onResumeFragments() new RetainedFragmentAlert was created *****");
        }
        else {
            alertsInterface = mRetainedFragmentAlert.getData();
            if (alertsInterface != null) {
                alertsInterface.setContext(this);
                logInfo("onResumeFragments() RetainedFragmentAlert.getData() called.  alertsAlertsInterface object NOT null *****");
            }
            else {
                logInfo("onResumeFragments() RetainedFragmentAlert.getData() called but alertsAlertsInterface object NULL *****");
            }
        }
    }

    @Override
    protected void onPause() {
        logInfo("onPause() ********************* "+this.isFinishing());

        if (this.isFinishing()) {           //  WRITELOGSTOANDROIDFILE
            DebugUtils.closeAndroidLogFile();
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        logInfo("onStop() *********************");

        mRetainedFragmentFilter.setData(filtersInterface);      //  I could check if filtersInterface == null but there is no harm in storing a null.
        mRetainedFragmentAlert.setData(alertsInterface);        //  ditto
        logInfo("onStop() RetainedFragmentFilter/Alert.setData() called *****");

        if (this.isFinishing()) {           //  WRITELOGSTOANDROIDFILE
            DebugUtils.closeAndroidLogFile();
        }
    }

    @Override
    protected void onDestroy() {
        logInfo("onDestroy() ********************* "+upOrBackButtonPressed);

        if (this.isFinishing()) {           //  WRITELOGSTOANDROIDFILE
            DebugUtils.closeAndroidLogFile();
        }

        super.onDestroy();
    }

    //
    //  Beginning of listeners
    //

    //  DialogSetRxFreq listeners

    @Override
    public void dialogSetRxFreqSelectionListener(DialogFragment dialog, int which ) {
        logInfo("dialogSetRxFreqSelectionListener   *****1");
        if (mService != null) {
            messageSetRxFreqSet = true;
            mService.setRxFreqSend( which );
        }
    }

    @Override
    public void dialogSetRxFreqHelpListener(DialogFragment dialog ) {
        logInfo("dialogSetRxFreqHelpListener *****");
        DialogFragment testFrag = DialogSetRxFreqHelp.newInstance(0);
        testFrag.show( getSupportFragmentManager(), "DialogSetRxFreqHelp");
        testFrag.setCancelable(false);
    }

    @Override
    public void dialogSetRxFreqDismissListener(DialogFragment dialog ) {
        logInfo("dialogSetRxFreqDismissListener *****");
    }

    //  DialogSetRxFreqHelp listeners

    @Override
    public void dialogSetRxFreqHelpDismissListener(DialogFragment dialog ) {
        logInfo("dialogSetRxFreqHelpDismissListener *****");
        if (dialogSetRxFreqAfterDismissHelp) {
            //  If DialogSetRxFreqHelp was presented because the red arrow icon was pressed for the first time then
            //      bring up DialogSetRxFreq now.
            dialogSetRxFreqAfterDismissHelp = false;
            CharSequence[] setRxFreqCharSeq = mService.getSetRxCharSeq();
            if ((setRxFreqCharSeq != null) && (setRxFreqCharSeq.length > 0)) {
                DialogFragment dialogSetRxFreq = DialogSetRxFreq.newInstance(setRxFreqCharSeq);
                dialogSetRxFreq.show(getSupportFragmentManager(), "DialogSetRxFreq");
                dialogSetRxFreq.setCancelable(false);
            }
        }
    }

    @Override
    public void dialogSetRxFreqHelpSetupListener(DialogFragment dialog ) {
        logInfo("dialogSetRxFreqHelpSetupListener *****");
        dialogSetupDetailsDoCancel = true;
        dialogSetupDetailsStepNumber = 0;
        doingReplySetupDetails = true;
        manageReplySetupDetails();
    }

    //  DialogReplyHelp listener

    @Override
    public void dialogReplyHelpSetupListener(DialogReplyHelp dialog ) {
        logInfo("dialogReplyHelpSetupListener   *****");
        dialogSetupDetailsDoCancel = true;
        dialogSetupDetailsStepNumber = 0;
        doingReplySetupDetails = true;
        manageReplySetupDetails();
    }

    //  DialogSorting listeners

    @Override
    public void dialogSortingNextListener(DialogSorting dialog ) {
        logInfo("dialogSortingNextListener   "+dialog.selection+" *****");
        if (mService != null) {
            mService.setSorting(true, dialog.selection);
            String toastString = mService.describeSorting();
            Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_LONG).show();

            //  Display a dont-show-again dialog warning the user that three pass decoding will have three sorted lists.
            if (mService.threePassDecoding()) {
                if (!WSJTXUtils.getSortingDontShowAgain(this)) {
                    DialogFragment sortingDont = DialogDontShowAgain.newInstance(0, R.string.dialog_sorting_dont_text, 3);
                    sortingDont.show(getSupportFragmentManager(), "DialogSortingDont");
                    sortingDont.setCancelable(false);
                }
            }
        }
    }

    @Override
    public void dialogSortingCancelListener(DialogSorting dialog ) {
        logInfo("dialogSortingCancelListener   *****");
    }

    @Override
    public void dialogSortingHelpListener(DialogSorting dialog ) {
        logInfo("dialogSortingHelpListener   *****");
        DialogFragment helpY = FiltersInterface.BasicClassHelp.newInstance( R.string.dialog_sorting_help );
        helpY.show( getSupportFragmentManager(), "Help class");
    }

    //  DialogMinSignal listeners

    @Override
    public void dialogMinSignalHelpListener(DialogFragment dialog ) {
        logInfo("dialogMinSignalHelpListener   *****");
        alertsInterface.doHelp();
    }

    @Override
    public void dialogMinSignalCancelListener(DialogFragment dialog ) {
        logInfo("dialogMinSignalCancelListener *****");
        alertsInterface.doCancel();
    }

    @Override
    public void dialogMinSignalFinishListener(DialogFragment dialog ) {
        logInfo("dialogMinSignalFinishListener *****");
        alertsInterface.doNext( dialog );
    }

    @Override
    public void dialogMinSignalBackButtonListener(DialogFragment dialog ) {
        logInfo("dialogMinSignalBackButtonListener *****");
        alertsInterface.doBackButton();
    }

    //  DialogAlertSettings listeners and onClick() routines.

    @Override
    public void dialogAlertSettingsMinSignalListener(DialogFragment dialog) {
        logInfo("dialogAlertSettingsMinSignalListener   ************");
        alertsInterface.doSelection(dialog, 1);
    }

    @Override
    public void dialogAlertSettingsHelpListener(DialogFragment dialog ) {
        logInfo("dialogAlertSettingsHelpListener   ************");
        alertsInterface.doHelp();
    }

    @Override
    public void dialogAlertSettingsCancelListener(DialogFragment dialog ) {
        logInfo("dialogAlertSettingsCancelListener   ************");
        alertsInterface.doCancel();
    }

    @Override
    public void dialogAlertSettingsFinishListener(DialogFragment dialog ) {
        logInfo("dialogAlertSettingsFinishListener   ************");
        alertsInterface.doNext( dialog );
    }

    @Override
    public void dialogAlertSettingsBackButtonListener(DialogFragment dialog ) {
        logInfo("dialogAlertSettingsBackButtonListener   ************");
        alertsInterface.doBackButton();
    }

    @Override
    public void dialogAlertSettingsTestSoundListener(DialogAlertSettings dialog, int soundVibrateTrueIndex ) {
        logInfo("dialogAlertSettingsTestSoundListener   ************");
        mService.testVibrationAndSound( soundVibrateTrueIndex );
    }

    //  Called when user clicks on the text associated with the TEST button
    public void alertSettingsButtonSoundHelp(View view) {
        logInfo("alertSettingsButton1 onClick *****");
        int resource;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resource = R.string.help_no_sound_above_api_26;
        } else {
            resource = R.string.help_no_sound_below_api_26;
        }
        DialogFragment helpY = FiltersInterface.BasicClassHelp.newInstance( resource );
        helpY.show( getSupportFragmentManager(), "Help class");
    }

    //  DialogPrefixCallsign listeners

    @Override
    public void dialogPrefixCallsignHelpListener(DialogFragment dialog, boolean doingFilters) {
        logInfo("dialogPrefixCallsignHelpListener   ************");
        if (doingFilters) {
            filtersInterface.doHelp();
        } else {
            alertsInterface.doHelp();
        }
    }

    @Override
    public void dialogPrefixCallsignCancelListener(DialogFragment dialog, boolean doingFilters) {
        logInfo("dialogPrefixCallsignCancelListener   ************");
        if (doingFilters) {
            filtersInterface.doCancel();
        } else {
            alertsInterface.doCancel();
        }
    }

    @Override
    public void dialogPrefixCallsignFinishListener(DialogFragment dialog, boolean doingFilters) {
        logInfo("dialogPrefixCallsignFinishListener   ************");
        DialogPrefixCallsign dpc = ( DialogPrefixCallsign )dialog;
        logInfo("             listItems: ***** "+dpc.listItems.size());
        for (int iii = 0; iii < dpc.listItems.size(); iii++) {
            logInfo("                         "+dpc.listItems.get(iii)+"*****");
        }
        if (doingFilters) {
            filtersInterface.doNext();
        } else {
            alertsInterface.doNext( dialog );
        }
    }

    @Override
    public void dialogPrefixCallsignBackButtonListener(DialogFragment dialog, boolean doingFilters) {
        logInfo("dialogPrefixCallsignBackButtonListener   ************");
        if (doingFilters) {
            filtersInterface.doBackButton();
        } else {
            alertsInterface.doBackButton();
        }
    }

    //  DialogFilterAlerts listeners

    @Override
    public void dialogFilterAlertsHelpListener(DialogFragment dialog, boolean doingFilters ) {
        logInfo("FilterAlertsHelpListener   ************");
        if (doingFilters) {
            filtersInterface.doHelp();
        } else {
            alertsInterface.doHelp();
        }
    }

    @Override
    public void dialogFilterAlertsCancelListener(DialogFragment dialog, boolean doingFilters ) {
        logInfo("FilterAlertsCancelListener   ************");
        if (doingFilters) {
            filtersInterface.doCancel();
        } else {
            alertsInterface.doCancel();
        }
    }

    @Override
    public void dialogFilterAlertsNextListener(DialogFragment dialog, boolean doingFilters ) {
        logInfo("FilterAlertsNextListener   ************");
        if (doingFilters) {
            filtersInterface.doNext();
        } else {
            alertsInterface.doNext( dialog );
        }
    }

    @Override
    public void dialogFilterAlertsSelectionListener(DialogFragment dialog, int which, boolean doingFilters ) {
        logInfo("FilterAlertsSelectionListener   ************");
        if (doingFilters) {
            filtersInterface.doSelection(dialog, which);
        } else {
            alertsInterface.doSelection(dialog, which);
        }
    }

    @Override
    public void dialogFilterAlertsBackButtonListener(DialogFragment dialog, boolean doingFilters ) {
        logInfo("FilterAlertsBackButtonListener   ************");
        if (doingFilters) {
            filtersInterface.doBackButton();
        } else {
            alertsInterface.doBackButton();
        }
    }

    //
    //  end of listeners
    //

    private boolean isGoogleMapsInstalled()
    {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
            if (!info.enabled) {
                return false;
            }
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }


    public static class RetainedFragmentFilter extends Fragment {
        private FiltersInterface data;                          // data object we want to retain

        @Override
        public void onCreate(Bundle savedInstanceState) {       // this method is only called once for this fragment
            super.onCreate(savedInstanceState);


            setRetainInstance(true);                            // This call causes Android to retain this fragment when Activity is destroyed and created.
        }
        public void setData(FiltersInterface data) { this.data = data; }
        public FiltersInterface getData() { return data; }
    }

    public static class RetainedFragmentAlert extends Fragment {
        private AlertsInterface data;                           // data object we want to retain

        @Override
        public void onCreate(Bundle savedInstanceState) {       // this method is only called once for this fragment
            super.onCreate(savedInstanceState);
            setRetainInstance(true);                            // This call causes Android to retain this fragment when Activity is destroyed and created.
        }
        public void setData(AlertsInterface data) { this.data = data; }
        public AlertsInterface getData() { return data; }
    }


    public static class MainDisplayHelp extends DialogFragment {

        private static final String NO_CONNECTIVITY = "no_connectivity";

        private boolean parameterConnectivity = false;

        public static MainDisplayHelp newInstance( boolean parameter1Conn ) {
            MainDisplayHelp fragment = new MainDisplayHelp();
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

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            if (readBundle(getArguments())) {
                if (DO_LOGGING) {
                    Log.i(TAG,"MainDisplayHelp.onCreateDialog readBundle() ok  *****" + parameterConnectivity); // cannot call logInfo() because in static class.
                }
            }

            //  Prepare SpannableStringBuilder for the colored lines.
            String sss = "";
            if (!parameterConnectivity) {
                sss = getResources().getString(R.string.display_no_conn_text);
            }
            String sss2 = sss+getResources().getString(R.string.main_display_text);
            String sss3 = sss2+getResources().getString(R.string.main_display_text2);
            String sss4 = sss3+getResources().getString(R.string.main_display_text3);
            String sss5 = sss4+getResources().getString(R.string.main_display_text4);
            String sss6 = sss5+getResources().getString(R.string.main_display_text5);
            String sss7 = sss6+getResources().getString(R.string.main_display_text6);
            String sss8 = sss7+getResources().getString(R.string.main_display_text7);
            String sss9 = sss8+getResources().getString(R.string.main_display_text8);
            String sssA = sss9+getResources().getString(R.string.main_display_text8a);
            String sssB = sssA+getResources().getString(R.string.main_display_text9);
            String sssC = sssB+getResources().getString(R.string.main_display_text9a);
            SpannableStringBuilder ssb = new SpannableStringBuilder(sssC);
            ssb.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), 0, sss.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
            ssb.setSpan(new BackgroundColorSpan(Color.GREEN), sss2.length(), sss3.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
            ssb.setSpan(new BackgroundColorSpan(Color.RED), sss4.length(), sss5.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
            ssb.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ), sss6.length(), sss7.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );

            //int dxColor = getResources().getColor(R.color.dxCallColor);
            int dxColor = ContextCompat.getColor(requireContext(), R.color.dxCallColor);
            ssb.setSpan(new ForegroundColorSpan(dxColor), sss8.length(), sss9.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), sss8.length(), sss8.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.setSpan(new BackgroundColorSpan(Color.YELLOW), sssA.length(), sssB.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );

            LayoutInflater inflater = getActivity().getLayoutInflater();
            View mainDisplayHelpView = inflater.inflate(R.layout.dialog_main_display_help, null);

            builder.setTitle(R.string.main_display_title);

            builder.setView( mainDisplayHelpView );

            TextView text0View = mainDisplayHelpView.findViewById(R.id.main_display_help_text0);
            text0View.setText(ssb);

            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (DO_LOGGING) {
                        Log.i(TAG, "MainDisplayHelp OK *****");   // cannot call logInfo() because in static class.
                    }
                }
            });

            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class DialogNoWifi extends DialogFragment {
        @Override
        public void onStart() {         //   This is done so that the dialog can have a link in it.
            super.onStart();
            ((TextView) getDialog().findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.no_wifi_title);
            builder.setMessage(R.string.no_wifi_text);

            return builder.create();
        }
    }


    public static class DialogNoGMaps extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.no_gmaps_title);
            builder.setMessage(R.string.no_gmaps_text);
            builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (DO_LOGGING) {
                        Log.i(TAG, "DialogNoGMaps Dismiss *****");
                    }
                }
            });

            return builder.create();
        }
    }


    public static class DialogExpiration extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Application Expired");
            builder.setMessage("\n\nThis\n    application has\n        EXPIRED.\n\nPlease\n    remove it\n        from your \n                Android device.\n\nThank you\n\n");

            return builder.create();
        }
    }

    @Override
    public void onBackPressed() {
        //  I want it to do nothing if onBackPressed().
        logInfo("onBackPressed() HOME *****");
    }


    //  WRITELOGSTOANDROIDFILE
    //  Callback received when a permissions request has been completed.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        /*
        logInfo("onRequestPermissionsResult() *****");

        if (requestCode == DebugUtils.REQUEST_PERMISSIONS_STORAGE_REQUEST_CODE) {
            if (grantResults.length < 1) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                logInfo("nRequestPermissionsResult() Storage - User interaction was cancelled. *****");
                Toast.makeText(getApplicationContext(), "Memory permissions interrupted", Toast.LENGTH_LONG).show();
            }
            else if ( grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
                //  Permission granted.
                logInfo("RequestPermissionsResult() Storage - Permission granted. *****");
                Toast.makeText(getApplicationContext(), "Memory permissions granted", Toast.LENGTH_LONG).show();
                DebugUtils.createAndInitAndroidLogFile();
            }
            else {
                logInfo("RequestPermissionsResult() Storage - Permission denied. *****");
                Toast.makeText(getApplicationContext(), "Memory permissions DENIED", Toast.LENGTH_LONG).show();
            }
        }
        */
    }



    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

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


import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Locale;

import static java.lang.String.format;


public class UDPService extends Service {

    private static final String PACKAGE_NAME = "com.feotec.wsjt_xmonitor";
    private static final String TAG = UDPService.class.getSimpleName();
    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    static final String ACTION_BROADCAST_MAIN = PACKAGE_NAME + ".broadcastMain";
    private static final boolean DO_LOGGING = DebugUtils.loggingUDPService;
    private static final int CONVERT_QINT_ERROR = -9999;
    public static final long[] vibrationTimings = { 0, 1000, 200, 1000, 200, 1000 };    //  used here and in MainActivity

    private final IBinder mBinder = new LocalBinder();
    private boolean threadRunning;
    private DatagramSocket ds = null;
    //private MulticastSocket ds = null;
    private int totalPackets;
    private int totalDuplicates;
    private boolean mChangingConfiguration;
    private boolean mServiceRunning;    // set in onStartCommand(), cleared in onDestroy().  If OS kills service onDestroy won't be called but this will be cleared anyhow.

    private static final int NOTIFICATION_ID = 407776100;       // The identifier for the notification displayed for the foreground service.
    private NotificationManagerCompat mNotificationManager;
    private boolean inForeground;

    private SpannableStringBuilder displaySpanBuffer = null;
    private SpannableStringBuilder displaySpanBufferMain = null;
    private final int DISPLAY_RING_BUFFER_SIZE = 14000;
    private boolean isCQ;
    private String myCall = null;
    private String myGrid = null;
    private boolean isMyCall;
    private String currentDXCall = null;
    private String currentDXGrid = null;
    private boolean isJTDX;
    private int currentRxFreq = 0;
    private boolean endOfBurst;                     //  Reflects end of burst based on Msg 1 decode field transitioning from true to false.  Used to send broadcast msg.
                                                    //      There is currently no backup for this if msg 1 is missed.
    private boolean decodeFlagMirror;               //  Reflects the current status of the decode field in Message 1.
    private boolean suppressDashedLine;             //  MSK-144 mode does not want the dotted line displayed.
    private InetAddress inetAddress;                //  Used in udpLoop(), new DatagramSocket().
    private int ipAddressHashCode;
    private boolean mDisplaySpanBufferChanging;
    private boolean mDisplaySpanBufferMainChanging;
    private boolean dataFlowing;                    //  Set to true the first time a packet is received.  Used in the interface.
    private boolean dataMsg2or10Flowing;            //  Set to true the first time a msg 2 or 10 is received.  Used in udpLoop().  If clear then it will send broadcasts on
                                                    //      all messages.  Without this the user will have to wait for end-of-burst, which in WSPR mode could take 2 min.
    private boolean cqOnlyMainDisplay;              //  if true then only provide main display with CQ messages.
    Filters filterObject = null;                    //  package-private
    Alerts alertObject = null;                      //  package-private
    private HelperStationList helperStationList = null;    //  instance of HelperStationList class.
    private LinkedList<DecodeDataStruct> decodeDataList = new LinkedList<>();
    private final int MAX_COUNTRY_SIZE = 13;

    private Uri notificationUri = null;
    private Vibrator vibrator;
    private boolean deviceHasVibrator;
    private Ringtone ringtone;

    private boolean alertsNowDisplayed;
    private ArrayList<CharSequence> notificationHold;

    private boolean sorting;
    private enum SortingOn { SORTING_NONE, SORTING_SNR, SORTING_FREQ, SORTING_DISTANCE, SORTING_AZIMUTH };
    private SortingOn sortingOn;

    static final int DATAGRAM_BUFFER_SIZE = 512;

    private static final int NO_SNR = -999;
    private static final int MSG4_INHIBIT_COUNT = 2;
    private byte[] msg4Buffer;
    private int msg4SNR;            //  Used if more than one station caused an alert.  Also used as a flag.  A reply will not be sent out unless msg4SNR != NO_SNR.
    private int msg4Length;         //  need this variable because msg4Buffer[] is fixed length so msg4Buffer.length won't give the number of bytes to be sent.
    private boolean msg4Enabled;    //  If alerts are on it will still collect reply, just not send it.
    private int msg4Inhibit;        //  a counter.  Used to inhibit sending ms4 for two bursts after transmitting.

    private boolean distanceInMiles;
    private boolean clearScreenFromWSJTX;
    private boolean clearScreenWhenDoneWithBurst;

    private String wsjtxFreq = null;
    private String wsjtxMode = null;

    //  These three variables plus threePassDecodeInUse (a boolean local to convertStatusMessage()) handle WSJT-X v2.2.0 three pass decoding.  Three pass decoding is accompanied
    //      by three sets of framing msg1 (decode on-off).  First the version is read from msg0 in convertHeartbeatMessage().  The format is '2.2.0', a String.  If it is 2.2.0 or
    //      higher then threePassDecode is set true, otherwise false.  This really only says that it is capable of three pass decoding, since FT8 is the only mode that does it.
    //  Next convertStatusMessage() checks to see if the mode is FT8.  If so then it sets the local boolean threePassDecodeInUse.  Then, within the same status message it will
    //      check (as before) if transitioning from decode true to decode false.  If so and if threePassDecodeInUse is true then it will bump decodeCounter and if set to 3 or greater
    //      then it will set endOfBurst true.  From there everything proceeds as before.
    //  If one of the msg1 decode false is missed then decodeCounter will get out of sync and never get back.  So as a safety valve I record the current time since boot into
    //      timeSinceLastDecode.  If more than 7 seconds has elapsed since the last transition from decode on to off then it will set decodeCounter to zero.
    private int decodeCounter;
    private boolean threePassDecode;
    private long timeSinceLastDecode;
    private boolean threePassDecodeInUse;

    //
    //  The following block of variables is for the setRxFreq algorithm.
    //
    private static class SetRxFreqData {
        CharSequence setRxFreqCharSeq;          //  The text for the dialog
        byte[] setRxFreqMsg4Buffer;             //  The msg4 response
        int setRxFreqMsg4Length;                //  The msg4 response length, includes the first 12 bytes not in buffer
        String callsign;                        //  callsign, for comparison purposes in setRxFreqSend()
        int dFreq;                              //  Rx Freq, for comparison purposes in setRxFreqSend()

        //  primary constructor
        SetRxFreqData() {}
        //  constructor to perform a deep copy of all the elements in the class, for use with the copyOf() method.
        private SetRxFreqData( CharSequence setRxFreqCharSeqParam, byte[] setRxFreqMsg4BufferParam, int setRxFreqMsg4LengthParam, String callsignParam, int dFreqParam ) {
            setRxFreqCharSeq = setRxFreqCharSeqParam; //setRxFreqCharSeqParam.subSequence( 0, setRxFreqCharSeqParam.length() );
            setRxFreqMsg4Buffer = new byte[ setRxFreqMsg4BufferParam.length ];
            System.arraycopy(setRxFreqMsg4BufferParam, 0, setRxFreqMsg4Buffer, 0, setRxFreqMsg4BufferParam.length);
            setRxFreqMsg4Length = setRxFreqMsg4LengthParam;
            callsign = callsignParam;
            dFreq = dFreqParam;
        }
        SetRxFreqData copyOf() {
            return new SetRxFreqData( setRxFreqCharSeq, setRxFreqMsg4Buffer, setRxFreqMsg4Length, callsign, dFreq );
        }
    }
    private ArrayList<SetRxFreqData> setRxFreqArrayList;    //  setup in buildBurstAndAppend().  It collects data from decodeDataList
    private SetRxFreqData[] setRxFreqArrayCopy; //  holds a copy of setRxFreqArray while DialogSetRxFreq is up.
    private boolean setRxFreqArrayInUse;        //  Set when setRxFreqArray[] is being manipulated by buildBurstAndAppend() so dialog can't grab it.
    private boolean currentModeWSPR;            //  Set/cleared in convertStatusMessage().  Used by setRxFreq business because it doesn't work in WSPR.
    private InetAddress replyInetAddress;
    private int replyInetPort;

    long firstHeartbeatMsgTime;
    int heartbeatMessageCounter;

    //  Timer for checking whether msg4 (setting Rx Frequency, not alerts) got through successfully.  setRxFreqSend() below initiates the timer by calling
    //          timerHandler.postDelayed(timerRunnable, timerRunnableTimeout);
    //  This run() will then execute when the time (set by timerRunnableTimeout) has expired.  Within this routine I can do this again (substituting 'this' for
    //  the first parameter) and the timer will run forever, even after the app has terminated.  If the timer is running and I want to terminate it early I can do
    //          timerHandler.removeCallbacks(timerRunnable);
    //  This will take the timer out of the queue.  This is done with the screen timers in BaseActivity but there isn't any need here.
    private static final int timerRunnableTimeout = 200;        // in mS
    private Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {		// Runnable is an Interface
        @Override
        public void run() {			// Runnable has one required method, run()

            logInfo("Timer setRxFreq *****1");
            if ( (currentDXCall != null) && (!currentDXCall.equals("")) ) {     //  should never happen because setRxFreqSend() won't initiate msg4 if this is the case.
                if (!currentDXCall.equals(setRxFreqOrigDXCall)) {   //  if callsign changed then msg4 went through successfully
                    setRxFreqCounter = 0;
                    setRxFreqStatus = 1;
                    return;
                }
            }
            if (currentRxFreq != 0) {                                       //  should never happen because setRxFreqSend() won't initiate msg4 if this is the case.
                if (currentRxFreq != setRxFreqOrigRxFreq) {         //  if receive frequency changed then msg4 went through successfully
                    setRxFreqCounter = 0;
                    setRxFreqStatus = 1;
                    return;
                }
            }
            if ( (setRxFreqSelection != -1) && (setRxFreqSelection < setRxFreqArrayCopy.length )) {     // should never happen, just in case ...
                setRxFreqCounter++;
                if (setRxFreqCounter < 3) {
                    logInfo("Timer setRxFreq resending *****1");
                    setRxFreqSend(setRxFreqSelection);
                } else {
                    setRxFreqCounter = 0;
                    setRxFreqStatus = -1;
                    doLocalBroadcastMain();
                    logInfo("Timer setRxFreq quit trying *****1");
                }
            }
            //timerHandler.postDelayed(this, timerRunnableTimeout);
        }
    };
    private String setRxFreqOrigDXCall = null;
    private int setRxFreqOrigRxFreq = 0;
    private int setRxFreqSelection = -1;
    private int setRxFreqCounter = 0;
    private int setRxFreqStatus = 0;      //  int instead of boolean.  0 means unknown, -1 is failure, +1 is success


    //  The purpose of this is to return a reference to this Service to MainActivity.
    class LocalBinder extends Binder {
        //  Tom - one method, getService() - return a reference to this service.
        UDPService getService() {
            return UDPService.this;
        }
    }

    @Override
    public void onCreate() {
        logInfo("UDPService onCreate(): " + format(Locale.US,"Thread %d", Thread.currentThread().getId() ) + "*********************");
        totalPackets = totalDuplicates = 0;
        isCQ = isMyCall = false;
        displaySpanBuffer = new SpannableStringBuilder("");
        displaySpanBufferMain = new SpannableStringBuilder("");
        mChangingConfiguration = false;
        mServiceRunning = false;
        mDisplaySpanBufferChanging = false;
        mDisplaySpanBufferMainChanging = false;
        endOfBurst = decodeFlagMirror = suppressDashedLine = false;
        inForeground = false;
        dataFlowing = false;
        dataMsg2or10Flowing = false;
        cqOnlyMainDisplay = false;
        alertsNowDisplayed = false;
        notificationHold = null;
        helperStationList = new HelperStationList( this );
        firstHeartbeatMsgTime = 0;
        heartbeatMessageCounter = -1;   // start with -1 to make estimated number of heartbeat messsages come out right.

        sorting = false;
        sortingOn = SortingOn.SORTING_NONE;

        msg4Buffer = new byte[DATAGRAM_BUFFER_SIZE]; // = { 0xad, 0xbc, 0xcb, 0xda, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x04 };
        msg4Buffer[0] = (byte)0xad;     msg4Buffer[1] = (byte)0xbc;     msg4Buffer[2] = (byte)0xcb;     msg4Buffer[3] = (byte)0xda;
        msg4Buffer[4] = (byte)0x00;     msg4Buffer[5] = (byte)0x00;     msg4Buffer[6] = (byte)0x00;     msg4Buffer[7] = (byte)0x02;
        msg4Buffer[8] = (byte)0x00;     msg4Buffer[9] = (byte)0x00;     msg4Buffer[10] = (byte)0x00;    msg4Buffer[11] = (byte)0x04;
        msg4SNR = NO_SNR;
        msg4Length = 0;
        msg4Enabled = false;
        msg4Inhibit = 0;

        setRxFreqArrayList = null;
        setRxFreqArrayCopy = null;
        setRxFreqArrayInUse = false;
        currentModeWSPR = false;
        replyInetAddress = null;
        replyInetPort = 0;
        isJTDX = false;
        decodeCounter = 0;
        threePassDecode = threePassDecodeInUse = true;
        timeSinceLastDecode = 0;

        distanceInMiles = WSJTXUtils.getMilesNotKm( this );     // DialogKmMiles will write any changes to memory.  It will also call setKmOrMiles() below.
                                                                        //   So UDPService only needs to access this at startup.
        clearScreenFromWSJTX = WSJTXUtils.getClearScreenFromWSJTX( this );  // similar to distanceInMiles boolean, this only needs to be read at startup.
        clearScreenWhenDoneWithBurst = false;

        //  Tom - Get an instance of NotificationManaterCompat using static method .from( Context ), only used when calling .notify().
        mNotificationManager = NotificationManagerCompat.from(this);

        //  notificationUri has the Uri for the default sound (beep).  It is used below when setting up channels and in getNotification
        try {
            //  See notes 3/12-13/2021 for an explanation of the below lines.
            notificationUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
            //notificationUri = RingtoneManager.getDefaultUri( RingtoneManager.TYPE_NOTIFICATION); //
            //notificationUri = Settings.System.DEFAULT_RINGTONE_URI;
            //notificationUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        } catch(Exception ex) {
            logInfo("Exception onCreate() notificationUri = Settings.System.DEFAULT_NOTIFICATION_URI:  ********** " + ex.getMessage());
        }

        //  Below is used only by MainActivity when an alert occurs while MainActivity is up (by calling mainActivityVibrate() below).
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        deviceHasVibrator = vibrator.hasVibrator();     //  some devices have no vibrator.

        //  Below code sets up ringtone object.  Used only by MainActivity when an alert occurs while MainActivity is up (by calling mainActivitySound() below).
        ringtone = RingtoneManager.getRingtone(this, notificationUri ); // will return null if failure
        if (ringtone != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                AudioAttributes attrib = new AudioAttributes.Builder()      // An identical class is initialized immediately below.  But it has to be conditioned on API > 21 (5.0)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)       //      So might as well leave it here.
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ringtone.setAudioAttributes(attrib);
            } else {
                doRingtonePre21();
                //ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel version 8.0 (Oreo, build 26) and up.
            //  IMPORTANT - when making changes to this block of code the app must be uninstalled and reinstalled for the changes to take effect.  As an example, the sound
            //      is set to notificationUri (a single beep).  But the user can change it and the .setSound() call below won't change it back.

            //  Set up four channels.  (SET IMPORTANCE to HIGH will have it displayed at top of screen).
            NotificationChannel mChannel, mChannelVib, mChannelSnd, mChannelVibSnd;
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);  // get notification manager via Context.getSystemService()
            AudioAttributes attrib = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            //  First channel is the silent one, same name and ID as older versions.
            mChannel = new NotificationChannel(
                    getString(R.string.notification_channel_id),    //  Channel ID
                    getString(R.string.notification_channel),       //  the "visible name" of the channel
                    NotificationManager.IMPORTANCE_DEFAULT);        //  importance
            mChannel.setDescription(getString(R.string.channel_description));
            mChannel.setLockscreenVisibility( NotificationCompat.VISIBILITY_PUBLIC );
            mChannel.setSound(null, null);      //  silence if both parameters null.  cannot change after channel created.

            try {
                notificationManager.createNotificationChannel(mChannel);
            } catch(Exception ex) {
                logInfo("Exception onCreate() createNotificationChannel (silent channel):  ********** " + ex.getMessage());
            }

            //  Second channel, vibrate only
            mChannelVib = new NotificationChannel(
                    getString(R.string.notification_channel_vib_id),    //  Channel ID - the only thing that links this channel with NotificationCompat.Builder, the actual notification
                    getString(R.string.notification_channel_vib),       //  the "visible name" of the channel
                    NotificationManager.IMPORTANCE_DEFAULT);
            mChannelVib.setDescription(getString(R.string.channel_description));
            mChannelVib.setLockscreenVisibility( NotificationCompat.VISIBILITY_PUBLIC );
            mChannelVib.setSound(null, null);      //  silence if both parameters null.  cannot change after channel created.
            mChannelVib.setVibrationPattern(vibrationTimings);
            mChannelVib.enableVibration(true);

            try {
                notificationManager.createNotificationChannel(mChannelVib);
            } catch(Exception ex) {
                logInfo("Exception onCreate() createNotificationChannel (vibrate channel):  ********** " + ex.getMessage());
            }

            //  Third channel, sound only
            mChannelSnd = new NotificationChannel(
                    getString(R.string.notification_channel_sound_id),    //  Channel ID
                    getString(R.string.notification_channel_sound),       //  the "visible name" of the channel
                    NotificationManager.IMPORTANCE_DEFAULT);
            mChannelSnd.setDescription(getString(R.string.channel_description));
            mChannelSnd.setLockscreenVisibility( NotificationCompat.VISIBILITY_PUBLIC );
            mChannelSnd.setSound(notificationUri, attrib);

            try {
                notificationManager.createNotificationChannel(mChannelSnd);
            } catch(Exception ex) {
                logInfo("Exception onCreate() createNotificationChannel (sound channel):  ********** " + ex.getMessage());
            }

            //  Fourth channel, vibrate and sound.
            mChannelVibSnd = new NotificationChannel(
                    getString(R.string.notification_channel_vibsound_id),    //  Channel ID
                    getString(R.string.notification_channel_vibsound),       //  the "visible name" of the channel
                    NotificationManager.IMPORTANCE_DEFAULT);
            mChannelVibSnd.setDescription(getString(R.string.channel_description));
            mChannelVibSnd.setLockscreenVisibility( NotificationCompat.VISIBILITY_PUBLIC );
            mChannelVibSnd.setSound(notificationUri, attrib);
            mChannelVibSnd.setVibrationPattern(vibrationTimings);
            mChannelVibSnd.enableVibration(true);

            try {
                notificationManager.createNotificationChannel(mChannelVibSnd);
            } catch(Exception ex) {
                logInfo("Exception onCreate() createNotificationChannel (vibrate-sound channel):  ********** " + ex.getMessage());
            }
        }
    }

    //  Made into a function so that the deprecation warning suppression could be reduced to one line and not prevent me from seeing future warnings in the calling routine.
    @SuppressWarnings("deprecation")
    private void doRingtonePre21() {
        ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);   // method won't be called if ringtone == null so no need to check here.
    }

    //  Made into a function so that the deprecation warning suppression could be reduced to one line and not prevent me from seeing future warnings in the calling routine.
    @SuppressWarnings("deprecation")
    private void doVibratePre26() {
        vibrator.vibrate(UDPService.vibrationTimings, -1);
    }


    @Override
    public void onDestroy() {
        logInfo("UDPService onDestroy() *********************");
        mServiceRunning = false;

        if (threadRunning) {        // if threadRunning is still set then the OS may be killing the service.  In that case kill the thread before it restarts.
            threadRunning = false;
            if (ds != null) { ds.close();  }     // closing the socket forces the ds.receive() to exit, closing the thread.
        }
    }

    private void threadMethod() {
        Runnable threadRunnable = new Runnable() {
            @Override
            public void run() {
                logInfo("UDPService threadMethod(): " + format(Locale.US, "Thread %d PID %d", Thread.currentThread().getId(), android.os.Process.myPid() ) + "*********************");
                udpLoop();
                logInfo("UDPService threadMethod(): quit " + format(Locale.US, "Thread %d", Thread.currentThread().getId()) + "*********************");
            }
        };
        Thread UDPThread = new Thread( threadRunnable );
        UDPThread.start();
    }

    //  Tom - this method is called every time startService() is called.  It is not called when the screen rotates.  Note the intent parameter is the
    //      same one passed to startService() below.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logInfo("UDPService onStartCommand(): " + format(Locale.US,"Thread %d", Thread.currentThread().getId() ) + "*********************");

        threadMethod();
        threadRunning = true;
        mServiceRunning = true;
        return START_STICKY;        // Tells the system to try to recreate the service if the OS kills it.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //  Tom - this method appears to only be called when the screen is rotated.
        logInfo("UDPService onConfigurationChanged() *********************");
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) binds with this service.
        logInfo("UDPService onBind() *********************");

        stopForeground(true);   // true means remove notifications.  If service is not in foreground then this does nothing.
        inForeground = false;
        mChangingConfiguration = false;
        //  Tom - note that super.onBind() is not called.  It only returns NULL, no binding.
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) binds once again with this service.
        logInfo("UDPService onRebind() *********************");

        stopForeground(true);   // true means remove notifications.  If service is not in foreground then this does nothing.
        inForeground = false;
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //  Called when MainActivity.onStop() calls unbindService().
        logInfo("UDPService onUnbind() *********************");

        //  Tom - if not called when screen rotated AND service is running then move service to foreground.
        if (!mChangingConfiguration && mServiceRunning) {
            logInfo("UDPService onUnbind() Starting foreground service *********************");
            //  Service.startForeground().  Important to note that Service.startService() MUST be called first
            String notificationMessage = format(Locale.US,getResources().getString(R.string.notification_string),totalPackets);
            ArrayList<CharSequence> notificationArray = new ArrayList<>();
            notificationArray.add(notificationMessage);
            alertsNowDisplayed = false;         //  These two data objects are to keep an alert displayed on the notification until the next comes along.
            notificationHold = null;            //      Clear them every time the user moves the app to the background.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, getNotification( notificationArray, false ), FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, getNotification( notificationArray, false ));
            }
            inForeground = true;
        }
        return true; // Ensures onRebind() is called when a client re-binds.  If returns false then (apparently) onBind will be called.
    }

    //  Tom - This returns a Notification class which displays data on the status bar.  It is called from two places, when going into foreground (from Service.startForeground()
    //      which is called from onUnbind() above) and when the notification needs to be updated (when in foreground, below in UDPLoop() at the end of the burst).
    private Notification getNotification( ArrayList<CharSequence>  notificationList, boolean alertThisTime ) {
        logInfo("getNotifications() called *********************");

        //  This block of code is to keep an alert displayed on the notification until the next comes along.  The last notification will continue
        //      to be displayed until the user places the app back in the foreground.  Then if he moves to the background these variables are reset.
        if (alertThisTime) {
            notificationHold = new ArrayList<>(notificationList);
            if (!alertsNowDisplayed) {
                alertsNowDisplayed = true;
            }
        }
        else {              //  If have an alert up and no alert this time then keep the previous alert displayed.
            if (alertsNowDisplayed) {
                notificationList = new ArrayList<>(notificationHold);
            }
        }

        // The PendingIntent to launch activity when the user presses on the notification
        Intent tActivityIntent = new Intent(this, MainActivity.class);
        tActivityIntent.setAction(Intent.ACTION_MAIN);          //  These two statements allow the activity that was running to be restored when returning
        tActivityIntent.addCategory(Intent.CATEGORY_LAUNCHER);  //      via notification.  I added these without understanding.

        PendingIntent activityPendingIntent = PendingIntent.getActivity(this,
                (int)System.currentTimeMillis(),        //  This and the last parameter (FLAG_UPDATE_CURRENT) have to be set or else with my Android 8.0
                tActivityIntent,                        //    phone when returning from a notification onDestroy()-isFinishing() is called and the service
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);     //    is killed and data is lost.  Didn't happen on Android 6.0 or 4.4.

        String title;
        //  Set variables for title, sound, and vibration
        Uri uriUsed = null;         //  for <8.0.  if null then no sound
        long[] longUsed = null;     //  for <8.0.  if null then no vibration
        String channelIdUsed;       //  for 8.0+, set to channel that has sound and vibration configured
        if (!alertThisTime) {                                               //  If no alerts or if not the end of burst (called from startForeground() above) then ...
            channelIdUsed = getString(R.string.notification_channel_id);                    //  ... silence (8.0+).  For <8.0 uriUsed and longUsed are already null
            title = "WSJT-X Monitor";
        } else {                                                            //  Else if alerts are on then ...
            if (alertObject == null) {                                          //  Should never happen that alertObject == null when alertThisTime is true.
                channelIdUsed = getString(R.string.notification_channel_id);
            } else {
                channelIdUsed = alertObject.getCurrentChannelID();              //  ... get appropriate channel ID.  Note it could be silent
                if (alertObject.getSound()) {                                   //  ... and configure sound
                    uriUsed = notificationUri;
                }
                if (alertObject.getVibrate()) {                                 //  ... and configure vibration
                    longUsed = vibrationTimings;
                }
            }
            title = getResources().getString(R.string.title_notification);
        }

        String firstLine;
        if (notificationList.size() > 1) {
            firstLine = String.format(Locale.US, "%s %s", notificationList.get(0), getResources().getString(R.string.notification_more) );
        } else {
            firstLine = String.format(Locale.US, "%s", notificationList.get(0));
        }

        //  Tom - the constructor requires a channel ID, second parameter.  It is ignored on versions older than 8.0.  But on 8.0+ the channel ID
        //      must match the channel ID on the NotificationChannel constructor above (in onCreate()).
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelIdUsed )
                .setContentIntent(activityPendingIntent)            //  return to app if user presses notification.
                .setContentText(firstLine)
                .setContentTitle(title)
                .setOngoing(true)                                       //  this prevents clear button from removing the notification.  It also places this notification first.
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)       //  for < 8.0.  For 8.0+ it uses the channel IMPORTANCE_ value (above)
                .setSmallIcon(R.mipmap.ic_launcher)                     //  required
                .setTicker(notificationList.get(0))                   //  only used before < 5.0.
                .setVibrate(longUsed)                                   //  ignored for 8.0+
                .setSound(uriUsed,AudioManager.STREAM_NOTIFICATION)     //  ignored for 8.0+, it seems to go through notification stream by default but there's no harm in making it explicit
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis());
        if (notificationList.size() > 1) {
            NotificationCompat.InboxStyle inBox = new NotificationCompat.InboxStyle();
            int maxLines = notificationList.size();
            if (maxLines > 6) { maxLines = 6; }
            for (int iii = 0; iii < maxLines; iii++) {      //  when the notification is expanded the first line disappears so it's necessary to start with first line.
                inBox.addLine(notificationList.get(iii));
            }
            builder.setStyle(inBox);
        }
        return builder.build();
    }


    //
    //
    //  Interface functions for MainActivity or ancestors to call.
    //
    //

    //  Tom - This function initiates a service by calling Context.startService().
    public void startUDPService() {
        logInfo("UDPService startUDPService() *********************");

        //  Since startUDPService() is called from ServiceConnection.onServiceConnected() it will also be called during screen rotation.  This flag
        //      prevents startService() from being called if it's already running.
        if (!mServiceRunning) {
            //  The big one, Service.startService().  It is important to note that this MUST be called before Service.startForeground().
            Intent tIntent = new Intent(getApplicationContext(), UDPService.class);
            startService(tIntent);
        }
    }

    //  Tom - This function stops service by calling stopSelf().
    public void stopUDPService() {
        int allPackets = WSJTXUtils.getAndUpdateNumberOfPackets( this, totalPackets );
        logInfo("UDPService stopUDPService() "+totalPackets+" packets, "+allPackets+" overall *********************");
        if (DO_LOGGING) {
            helperStationList.debugDumpHistory();
        }

        threadRunning = false;
        if (ds != null) { ds.close(); }     // closing the socket forces the ds.receive() to exit, closing the thread.  Alternatively, I can send it a bogus packet from a one-off thread.
        stopSelf();
    }

    public SpannableStringBuilder getDisplaySpanBuffer() {
        if (mDisplaySpanBufferChanging) {
            return new SpannableStringBuilder("-");
        }
        else {
            return displaySpanBuffer;   //  Will always be called after UDPService.onCreate() so displaySpanBuffer will never be null.
        }
    }

    public SpannableStringBuilder getDisplaySpanBufferMain() {
        if (mDisplaySpanBufferMainChanging) {
            return new SpannableStringBuilder("-");
        }
        else {
            return displaySpanBufferMain;
        }
    }

    public void setInetAddress( InetAddress inetAddressParam, int ipAddressHashCodeParam ) {
        if (inetAddressParam != null) {
            inetAddress = inetAddressParam;
            ipAddressHashCode = ipAddressHashCodeParam;
        }
    }

    public boolean dataIsFlowing() {
        return dataFlowing;
    }

    public boolean cqOnly() { return cqOnlyMainDisplay; }
    public void switchCQOnly( boolean newValue ) {
        cqOnlyMainDisplay = newValue;
    }

    public void setKmOrMiles( boolean milesNotKm ) {
        distanceInMiles = milesNotKm;
    }

    public void clearScreen() {
        if (mDisplaySpanBufferMainChanging) {
            clearScreenWhenDoneWithBurst = true;    // if displaySpanBufferMain changing then clear when it is done updating.
            return;
        }
        displaySpanBufferMain.delete(0, displaySpanBufferMain.length());
        doLocalBroadcastMain();
    }

    public void enableClearScreenFromWSJTX( boolean param ) {
        clearScreenFromWSJTX = param;
    }

    public boolean alerts() { return (alertObject != null); }

    public boolean filters() { return (filterObject != null); }

    //  This routine is called from the AlertsInterface, Alerts Options menu.  It allows the user to test vibration and sound.
    public void testVibrationAndSound( int soundVibIndex ) {

        if (deviceHasVibrator) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(UDPService.vibrationTimings, -1));
            } else {
                doVibratePre26();
                //vibrator.vibrate(UDPService.vibrationTimings, -1);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //  For Android version with channels, set ringtone object to what URI is used by that channel.
            String channelId = Alerts.getChannelIdForSound( soundVibIndex );    //  returns null string if channel ID has no sound.
            if (!channelId.equals("")) {                                        //  if no sound then just keep default sound.
                createRingtone( channelId );
            }
        }
        if (ringtone != null) {
            ringtone.play();
        }
    }

    public void newAlertSetRingtone() {
        //  For Android version with channels, set ringtone object to the URI for that channel.  The idea is to make the MainActivity ringtone the same
        //      as the notification ringtone.  This is called from AlertsInterface immediately after an Alert object is created.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (alertObject != null) {                  //  should never be null
                if (alertObject.getSound()) {           //  see if this channel has sound.
                    String channelId = alertObject.getCurrentChannelID();
                    createRingtone( channelId );
                }
            }
        }
    }

    //  Private method called from testVibrationAndSound() and newAlertSetRingtone() above.  It is passed the channelId.  It gets the Notification channel from that id
    //      and reads the sound URI.  It then assigns the sound Uri to the Ringtone object.
    //  This method should only be called if the channel has sound.  Or else the Uri will be null.
    private void createRingtone( String channelId ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (channelId.equals("")) { return; }       // should never happen.  Just in case.  If null it will cause an exception.
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel thisChannel = notificationManager.getNotificationChannel(channelId);
            if (thisChannel == null) { return; }        // should also never happen.  This will create an exception.
            Uri thisUri = thisChannel.getSound();
            if (thisUri == null) { return; }            //  also should never happen.  This won't cause an exception but still want to do this.
            ringtone = RingtoneManager.getRingtone(this, thisUri);
            AudioAttributes attrib = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            if (ringtone != null) {
                ringtone.setAudioAttributes(attrib);
            }
        }
    }

    public boolean deviceHasVibrator() {
        return deviceHasVibrator;
    }

    //  These two routines are called below, at the end of the burst when MainActivity is in the foreground.
    private void mainActivityVibrate() {
        if (alertObject != null) {
            if (alertObject.getVibrate()) {
                if (deviceHasVibrator) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createWaveform(UDPService.vibrationTimings, -1));
                    } else {
                        doVibratePre26();
                        //vibrator.vibrate(UDPService.vibrationTimings, -1);
                    }
                }
            }
        }
    }

    private void mainActivitySound() {
        if (alertObject != null) {
            if (alertObject.getSound()) {
                if (ringtone != null) {
                    ringtone.play();
                }
            }
        }
    }

    public void setSorting( boolean parameter, int number ) {
        sorting = parameter;
        if (!sorting) {
            sortingOn = SortingOn.SORTING_NONE;
        } else {
            switch (number) {
                case 0:
                    sortingOn = SortingOn.SORTING_SNR;
                    break;
                case 1:
                    sortingOn = SortingOn.SORTING_FREQ;
                    break;
                case 2:
                    sortingOn = SortingOn.SORTING_DISTANCE;
                    break;
                case 3:
                    sortingOn = SortingOn.SORTING_AZIMUTH;
                    break;
                default:        //  should never happen
                    sorting = false;
                    sortingOn = SortingOn.SORTING_NONE;
            }
        }
    }

    public String describeSorting() {
        String returnValue;

        if (sortingOn == SortingOn.SORTING_SNR) {
            returnValue = getString(R.string.dialog_sorting_toast)+" "+getString(R.string.dialog_sorting_snr);
        } else if (sortingOn == SortingOn.SORTING_FREQ) {
            returnValue = getString(R.string.dialog_sorting_toast)+" "+getString(R.string.dialog_sorting_freq);
        } else if (sortingOn == SortingOn.SORTING_DISTANCE) {
            returnValue = getString(R.string.dialog_sorting_toast)+" "+getString(R.string.dialog_sorting_distance);
        } else if (sortingOn == SortingOn.SORTING_AZIMUTH) {
            returnValue = getString(R.string.dialog_sorting_toast)+" "+getString(R.string.dialog_sorting_azimuth);
        } else {
            returnValue = getString(R.string.dialog_sorting_toast_off);
        }

        return returnValue;
    }

    public boolean doingSorting() {
        return sorting;
    }

    public String getMyCallAndGrid() {
        if (myCall != null) {
            if (myGrid != null) {
                return myCall + "/" + myGrid;
            } else {
                return myCall + "/" + getString(R.string.unknown);
            }
        } else {
            return getString(R.string.unknown);
        }
    }

    public String getDXCallAndGrid() {
        if ( (currentDXCall != null) && (!currentDXCall.equals("")) ){
            if ( (currentDXGrid != null) && (!currentDXGrid.equals("")) ) {
                return currentDXCall + "/" + currentDXGrid;
            } else {
                return currentDXCall + "/" + getString(R.string.unknown);
            }
        } else {
            return getString(R.string.unknown);
        }
    }

    public HelperStationList getHelperStationList() { return helperStationList; }

    public boolean doingReply() { return msg4Enabled; }
    public void setDoingReply( boolean newValue ) {
        if (alertObject == null) {
            msg4Enabled = false;          //  if alerts are off then always turn this off.
        } else {
            msg4Enabled = newValue;
        }
        msg4SNR = NO_SNR;
        msg4Length = 0;
        msg4Inhibit = 0;
    }

    public String getWsjtxFreq() {
        if (wsjtxFreq == null) {
            return "";
        } else {
            return wsjtxFreq;
        }
    }

    public String getWsjtxMode() {
        if (wsjtxMode == null) {
            return "";
        } else {
            return wsjtxMode;
        }
    }

    //  The next five methods are for setting the receive frequency via dialog box.
    //

    //  Interface - Return status of attempt to set Rx freq.  0 == unknown, -1 == failure, +1 == success
    public int getSetRxStatus() {
        return setRxFreqStatus;
    }

    public boolean isCurrentModeWSPR() { return currentModeWSPR; }

    //  Interface - Get the entire array of strings (actually CharSequence) to display in the dialog.  It returns null if setRxFreqArray[] is being manipulated.
    public CharSequence[] getSetRxCharSeq() {
        CharSequence[] returnValue = null;
        if (!setRxFreqArrayInUse) {
                /*
            if (setRxFreqArray != null) {
                setRxFreqArrayCopy = new SetRxFreqData[setRxFreqArray.length];  //  Make a deep copy of setRxFreqArray.  This will allow setRxFreqArray to be
                                                                                //      overwritten in the next burst, if DialogSetRxFreq is still up.
                returnValue = new CharSequence[setRxFreqArray.length];
                for (int iii = 0; iii < setRxFreqArray.length; iii++) {
                    returnValue[iii] = setRxFreqArray[iii].setRxFreqCharSeq;
                    setRxFreqArrayCopy[iii] = setRxFreqArray[iii].copyOf();
                }
            }
                 */
            if (setRxFreqArrayList != null) {
                setRxFreqArrayCopy = new SetRxFreqData[setRxFreqArrayList.size()];  //  Make a deep copy of setRxFreqArray.  This will allow setRxFreqArray to be
                                                                                    //      overwritten in the next burst, if DialogSetRxFreq is still up.
                returnValue = new CharSequence[setRxFreqArrayList.size()];
                for (int iii = 0; iii < setRxFreqArrayList.size(); iii++) {
                    returnValue[iii] = setRxFreqArrayList.get(iii).setRxFreqCharSeq;
                    setRxFreqArrayCopy[iii] = setRxFreqArrayList.get(iii).copyOf();
                }
            }
        }
        return returnValue;
    }

    //  Interface - intiate sending msg4.
    public void setRxFreqSend( final int selection ) {
        //  if msg1 has not arrived yet.  This shouldn't happen unless using a mode like MSK-144 that doesn't use it.
        //if ( (currentRxFreq == 0) || (currentDXCall == null) || (currentDXCall.equals("")) ) {
        if (currentRxFreq == 0) {       //  currentRxFreq will only be zero if msg1 has not arrived.
            logInfo("setRxFreqSend() - no msg1 yet *****1");
            return;
        }
            //  If the user selected the same receive freq and callsign that is the current DX selection then don't bother.
        if ( ( setRxFreqArrayCopy[selection].dFreq == currentRxFreq ) && ( setRxFreqArrayCopy[selection].callsign.equals(currentDXCall) ) ) {
            logInfo("setRxFreqSend() - no change made *****1");
            return;
        }

        //  Cannot send UDP packet from the main UI thread.  So start a new thread just for that purpose.
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                setRxFreqSendIt( selection );
            }
        });
        setRxFreqSelection = selection;             //  save the selection in case it has to be sent again
        setRxFreqOrigDXCall = currentDXCall;        //  for comparison in timer to see if msg4 got through.  See timer.
        setRxFreqOrigRxFreq = currentRxFreq;        //  ditto
        setRxFreqStatus = 0;                        //  status == unknown
        timerHandler.postDelayed(timerRunnable, timerRunnableTimeout);  //  initiate timer
        thread2.start();                            //  initiate thread
    }

    //  only called from the thread initiated in setRxFreqSend() immediately above.
    private void setRxFreqSendIt( int selection ) {

        SetRxFreqData data = setRxFreqArrayCopy[ selection ];

        byte[] msg4LocalBuf = new byte[ data.setRxFreqMsg4Length ];
        msg4LocalBuf[0] = (byte)0xad;     msg4LocalBuf[1] = (byte)0xbc;     msg4LocalBuf[2] = (byte)0xcb;     msg4LocalBuf[3] = (byte)0xda;
        msg4LocalBuf[4] = (byte)0x00;     msg4LocalBuf[5] = (byte)0x00;     msg4LocalBuf[6] = (byte)0x00;     msg4LocalBuf[7] = (byte)0x02;
        msg4LocalBuf[8] = (byte)0x00;     msg4LocalBuf[9] = (byte)0x00;     msg4LocalBuf[10] = (byte)0x00;    msg4LocalBuf[11] = (byte)0x04;
        System.arraycopy( data.setRxFreqMsg4Buffer, 0, msg4LocalBuf, 12, data.setRxFreqMsg4Length-12);

        //  ds is already allocated in udpLoop() below, replyInetAddress and replyInetPort are filled in with each received packet (also in udpLoop()).
        try {
            DatagramPacket dp2 = new DatagramPacket(msg4LocalBuf, data.setRxFreqMsg4Length, replyInetAddress, replyInetPort);
            ds.send(dp2);
        } catch (Exception ex) {
            if (threadRunning) {
                logInfo("thread Exception 3 " + ex.getMessage()+" *****");
            }
        }
        logInfo("setRxFreqSend() sent *****1");
    }

    //  Interface - tell MainActivity if three-pass-decoding is in use.
    public boolean threePassDecoding() { return threePassDecodeInUse; }

    //
    //
    //  End of interface functions for MainActivity to call.
    //
    //

    private void doLocalBroadcastMain() {
        Intent mIntent2 = new Intent(ACTION_BROADCAST_MAIN);
        LocalBroadcastManager mBroadcast2 = LocalBroadcastManager.getInstance(getApplicationContext());
        mBroadcast2.sendBroadcast(mIntent2);
    }

    private void doLocalBroadcast() {
        Intent mIntent = new Intent(ACTION_BROADCAST);
        LocalBroadcastManager mBroadcast = LocalBroadcastManager.getInstance(getApplicationContext());
        mBroadcast.sendBroadcast(mIntent);
    }

    private void udpLoop() {

        byte[] oldmsg = new byte[DATAGRAM_BUFFER_SIZE];
        try {

            if (getResources().getBoolean(R.bool.multicastIP)) {
                //  Create DatagramSocket.  It's actually a MulticastSocket (a descendant of DatagramSocket).  I do it this way so I can quickly switch between
                //      multicast or not.  Since I'm doing this inetAddress variable is unused.  Below I create a DatagramSocket with:
                //                  ds = new DatagramSocket(WSJTXUtils.getDatagramPort(this), inetAddress );
                //      The variable ds is defined as a DatagramSocket.
                MulticastSocket ds2 = new MulticastSocket(WSJTXUtils.getDatagramPort(this) );
                InetAddress group = InetAddress.getByName("239.255.1.2");
                ds2.joinGroup(group);
                ds = ds2;   // ds will not have access to Multicast-only methods but I'm not using any.  However, overridden methods will call the Multicast version.
            } else {
                //  Note - due to a bug, inetAddress has been null since the beginning.  Only when I called inetAddress.hashcode() did I realize that.
                //      When the second parameter here is null then the kernel will choose an IP.
                ds = new DatagramSocket(WSJTXUtils.getDatagramPort(this), inetAddress);
            }
            //ds.setSoTimeout( 2*60*1000 );

            while (threadRunning) {
                //  Doesn't seem like a good idea to recreate the byte array and data packet with each pass through the loop.  However,
                //      if I don't then I often receive a packet with the same data as the previous packet.
                byte[] msg = new byte[DATAGRAM_BUFFER_SIZE];
                DatagramPacket dp = new DatagramPacket(msg, msg.length);
                ds.receive(dp);

                replyInetAddress = dp.getAddress();
                replyInetPort = dp.getPort();

                logInfo("UDPService.udpLoop source = "+replyInetAddress+", port "+replyInetPort+" *****");

                //  Check for duplicate packets, however, only for message types 2 and 6 (normal decode and WSPR decode).
                if (Arrays.equals(msg, oldmsg)) {
                    int messageType = convertQint32ToInt( msg, 8, dp.getLength() );
                    if (( messageType == 2 ) || ( messageType == 10 ))  {
                        logInfo("Duplicate packet ************************************");
                        totalDuplicates++;
                        continue;
                    }
                }
                System.arraycopy( msg, 0, oldmsg, 0, DATAGRAM_BUFFER_SIZE );

                boolean sendBroadcast = processPacket(msg, dp.getLength());         //  Returns true if the message is not msg 2 or 10
                if (sendBroadcast) {
                    //  If something other than message 2 or 10 then send broadcast, even if no activity is running to receive it.
                    doLocalBroadcast();             //  sent to DisplayAll
                    if (!dataMsg2or10Flowing) {
                        //  Don't normally send these messages to MainActivity.  But if haven't gotten any decodes send broadcast to MainActivity to
                        //      let the user know that he is connected.  Otherwise in WSPR mode it could take two min.
                        dataMsg2or10Flowing = true;     // only need to send out one.
                        doLocalBroadcastMain();     //  sent to MainActivity
                    }
                }
                if (endOfBurst) {
                    endOfBurst = false;

                    if (msg4Enabled) {
                        if (msg4Inhibit > 0) {
                            logInfo("Reply OFF, msg4Inhibit count " + msg4Inhibit + " *****");
                            msg4Inhibit--;
                        }
                        if (msg4SNR != NO_SNR) {
                            msg4SNR = NO_SNR;
                            msg4Inhibit = 0;        //  just in case it goes negative, should never happen
                            DatagramPacket dp2 = new DatagramPacket(msg4Buffer, msg4Length, dp.getAddress(), dp.getPort());
                            ds.send(dp2);
                            logInfo("Reply Sent ***** ------------- *****");
                        }
                    }

                    ArrayList<CharSequence> alertArray = buildBurstAndAppend();
                    doLocalBroadcastMain();         //  sent to MainActivity
                    if (inForeground) {
                        //  If in foreground (activities are not running) then update notification display.
                        boolean alertThisTime = true;
                        if (alertArray.size() == 0) {
                            String sss = getResources().getString(R.string.notification_string);
                            String notificationMessage = String.format(Locale.US, sss, totalPackets);
                            alertArray.add(notificationMessage);
                            alertThisTime = false;  //  need this variable because if no alerts alertArray.size == 1.  It could also be == 1 if had one alert.
                        }
                        mNotificationManager.notify(NOTIFICATION_ID, getNotification( alertArray, alertThisTime ));
                    }
                    else {
                        if (alertArray.size() > 0) {
                            mainActivitySound();
                            mainActivityVibrate();
                        }
                    }
                }
            }
            logInfo("UDPService.udpLoop thread quit on its own ******");
        } catch (SocketTimeoutException ex) {
            logInfo("thread timeout " + ex.getMessage()+" *****");
            //android.os.Process.killProcess(android.os.Process.myPid());   //  leftover from my attempt to kill the app (because of network timeout) from service.
            //System.exit(0);
        } catch (Exception ex) {
            if (threadRunning) {
                logInfo("thread Exception 2 " + ex.getMessage()+" *****");
            }
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }

    //
    //  Decodes the contents of packet.  Returns true if the packet contains something other than message 2 or 10.  The calling routine
    //      will send a broadcast under those conditions.
    //  Called from UDPLoop() above.
    //
    private boolean processPacket( byte[] msg, int messageLength ) {
        boolean returnValue = false;
        totalPackets++;

        //  The header is always 0xadbccbda but since there is no unsigned byte values in Java I have to interpret
        //      them as signed byte values.
        if ( ( msg[0] == -83 ) && ( msg[1] == -68 ) && ( msg[2] == -53 ) && ( msg[3] == -38 ) )
        {
            String displayString;

            if (!dataFlowing) {
                WSJTXUtils.setIpHashCode(this, ipAddressHashCode );        //  store IP address for future startups to check if IP has changed.
                logInfo("processPacket() - storing new IP hash code *****");
            }
            dataFlowing = true;

            int schema = convertQint32ToInt( msg, 4, messageLength );               //  4 bytes schema number, always the second field, after 0xadbccbda
            int messageType = convertQint32ToInt( msg, 8, messageLength );          //  4 bytes message number.
            if ( (schema == CONVERT_QINT_ERROR) || (messageType == CONVERT_QINT_ERROR) ) {
                return false;
            }

            //
            //  Decode message, calling the appropriate function.
            if (messageType == 0) {
                String partialString = convertHeartbeatMessage( msg, messageLength );
                displayString = String.format(Locale.US,"s%d.m%d.%s - %d %d %d",
                        schema, messageType, partialString, totalPackets, totalDuplicates, messageLength );
                returnValue = true;
            }
            else if (messageType == 1) {
                String partialString = convertStatusMessage( msg, messageLength );
                displayString = String.format(Locale.US,"s%d.m%d.%s - %d %d %d",
                        schema, messageType, partialString, totalPackets, totalDuplicates, messageLength );
                returnValue = true;
            }
            else if (messageType == 2) {
                String partialString = convertDecodeMessage( msg, messageLength );
                displayString = String.format(Locale.US,"s%d.m%d.%s - %d %d %d",
                        schema, messageType, partialString, totalPackets, totalDuplicates, messageLength );
            }
            else if (messageType == 10) {
                String partialString = convertWSPRDecodeMessage( msg, messageLength );
                displayString = String.format(Locale.US,"s%d.m%d.%s - %d %d %d",
                        schema, messageType, partialString, totalPackets, totalDuplicates, messageLength );
            }
            else {
                ReturnUtf8Struct uniqueId = strangeUtf8(msg, 12, messageLength);
                displayString = String.format(Locale.US, "s%d.m%d.%s - %x %x %x %x - %d %d %d",
                        schema, messageType, uniqueId.theString,
                        msg[22], msg[22], msg[22], msg[22], totalPackets, totalDuplicates, messageLength);
                returnValue = true;
                //  The option for the app to clear the screen when the user clears the WSJT-X screen involves message 3.  Since the action is fairly trivial do it here.
                if (messageType == 3) {
                    if (clearScreenFromWSJTX) {
                        clearScreen();
                    }
                }
            }

            //
            //  Update buffer for DisplayAll activity
            mDisplaySpanBufferChanging = true;
            displaySpanBuffer.append("\n ").append(displayString);
            if (displaySpanBuffer.length() > DISPLAY_RING_BUFFER_SIZE) {
                //  SpannableStringBuilder has no search feature so I have to find the first LF with a loop.  Actually the first LF
                //      after the last DISPLAY_RING_BUFFER_SIZE characters.
                int iii = displaySpanBuffer.length()-DISPLAY_RING_BUFFER_SIZE;      // start searching at the oversize
                while ( iii < displaySpanBuffer.length() ) {
                    if (displaySpanBuffer.charAt(iii) == '\n') {                    //  if LF then break
                        break;
                    }
                    iii++;
                }
                //logInfo("processPacket LF "+iii+", displaySpanBuffer.length() "+displaySpanBuffer.length()+", displayString.length() "+displayString.length()+", start search "+(displaySpanBuffer.length()-DISPLAY_RING_BUFFER_SIZE));
                displaySpanBuffer.delete(0, iii+1);
            }
            if ( (messageType == 2) || (messageType == 10) ){
                if (isCQ) {
                    displaySpanBuffer.setSpan(new ForegroundColorSpan(Color.GREEN),
                            displaySpanBuffer.length() - displayString.length(), displaySpanBuffer.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else if (isMyCall) {
                    displaySpanBuffer.setSpan(new ForegroundColorSpan(Color.RED),
                            displaySpanBuffer.length() - displayString.length(), displaySpanBuffer.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else {
                    displaySpanBuffer.setSpan(new ForegroundColorSpan(Color.WHITE),
                            displaySpanBuffer.length() - displayString.length(), displaySpanBuffer.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            mDisplaySpanBufferChanging = false;
            isCQ = isMyCall = false;    //  Used in two places so clear them here, after both are finished.

            logInfo("processPacket "+displayString+" "+format(Locale.US, "Thread %d", Thread.currentThread().getId())+"*****");
        }

        return returnValue;
    }


    //  Returns an ArrayList<CharSequence> which is passed to getNotification().  That is the notification string to be displayed.
    private ArrayList<CharSequence> buildBurstAndAppend() {
        ArrayList<CharSequence> alertArray = new ArrayList<>();     //  This is the notification string to print when app in background.
        //boolean atLeastOneMsgNotWSPR = false;

        //logInfo("BuildBurstAndAppend() *****************");

        if (decodeDataList.size() == 0) {
            return alertArray;
        }

        long start,end;                 // debug lines
        start = System.nanoTime();      // debug lines

        //  I have to convert to an array to sort.  I've read that sorting within the linked list is very inefficient.  So I just use the array
        //      for the whole method rather than convert back to a linked list.
        DecodeDataStruct[] decodeDataArray = decodeDataList.toArray( new DecodeDataStruct[0]);
        decodeDataList.clear();     // clear out the linked list in preparation for the next burst.

        if (sorting) {
            if (sortingOn == SortingOn.SORTING_SNR) {
                Arrays.sort(decodeDataArray);
            } else if (sortingOn == SortingOn.SORTING_FREQ) {
                Arrays.sort(decodeDataArray, new DecodeDataStruct.FreqComparator());
            } else if (sortingOn == SortingOn.SORTING_DISTANCE) {
                Arrays.sort(decodeDataArray, new DecodeDataStruct.DistanceComparator());
            } else if (sortingOn == SortingOn.SORTING_AZIMUTH) {
                Arrays.sort(decodeDataArray, new DecodeDataStruct.AzimuthComparator());
            } else {    //  should never happen
                sorting = false;
                sortingOn = SortingOn.SORTING_NONE;
            }
        }

        //  Unlike displaySpanBuffer (for DisplayAll activity) this buffer, displaySpanBufferMain, doesn't get modified until the end of the burst.

        mDisplaySpanBufferMainChanging = true;  //  make this function an atomic operation WRT the main display

        if (suppressDashedLine) {
            suppressDashedLine = false;     // set in MSK-144 mode, which does not use msg1.  So endOfBurst is set after every decode in order to force a display.  But don't want dashed line.
        } else {
            //  Initialize the array for set-rx-freq only at the beginning of the burst.
            setRxFreqArrayList = new ArrayList<>();

            //  Now build dotted line.
            if ( (wsjtxMode == null) || (wsjtxFreq == null) ) {
                displaySpanBufferMain.append("\n ").append("-------------------------------------");
            } else {
                //  Create a dashed line of 37 characters with the frequency and mode embedded in it.
                int lenUsed = wsjtxFreq.length() + wsjtxMode.length() + 1;      //  length of two Strings plus one space in between
                if (lenUsed > 21) {     // should never happen, just in case
                    lenUsed = 21;
                }
                char[] dashes21 = { '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-', '-' };
                String dashes = new String(dashes21, 0, 21 - lenUsed);
                String appendString = String.format(Locale.US, "---------- [ %s %s ] %s", wsjtxFreq, wsjtxMode, dashes);
                SpannableStringBuilder appendSSB = new SpannableStringBuilder(appendString);
                if (appendSSB.length() > 13 + lenUsed) {      //  should always be true, just in case
                    appendSSB.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 13, 13 + lenUsed, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                displaySpanBufferMain.append("\n ").append(appendSSB);
            }
        }

        //  Get the max size of the message(maxMsgSize) if msg 2.  Also get max size of country field (maxCountrySize) for both msg 2 and 10.
        int maxMsgSize = 0;
        int maxCountrySize = 0;
        for (int iii = 0; iii < decodeDataArray.length; iii++) {
            if (!decodeDataArray[iii].isWSPR) {
                if (decodeDataArray[iii].xmessage.length() > maxMsgSize) {
                    maxMsgSize = decodeDataArray[iii].xmessage.length();
                }
            }
            if (decodeDataArray[iii].country.length() > maxCountrySize) {
                if (decodeDataArray[iii].grid.length() > 0) {         // don't count this long country name if there is no grid to print out.
                    maxCountrySize = decodeDataArray[iii].country.length();
                }
            }
        }
        if (maxCountrySize > MAX_COUNTRY_SIZE) {
            maxCountrySize = MAX_COUNTRY_SIZE;
        }
        //logInfo("buildBurstAndAppend() - maxMsgSize: "+maxMsgSize+" maxCountrySize: "+maxCountrySize+" *****");

        //  Now iterate through the decode data in this burst.  Create strings with the data, add colors/bold/italics if necessary, append to
        //      displaySpanBufferMain.
        for (int iii = 0; iii < decodeDataArray.length; iii++) {
            //  maxMsgSize is the largest message in the burst.  Pad the country field of  the other messages to that size.  This aligns the countries list.  This is
            //      accomplished by creating String spaces2 which, when printed, does the padding
            char[] spaces20 = { ' ' , ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ' };
            int countrySize = decodeDataArray[iii].country.length();
            String spaces2;
            if ((maxCountrySize-countrySize) <= 0) {     // since maxCountrySize is limited to MAX_COUNTRY_SIZE then no need to check "if ((maxCountrySize-countrySize) < 20)"
                spaces2 = "";
            }
            else {
                spaces2 = new String(spaces20,0,maxCountrySize-countrySize);
            }

            //  Create strings tempDistance and tempAzimuth for printing out those two.
            String tempDistance;
            if (decodeDataArray[iii].distanceInKm.equals("")) {
                tempDistance = "";
            }
            else {
                if (distanceInMiles) {
                    tempDistance = decodeDataArray[iii].distanceInMi + "mi";
                } else {
                    tempDistance = decodeDataArray[iii].distanceInKm + "km";
                }
            }
            String tempAzimuth;
            if (decodeDataArray[iii].azimuth.equals("")) {
                tempAzimuth = "";
            }
            else {
                tempAzimuth = decodeDataArray[iii].azimuth+"deg";
            }

            if (decodeDataArray[iii].isWSPR) {
                //  WSPR - not much else to consider other than isNewStation.
                String temp = String.format(Locale.US,"%s %3d %8d %2d %8s %6s %3d :%s%s %s %s",
                        decodeDataArray[iii].qtimeStr2, decodeDataArray[iii].SNR, decodeDataArray[iii].dFreqWSPR, decodeDataArray[iii].drift,
                        decodeDataArray[iii].callsign, decodeDataArray[iii].grid, decodeDataArray[iii].power, decodeDataArray[iii].country,
                        spaces2, tempDistance, tempAzimuth);
                SpannableStringBuilder tempLineBuffer = new SpannableStringBuilder(temp);

                if (decodeDataArray[iii].isAlert) {
                    if (alertObject != null) {  //  there is a remote possibility that the alert was turned off between message received and this method called.
                        tempLineBuffer.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        alertArray.add( alertObject.doAlertString( decodeDataArray[iii].callsign, decodeDataArray[iii].SNR, decodeDataArray[iii].country, decodeDataArray[iii].grid ));
                    }
                }
                if (decodeDataArray[iii].isNewStation) {
                    tempLineBuffer.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ),0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                displaySpanBufferMain.append("\n ").append(tempLineBuffer);
            }
            else if ( (!cqOnlyMainDisplay) || ( decodeDataArray[iii].isCQ ) ) {            // if (not WSPR) AND ( (CQ only OFF) or (CQ only ON and CQ message) )then ...
                //atLeastOneMsgNotWSPR = true;

                //  In the same manner as padded the country field (above), pad the message field.
                int msgSize = decodeDataArray[iii].xmessage.length();
                String spaces;
                if ((maxMsgSize-msgSize) < 20) {
                    spaces = new String(spaces20, 0, maxMsgSize - msgSize);
                }
                else {
                    spaces = "";
                }

                String temp = String.format(Locale.US,"%s %3d %4d %s %s%s :%s%s %s  %s %s",
                        decodeDataArray[iii].qtimeStr2, decodeDataArray[iii].SNR, decodeDataArray[iii].dFreq, decodeDataArray[iii].mode, decodeDataArray[iii].xmessage, spaces,
                        decodeDataArray[iii].country, spaces2, decodeDataArray[iii].grid, tempDistance, tempAzimuth
                        );
                SpannableStringBuilder tempLineBuffer = new SpannableStringBuilder(temp);
                if (decodeDataArray[iii].isAlert) {
                    if (alertObject != null) {  //  there is a remote possibility that the alert was turned off between message received and this method called.
                        tempLineBuffer.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        alertArray.add( alertObject.doAlertString( decodeDataArray[iii].callsign, decodeDataArray[iii].SNR, decodeDataArray[iii].country, decodeDataArray[iii].grid ));
                    }
                }
                else if (decodeDataArray[iii].isMyCall) {
                    tempLineBuffer.setSpan(new BackgroundColorSpan(Color.RED),0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else if (decodeDataArray[iii].isCQ) {
                    tempLineBuffer.setSpan(new BackgroundColorSpan(Color.GREEN),0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                boolean isMyCallSoMakeTextRed = false;
                if ( (currentDXCall != null) && (!currentDXCall.equals("")) ){
                    if (currentDXCall.equals(decodeDataArray[iii].callsign)) {
                        if (!decodeDataArray[iii].isMyCall) {           //  don't do this if background is already red cuz text color (dxCallColor) is too similar.
                            //int dxColor = getResources().getColor(R.color.dxCallColor);
                            int dxColor = ContextCompat.getColor(this, R.color.dxCallColor);
                            tempLineBuffer.setSpan(new ForegroundColorSpan(dxColor), 0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            tempLineBuffer.setSpan(new StyleSpan(Typeface.BOLD), 0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            isMyCallSoMakeTextRed = true;
                        }
                    }
                }

                //  These 4 lines are same as the last four in the "if (isWSPR)" block above.  But cannot move these outside of if/else block because there
                //      will be some msg 2 decodes that aren't printed (CQ only on and deocde isn't CQ)
                if (decodeDataArray[iii].isNewStation) {
                    tempLineBuffer.setSpan(new StyleSpan( Typeface.BOLD_ITALIC ),0, tempLineBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                displaySpanBufferMain.append("\n ").append(tempLineBuffer);

                //
                //  Build up SetRxFreqData[], an array of class SetRxFreqData.  Each element contains the text from the display, used to allow the user to make his reply,
                //      plus the bytes contained in the reply itself.  Note that the first 12 bytes of msg4 are always the same.  They are not stored in this structure.
                //

                if (setRxFreqArrayList != null) {

                    setRxFreqArrayInUse = true;     //  a flag to prevent setRxFreqArray from being accessed while it is being built.

                    String temp2 = String.format(Locale.US, "%3d %4d %s%s :%s %s %s %s",
                            decodeDataArray[iii].SNR, decodeDataArray[iii].dFreq, decodeDataArray[iii].xmessage, spaces,
                            decodeDataArray[iii].country, decodeDataArray[iii].grid, tempDistance, tempAzimuth
                    );

                    SpannableStringBuilder tempReplyLine = new SpannableStringBuilder(temp2);
                    if (decodeDataArray[iii].isAlert) {
                        if (alertObject != null) {  //  there is a remote possibility that the alert was turned off between message received and this method called.
                            tempReplyLine.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            //alertArray.add(alertObject.doAlertString(decodeDataArray[iii].callsign, decodeDataArray[iii].SNR, decodeDataArray[iii].country, decodeDataArray[iii].grid));
                        }
                    } else if (decodeDataArray[iii].isMyCall) {
                        tempReplyLine.setSpan(new BackgroundColorSpan(Color.RED), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (decodeDataArray[iii].isCQ) {
                        tempReplyLine.setSpan(new BackgroundColorSpan(Color.GREEN), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (isMyCallSoMakeTextRed) {
                        int dxColor = ContextCompat.getColor(this, R.color.dxCallColor);
                        tempReplyLine.setSpan(new ForegroundColorSpan(dxColor), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tempReplyLine.setSpan(new StyleSpan(Typeface.BOLD), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (decodeDataArray[iii].isNewStation) {
                        tempReplyLine.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, tempReplyLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    //  Now fill in the data structure.
                    SetRxFreqData setRxFreqDataObject = new SetRxFreqData();
                    setRxFreqDataObject.setRxFreqCharSeq = tempReplyLine;                           //  The text for the Dialog
                    setRxFreqDataObject.setRxFreqMsg4Buffer = new byte[decodeDataArray[iii].msg4Length - 12];
                    System.arraycopy(decodeDataArray[iii].msg4Buffer, 0,                    //  The msg4 response
                            setRxFreqDataObject.setRxFreqMsg4Buffer, 0, decodeDataArray[iii].msg4Length - 12);
                    setRxFreqDataObject.setRxFreqMsg4Length = decodeDataArray[iii].msg4Length;      //  The msg4 response length, includes the first 12 bytes not in buffer.
                    setRxFreqDataObject.callsign = decodeDataArray[iii].callsign;
                    setRxFreqDataObject.dFreq = decodeDataArray[iii].dFreq;
                    setRxFreqArrayList.add( setRxFreqDataObject );
                }

                //
                //  End of SetRxFreqData block of code.
                //
            }
        }

        setRxFreqArrayInUse = false;

        if (clearScreenWhenDoneWithBurst) {
            //  There is a remote possibility that when clearScreen() (above) is called that mDisplaySpanBufferMainChanging would be set.  If that is the case
            //      then this flag is set and when this process is done the buffer is cleared at that time.
            clearScreenWhenDoneWithBurst = false;
            displaySpanBufferMain.delete(0, displaySpanBufferMain.length());
        } else {
            //  Now limit the size of displaySpanBufferMain to approximately DISPLAY_RING_BUFFER_SIZE bytes.
            if (displaySpanBufferMain.length() > DISPLAY_RING_BUFFER_SIZE) {
                //  SpannableStringBuilder has no search feature so I have to find the first LF with a loop.  Actually the first LF
                //      after the last DISPLAY_RING_BUFFER_SIZE characters.
                int iii = displaySpanBufferMain.length() - DISPLAY_RING_BUFFER_SIZE;      // start searching at the oversize
                while (iii < displaySpanBufferMain.length()) {
                    if (displaySpanBufferMain.charAt(iii) == '\n') {                    //  if LF then break
                        break;
                    }
                    iii++;
                }
                displaySpanBufferMain.delete(0, iii + 1);
            }
        }

        end = System.nanoTime();        //  debug lines
        logInfo("S+T "+decodeDataArray.length+" "+((end-start)/100000)+" *******");     // debug lines

        mDisplaySpanBufferMainChanging = false;
        return alertArray;
    }

    private String convertWSPRDecodeMessage( byte[] buf, int messageLength ) {
        //  Error checking is done to make sure buf[] is long enough to decode the message.  This avoids an IndexOutOfBoundsException.
        //      Each function call below is passed currentOffset and messageLength.  Before any decoding is done messageLength is
        //      checked to see if there are enough bytes remaining (beyond currentOffset) to make the necessary conversion.
        int currentOffset = 12;

        dataMsg2or10Flowing = true;     //  set once and never cleared.  Used to indicate that msg 2 or 10 has been received.
        decodeFlagMirror = true;        //  normally set when msg 1 (status) indicates a start of burst.  However, if that is missed then it can be set here.

        ReturnUtf8Struct uniqueId = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += uniqueId.theOffset;

        String newBool;
        if (buf[currentOffset++] == 1) { newBool = "nT"; } else { newBool = "nF"; }

        String qtimeStr = convertQtime( buf, currentOffset, messageLength );
        currentOffset += 4;

        int SNR = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 12;    //  offset of 4 bytes for SNR plus 8 for ignored delta time.

        long dFreq = convertQint64ToLong( buf, currentOffset, messageLength );
        currentOffset += 8;

        int drift = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 4;

        ReturnUtf8Struct callsign = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += callsign.theOffset;

        ReturnUtf8Struct grid = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += grid.theOffset;

        int power = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 4;

        String offAirBool;
        if (buf[currentOffset++] == 1) { offAirBool = "oT"; } else { offAirBool = "oF"; }

        //  currentOffset should now match messageLength.  If not, notify user of a possible error.
        String possibleError;
        if ( currentOffset != messageLength ) {
            possibleError = "(Packet Error?)";
        }
        else {
            possibleError = "";
        }

        //  Get info on station
        ReturnStationData stationData = helperStationList.checkStation(callsign.theString, grid.theString, false);

        //  Collect the decoded data into decodeData and push it on to decodeDataList linked list.
        DecodeDataStruct decodeData = new DecodeDataStruct();
        decodeData.isWSPR = true;
        decodeData.qtimeStr2 = qtimeStr.replace(":","");    // remove ':' from qtimeStr
        decodeData.SNR = SNR;
        decodeData.dFreqWSPR = dFreq;
        decodeData.drift = drift;
        decodeData.callsign = callsign.theString;
        decodeData.grid = grid.theString;
        decodeData.power = power;
        decodeData.country = stationData.country;
        decodeData.continent = stationData.continent;
        decodeData.isNewStation = stationData.isNewStation;
        decodeData.distanceInKm = stationData.distance;
        decodeData.distanceInMi = stationData.distanceMi;
        decodeData.distanceInKmNum = stationData.distanceInKmNum;
        decodeData.azimuth = stationData.azimuth;
        decodeData.azimuthNum = stationData.azimuthNum;
        decodeData.isAlert = false;

        boolean okToAddToLinkedList = true;
        //  if filter is on then check if this station should be kept
        if (filterObject != null) {
            okToAddToLinkedList = filterObject.doFilter(decodeData.callsign, decodeData.country, decodeData.continent);
        }
        //  if alerts are on AND this station passed thru the filter (if any) the see if need to alert on this.
        if ( (alertObject != null) && (okToAddToLinkedList) ) {
            decodeData.isAlert = alertObject.doAlert( decodeData.callsign, decodeData.country, decodeData.continent, decodeData.SNR, false );
        }
        //  Add to linked list if ok.
        if (okToAddToLinkedList) {
            decodeDataList.add(decodeData);       //  add to linked list
        }
        if (getResources().getBoolean(R.bool.includeMapping)) {
            helperStationList.doFilterForMapping(okToAddToLinkedList);
        }

        return String.format(Locale.US, "%s.%s.%s.%3d.%8d.%2d.%s.%s.%d.%s %s",
                uniqueId.theString, newBool, qtimeStr, SNR, dFreq,
                drift, callsign.theString,
                grid.theString,
                power, offAirBool, possibleError
        );
    }


    private String convertDecodeMessage( byte[] buf, int messageLength ) {
        //  Error checking is done to make sure buf[] is long enough to decode the message.  This avoids an IndexOutOfBoundsException.
        //      Each function call below is passed currentOffset and messageLength.  Before any decoding is done messageLength is
        //      checked to see if there are enough bytes remaining (beyond currentOffset) to make the necessary conversion.
        int currentOffset = 12;

        dataMsg2or10Flowing = true;     //  set once and never cleared.  Used to indicate that msg 2 or 10 has been received.
        decodeFlagMirror = true;        //  normally set when msg 1 (status) indicates a start of burst.  However, if that is missed then it can be set here.

        ReturnUtf8Struct uniqueId = strangeUtf8(buf, currentOffset, messageLength );    // Unique ID = 'WSJT-X'
        currentOffset += uniqueId.theOffset;

        String newBool;
        if (buf[currentOffset++] == 1) { newBool = "nT"; } else { newBool = "nF"; }

        String qtimeStr = convertQtime( buf, currentOffset, messageLength );
        currentOffset += 4;

        int SNR = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 12;    //  offset of 4 bytes for SNR plus 8 for ignored delta time.

        int dFreq = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 4;

        ReturnUtf8Struct mode = strangeUtf8( buf, currentOffset, messageLength );
        currentOffset += mode.theOffset;
        if (mode.theString.equals("&")) {       //  special case for MSK-144 mode (indicated by '&').  There are no msg 1 to frame the decode.
            decodeFlagMirror = false;
            endOfBurst = true;
            suppressDashedLine = true;
        }

        ReturnUtf8Struct message = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += message.theOffset;
        int indexOfSpaces = message.theString.indexOf("   ");
        if (indexOfSpaces != -1) {
            String tempStr = message.theString.substring(0,indexOfSpaces);
            message = new ReturnUtf8Struct();
            message.theString = tempStr;
        }

        if (message.theString.indexOf("CQ") == 0) {
            isCQ = true;
        }
        else {
            isCQ = false;
        }
        if (myCall != null) {
            isMyCall = message.theString.contains(myCall);      // returns true if myCall is contained in message.theString.
        }

        String newConfidence;
        if (buf[currentOffset++] == 1) { newConfidence = "  cT"; } else { newConfidence = "  cF"; }
        int replyEndOfCopy = currentOffset;

        String newOffAir;
        if (buf[currentOffset++] == 1) { newOffAir = "oT"; } else { newOffAir = "oF"; }

        //  currentOffset should now match messageLength.  If not, notify user of a possible error.
        String possibleError;
        if ( currentOffset != messageLength ) {
            possibleError = "(Packet Error?)";
        }
        else {
            possibleError = "";
        }

        //  Get info on the station
        ReturnStationData stationData = helperStationList.checkStation(message.theString, "", isMyCall);

        //  Collect the decoded data into decodeData and push it on to decodeDataList linked list.
        DecodeDataStruct decodeData = new DecodeDataStruct();
        decodeData.isWSPR = false;
        decodeData.qtimeStr2 = qtimeStr.replace(":","");    // remove ':' from qtimeStr
        decodeData.SNR = SNR;
        decodeData.dFreq = dFreq;
        decodeData.mode = mode.theString;
        decodeData.xmessage = message.theString;
        decodeData.isCQ = isCQ;
        decodeData.isMyCall = isMyCall;
        decodeData.callsign = stationData.callsign;
        decodeData.country = stationData.country;
        decodeData.continent = stationData.continent;
        decodeData.isNewStation = stationData.isNewStation;
        if (stationData.gridSquare != null) {
            decodeData.grid = stationData.gridSquare;
        }
        else {
            decodeData.grid = "";
        }
        decodeData.distanceInKm = stationData.distance;
        decodeData.distanceInMi = stationData.distanceMi;
        decodeData.distanceInKmNum = stationData.distanceInKmNum;
        decodeData.azimuth = stationData.azimuth;
        decodeData.azimuthNum = stationData.azimuthNum;
        decodeData.isAlert = false;

        boolean okToAddToLinkedList = true;
        //  if filter is on then check if this station should be kept
        if (filterObject != null) {
            okToAddToLinkedList = filterObject.doFilter(decodeData.callsign, decodeData.country, decodeData.continent);
        }
        //  if alerts are on AND this station passed thru the filter (if any) then see if need to alert on this.
        if ( (alertObject != null) && (okToAddToLinkedList) ) {
            decodeData.isAlert = alertObject.doAlert( decodeData.callsign, decodeData.country, decodeData.continent, decodeData.SNR, decodeData.isMyCall );
            if (decodeData.isAlert) {
                if (msg4Enabled) {
                    if (msg4Inhibit == 0) {
                        if (!helperStationList.stationHasBeenWorked()) {
                            if (SNR > msg4SNR) {
                                //for (int iii = 0; iii < 100; iii++) {
                                //    msg4Buffer[iii] = -2;
                                //}
                                msg4SNR = SNR;
                                msg4Length = replyEndOfCopy;
                                System.arraycopy(buf, 12, msg4Buffer, 12, uniqueId.theOffset);
                                System.arraycopy(buf, 12 + uniqueId.theOffset + 1, msg4Buffer, 12 + uniqueId.theOffset, replyEndOfCopy - (12 + uniqueId.theOffset + 1));
                                msg4Buffer[replyEndOfCopy - 1] = (byte) 0x00;        //  the -1 is added because replyEndOfCopy points to the last byte in buf[] but buf[] has the one byte not copied.
                                logInfo("msg4Buffer complete *****");
                            }
                        }
                    }
                }
            }
        }
        //  Add to linked list if ok.
        if (okToAddToLinkedList) {
            //  ... but first capture raw bytes for possible response with msg 4
            decodeData.msg4Length = replyEndOfCopy;         // replyEndOfCopy is local variable, points to end of msg2 that is copied into msg4, includes first 12 bytes not copied.
            // copy unique ID first ("WSJT-X")
            System.arraycopy( buf, 12, decodeData.msg4Buffer, 0, uniqueId.theOffset );
            // skip the filed "new" (1 byte) and copy everythng after that, up to replyEndOfCopy, into decodeData.msg4Buffer
            System.arraycopy( buf, 12 + uniqueId.theOffset + 1, decodeData.msg4Buffer, uniqueId.theOffset, replyEndOfCopy - (12 + uniqueId.theOffset + 1));
            if (replyEndOfCopy > 13) {    //  just in case, should never happen
                decodeData.msg4Buffer[(replyEndOfCopy - 1) - 12] = (byte) 0x00;        //  the -1 is added because replyEndOfCopy points to the last byte in buf[] but buf[] has the one byte not copied.
            }

            //  Finally add to the linked list.
            decodeDataList.add(decodeData);
        }
        if (getResources().getBoolean(R.bool.includeMapping)) {
            helperStationList.doFilterForMapping(okToAddToLinkedList);
        }

        return String.format(Locale.US, "%s.%s.%s.%3d.%4d.%s.%s.%s.%s %s",
                uniqueId.theString, newBool, qtimeStr, SNR, dFreq, mode.theString, message.theString,
                newConfidence,newOffAir,possibleError
        );
    }


    //  The heartbeat msg, msg0, arrives every 15 seconds.  However, where during the minute they arrive is not definded.  It depends on where
    //      within the minute WSJT-X was started.  In particular, it can arrive right in the middle of a decode burst.
    private String convertHeartbeatMessage( byte[] buf, int messageLength ) {
        int currentOffset = 12;
        String estimatedErrorRate;

        heartbeatMessageCounter++;
        if (heartbeatMessageCounter == 0) {
            firstHeartbeatMsgTime = SystemClock.elapsedRealtime();      //  get time of first heartbeat message message for calculating packet loss rate
            estimatedErrorRate = "";
        } else {
            long timeSinceStart = SystemClock.elapsedRealtime() - firstHeartbeatMsgTime;
            //estimatedErrorRate = String.format(Locale.US, "e(%d %d)",heartbeatMessageCounter, timeSinceStart);

            //remove before release and replace with above commented out line.
            double timeSinceStartDouble = (double) timeSinceStart;
            double expectedNumHeartbeats = timeSinceStartDouble / 15000;    // convert to 15 second intervals
            double estPacketErrorRate = 1.0 - (((double) heartbeatMessageCounter) / expectedNumHeartbeats);
            estimatedErrorRate = String.format(Locale.US, "e(%d %d %1.3f %3.1f)", heartbeatMessageCounter, timeSinceStart, expectedNumHeartbeats, estPacketErrorRate*100);
        }

        ReturnUtf8Struct uniqueId = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += uniqueId.theOffset;
        isJTDX = uniqueId.theString.equals("JTDX");

        int maxSchema = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 4;

        ReturnUtf8Struct version = strangeUtf8( buf, currentOffset, messageLength );
        currentOffset += version.theOffset;
        logInfo("Version = "+version.theString+"*****3");
        byte[] verBytes = version.theString.getBytes();
        //  The format is '2.2.0'.  So I'm checking to see if the version is greater or equal to 2.2.x
        threePassDecode = false;
        if (verBytes[0] > 50) {                            //  50 == '2'
            threePassDecode = true;
        } else if (verBytes[0] == 50) {
            if (verBytes[2] >= 50) {
                threePassDecode = true;
            }
        }
        logInfo("Version - threePassDecode = "+ threePassDecode +"*****3");

        ReturnUtf8Struct revision = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += revision.theOffset;

        //  currentOffset should now match messageLength.  If not, notify user of a possible error.
        String possibleError;
        if ( currentOffset != messageLength ) {
            possibleError = "(Packet Error?)";
        }
        else {
            possibleError = "";
        }

        return String.format(Locale.US,"%s.ms%d.ver%s-%s %s %s",
                uniqueId.theString, maxSchema, version.theString, revision.theString, possibleError, estimatedErrorRate);
    }


    private String convertStatusMessage( byte[] buf, int messageLength ) {
        int currentOffset = 12;

        ReturnUtf8Struct uniqueId = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += uniqueId.theOffset;

        long dialFreq = convertQint64ToLong( buf, currentOffset, messageLength );
        double dialFreqDouble = (double)dialFreq/1000.0;
        wsjtxFreq = String.format(Locale.US,"%.1f",dialFreqDouble);
        currentOffset += 8;

        ReturnUtf8Struct mode = strangeUtf8(buf, currentOffset, messageLength );
        wsjtxMode = mode.theString;
        if (mode.theString.equals("WSPR")) {
            currentModeWSPR = true;
        } else {
            currentModeWSPR = false;
        }
        threePassDecodeInUse = (mode.theString.equals("FT8")) && (threePassDecode);

        currentOffset += mode.theOffset;

        ReturnUtf8Struct dxCall = strangeUtf8(buf, currentOffset, messageLength );
        currentDXCall = dxCall.theString;
        currentOffset += dxCall.theOffset;

        ReturnUtf8Struct report = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += report.theOffset;

        ReturnUtf8Struct txMode = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += txMode.theOffset;

        String txEnBool;
        if (buf[currentOffset++] == 1) {
            txEnBool = "TxEn:T";
            if (msg4Enabled) {
                msg4Inhibit = MSG4_INHIBIT_COUNT;
            }
        } else {
            txEnBool = "TxEn:F";
        }

        String txBool;
        if (buf[currentOffset++] == 1) { txBool = "Tx:T"; } else { txBool = "Tx:F"; }

        String decBool;
        if (buf[currentOffset++] == 1) {
            decBool = "Dec:T";
            decodeFlagMirror = true;
        } else {
            decBool = "Dec:F";
            if (decodeFlagMirror) {     // if transitioned from decode==true to decode==false
                /*
                    TOM - note that this code can be be reinstalled if I want to switch between three-pass-decode update and one single update.  The
                    commented out code code here can be used for one single update.

                if (threePassDecodeInUse) {
                    //  Safety valve in case a msg1 is missed.  If more than 7 seconds have elapsed since the last deocde then reset decodeCounter
                    long thisDecodeTime = SystemClock.elapsedRealtime();
                    if (timeSinceLastDecode != 0) {
                        if ((thisDecodeTime - timeSinceLastDecode) > 7000) {
                            decodeCounter = 0;
                        }
                        logInfo("Status - diff = "+(thisDecodeTime - timeSinceLastDecode)+" decodeCounter = "+decodeCounter+" *****3");
                    }
                    timeSinceLastDecode = thisDecodeTime;
                    //  Now count three decodes before setting endOfBurst.
                    decodeCounter++;
                    if (decodeCounter >= 3) {
                        endOfBurst = true;
                        decodeCounter = 0;
                    }
                } else {
                    endOfBurst = true;
                }
                 */
                endOfBurst = true;
                if (threePassDecodeInUse) {
                    //  Safety valve in case a msg1 is missed.  If more than 7 seconds have elapsed since the last deocde then reset decodeCounter
                    long thisDecodeTime = SystemClock.elapsedRealtime();
                    if (timeSinceLastDecode != 0) {
                        logInfo("Status - diff = " + (thisDecodeTime - timeSinceLastDecode) + " decodeCounter = " + decodeCounter + " *****3");
                        if ((thisDecodeTime - timeSinceLastDecode) > 7000) {
                            decodeCounter = 0;
                        }
                    }
                    timeSinceLastDecode = thisDecodeTime;

                    //  Now count three decodes before setting endOfBurst.
                    decodeCounter++;
                    if (decodeCounter != 1) {
                        suppressDashedLine = true;
                    } else {
                        suppressDashedLine = false;     //  this is always cleared in buildBurstAndAppend() right after it is checked but if no decodes then buildBurstAndAppend() won't get that far.
                    }
                    logInfo("Status - threePassDecodeInUse, decodeCounter=" + decodeCounter + ", suppressDashedLine=" + suppressDashedLine + " *****3");
                    if (decodeCounter >= 3) {
                        decodeCounter = 0;
                    }
                }
            }
            decodeFlagMirror = false;
        }

        int rxDF = convertQint32ToInt( buf, currentOffset, messageLength );
        currentRxFreq = rxDF;
        currentOffset += 4;

        int txDF = convertQint32ToInt( buf, currentOffset, messageLength );
        currentOffset += 4;

        ReturnUtf8Struct deCall = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += deCall.theOffset;
        if ( (myCall == null) && (!deCall.theString.equals(""))){
            //myCall = new String(deCall.theString);
            myCall = deCall.theString;
            logInfo("myCall = "+myCall+" ********************");
        }

        ReturnUtf8Struct deGrid = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += deGrid.theOffset;
        if ((myGrid == null) && (!deGrid.theString.equals(""))) {
            myGrid = deGrid.theString;
            helperStationList.setMyGrid(myGrid);
            //  There is the possibility that the users grid changes.  I'm not handling that here.  There is HelperStationList.getMyGrid() to check if it has changed.
            //  If I use getMyGrid() then I need to be aware that it is always a 6 character grid.  If the user inputs a 4 char grid then 'mm' is appended.
        }

        ReturnUtf8Struct dxGrid = strangeUtf8(buf, currentOffset, messageLength );
        currentDXGrid = dxGrid.theString;
        currentOffset += dxGrid.theOffset;

        String wdBool;
        if (buf[currentOffset] == 1) { wdBool = "wd:T"; } else { wdBool = "wd:F"; }

        //  currentOffset == 97.  The remaining fields are in the documentation but don't seem to be implemented.
        /*
        ReturnUtf8Struct subMode = strangeUtf8(buf, currentOffset, messageLength );
        currentOffset += subMode.theOffset;

        String fastBool;
        if (buf[currentOffset++] == 1) { fastBool = "fT"; } else { fastBool = "fF"; }

        int specialOpMode = (int)buf[currentOffset++];

        //  currentOffset should now match messageLength.  If not, notify user of a possible error.
        String possibleError;
        if ( currentOffset != messageLength ) {
            possibleError = String.format(Locale.getDefault(),"(Packet Error? %d)", currentOffset );
        }
        else {
            possibleError = "";
        }
        */
        String possibleError = "";      //  Set to null since last fields are not implemented and the length of this message is variable.

        return String.format(Locale.US,"%s.%d.%s.%s.%s.%s %s %s %s Rx:%d.Tx:%d.%s.%s.%s.%s %s",
                uniqueId.theString, dialFreq, mode.theString, dxCall.theString, report.theString, txMode.theString,
                txEnBool, txBool, decBool, rxDF, txDF, deCall.theString, deGrid.theString, dxGrid.theString,
                wdBool,
                possibleError);
    }


    private int convertQint32ToInt( byte[] buf, int offset, int messageLength ) {
        if (messageLength < (offset+4) ) {      // make sure there are at least four bytes still in packet before trying to process.
            return CONVERT_QINT_ERROR;          //  CONVERT_QINT_ERROR == -9999 indicates an error.
        }
        else {
            //  Convert quint32, qint32.  Unsigned values will be (ugh!) converted to signed since Java
            //      has no unsigned four bit values.  Default ByteBuffer is big-endian.
            ByteBuffer bbuf = ByteBuffer.wrap(buf, offset, 4);
            return bbuf.getInt();
        }
    }

    private long convertQint64ToLong( byte[] buf, int offset, int messageLength ) {
        if (messageLength < (offset+8) ) {      // make sure there are at least eight bytes still in packet before trying to process.
            return CONVERT_QINT_ERROR;
        }
        else {
            //  Convert quint64, qint64.  Unsigned values will be converted to signed.  Default ByteBuffer is big-endian.
            ByteBuffer bbuf = ByteBuffer.wrap(buf, offset, 8);
            return bbuf.getLong();
        }
    }

    private ReturnUtf8Struct strangeUtf8(byte[] buf, int offset, int messageLength ) {
        ReturnUtf8Struct returnValue = new ReturnUtf8Struct();
        int length = convertQint32ToInt(buf, offset, messageLength );
        int remainingBytes = messageLength-offset;
        if ( ( length > remainingBytes ) ||             //  If length is greater than the number of bytes left in the message - OR -
                ( length == CONVERT_QINT_ERROR ) ) {    //      convertQint32ToInt() returns CONVERT_QINT_ERROR if there are not at least four bytes remaining in packet
            returnValue.theString = "";
            returnValue.theOffset = 0;
        }
        else if ( length == -1 ) {          //      -1 is a valid Utf8 length value.  It's used when there is a zero length text.  Offset needs to be 4
            returnValue.theString = "";
            returnValue.theOffset = 4;
        }
        else {                              //  Normal Utf8 text.  Note that length can be zero and this code still works.
            returnValue.theString = new String(buf, offset + 4, length);
            returnValue.theOffset = 4 + length;
        }
        return returnValue;
    }

    private String convertQtime( byte[] buf, int offset, int messageLength ) {
        int qtimeInt = convertQint32ToInt( buf, offset, messageLength );
        if (qtimeInt == CONVERT_QINT_ERROR) {               // convertQint32ToInt() will return CONVERT_QINT_ERROR if there are not at least four bytes remaining in packet.
            return "00:00:00";
        }
        else {
            int dddu = qtimeInt / 1000;
            int hrs = dddu / 3600;
            dddu = dddu - (hrs * 3600);
            int min = dddu / 60;
            int sec = (dddu - (min * 60));
            return String.format(Locale.US, "%02d:%02d:%02d", hrs, min, sec);
        }
    }



    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

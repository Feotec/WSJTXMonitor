package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;

public class BaseActivity extends AppCompatActivity
        implements  DialogAbout.dialogAboutListener, DialogPort.dialogPortListener, DialogEULA.dialogEULAListener,
        DialogSetup.dialogSetupListener, DialogSetupDetails.dialogSetupDetailsListener,
        DialogNewFeatures.dialogNewFeaturesListener, DialogSettings.dialogSettingsListener,
        DialogKmMiles.dialogKmMilesListener, DialogClearScreenWSJTX.dialogClearScreenListener,
        DialogIPChanged.dialogIPChangedListener, DialogClearScreen.dialogClearScreenListener
{

    private static final String TAG = BaseActivity.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingBaseActivity;

    private final static String KEY_ABOUT_MENU_CALLED_FROM = "key_about_menu_called_from";
    private final static String KEY_INITIAL_STARTUP = "key_initial_startup";
    private final static String KEY_SCREEN_ON = "key_screen_on";
    private final static String KEY_DIALOG_SETUP_DO_CANCEL = "key_dialog_setup_do_cancel";
    private final static String KEY_DOING_REPLY_SETUP_DETAILS = "key_doing_reply_setup_details";

    protected static boolean appThinksServiceIsRunning = false;          //  used in onDestroy().
    protected UDPService mService = null;               // A reference to the service used to access the interface functions.
    protected boolean mBound = false;                   // Tracks the bound state of the service.

    protected boolean wifiConnected = false;            //  Set to true by checkWifi() below.
    static protected String ipAddressString = null;     //  Filled in by checkWifi() if connected, null otherwise.
    protected InetAddress inetAddress;                  //  Filled in by checkWifi() if connected.
    protected int ipAddressInt;                         //  Filled in by checkWiFi() if connected.  This is the hash code.  Needed here because InetAddress.hashCode() doesn't always work.
    protected boolean ipAddressChanged = false;

    protected boolean aboutMenuCalledFromOverflow;      //  Used to prevent DialogIPPort from appearing if DialogAbout is invoked from overflow menu

    protected int dialogSetupDetailsStepNumber = 0;     //  Counter for DialogSetupDetails.  Not critical.  The value is preserved in DialogSetupDetails fragment
    final private int DIALOG_SETUP_DETAILS_STEPS = 4;   //  The number of times DialogSetupDetails is invoked.
    final private int REPLY_SETUP_DETAILS_STEPS = 3;    //  The number of times DialogSetupDetails is invoked when doingReplySetupDetails == true

    static protected boolean upOrBackButtonPressed = false;

    protected boolean initialStartup = true;

    Handler uiHandler = null;
    Runnable uiRunnable = null;

    protected static boolean screenAlwaysOnForDisplay = false;    //  Static because I need one variable for both MainActivity and DisplayAll activity.

    protected ScrollView scrollView = null;
    protected HorizontalScrollView horizontalScrollView = null;

    protected boolean installedAndJustUpgraded = false;

    protected boolean dataNeverFlowed = false;
    protected boolean dialogSetupDetailsDoCancel = false;       //  if true then the CANCEL button is allowed while working through DialogSetupDetails.
    protected boolean doingReplySetupDetails = false;           //  if true then DialogSetupDetails will be managed by manageReplySetupDetails() instead of manageDialogSetupDetails()

    protected boolean isDisplayAll = false;

    //  This code below is intended to inhibit the screen from updating while the user is scrolling it and for several seconds later
    //      (SCREEN_TIMER_COUNTER_INIT seconds).  There are three components.
    //          1) This one second count-down timer with its counter, screenTimerCounter.
    //          2) Code in the descendent class scrollerText.OnTouchListener.onTouch().
    //          3) A method, BaseActivity.userScrollCheck(), immediately below.
    //  When the user touches the screen OnTouchListener.onTouch() is called.  In that method screenTimerCounter is initialized and this timer is
    //      kicked off.  Also the current X and Y position are recorded (in lastScrollX and lastScrollY).  One note is that while the user touches the
    //      screen the system won't update the screen positions, even if the user scrolls.
    //  While screenTimerCounter is non-zero MyReceiver.onReceive() in the descendent class will not update display.
    //  The timer will call userScrollCheck() in this class.  That method will check to see if the user actually scrolled the screen as opposed
    //      to just touching it.  (It compares the current X and Y position with lastScrollX and lastScrollY).  If the screen hasn't been scrolled then the
    //      timer is cancelled.  This allows the user to tap the screen and two seconds later the display will update.
    //  When the timer expires this counter will update the screen.
    //
    //  The timer is cancelled by onPause() below.  This means that rotating the screen will cancel.
    //
    //  The screen update is done via handlers and runnables declared here but defined in descendent class.
    //  An alternative implementation has OnTouchListener.onTouch() record lastScrollX and lastScrollY only screenTimerCounter == 0.  This allows the user
    //      to scroll back to the bottom (a natural thing to do) and it will again update the display.  The drawback is that the user may constantly tap the
    //      screen and wonder why it won't update.  NOTE - an implementation of this method is archived at 03_31_2019_2245_WSJTXMonitor.zip.
    //
    final protected int SCREEN_TIMER_COUNTER_INIT = 20;
    final protected int SCREEN_TIMER_TIMEOUT_MS = 500;
    protected int lastScrollX,lastScrollY;
    protected int screenTimerCounter = 0;
    protected Handler screenTimerHandler = new Handler();
    protected Runnable screenTimerRunnable = new Runnable() {
        @Override
        public void run() {
            //  This is a one second timer for screen display.  When the user touches the screen it will initiate this timer and set the
            //      screenTimerCounter to its initial value.  When the timer expires then the uiRunnable will update the display.
            logInfo("screenTimerRunnable.run() ***** "+
                    format(Locale.getDefault()," Thread %d  (counter %d)", Thread.currentThread().getId(), screenTimerCounter ));

            if (screenTimerCounter > 0) {
                screenTimerCounter--;
                screenTimerHandler.postDelayed(this, SCREEN_TIMER_TIMEOUT_MS);
                if (screenTimerCounter == SCREEN_TIMER_COUNTER_INIT-2) {
                    //  Check this one second after user release screen to check if user actually moved it.  (_COUNTER_INIT-2 since the counter has
                    //      already been decremented).  I wanted to do this last in case screenTimerRunnable is cancelled.
                    if ((horizontalScrollView != null) && (scrollView != null)) {
                        //  call userScrollCheck() in descendent class.  This is done after the call to postDelayed() because userScrollCheck() may cancel
                        //      the timer.
                        userScrollCheck();
                        //  Could check if timer cancelled (if screenTimerCounter == 0) and, if so, update screen.
                    }
                }
            }
            else {
                if (( uiHandler != null) && (uiRunnable != null) ) {        //  update the screen.
                    uiHandler.post(uiRunnable);
                }
            }
            //  If timer expired do nothing.  Let the normal update process restore the screen.  This gives the user more time.
        }
    };

    //  This checks to see if the user actually scrolled the view, as opposed to just touching it.  It is called from screenTimerRunnable.run() above.
    private void userScrollCheck() {
        int xxx = horizontalScrollView.getScrollX();
        int yyy = scrollView.getScrollY();
        if ((xxx == lastScrollX) && (yyy == lastScrollY)) {
            screenTimerCounter = 0;
            screenTimerHandler.removeCallbacks(screenTimerRunnable);
            logInfo("BaseActivity.userScrollCheck() cancelled screenTimerCounter *****  ("+lastScrollX+" "+lastScrollY+")");
            if (( uiHandler != null) && (uiRunnable != null) ) {        //  update the screen.
                uiHandler.post(uiRunnable);
            }
        }
    }

    // Monitors the state of the connection to the service.  This provides a reference to the Service so that I can access the interface
    protected final ServiceConnection mServiceConnection = new ServiceConnection() {

        //  Tom - this method is called when the service has made the connection.  The second parameter, service, is an service.
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logInfo("ServiceConnection.onServiceConnected() *********************");

            //  Tom - LocalBinder class (extending binder class) is nested class defined in UDPService class.  This typecasts
            //      IBinder interface as UDPService.LocalBinder.
            UDPService.LocalBinder binder = (UDPService.LocalBinder) service;
            //  LocalBinder class has only one function defined in GPSService.java, getService() which returns the
            //      GPSService instance
            mService = binder.getService();
            mBound = true;

            //  Unusual to do these three things here, especially UDPService.startUDPService().  I wanted the service to start as soon as the app begins.
            //      startUDPService() checks to see if the service is already running.  This is important since this code will be called every time the app
            //      unbinds (goes to background or screen rotation).  Also, UDPService.getDisplaySpanBuffer() has to check that the buffer is not being
            //      manipulated or else scrollerText.setText() will get an exception (see updateScrollerView(), I left the try{} block in there).
            appThinksServiceIsRunning = true;
            mService.startUDPService();
            mService.setInetAddress( inetAddress, ipAddressInt );

            //  Update the UI in descendant class (uiHander and uiRunnable are declared here but assigned in the descendant class).  This allows the UI to be
            //      updated as soon as the UI is created, if due to the activity starting or a screen rotation.
            if (( uiHandler != null) && (uiRunnable != null) ) {
                uiHandler.post(uiRunnable);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logInfo("ServiceConnection.onServiceDisconnected() *********************");
            //  Tom - This is not called at normal shutdown of the Service but rather if the "process hosting the service has crashed or been killed".
            //  TODO - The binding the service needs to be killed if this method executes, the mBound = false doesn't do it.
            mService = null;
            mBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

        logInfo( format(Locale.getDefault(),"onCreate(): Thread %d, PID %d, Package name %s ******",
                Thread.currentThread().getId(), android.os.Process.myPid(), getPackageName() ) );

        aboutMenuCalledFromOverflow = false;
        wifiConnected = checkWifi();

        updateValuesFromBundle(savedInstanceState);

        /*      Some simple code to determine the descendant class of BaseActivity.  I have isDisplayAll boolean but it is not set true until
                DisplayAll activity is started.  BaseActivity.onCreate() will run first so I need another method.  Both of these work.

        String simpleName = this.getClass().getSimpleName();
        logInfo("SIMPLE NAME "+simpleName+" ***************");        //  Either "DisplayAll" or "MainActivity".
        ---- OR ----
        if (this instanceof DisplayAll) {
            logInfo("DESCENDENT CLASS == DisplayAll ***************");
        }
        if (this instanceof MainActivity) {
            logInfo("DESCENDENT CLASS == MainActivity ***************");
        }
        */

        //  If initialStartup AND this BaseActivity is the parent of MainActivity.  If it is a parent of DisplayAll then this code should not be executed.
        if ( (initialStartup) && (this instanceof MainActivity) ) {
            //  For later use, recall number of startups, bump it, and write it back.
            int numStartups = WSJTXUtils.getAndBumpNumberOfStartups(this);
            logInfo("Number of startups " + numStartups + " ***************");

            //  Alerts class has several strings read from strings.xml.  I don't want to pass Context with each constructor so there is a static method initializing static strings.
            Alerts.initializeChannelIds( this );    //  no need to do this with every screen rotation.

            //  Check if first install or if an upgrade.  If first install then set dataNeverFlowed.  If upgrade set installedAndJustUpgraded.
            //      In either case call WSJTXUtils.setAppVersionInteger() so this doesn't happen until the next upgrade.
            int versionCodeRead = WSJTXUtils.getAppVersionInteger( this );
            int versionCodeReal = WSJTXUtils.getHardcodedVersionCode( this );
            logInfo("Version code stored: "+versionCodeRead+", app version code: "+versionCodeReal+" ***************");
            if ( versionCodeRead != versionCodeReal ) {
                //  If executing here it means that it is either a new build (versionCodeRead == 0) or it is the first
                //      startup after a revision update.
                if (versionCodeRead == 0) {             //  if first startup of new installation ...
                    dataNeverFlowed = true;
                    WSJTXUtils.setDataNeverFlowed( this, true);
                } else {                                //  if not a new installation but rather an upgrade.
                    installedAndJustUpgraded = true;    //  ... then show new features, just once.
                }
                WSJTXUtils.setAppVersionInteger( this );
            }
        }

        dataNeverFlowed = WSJTXUtils.getDataNeverFlowed( this );    //  set dataNeverFlowed on what is read from shared preferences.
        logInfo("dataNeverFlowed: "+dataNeverFlowed+" ***************");

        // To test the What's New dialog, uncomment the below line.
        //installedAndJustUpgraded = true;

        if (screenAlwaysOnForDisplay) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        logInfo("onSaveInstanceState() ********************* initialStartup: "+initialStartup);
        savedInstanceState.putBoolean(KEY_ABOUT_MENU_CALLED_FROM, aboutMenuCalledFromOverflow);
        savedInstanceState.putBoolean(KEY_INITIAL_STARTUP, initialStartup);
        savedInstanceState.putBoolean(KEY_SCREEN_ON, screenAlwaysOnForDisplay);
        savedInstanceState.putBoolean(KEY_DIALOG_SETUP_DO_CANCEL, dialogSetupDetailsDoCancel);
        savedInstanceState.putBoolean(KEY_DOING_REPLY_SETUP_DETAILS, doingReplySetupDetails);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        logInfo("updateValuesFromBundle() ********************* ");
        if (savedInstanceState != null) {
            logInfo("updateValuesFromBundle() before read ****** initialStartup: "+initialStartup);
            if (savedInstanceState.keySet().contains(KEY_ABOUT_MENU_CALLED_FROM)) {
                aboutMenuCalledFromOverflow = savedInstanceState.getBoolean(KEY_ABOUT_MENU_CALLED_FROM);
            }
            if (savedInstanceState.keySet().contains(KEY_INITIAL_STARTUP)) {
                initialStartup = savedInstanceState.getBoolean(KEY_INITIAL_STARTUP);
                logInfo("updateValuesFromBundle() after read ****** initialStartup: "+initialStartup);
            }
            if (savedInstanceState.keySet().contains(KEY_SCREEN_ON)) {
                screenAlwaysOnForDisplay = savedInstanceState.getBoolean(KEY_SCREEN_ON);
                logInfo("updateValuesFromBundle() after read ****** screenAlwaysOnForDisplay: "+screenAlwaysOnForDisplay);
            }
            if (savedInstanceState.keySet().contains(KEY_DIALOG_SETUP_DO_CANCEL)) {
                dialogSetupDetailsDoCancel = savedInstanceState.getBoolean(KEY_DIALOG_SETUP_DO_CANCEL);
                logInfo("updateValuesFromBundle() after read ****** dialogSetupDetailsDoCancel: "+dialogSetupDetailsDoCancel);
            }
            if (savedInstanceState.keySet().contains(KEY_DOING_REPLY_SETUP_DETAILS)) {
                doingReplySetupDetails = savedInstanceState.getBoolean(KEY_DOING_REPLY_SETUP_DETAILS);
                logInfo("updateValuesFromBundle() after read ****** doingReplySetupDetails: "+doingReplySetupDetails);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        logInfo("onCreateOptionsMenu() called ***************");

        // Inflate our menu from the resources by using the menu inflater.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    //  Called every time the menu is opened.s
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        logInfo("onPrepareOptionsMenu() called ***************");

        MenuItem screenItem = menu.findItem(R.id.action_screen_always_on);
        if (screenAlwaysOnForDisplay) {
            screenItem.setChecked(true);
        }
        else {
            screenItem.setChecked(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // used to be a switch statement until Google made the R.id.* non-final
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            logInfo("onOptionsItemSelected() settings ***************");
            DialogFragment dialogSettings = new DialogSettings();
            dialogSettings.show(getSupportFragmentManager(), "Dialog Settings");
            dialogSettings.setCancelable(false);
            return true;
        } else if (itemId == R.id.action_screen_always_on) {
            logInfo("onOptionsItemSelected() Screen Always on ***************");
            if (screenAlwaysOnForDisplay) {
                screenAlwaysOnForDisplay = false;
                item.setChecked(false);
                //  An XML alternative is android:keepScreenOn="true", at the level of ConstraintLayout
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                screenAlwaysOnForDisplay = true;
                item.setChecked(true);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            return true;
        } else if (itemId == R.id.action_about) {
            logInfo("onOptionsItemSelected() About ***************");
            bringupAboutMenu();
            aboutMenuCalledFromOverflow = true;
            return true;
        }
        logInfo("onOptionsItemSelected() default ***** ");
        return super.onOptionsItemSelected(item);
    }


    //  Called from above (onOptionsItemSelected()) and from MainActivity.onResume() if initialStartup == true and wifiConnected == true.
    protected void bringupAboutMenu() {
        FragmentManager fragMan = getSupportFragmentManager();
        DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("DialogAbout");
        if (testFrag == null) {
            testFrag = new DialogAbout();
            testFrag.show(getSupportFragmentManager(), "DialogAbout");
            testFrag.setCancelable(false);      //  need to be setCancelable() because another Dialog needs to follow.
        }
    }

    //  Returns true if this is the paid version and the free version is installed.  Will also return true if this is the free version and the paid version
    //      is installed.
    //  This is called at startup either by manageDialogSetup() (below) under normal startup conditions or by dialogSetupDetailsNext() (below) if
    //      dataNeverFlowed == true and the user goes through the four page setup details business.
    //  When called from manageDialogSetup() if it return false then DialogSetup will be displayed as normal.  If it returns true then DialogSetup will not be
    //      displayed because a don't-show-again dialog (invoked here) will be displayed instead.  The return value is ignored when called from dialogSetupDetailsNext().
    //  The don't-show-again dialog warns the user that both apps cannot run ath the same time unless the port values are changed.
    protected boolean isOppositeAppInstalled() {
        String oppositeAppName = getResources().getString(R.string.opposite_app_name);

        //  Get list of all installed packages and look for the free/paid application.
        List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            //logInfo( "Installed package :" + packageInfo.packageName + " ***************");
            if (packageInfo.packageName.contains(oppositeAppName)) {
                //  I'm doing a contains() instead of equals() because the .debug version might be installed.
                logInfo( "isOppositeAppInstalled() - Found "+oppositeAppName+" in "+packageInfo.packageName+" ***************");
                if (!WSJTXUtils.getBothAppsDontShowAgain( this )) {
                    DialogFragment bothAppsInstalledDont = DialogDontShowAgain.newInstance(R.string.dialog_both_apps_title,
                            R.string.dialog_both_apps_text, 2);
                    bothAppsInstalledDont.show(getSupportFragmentManager(), "DialogBothAppsInstalledDont");
                    bothAppsInstalledDont.setCancelable(false);
                    return true;
                }
                logInfo( "    User clicked dont-show-again ***************");
                return false;
            }
        }
        logInfo( "Opposite app "+oppositeAppName+" not installed ***************");
        return false;
    }

    //
    //  checkWifi() - checks the Wifi state.  If not connected or on error it returns false.  Otherwise it returns true and fills in
    //      inetAddress and ipAddressString class variables.
    //  Google docs says that the call to WiFiManager.getConnectionInfo() requires ACCESS_COURSE_LOCATION permissions.  I really don't want
    //      to add this.  It seems to work on all my devices without it.
    //
    protected boolean checkWifi() {
        byte[] ipByteArray;
        //getIPAddress();
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();        //  may produce null pointer exception
            SupplicantState supState = wifiInfo.getSupplicantState();
            if (supState == SupplicantState.DISCONNECTED) {
                return false;
            }
            ipAddressInt = wifiInfo.getIpAddress();
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ipAddressInt = Integer.reverseBytes(ipAddressInt);
            }
            ipByteArray = BigInteger.valueOf(ipAddressInt).toByteArray();
            inetAddress = InetAddress.getByAddress(ipByteArray);
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
            int storedIPHash = WSJTXUtils.getIpHashCode( this );
            //  I was using inetAddress.hashCode() but it returned the wrong value for 192.168.1.209.  ipAddressInt above has the correct value so use it.
            if (storedIPHash != ipAddressInt) {
                if (storedIPHash != 0) {
                    ipAddressChanged = true;
                }
            }

            if (getResources().getBoolean(R.bool.multicastIP)) {
                WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("WSJT-X Monitor Multicast Lock");
                multicastLock.acquire();
            }

            logInfo("inetAddress "+inetAddress+"  "+ipAddressString+"  hashcode "+inetAddress.hashCode()+", stored hashcode "+storedIPHash+", ipAddressInt "+ipAddressInt+" ***********");
        } catch (Exception ex) {
            logInfo("checkWifi() exception " + ex.getMessage());
            return false;
        }
        return true;
    }

    /*
    //  This is a function built to test the date collected from LinkProperties and NetworkCapabilities class.  It was never
    //      included in a release.
    protected void getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    String sAddr = addr.getHostAddress();
                    //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                    boolean isIPv4 = sAddr.indexOf(':')<0;
                    logInfo("getIPAddress() saddr "+sAddr+", isIPv4 "+isIPv4+"  **********");
                }
            }
        } catch (Exception ex) {
            logInfo("getIPAddress() exception1 " + ex.getMessage()+"  **********");
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
                Network currentNetwork = connectivityManager.getActiveNetwork();
                //InetAddress addr2 = currentNetwork.getByName

                LinkProperties linkProperties = connectivityManager.getLinkProperties(currentNetwork);
                logInfo("               currentNetwork DHCP Server Address "+linkProperties.getDhcpServerAddress()+"  **********");
                logInfo("               currentNetwork Domains "+linkProperties.getDomains()+"  **********");
                logInfo("               currentNetwork InterfaceName "+linkProperties.getInterfaceName()+"  **********");
                logInfo("               currentNetwork DNS Server "+linkProperties.getPrivateDnsServerName()+"  **********");
                logInfo("               currentNetwork toString() "+linkProperties.toString()+"  **********");

                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(currentNetwork);
                logInfo("               currentNetwork Downstream Bandwidth kbps "+caps.getLinkDownstreamBandwidthKbps()+" **********");
                logInfo("               currentNetwork Downstream Bandwidth kbps "+caps.getLinkUpstreamBandwidthKbps()+" **********");
                logInfo("               currentNetwork Transport Bluetooth "+caps.hasTransport( NetworkCapabilities.TRANSPORT_BLUETOOTH )+" **********");
                logInfo("               currentNetwork Transport Cellular "+caps.hasTransport( NetworkCapabilities.TRANSPORT_CELLULAR )+" **********");
                logInfo("               currentNetwork Transport Ethernet "+caps.hasTransport( NetworkCapabilities.TRANSPORT_ETHERNET )+" **********");
                logInfo("               currentNetwork Transport USB "+caps.hasTransport( NetworkCapabilities.TRANSPORT_USB )+" **********");
                logInfo("               currentNetwork Transport VPN "+caps.hasTransport( NetworkCapabilities.TRANSPORT_VPN )+" **********");
                logInfo("               currentNetwork Transport WiFi "+caps.hasTransport( NetworkCapabilities.TRANSPORT_WIFI )+" **********");

            } catch (Exception ex) {
                logInfo("getIPAddress() exception2 " + ex.getMessage()+"  **********");
            }
        }
    }
    */
    @Override
    protected void onStart() {
        super.onStart();
        logInfo("onStart() *********************");

        if (wifiConnected) {
            //  Calling bindService() will create a bound but not started Service.  The service is started when UDPService.startUDPService() interface
            //      function is called (startService() is within that method).
            //  More fundumental, calling bindService() if the Service doesn't already exist will cause onCreate() to be called (the Context.BIND_AUTO_CREATE flag).
            //      Note the second parameter (mServiceConnection) is the name of the callback above.
            Intent tIntent = new Intent(this, UDPService.class);
            bindService(tIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        logInfo("onResume() *********************");
    }

    @Override
    protected void onPause() {
        super.onPause();
        logInfo("onPause() ********************* "+this.isFinishing());

        screenTimerCounter = 0;
        screenTimerHandler.removeCallbacks(screenTimerRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        logInfo("onStop() *********************");

        if (mBound) {
            // Unbind from the service. This signals to the service that the MainActivity is no longer in the foreground.
            unbindService(mServiceConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        logInfo("onDestroy() *********************");

        if (this.isFinishing() && !upOrBackButtonPressed) {
            logInfo("onDestroy()  and isFinishing() *********************");
            //  Tom - make sure we are actually shutting down and not rotating the screen before attempting to kill the Service.
            if (appThinksServiceIsRunning) {
                //  Tom - if program is terminating but service is still running in foreground then call this to stop it.  This
                //      will call stopSelf() which will cause the OS to call Service's onDestroy().
                mService.stopUDPService();
            }
            //initialStartup = true;      //  static variables may not always be destroyed at shutdown.
            ipAddressString = null;

            //  Turn off flag in case it was left on.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            screenAlwaysOnForDisplay = false;

            //  Using either of the below lines to kill the program is rather harsh but it will solve the problem of the static
            //      variable not being destroyed.  Killing it in this manner flushes the cache for this process.  See Topic 6 in Android.docx.
            //android.os.Process.killProcess(android.os.Process.myPid());
            //System.exit(0);
        }

        //  Since I have such trouble with the above code working or not being run at all, I'm taking care of the app's
        //      business before calling super().  Normally I always call super() first.
        super.onDestroy();
    }

    protected void doFinish() {
        logInfo("doFinish() finishAndRemoveTask() ***************");
        this.finishAndRemoveTask();
        /*  Minimum SDK is now 21 (LOLLIPOP) so this if statement is unnecessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            logInfo("doFinish() finishAndRemoveTask() ***************");
            this.finishAndRemoveTask();
        } else {
            logInfo("doFinish() finishAffinity() ***************");
            this.finishAffinity();
        }
         */
    }

    //
    //
    //  These methods implement the listener interface for DialogAbout, DialogEULA, DialogNewFeatures, and DialogIPChanged classes.
    //
    //  The first two share the use of aboutMenuCalledFromOverflow boolean.  It is normally clear and set when the DialogAbout
    //      is invoked from the overflow menu.  Under these conditions I don't want DialogSetup to be displayed.  Since DialogAbout and
    //      DialogEULA are not cancelable (.setCancelable() is false) then the only way to exit those dialogs is via the buttons.
    //  For DialogAbout if the user exits via Dismiss then it will check aboutMenuCalledFromOverflow in the listener.  If clear it will bring
    //      up DialogSetup.  In either case it will clear aboutMenuCalledFromOverflow.
    //  If the user chooses to view EULA then an identical action will take place when the user presses dismiss button from EULA dialog.
    //

    @Override
    public void dialogAboutNegativeClick(DialogFragment dialog) {
        //  On negative (EULA) click it brings up EULA popup.
        logInfo("dialogAboutNegativeClick Listener  ************");
        FragmentManager fragMan = getSupportFragmentManager();
        DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("DialogEULA");
        if (testFrag == null) {
            testFrag = new DialogEULA();
            testFrag.show(getSupportFragmentManager(), "DialogEULA");
            testFrag.setCancelable(false);      //  need to be setCancelable() because another Dialog may need to follow.
        }
    }

    @Override
    public void dialogAboutPositiveClick(DialogFragment dialog) {
        //  On positive click (Dismiss) it brings up DialogSetup
        logInfo("dialogAboutPositiveClick Listener  ************");
        if (!aboutMenuCalledFromOverflow) {         //  If not called from overflow (i.e. - called from startup)
            if (mService != null) {
                if (!mService.dataIsFlowing()) {        //  ... and if data has not yet arrived then bring up DialogIPPort.
                    manageDialogSetup();
                }
            }
        }
        aboutMenuCalledFromOverflow = false;
    }

    @Override
    public void dialogEULAOkClick(DialogFragment dialog) {
        //  On positive click (Dismiss) it brings up DialogSetup popup
        logInfo("dialogEULAOkClick Listener  ************");
        if (!aboutMenuCalledFromOverflow) {         //  If not called from overflow (i.e. - called from startup)
            if (mService != null) {
                if (!mService.dataIsFlowing()) {        //  ... and if data has not yet arrived then bring up DialogIPPort.
                    manageDialogSetup();
                }
            }
        }
        aboutMenuCalledFromOverflow = false;
    }

    //
    //  This is a listener for the DialogNewFeatures Dismiss button.  It needs to check mService.dataIsFlowing() and, if necessary,
    //      bring up the DialogSetup dialog.  This is similar to the dialogEULAOkClick and dialogAboutPositiveClick listeners
    //      immediately above.
    //
    @Override
    public void dialogNewFeaturesDismiss(DialogFragment dialog) {
        //  On positive click (Dismiss) it brings up IP-Port popup
        logInfo("dialogNewFeaturesDismiss Listener  ************");
        doingReplySetupDetails = false;
        if (mService != null) {
            if (!mService.dataIsFlowing()) {        //  ... and if data has not yet arrived then bring up DialogSetup.
                manageDialogSetup();
            }
        }
    }

    //  This method is the center of a rather complicated display of dialogs at startup.  It is called from the above listeners 1) About Dismiss listener, 2) EULA Dismiss
    //      listener, and 3) Dialog New Features Dismiss listener.  Note that this method won't be called if data has already started to flow by the time any one of the
    //      three dialogs have been dismissed.
    //
    //  This first part of this function is to force users, upon a new installation, to view the four steps for setting up WSJT-X.  It will do this if dataNeverFlowed == true.
    //      If dataNeverFlowed == false then it will check ipAddressChanged and invoke DialogIPChanged if so.  If that is also false then it will call isOppositeAppInstalled().
    //      isOppositeAppInstalled() (above) will return false if it does nothing.  In that case we bring up the normal DialogSetup window, which dismisses automatically
    //      when data flow.
    //
    private void manageDialogSetup() {
        doingReplySetupDetails = false;     // This is set true when displaying details for replies, not for setting up IP and port.
        if (dataNeverFlowed) {              // if dataNeverFlowed then force them into DialogSetupDetails.
            dialogSetupDetailsDoCancel = false;
            dialogSetupDetailsStepNumber = 0;
            manageDialogSetupDetails();
        } else {
            if (ipAddressChanged) {         // If IP address changed then warn the listener
                DialogFragment testFrag = DialogIPChanged.newInstance( ipAddressString );
                testFrag.show(getSupportFragmentManager(), "DialogIPChanged");  // Note - when DialogSetup is called from the menu it has a different tag so it won't be dismissed when data flows
                testFrag.setCancelable(false);
            } else {                        //  ... otherwise bring up DialogSetup as usual.
                if (!isOppositeAppInstalled()) {
                    DialogFragment testFrag = DialogSetup.newInstance(true, ipAddressString);
                    testFrag.show(getSupportFragmentManager(), "DialogSetup");  // Note - when DialogSetup is called from the menu it has a different tag so it won't be dismissed when data flows
                    testFrag.setCancelable(false);
                }
            }
        }
    }

    //  This is a listener from DialogIPChanged dialog if the user clicks help.
    @Override
    public void dialogIPChangedHelp(DialogFragment dialog) {
        //  On neutral click bring up manageDialogSetupDetails()
        logInfo("dialogIPChangedListener Listener  ************");
        doingReplySetupDetails = false;     // This is set true when displaying details for replies, not for setting up IP and port.
        dialogSetupDetailsDoCancel = false;
        dialogSetupDetailsStepNumber = 0;
        manageDialogSetupDetails();
    }

    //
    //
    //  These methods implement the listener interface for DialogSetup and DialogSetupDetails class.  If DialogSetup user clicks "Details" then
    //      this listener is called which then invokes DialogSetupDetails by calling manageDialogSetupDetails().  Below that are three listeners
    //      for DialogSetupDetails class (cancel, back, and next/finish).
    //
    //

    //  Listener form DialogSetup when user clicks on details.
    @Override
    public void dialogSetupAskForDetails(DialogFragment dialog) {
        //  On positive click (Dismiss) it brings up DialogSetup popup
        logInfo("dialogSetupAskForDetails Listener  ************");
        dialogSetupDetailsDoCancel = true;
        dialogSetupDetailsStepNumber = 0;
        doingReplySetupDetails = false;
        manageDialogSetupDetails();
    }

    //  This and the three listeners below, dialogSetupDetailsNext() ....back() and ....cancel(), manage the four page graphical setup details.  It is called from
    //      dialogSetupDetailsNext() and ...back() as the user progresses through the four pages.
    //  It is initially called from three places, all above in BaseActivity:
    //      1) manageDialogSetup() - manageDialogSetup() is called when the user dismisses the about dialog, the EULA dialog, and the new features dialog.  manageDialogSetup
    //         will call this method if dataNeverFlowed == True.  This is how I force the user to see these four pages when the app is first installed.
    //      2) dialogSetupAskForDetails() - a listener called from DialogSetup, when the user presses the Details button.
    //      3) dialogIPChangedHelp() - a listener called from DialogIPChanged, when the user presses the Help button.
    //  The method uses the counter dialogSetupDetailsStepNumber which counts from 0-3.  Its value determines what is displayed by DialogSetupDetails.
    public void manageDialogSetupDetails() {
        int param1, param2, param3, param5;
        boolean mIsLargeLayout = getResources().getBoolean(R.bool.large_layout);
        String specialString;
        switch (dialogSetupDetailsStepNumber) {
            case 0:
                param1 = R.string.title_text_step1;
                param2 = R.string.header_text_step1;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step1;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step1_sm;
                }
                param5 = R.string.next;
                specialString = "";
                break;
            case 1:
                param1 = R.string.title_text_step2;
                param2 = R.string.header_text_step2;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step2;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step2_sm;
                }
                param5 = R.string.next;
                specialString = "";
                break;
            case 2:
                param1 = R.string.title_text_step3;
                param2 = R.string.header_text_step3a;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step3;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step3_sm;
                }
                param5 = R.string.next;
                specialString = getResources().getString(R.string.header_text_step3a)+"\n    "+ipAddressString+
                        getResources().getString(R.string.header_text_step3b)+"\n    "+Integer.toString( WSJTXUtils.getDatagramPort( this ) )+
                        getResources().getString(R.string.header_text_step3c);
                break;
            case 3:
                param1 = R.string.jtalert_text;
                param2 = R.string.jtalert_header_text;
                param3 = 0;
                param5 = R.string.finish;
                specialString = "";
                break;
            default:        //   if dialogSetupDetailsStepNumber is not 0, 1, or 2, then act as though it were zero.
                param1 = R.string.title_text_step1;
                param2 = R.string.header_text_step1;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step1;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step1_sm;
                }
                param5 = R.string.next;
                dialogSetupDetailsStepNumber = 0;
                specialString = "";
                break;
        }
        DialogFragment testFrag = DialogSetupDetails.newInstance(param1, param2, param3, param5, dialogSetupDetailsStepNumber, specialString, dialogSetupDetailsDoCancel);
        testFrag.show( getSupportFragmentManager(), "DialogSetupDetails");
        testFrag.setCancelable(false);
    }

    @Override
    public void dialogSetupDetailsNext(DialogFragment dialog, int counter) {
        logInfo("dialogSetupDetailsNext  ***** "+counter+" "+(dialogSetupDetailsStepNumber+1) );
        dialogSetupDetailsStepNumber = counter;
        if (doingReplySetupDetails) {
            if (dialogSetupDetailsStepNumber < REPLY_SETUP_DETAILS_STEPS) {
                manageReplySetupDetails();
            } else {
                dialogSetupDetailsStepNumber = 0;
                doingReplySetupDetails = false;
            }
        } else {
            if (dialogSetupDetailsStepNumber < DIALOG_SETUP_DETAILS_STEPS) {
                manageDialogSetupDetails();
            } else {
                dialogSetupDetailsStepNumber = 0;
                //  Call isOppositeAppInstalled().  It is self-contained.  Ignore any return value.
                isOppositeAppInstalled();
            }
        }
    }

    @Override
    public void dialogSetupDetailsBack(DialogFragment dialog, int counter) {
        logInfo("dialogSetupDetailsBack  ***** "+counter+" "+(dialogSetupDetailsStepNumber-1) );
        dialogSetupDetailsStepNumber = counter;
        if (dialogSetupDetailsStepNumber < 0 ) {
            dialogSetupDetailsStepNumber = 0;
            doingReplySetupDetails = false;
        }
        else {
            if (doingReplySetupDetails) {
                manageReplySetupDetails();
            } else {
                manageDialogSetupDetails();
            }
        }
    }

    @Override
    public void dialogSetupDetailsCancel(DialogFragment dialog) {
        logInfo("dialogSetupDetailsCancel  ***************");
        dialogSetupDetailsStepNumber = 0;
        doingReplySetupDetails = false;
    }


    //  This mirrors the manageDialogSetupDetails() method above.  It is for setting up the Reply to Alerts, allowing WSJT-X accept UDP packets.  It is called from
    //      MainActivity.dialogReplyHelpSetupListener() above (a listener for DialogReplyHelp class) and from the two DialogSetupDetails listeners immediately above.
    public void manageReplySetupDetails() {
        int param1, param2, param3, param5;
        boolean mIsLargeLayout = getResources().getBoolean(R.bool.large_layout);
        String specialString;
        switch (dialogSetupDetailsStepNumber) {
            case 0:
                param1 = R.string.reply_title_text_step1;
                param2 = R.string.reply_header_text_step1;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step1;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step1_sm;
                }
                param5 = R.string.next;
                specialString = "";
                break;
            case 1:
                param1 = R.string.reply_title_text_step2;
                param2 = R.string.reply_header_text_step2;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step2;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step2_sm;
                }
                param5 = R.string.next;
                specialString = "";
                break;
            case 2:
                param1 = R.string.reply_title_text_step3;
                param2 = R.string.reply_header_text_step3;
                if (mIsLargeLayout) {
                    param3 = R.drawable.reply_setup_step3;
                }
                else {
                    param3 = R.drawable.reply_setup_step3_sm;
                }
                param5 = R.string.finish;
                specialString = "";
                break;
            default:        //   if dialogSetupDetailsStepNumber is not 0, 1, or 2, then act as though it were zero.
                param1 = R.string.reply_title_text_step1;
                param2 = R.string.reply_header_text_step1;
                if (mIsLargeLayout) {
                    param3 = R.drawable.wsjtx_setup_step1;
                }
                else {
                    param3 = R.drawable.wsjtx_setup_step1_sm;
                }
                param5 = R.string.next;
                specialString = "";
                break;
        }
        DialogFragment testFrag = DialogSetupDetails.newInstance(param1, param2, param3, param5, dialogSetupDetailsStepNumber, specialString, dialogSetupDetailsDoCancel);
        testFrag.show( getSupportFragmentManager(), "DialogSetupDetails");
        testFrag.setCancelable(false);
    }


    //
    //
    //  This method implements the listener interface for DialogSettings class.  Immediately below that are listeners for some of its submenus.
    //
    //

    @Override
    public void dialogSettingsSelectionListener(DialogFragment dialog, int which ) {
        logInfo("DialogSettingsSelectionListener  "+which+" ************");
        if (which == 0) {
            if (isDisplayAll) {
                logInfo("dialogSettingsSelectionListener() DisplayAll - setting isFinish() boolean ***************");
                WSJTXUtils.setIsFinishInvoked( this, true );
            }
            doFinish();
        } else if (which == 1) {
            DialogFragment clearScreenDialog = new DialogClearScreen();
            clearScreenDialog.show( getSupportFragmentManager(), "DialogClearScreen");
            clearScreenDialog.setCancelable(false);
        } else if (which == 2) {
            DialogFragment kmMilesDialog = new DialogKmMiles();
            kmMilesDialog.show( getSupportFragmentManager(), "DialogKmMiles");
            kmMilesDialog.setCancelable(false);
        } else if (which == 3) {
            DialogFragment statusDialog = new DialogStatus();
            statusDialog.show(getSupportFragmentManager(), "DialogStatus");
            statusDialog.setCancelable(false);
        } else if (which == 4) {
            DialogFragment portDialog = new DialogPort();
            portDialog.show(getSupportFragmentManager(), "PortDialog");
            portDialog.setCancelable(false);
        } else if (which == 5) {
            DialogFragment testFrag = DialogSetup.newInstance(false,ipAddressString);
            testFrag.show( getSupportFragmentManager(), "DialogSetupSecondTag");    //  different tag so MainActivity.manageDisplay() doesn't dismiss it.
            testFrag.setCancelable(false);
        }
    }

    @Override
    public void dialogSettingsHelpListener(DialogFragment dialog ) {
        logInfo("dialogSettingsHelpListener  ************");
        DialogFragment settingsDialogHelp = DialogMsgOnlyNoTitle.newInstance( R.string.dialog_settings_help );
        settingsDialogHelp.show( getSupportFragmentManager(), "DialogMsgOnlyNoTitle");
        settingsDialogHelp.setCancelable(false);
    }

    //  Listeners for the clear screen settings submenu

    @Override
    public void dialogClearScreenSelectionListener( DialogFragment dialog, int which ) {
        logInfo("dialogClearScreenSelectionListener  "+which+" ************");
        if (which == 0) {
            mService.clearScreen();
        } else if (which == 1) {
            DialogFragment clearScreenDialog = new DialogClearScreenWSJTX();
            clearScreenDialog.show(getSupportFragmentManager(), "DialogClearScreenWSJTX");
            clearScreenDialog.setCancelable(false);
        }
    }

    @Override
    public void dialogClearScreenHelpListener(DialogFragment dialog ) {
        logInfo("dialogClearScreenHelpListener  ************");
        DialogFragment clearScreenDialogHelp = DialogMsgOnlyNoTitle.newInstance( R.string.dialog_clear_screen_help );
        clearScreenDialogHelp.show( getSupportFragmentManager(), "DialogMsgOnlyNoTitle");
        clearScreenDialogHelp.setCancelable(false);
    }

    //  Listener for the setting from km to miles

    @Override
    public void dialogKmMilesDoneListener(DialogFragment dialog, boolean milesNotKm, boolean showDontMessage ) {
        mService.setKmOrMiles( milesNotKm );
        if (showDontMessage) {
            if (!WSJTXUtils.getKmMilesDontShowAgain( this )) {
                int msg;
                if (milesNotKm) {
                    msg = R.string.dialog_distance_dont1_miles;
                } else {
                    msg = R.string.dialog_distance_dont1_km;
                }
                DialogFragment clearScreenDialogHelpDont = DialogDontShowAgain.newInstance( 0, msg, 1 );
                clearScreenDialogHelpDont.show( getSupportFragmentManager(), "DialogClearScreenDontShowAgainHelp");
                clearScreenDialogHelpDont.setCancelable(false);
            }
        }
    }

    //  Listeners for settings option clear screen from WSJT-X.

    @Override
    public void dialogClearScreenWSJTXDoneListener(DialogFragment dialog, boolean clearScreen, boolean didntViewHelp ) {
        logInfo("dialogClearScreenWSJTXDoneListener ************");
        mService.enableClearScreenFromWSJTX( clearScreen );
        //  If the option is enabled AND don't-show-message-again checkbox has not been checked AND user has not viewed help this time then show "don't show again" help dialog.
        //      (The DialogClearScreenDontShowAgainHelp message is the same as DialogClearScreenHelp message so that is the reason for
        //       conditioning it on whether the user has already pressed the help button).
        if (clearScreen) {
            if (!WSJTXUtils.getClearScreenDontShowAgain( this )) {
                if (didntViewHelp) {
                    DialogFragment clearScreenDialogHelpDont = DialogDontShowAgain.newInstance( R.string.dialog_clear_screen_help_dont_title,
                                                                                    R.string.dialog_clear_screen_help_dont, 0);
                    clearScreenDialogHelpDont.show( getSupportFragmentManager(), "DialogClearScreenDontShowAgainHelp");
                    clearScreenDialogHelpDont.setCancelable(false);
                }
            }
        }
    }

    @Override
    public void dialogClearScreenWSJTXHelpListener(DialogFragment dialog) {
        logInfo("dialogClearScreenWSJTXHelpListener ************");
        DialogFragment clearScreenDialogHelp = DialogMsgOnlyNoTitle.newInstance( R.string.dialog_clear_screen_WSJTX_message_help );
        clearScreenDialogHelp.show( getSupportFragmentManager(), "DialogClearScreenHelp");
        clearScreenDialogHelp.setCancelable(false);
    }

    //  These methods implement the listener interface for DialogPort class.  Following the two listener methods are two static classes
    //      for Success, and Error.

    @Override
    public void dialogPortDoneClick(DialogFragment dialog, String portInput ) {
        logInfo("dialogPortDoneClick Listener ************"+portInput);
        int newPortValue = 0;
        try {
            newPortValue = Integer.parseInt(portInput);
        } catch (NumberFormatException nfe) {
            // Should never happen because DialogPort EditText is constrained to only five numeric digits but if it did newPortValue
            //      will still be 0 and below if statement will catch the error
            logInfo("dialogPortDoneClick Listener - invalid number ************");
        }
        if ( (newPortValue < 1024) || (newPortValue > 32767) ) {
            DialogFragment portDialogError = new DialogPortError();
            portDialogError.show( getSupportFragmentManager(), "PortDialogError");
        }
        else {
            //  Number ok.  Check to see if it is the same as the current number.  If so then do nothing.
            if (newPortValue != WSJTXUtils.getDatagramPort(this)) {
                WSJTXUtils.setDatagramPort( this, newPortValue );
                DialogFragment portDialogSuccess = new DialogPortSuccess();
                portDialogSuccess.show(getSupportFragmentManager(), "PortDialogSuccess");
            }
        }
    }

    @Override
    public void dialogPortHelpClick(DialogFragment dialog) {
        logInfo("dialogPortHelpClick Listener ************");
        DialogFragment portDialogHelp = DialogMsgOnlyNoTitle.newInstance( R.string.port_message_help_text );
        portDialogHelp.show( getSupportFragmentManager(), "DialogMsgOnlyNoTitle");
        portDialogHelp.setCancelable(false);
    }

    public static class DialogPortSuccess extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.port_message_success_title)
                    .setMessage(R.string.port_message_success_text)
                    .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (DO_LOGGING) {
                                Log.i(TAG, "DialogPortSuccess OK ***************");   // cannot call logInfo() because in static class.
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class DialogPortError extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.port_message_error_title)
                    .setMessage(R.string.port_message_error_text)
                    .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (DO_LOGGING) {
                                Log.i(TAG, "DialogPortError OK ***************");     // cannot call logInfo() because in static class.
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    //  Static class for any dialog that only has a message and no title, such as help dialogs.  Note that it does getText() instead
    //      of getString() so that HTML markup characters will work.  Also note that it has only one button and it's labelled "dismiss".
    public static class DialogMsgOnlyNoTitle extends DialogFragment {
        private static final String TAG = DialogMsgOnlyNoTitle.class.getSimpleName();
        private static final String MESSAGE_INT = "message_int";

        private int messageInt;

        public static DialogMsgOnlyNoTitle newInstance(int messageIntParam ) {
            DialogMsgOnlyNoTitle fragment = new DialogMsgOnlyNoTitle();
            Bundle args = new Bundle();
            args.putInt(MESSAGE_INT, messageIntParam);
            fragment.setArguments(args);
            return fragment;
        }

        private boolean readBundle(Bundle bundle) {
            if (bundle != null) {
                messageInt = bundle.getInt(MESSAGE_INT);
                return true;
            }
            else {
                return false;       //  should only happen if default constructor called.
            }
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (readBundle(getArguments())) {
                if (DO_LOGGING) {
                    Log.i(TAG,"DialogMsgOnlyNoTitle.onCreateDialog readBundle() ok  ***************"); // cannot call logInfo() because in static class.
                }
            }

            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            CharSequence message = getResources().getText(messageInt);
            builder.setMessage(message)
                    .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (DO_LOGGING) {
                                Log.i(TAG, "DialogMsgOnlyNoTitle OK ***************");
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
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

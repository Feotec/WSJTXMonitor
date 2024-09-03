package com.feotec.wsjt_xmonitor;

import android.app.Activity;
import android.content.Context;

public class DebugUtils {

    static final boolean loggingMainActivity = false;
    static final boolean loggingDisplayAll = false;
    static final boolean loggingBaseActivity = false;
    static final boolean loggingDialogAbout = false;
    static final boolean loggingDialogEULA = false;
    static final boolean loggingDialogPort = false;
    static final boolean loggingDialogSetup = false;
    static final boolean loggingDialogSetupDetails = false;
    static final boolean loggingUDPService = false;
    static final boolean loggingHelperStationList = false;
    static final boolean loggingHelperCountriesList = false;
    static final boolean loggingDialogNewFeatures = false;
    static final boolean loggingDialogPrefixCallsign = false;
    static final boolean loggingDialogFilterAlerts = false;
    static final boolean loggingFiltersInterface = false;
    static final boolean loggingAlertsInterface = false;
    static final boolean loggingAlerts = false;
    static final boolean loggingFilters = false;
    static final boolean loggingTouchableTextView = false;
    static final boolean loggingDialogMinSignal = false;
    static final boolean loggingDialogAlertSettings = false;
    static final boolean loggingDialogSorting = false;
    static final boolean loggingDialogReplyPreHelp = false;
    static final boolean loggingDialogReplyHelp = false;
    static final boolean loggingMapsActivity = false;
    static final boolean loggingDialogSettings = false;
    static final boolean loggingDialogKmMiles = false;
    static final boolean loggingDialogClearScreenWSJTX = false;
    static final boolean loggingDialogClearScreen = false;
    static final boolean loggingDialogDontShowAgain = false;
    static final boolean loggingDialogStatus = false;
    static final boolean loggingDialogSetRxFreq = false;
    static final boolean loggingDialogIPChanged = false;
    static final boolean loggingDialogSetRxFreqHelp = false;

    //static final boolean includeMapping = true;

    //
    //  This code is for logging to file on Android device.  Search for WRITELOGSTOANDROIDFILE across files in order to remove this feature.
    //  The public functions below are zeroed out.  Note that MainActivity.onRequestPermissionsResult() is a callback that needs to remain in
    //  MainActivity but is never used in release version.  It can be commented out.
    //
    //  IT WOULD BE BEST to remove WRITE_EXTERNAL_STORAGE from manifest permissions so that Google Play doesn't say this permission is needed.
    //

    static final int REQUEST_PERMISSIONS_STORAGE_REQUEST_CODE = 35;

    static void doStoragePermissions( Context context, Activity activity ) {
    }

    static void createAndInitAndroidLogFile() {
    }

    static void writeToAndroidLogFile( String tag, String message ) {
    }

    static void closeAndroidLogFile() {
    }

    static boolean expiration() {
        return false;
    }


}

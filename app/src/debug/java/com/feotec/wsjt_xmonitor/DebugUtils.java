package com.feotec.wsjt_xmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import android.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DebugUtils {

    //
    //      This is for DEBUG build
    //

    //  All objects except hashedDeviceID are package-private
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

    //static final boolean includeMapping = false;

    //
    //  This code is for logging to file on Android device.  Search for WRITELOGSTOANDROIDFILE across files in order to remove this feature.
    //  The public functions:
    //          doStoragePermissions() is called from MainActivity.onCreate()
    //          createAndInitAndroidLogFile() is called from doStoragePermissions() if permissions are already granted or from
    //              MainActivity.onRequestPermissionsResult() when permissions are granted.
    //          writeToAndroidLogFile() is called from each logInfo() file in every .java class
    //          closeAndroidLogFile() can be called from MainActivity.onPause(), .onStop(), and .onDestroy().  It is usually only actually called
    //              from .onDestroy().
    //  All this can be turned off by setting androidFileLoggingEnabled to false.
    //
    //  DON'T FORGET TO ENABLE PERMISSIONS IN MANIFEST FILE. *********************
    //
    //  In addition, note that MainActivity.onRequestPermissionsResult() is a callback that needs to remain in MainActivity but is never used
    //  if this feature is turned off, such as when building release versions.
    //
    //  IMPORTANT - there are no provisions for rotating the screen while this is running (there is no RetainedFragment to store androidLogFile PrintWriter).
    //
    //  Another note is that when this is started and it asks permissions then that is time when it can't be recording log messages.  So it has to be
    //      started a second time to capture things like UDPService.onCreate().
    //

    static final private boolean androidFileLoggingEnabled = false;              //  false to turn this all off.

    static final int REQUEST_PERMISSIONS_STORAGE_REQUEST_CODE = 35;
    static final private boolean loggingDebugUtils = false;
    static private PrintWriter androidLogFile = null;
    static private boolean writingToAndroidLogFile = false;                     //  set true only after Android log file has been successfully created.
    static private final String TAG = DebugUtils.class.getSimpleName();

    // Returns the current state of the WRITE_EXTERNAL_STORAGE permissions.
    static private boolean checkPermissionsStorage( Context context ) {
        return PermissionChecker.PERMISSION_GRANTED == PermissionChecker.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    //  Checks that external memory is present and writeable.
    static private boolean externalMemoryPresentAndWriteable() {
        boolean returnValue = false;
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            returnValue = true;
        }
        // Android Studio says MEDIA_MOUNTED_READ_ONLY is always false.
        //if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
        //    returnValue = false;
        //}
        return returnValue;
    }

    static private void requestPermissions( Activity context ) {
        ActivityCompat.requestPermissions(context,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                REQUEST_PERMISSIONS_STORAGE_REQUEST_CODE);
    }

    static void doStoragePermissions( Context context, Activity activity ) {

        if (!androidFileLoggingEnabled) {
            return;
        }

        //  See if permissions have already been granted
        if (!checkPermissionsStorage( context )) {
            //  If not check that memory is present and writable.
            if (externalMemoryPresentAndWriteable()) {
                requestPermissions( activity );
            }
        }
        else {
            if (externalMemoryPresentAndWriteable()) {
                createAndInitAndroidLogFile();
            }
        }
    }

    private static void createAndInitAndroidLogFile() {
        //  Tom - Environment.getExternalStoragePublicDirectory() returns the path to the Documents folder.
        File path = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS );
        //  Tom - this new file object will be used to create a file.  It will be created in the path specified by the
        //      File object path (/storage/emulated/0/Documents/).  It's not actually created until the Printwriter instance is created below.
        File fileAndroidLogFile = new File( path, "WSJTXMonitorLog.txt");
        try {
            //  Tom - according to the Environment class docs, the directory may not exist until I call mkdirs() method.  If I call
            //      path.mkdirs() it will return null because the directory already exists.
            //  Also note that if the directory already exists then mkdirs() will return false but an exception will not occur.
            if (!path.mkdirs()) {
                logInfo("Directory not created. ******");
            }
            //  Tom - now finally create the file.  I want to use PrintWriter object because it has formatted output.  The File
            //      object named file represents a file named TestLong.kml in directory /storage/emulated/0/Documents/.
            androidLogFile = new PrintWriter( fileAndroidLogFile );
            writingToAndroidLogFile = true;
            logInfo("createAndInitAndroidLogFile() file created ***** ");
            //mRetainedFragment.setData( outputStreamOncePerSecond );
        }
        catch (Exception ex) {
            logInfo("Exception in createAndInitFile(). "+ex.getMessage()+" ******");
            ex.printStackTrace();
        }
    }

    static void writeToAndroidLogFile( String tag, String message ) {
        if (writingToAndroidLogFile) {
            androidLogFile.write(System.currentTimeMillis()+" "+tag+": "+message + "\n");
            androidLogFile.flush();
        }
    }

    static void closeAndroidLogFile() {
        if (androidLogFile != (PrintWriter) null) {
            logInfo("closeAndroidLogFile() *****");
            androidLogFile.close();
        }
    }

    //  Returns true if expired, false otherwise.
    static boolean expiration() {
        //  Get current date
        Calendar currentCalendar = Calendar.getInstance(Locale.ENGLISH);
        Date currentDate = currentCalendar.getTime();
        String currentTimeSting = currentDate.toString();

        //  Get expiration date - IMPORTANT - the month begins with 0, Jan == 0, Feb == 1, etc.
        Calendar expirationCalendar = Calendar.getInstance(Locale.ENGLISH);
        expirationCalendar.set(2024,11,25,21,15,0);
        Date expirationDate = expirationCalendar.getTime();
        String expirationTimeString = expirationDate.toString();

        boolean expired = expirationDate.before(currentDate);

        logInfo(currentTimeSting+" - "+expirationTimeString+" - expired: "+expired+" *****");
        return expired;
        //return false;
    }

    private static void logInfo( String message ) {
        if (loggingDebugUtils) {
            Log.i(TAG,message);
            writeToAndroidLogFile(TAG,message);
        }
    }

}

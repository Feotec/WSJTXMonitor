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


import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

class WSJTXUtils {

    private static final String KEY_SORTING_DONT_SHOW_AGAIN = "key_sorting_dont_show_again";
    private static final String KEY_VERSION_INTEGER = "key_version_integer";
    private static final String KEY_REPLY_HELP_DONT_SHOW_AGAIN = "key_reply_help_dont_show_again";
    private static final String KEY_DATA_NEVER_FLOWED = "key_data_never_flowed";
    private static final String KEY_DATAGRAM_PORT = "key_datagram_port";
    private static final String KEY_IP_ADDRESS_HASH_CODE = "key_ip_address_hash_code";
    private static final String KEY_KM_MILES = "key_km_miles";
    private static final String KEY_KM_MILES_DONT_SHOW_AGAIN = "key_km_miles_dont_show_again";
    private static final String KEY_CLEAR_SCREEN_FROM_WSJTX = "key_clear_screen_from_WSJTX";
    private static final String KEY_CLEAR_SCREEN_DONT_SHOW_AGAIN = "key_clear_screen_dont_show_again";
    private static final String KEY_FINISH_INVOKED = "key_finish_invoked";
    private static final String KEY_BOTH_APPS_DONT_SHOW_AGAIN = "key_both_apps_dont_show_again";
    private static final String KEY_DATA_COLLECTION = "key_data_collection";

    //  counters
    private static final String KEY_NUM_STARTUPS = "key_num_startups";
    private static final String KEY_NUM_FILTER_USES = "key_num_filter_uses";
    private static final String KEY_NUM_ALERT_USES = "key_num_alert_uses";
    private static final String KEY_NUM_PACKETS = "key_num_packets";
    private static final String KEY_NUM_SET_RX = "key_num_set_rx";

    //  With no modifier these methods are all package-private.

    static boolean getIsFinishInvoked( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_FINISH_INVOKED, false);
    }

    static void setIsFinishInvoked( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_FINISH_INVOKED, param);
        editor.apply();
    }

    static boolean getKmMilesDontShowAgain( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_KM_MILES_DONT_SHOW_AGAIN, false);
    }

    static void setKmMilesDontShowAgain( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_KM_MILES_DONT_SHOW_AGAIN, param);
        editor.apply();
    }

    static boolean getClearScreenDontShowAgain( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_CLEAR_SCREEN_DONT_SHOW_AGAIN, false);
    }

    static void setClearScreenDontShowAgain( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_CLEAR_SCREEN_DONT_SHOW_AGAIN, param);
        editor.apply();
    }

    static boolean getBothAppsDontShowAgain( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_BOTH_APPS_DONT_SHOW_AGAIN, false);
    }

    static void setBothAppsDontShowAgain( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_BOTH_APPS_DONT_SHOW_AGAIN, param);
        editor.apply();
    }

    static boolean getSortingDontShowAgain( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_SORTING_DONT_SHOW_AGAIN, false);
    }

    static void setSortingDontShowAgain( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_SORTING_DONT_SHOW_AGAIN, param);
        editor.apply();
    }

    static boolean getClearScreenFromWSJTX( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_CLEAR_SCREEN_FROM_WSJTX, false);
    }

    static void setClearScreenFromWSJTX( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_CLEAR_SCREEN_FROM_WSJTX, param);
        editor.apply();
    }

    static boolean getMilesNotKm( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_KM_MILES, false);
    }

    static void setMilesNotKm( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_KM_MILES, param);
        editor.apply();
    }

    static int getAppVersionInteger( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getInt(KEY_VERSION_INTEGER, 0);
    }

    static void setAppVersionInteger( Context context ) {
        int versionInteger = getHardcodedVersionCode( context );
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_VERSION_INTEGER, versionInteger);
        editor.apply();
    }

    static boolean getReplyHelpDontShowAgain( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_REPLY_HELP_DONT_SHOW_AGAIN, false);
    }

    static void setReplyHelpDontShowAgain( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_REPLY_HELP_DONT_SHOW_AGAIN, param);
        editor.apply();
    }

    static boolean getDataNeverFlowed( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getBoolean(KEY_DATA_NEVER_FLOWED, true);
    }

    static void setDataNeverFlowed( Context context, boolean param ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putBoolean(KEY_DATA_NEVER_FLOWED, param);
        editor.apply();
    }

    static int getDatagramPort( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getInt(KEY_DATAGRAM_PORT, 2237);
    }

    static void setDatagramPort( Context context, int port ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_DATAGRAM_PORT, port);
        editor.apply();
    }

    static int getIpHashCode( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return tSharedPref.getInt(KEY_IP_ADDRESS_HASH_CODE, 0);
    }

    static void setIpHashCode( Context context, int hashCode ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_IP_ADDRESS_HASH_CODE, hashCode);
        editor.apply();
    }

    static int getAndUpdateNumberOfPackets(Context context, int newPackets ) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        int numPackets = tSharedPref.getInt(KEY_NUM_PACKETS, 0);
        if (newPackets > 0) {
            numPackets += newPackets;
            SharedPreferences.Editor editor = tSharedPref.edit();
            editor.putInt(KEY_NUM_PACKETS, numPackets);
            editor.apply();
        }
        return numPackets;
    }

    static int getAndBumpNumberOfStartups( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        int numStartups = tSharedPref.getInt(KEY_NUM_STARTUPS, 0);
        numStartups++;
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_NUM_STARTUPS, numStartups);
        editor.apply();
        return numStartups;
    }

    static int getAndBumpNumberOfSetRxFreq( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        int numSetRxFreq = tSharedPref.getInt(KEY_NUM_SET_RX, 0);
        numSetRxFreq++;
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_NUM_SET_RX, numSetRxFreq);
        editor.apply();
        return numSetRxFreq;
    }

    static int getAndBumpNumberOfFilterUses( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        int numFilters = tSharedPref.getInt(KEY_NUM_FILTER_USES, 0);
        numFilters++;
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_NUM_FILTER_USES, numFilters);
        editor.apply();
        return numFilters;
    }

    static int getAndBumpNumberOfAlertUses( Context context) {
        SharedPreferences tSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        int numAlerts = tSharedPref.getInt(KEY_NUM_ALERT_USES, 0);
        numAlerts++;
        SharedPreferences.Editor editor = tSharedPref.edit();
        editor.putInt(KEY_NUM_ALERT_USES, numAlerts);
        editor.apply();
        return numAlerts;
    }

    //
    //
    //  End of SharedPreferences
    //
    //

    @SuppressWarnings("deprecation")
    static int getHardcodedVersionCode( Context context ) {
        int versionInteger;
        try {
            //long versionLong = context.getPackageManager().getPackageInfo( context.getPackageName(), 0 ).getLongVersionCode();
            //  The above line is preferred for API 28+.  I've done the suppressed warnings instead.
            versionInteger = context.getPackageManager().getPackageInfo( context.getPackageName(), 0 ).versionCode;
        }
        catch (Exception ex) {
            versionInteger = 0;
        }
        return versionInteger;
    }

    static String getHardcodedVersionString( Context context ) {
        String versionName;
        try {
            versionName = context.getPackageManager().getPackageInfo( context.getPackageName(), 0 ).versionName;
        }
        catch (Exception ex) {
            versionName = context.getResources().getString(R.string.unknown);
        }
        return versionName;
    }


}

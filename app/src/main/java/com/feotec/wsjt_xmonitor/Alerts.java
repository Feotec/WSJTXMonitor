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
import android.text.SpannableStringBuilder;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

class Alerts implements Serializable {
    private static final String TAG = Alerts.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingAlerts;

    private static final long serialVersionUID = 6415360044511665737L;

    static final int NO_MIN_SIGNAL = -9999;     //  package-private

    private static String channelIdSilent;
    private static String channelIdVibrate;
    private static String channelIdSound;
    private static String channelIdVibrateSound;

    //  These are the elements that make up the alert
    private ArrayList<String> prefixCallsigns = null;
    private ArrayList<String> countries = null;
    private ArrayList<String> continents = null;
    private boolean alertOnMyCallsign;
    private boolean alertOnAll;
    private String channelIdCurrent;
    private boolean doingSound;
    private boolean doingVibrate;
    private int minSignal;
    private boolean rememberStation;
    private boolean initialized;

    //  This list is ignored by equals(), for obvious reasons.  It is also not filled in by either construtor (the one called by copyOf() could).  The
    //      result of all this is that when an Alert is created AND WHEN IT IS RECALLED this list will be cleared.
    private LinkedList<String> stationList = new LinkedList<>();

    //  Called at startup.  A static class to avoid passing a Context variable around into each initializer.
    static void initializeChannelIds( Context thisContext ) {
        //  Initialize the notification channel ID strings.
        channelIdSilent = thisContext.getResources().getString(R.string.notification_channel_id);
        channelIdVibrate = thisContext.getResources().getString(R.string.notification_channel_vib_id);
        channelIdSound = thisContext.getResources().getString(R.string.notification_channel_sound_id);
        channelIdVibrateSound = thisContext.getResources().getString(R.string.notification_channel_vibsound_id);
    }

    //  Returns the channel ID for the two channels that contain sound.  Returns null string otherwise.s
    static String getChannelIdForSound( int index ) {
        switch (index) {
            case 0:
                return channelIdVibrateSound;
            case 1:
                return channelIdSound;
            default:
                return "";
        }
    }

    Alerts( boolean alertOnMyCallsignParam,
            boolean alertOnAllParam,
            boolean[] checkedItemsContinent,        // = { false, false, false, false, false, false };
            CharSequence[] charSeqMajorCountries,   // = { "Argentina", "Australia", "Austria", "Brazil", "Britain", "Canada", "China", "Chile", "Denmark", "Finland",
                                                    //     "France", "Germany", "India", "Indonesia", "Italy", "Japan", "Mexico", "New Zealand", "Norway", "Poland", "Russia",
                                                    //     "Slovenia", "South Africa", "South Korea", "Spain", "Taiwan", "Thailand", "Ukraine", "USA", "Venezuala" };
            boolean[] checkedItemsMajorCountries,   // = { false, false, false, false, false, false, false, false, false, false, false, false,
                                                    //     false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
            CharSequence[] charSeqAllCountries,     // initialized in FilterAlertsInterface.java and passed on here ....
            boolean[] checkedItemsAllCountries,     // .... both *AllCountries[] MAY BE NULL.
            ArrayList<String> prefixCallsignItems,  // .... and this MAY BE NULL)
            boolean[] checkedItemsSoundVibration,   //  elements of checkedItemsSoundVibration[]: [0] Sound and Vibration, [1] Sound only, [2] Vibration only, [3] No sound or vibration
            int minSignalParam,
            boolean rememberStationParam
    )  {
        //  Create a static arrays for continent abbreviations.
        String[] continentAbbreviations = { "AF", "AS", "EU", "NA", "SA", "OC" };

        initialized = false;

        alertOnMyCallsign = alertOnMyCallsignParam;
        alertOnAll = alertOnAllParam;

        if (!alertOnAll) {                      //  if alertOnAll is true then don't bother initializing the location and callsign data elements.  They are meaningless.

            //  Prefixes and callsigns ...
            if (prefixCallsignItems != null) {
                if (prefixCallsignItems.size() > 0) {
                    prefixCallsigns = new ArrayList<>(prefixCallsignItems);   //  Simple line, without sorting.  Sorting is done later, when needed.
                }
            }

            //  Continents ....
            if (checkedItemsContinent.length != continentAbbreviations.length) {
                return;     // error, return with initialized == false, lengths of checkedItemsContinent should be the same as continentAbbreviations
            }
            for (int iii = 0; iii < checkedItemsContinent.length; iii++) {
                if (checkedItemsContinent[iii]) {
                    if (continents == null) {
                        continents = new ArrayList<>();
                    }
                    continents.add(continentAbbreviations[iii]);
                }
            }

            //  Countries ...
            if (checkedItemsMajorCountries.length != charSeqMajorCountries.length) {
                return;     // error, return with initialized == false
            }
            for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                if (checkedItemsMajorCountries[iii]) {
                    if (countries == null) {
                        countries = new ArrayList<>();
                    }
                    countries.add(charSeqMajorCountries[iii].toString());
                }
            }
            if ((checkedItemsAllCountries != null) && (charSeqAllCountries != null)) {
                for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                    if (checkedItemsAllCountries[iii]) {
                        if (countries == null) {
                            countries = new ArrayList<>();
                        }
                        //  check for duplicates
                        if (!countries.contains(charSeqAllCountries[iii].toString())) {
                            countries.add(charSeqAllCountries[iii].toString());
                        }
                    }
                }
            }
        }

        minSignal = minSignalParam;
        rememberStation = rememberStationParam;

        //  elements of checkedItemsSoundVibration[] [0] Sound and Vibration, [1] Sound only, [2] Vibration only, [3] No sound or vibration
        if (checkedItemsSoundVibration[0]) {
            channelIdCurrent = channelIdVibrateSound;
            doingSound = true;
            doingVibrate = true;
        } else if (checkedItemsSoundVibration[1]) {
            channelIdCurrent = channelIdSound;
            doingSound = true;
            doingVibrate = false;
        } else if (checkedItemsSoundVibration[2]) {
            channelIdCurrent = channelIdVibrate;
            doingSound = false;
            doingVibrate = true;
        } else if (checkedItemsSoundVibration[3]) {
            channelIdCurrent = channelIdSilent;
            doingSound = false;
            doingVibrate = false;
        } else {
            return;
        }

        initialized = true;
        logInfo("Alerts constructor public");
    }

    //  Second constructor.  Used for copyOf() below.
    private Alerts( boolean alertOnMyCallsignParam, boolean alertOnAllParam, ArrayList<String> prefixCallsignsParam, ArrayList<String> countriesParam,
                    ArrayList<String> continentsParam, String channelIdCurrentParam, boolean doingSoundParam, boolean doingVibrateParam, int minSignalParam,
                    boolean rememberStationParam, boolean initializedParam ) {
        if (initializedParam) {
            initialized = true;

            alertOnMyCallsign = alertOnMyCallsignParam;
            alertOnAll = alertOnAllParam;
            channelIdCurrent = channelIdCurrentParam;
            doingSound = doingSoundParam;
            doingVibrate = doingVibrateParam;

            minSignal = minSignalParam;
            rememberStation = rememberStationParam;

            prefixCallsigns = null;
            if (prefixCallsignsParam != null) {
                prefixCallsigns = new ArrayList<>(prefixCallsignsParam);
            }

            countries = null;
            if (countriesParam != null) {
                countries = new ArrayList<>(countriesParam);
            }

            continents = null;
            if (continentsParam != null) {
                continents = new ArrayList<>(continentsParam);
            }
        }

        logInfo("Alerts constructor private");
    }

    boolean getAlertInitialized() {
        return initialized;
    }

    ArrayList<String> getAlertPrefixCallsigns() {
        return prefixCallsigns;
    }

    ArrayList<String> getAlertContinents() {
        return continents;
    }

    ArrayList<String> getAlertCountries() {
        return countries;
    }

    boolean getAlertOnMyCallsign() { return alertOnMyCallsign; }

    boolean getAlertAlertOnAll() {
        return alertOnAll;
    }

    boolean getAlertRememberStation() {
        return rememberStation;
    }

    int getAlertMinSignal() {
        return minSignal;
    }

    boolean getAlertDoingVibrate() {
        return doingVibrate;
    }

    boolean getAlertDoingSound() {
        return doingSound;
    }

    Alerts copyOf() {
        return new Alerts( alertOnMyCallsign, alertOnAll, prefixCallsigns, countries, continents, channelIdCurrent, doingSound, doingVibrate, minSignal, rememberStation, initialized );
    }

    //  This method is called from AlertsInterface.java when a previous Alert is recalled.  It calls checkRemovedCountries() (a static method in Filters.java).
    //      checkRemovedCountries() will check if the filter contains countries that have been removed from countries<> and substitute the new names.
    void updateCountryNames( Context thisContext ) {
        if (Filters.checkRemovedCountries( thisContext, countries )) {
            logInfo("Filters.updateCountryNames() ERROR - no match for all countries in filter!  *******");
        }
    }

    String describeAlert( Context thisContext ) {
        final int MAX_ALERT_DESCRIPTION_LENGTH = 55;

        //  The user may select so many countries that this string will become meaningless if I just truncate it and append "..." at the end.  So I want to append
        //      a string that says "...+25 more" at the end of the countries list.  That way if they then remove one country the description will at least look
        //      different.
        //  In order to do this I need to build the second part of the description first (in ssb2).  That way I know its length.  Then, as I'm adding countries
        //      to the description I can stop when the length of ssb+ssb2 > MAX_ALERT_DESCRIPTION_LENGTH.  I still retain the older code (at the bottom) which
        //      checks again if length > MAX_ALERT_DESCRIPTION_LENGTH.  This is just in case other elements besides countries made ssb+ssb2 too long.
        //  Note - in Spanish the length of ssb2 can get up to 34 characters.

        SpannableStringBuilder ssb = new SpannableStringBuilder("");
        SpannableStringBuilder ssb2 = new SpannableStringBuilder("");

        //  Sound and vibration
        if ( (doingSound) && (doingVibrate) ) {
            ssb2.append( thisContext.getResources().getString(R.string.alert_description_sound) ).
                    append("-").append( thisContext.getResources().getString(R.string.alert_description_vibrate) );
        }
        else if (doingSound) {
            ssb2.append( thisContext.getResources().getString(R.string.alert_description_sound) );
        }
        else if (doingVibrate) {
            ssb2.append( thisContext.getResources().getString(R.string.alert_description_vibrate) );
        }
        else {
            ssb2.append( thisContext.getResources().getString(R.string.alert_description_silent) );
        }
        ssb2.append(", ");

        //  Minimum signal
        if (minSignal != NO_MIN_SIGNAL) {
            ssb2.append(" >").append(String.format(Locale.US, "%d dB, ", minSignal));
        }

        //  Remember station
        if (!rememberStation) {
            ssb2.append(" ").append( thisContext.getResources().getString(R.string.alert_description_remember_no) ).append(", ");
        }

        //  My callsign
        if (alertOnMyCallsign) {
            ssb.append( thisContext.getResources().getString(R.string.alert_description_alert_on_my_call) ).append(", ");
        }
        //  Countries & callsigns
        if (alertOnAll) {
            ssb.append( thisContext.getResources().getString(R.string.alert_description_alert_on_all) ).append(", ");
        }
        else {
            if (prefixCallsigns != null) {
                for (int iii = 0; iii < prefixCallsigns.size(); iii++) {
                    ssb.append(prefixCallsigns.get(iii)).append(", ");
                }
            }
            if (continents != null) {
                for (int iii = 0; iii < continents.size(); iii++) {
                    ssb.append(continents.get(iii)).append(", ");
                }
            }
            if (countries != null) {
                for (int iii = 0; iii < countries.size(); iii++) {
                    ssb.append(countries.get(iii)).append(", ");
                    if (ssb.length()+ssb2.length() > MAX_ALERT_DESCRIPTION_LENGTH) {    //  if combined length is too long ...
                        if (iii+1 < countries.size()) {                                 //  ... and if there are more countries to add
                            String extraString = String.format(Locale.US,"...+%d %s, ",
                                                        countries.size()-(iii+1),thisContext.getResources().getString(R.string.more));
                            int extraLength = extraString.length();
                            if ( ( ( MAX_ALERT_DESCRIPTION_LENGTH - ssb2.length() ) - extraLength ) > 5 ) {
                                ssb.delete((MAX_ALERT_DESCRIPTION_LENGTH - ssb2.length()) - extraLength, ssb.length());
                                ssb.append(extraString);
                                break;
                            }
                        }
                    }
                }
            }
        }

        ssb.append(ssb2);
        ssb.delete(ssb.length() - 2, ssb.length());    //  remove last comma
        if (ssb.length() > MAX_ALERT_DESCRIPTION_LENGTH) {
            ssb.delete(MAX_ALERT_DESCRIPTION_LENGTH - 3, ssb.length());
            ssb.append("...");
        }
        return ssb.toString();
    }

    boolean equals( Alerts otherAlert ) {
        if (initialized != otherAlert.initialized) {
            return false;
        }

        if (alertOnMyCallsign != otherAlert.alertOnMyCallsign) {
            return false;
        }
        if (alertOnAll != otherAlert.alertOnAll) {
            return false;
        }
        if (!channelIdCurrent.equals(otherAlert.channelIdCurrent)) {
            return false;
        }
        if (doingSound != otherAlert.doingSound) {
            return false;
        }
        if (doingVibrate != otherAlert.doingVibrate) {
            return false;
        }
        if (minSignal != otherAlert.minSignal) {
            return false;
        }
        if (rememberStation != otherAlert.rememberStation) {
            return false;
        }

        if ( (continents != null) && (otherAlert.continents != null) ) {       // if both are not null ...
            if (!continents.equals(otherAlert.continents)) {                   //  ... then use .equals() method
                return false;
            }
        }
        else if ( (continents != null) || (otherAlert.continents != null) ) {  // One or both are null.  It only matches if both are null.  If either are not ...
            return false;                                                       //  ... then return false.
        }

        if ( (countries != null) && (otherAlert.countries != null) ) {
            if (!countries.equals(otherAlert.countries)) {
                return false;
            }
        }
        else if ( (countries != null) || (otherAlert.countries != null) ) {
            return false;
        }

        if ( (prefixCallsigns != null) && (otherAlert.prefixCallsigns != null) ) {
            if (!sortArrayList(prefixCallsigns).equals(sortArrayList(otherAlert.prefixCallsigns))) {
                return false;
            }
        }
        else if ( (prefixCallsigns != null) || (otherAlert.prefixCallsigns != null) ) {
            return false;
        }

        return true;
    }

    private ArrayList<String> sortArrayList( ArrayList<String> list ) {
        //  Have to convert parameter list from ArrayList<> to String[].  Then sort.  Then add to new ArrayList in return statement.
        //  Note - it might have been better to do the sorting above, when the array if formed.  That way it would always be sorted.  The only advantage here is that
        //      the user will see (when listing previous filters) the prefixCallsign elements in the same order that it was typed in.
        String[] tempArray = new String[ list.size()];
        tempArray = list.toArray( tempArray );
        Arrays.sort(tempArray);
        return new ArrayList<>(Arrays.asList(tempArray));
    }

    String getCurrentChannelID() {
        if (initialized) {
            return channelIdCurrent;
        }
        else {
            return channelIdSilent;
        }
    }

    boolean getSound() {
        if (initialized) {
            return doingSound;
        }
        else {
            return false;   //  if not initialized then keep sound off.
        }
    }

    boolean getVibrate() {
        if (initialized) {
            return doingVibrate;
        }
        else {
            return false;   //  if not initialized then keep vibration off.
        }
    }

    //  This method returns true if the callsign-country-continent should generate an alert.
    boolean doAlert( String callsign, String country, String continent, int SNR, boolean isMyCall ) {
        boolean returnValue = false;

        if (!initialized) {         //  should never happen but if it does then ...
            return false;           //  ... no alert.
        }

        if (SNR < minSignal) {      //  If minSignal is on and signal is less than that then it doesn't matter what callsign, country, or continent are.
            return false;
        }

        if (alertOnMyCallsign) {
            if (isMyCall) {
                return true;        //  if alertOnMyCallsign == true and isMyCall == true then return true.  The rest doesn't matter.
            }
        }

        if (alertOnAll) {
            return doAlertHelp( callsign );            //  always alert unless already in list.
        }

        if (prefixCallsigns != null) {
            for (int iii = 0; iii < prefixCallsigns.size(); iii++) {
                String search = prefixCallsigns.get(iii);
                if (callsign.startsWith(search)) {
                    returnValue = true;
                }
            }
        }

        if (continents != null) {
            for (int iii = 0; iii < continents.size(); iii++) {
                String search = continents.get(iii);
                if (continent.contains(search)) {       // have to use .contains() because some continent strings have more than one, "EU,AS" for Russia.
                    returnValue = true;
                }
            }
        }

        if ( (countries != null) && (!returnValue) ) {
            for (int iii = 0; iii < countries.size(); iii++) {
                String search = countries.get(iii);
                if (country.equals(search) ) {          //  for countries use .equals() cuz they have to match exactly, distinguish between "New Zealand" and "New Zealand - Niue"
                    returnValue = true;
                }
            }
        }

        if (returnValue) {
            returnValue = doAlertHelp( callsign );
        }

        return returnValue;
    }

    //  This is called from doAlert() above.  It was made into a method because it was needed in two places within doAlert().  The return value
    //      is assigned to doAlert().returnValue.  It's called when the alert is valid.  It's job is to see if the station has already been
    //      alerted on (if it is already in stationList) and, if rememberStation == true, disable the alert on this station.  If it is not
    //      been alerted on (if not already in stationList) then add it to the linked list.
    //  IMPORTANT - IT SHOULD ONLY BE CALLED WHEN THE Alert IS VALID (when doAlert().returnValue == true).
    private boolean doAlertHelp( String callsign ) {
        boolean returnValue = true;         // only called when Alert is valid.  So default is to return true, keeping the current status.
        if (rememberStation) {
            //  If gotten this far and it still looks like a good alert then check stationList.
            Iterator iter = stationList.iterator();
            boolean alreadyInList = false;
            while (iter.hasNext()) {
                String nextCallsign = (String)iter.next();
                if (callsign.equals(nextCallsign)) {
                    //  If callsign is already in list then no alert (returnValue = false) and quit loop.
                    alreadyInList = true;
                    returnValue = false;
                    logInfo("doAlert() "+callsign+" already in list *****");
                    break;
                }
            }
            if (!alreadyInList) {
                //  If not already in list then add it.
                stationList.add(callsign);
                logInfo("doAlert() added "+callsign+" *****");
            }
        }
        return returnValue;
    }

    //  This is called from UDPService.  The CharSequence that it returns is used to build the notification string.
    CharSequence doAlertString( String callsign, int SNR, String country, String grid) {
        return String.format(Locale.US,"  %s    %3d dB    %s  %s", callsign, SNR, country, grid );
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }

}

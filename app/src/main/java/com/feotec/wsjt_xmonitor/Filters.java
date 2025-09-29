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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

class Filters implements Serializable {
    private static final boolean DO_LOGGING = DebugUtils.loggingFilters;
    private static final String TAG = Filters.class.getSimpleName();

    private static final long serialVersionUID = 6415360044511665736L;

    //  These five parameters are what makes the filter.
    private ArrayList<String> prefixCallsigns = null;
    private ArrayList<String> countries = null;
    private ArrayList<String> continents = null;
    private boolean includeFilter;
    private boolean initialized;

    //  This is the first of two constructors.  This one is called when a new Filters object is created by FiltersInterface, the user interface.  It is passed
    //      the arrays from FiltersInterface.
    //
    //      FiltersInterface.charSeqMajorCountries[] and FiltersInterface.checkedItemsMajorCountries[] are both passed.  It then runs through
    //          checkedItemsMajorCountries[] and, if checked, it copies that item of charSeqMajorCountries[] to countries<>.  It then does the same with
    //          FiltersInterface.charSeqAllCountries[] and .checkedItemsAllCountries[]. appending these country names to countries<>.
    //
    //      In the case of continents, FiltersInterface.checkedItemsContinent[] is passed but the continent names (which won't change) are hardcoded here.
    //          The results are placed in continents<>.
    //
    //      prefixCallsignItems is already an ArrayList<String> so the ArrayList constructor is used to copy the contents of FiltersInterface.prefixCallsignItems
    //          to prefixCallsigns.
    //
    Filters( boolean[] checkedItemsFilterType,      // = { false, false, false, false  };
             boolean[] checkedItemsContinent,       // = { false, false, false, false, false, false };
             CharSequence[] charSeqMajorCountries,  // = { "Argentina", "Australia", "Austria", "Brazil", "Britain", "Canada", "China", "Chile", "Denmark", "Finland",
                                                    //     "France", "Germany", "India", "Indonesia", "Italy", "Japan", "Mexico", "New Zealand", "Norway", "Poland", "Russia",
                                                    //     "Slovenia", "South Africa", "South Korea", "Spain", "Taiwan", "Thailand", "Ukraine", "USA", "Venezuala" };
             boolean[] checkedItemsMajorCountries,  // = { false, false, false, false, false, false, false, false, false, false, false, false,
                                                    //     false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
             CharSequence[] charSeqAllCountries,    // initialized in FiltersInterface.java and passed on here ....
             boolean[] checkedItemsAllCountries,    // .... both of these MAY BE NULL.
             ArrayList<String> prefixCallsignItems )//..... and this MAY BE NULL
    {
        //  Create a static arrays for continent abbreviations.
        String[] continentAbbreviations = { "AF", "AS", "EU", "NA", "SA", "OC" };

        //  initialized is an indication of success
        initialized = false;

        //  Filter type ...
        //      CharSequence[] charSeqFilterType = { "New Include Filter", "New Exclude Filter", "Reuse Previous Filters" };
        if (checkedItemsFilterType[0]) {
            includeFilter = true;
        }
        else if (checkedItemsFilterType[1]) {
            includeFilter = false;
        }
        else {
            //  invalid, one or the other must be selected
            return;     //  return with initialized == false
        }

        //  Prefixes and callsigns ...
        if (prefixCallsignItems != null) {
            if (prefixCallsignItems.size() > 0) {
                prefixCallsigns = new ArrayList<>(prefixCallsignItems);   //  Simple line, without sorting.
                /*
                //  Sort prefixCallsign.  Have to convert prefixCallsignItems from ArrayList<> to Stirng[].  Then sort.  Then add to prefixCallsigns.
                String[] tempArray = new String[ prefixCallsignItems.size()];
                tempArray = prefixCallsignItems.toArray( tempArray );
                Arrays.sort(tempArray);
                prefixCallsigns = new ArrayList<>();
                for (int iii = 0; iii < tempArray.length; iii++) {
                    prefixCallsigns.add( tempArray[iii] );
                }
                */
            }
        }

        //  Continents ....
        if ( checkedItemsContinent.length != continentAbbreviations.length ) {
            return;     // error, return with initialized == false, lengths of checkedItemsContinent should be the same as continentAbbreviations
        }
        for (int iii = 0; iii < checkedItemsContinent.length; iii++) {
            if (checkedItemsContinent[iii]) {
                if (continents == null) {
                    continents = new ArrayList<>();
                }
                continents.add( continentAbbreviations[iii] );
            }
        }

        //  Countries ...
        if ( checkedItemsMajorCountries.length != charSeqMajorCountries.length ) {
            return;     // error, return with initialized == false
        }
        for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
            if (checkedItemsMajorCountries[iii]) {
                if ( countries == null ) {
                    countries = new ArrayList<>();
                }
                countries.add( charSeqMajorCountries[iii].toString() );
            }
        }
        if ( (checkedItemsAllCountries != null) && (charSeqAllCountries != null) ) {
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

        //  Success ...
        initialized = true;
    }

    //  Second constructor.  Used for copyOf() below.
    private Filters( ArrayList<String> prefixCallsignsParam, ArrayList<String> countriesParam,
                     ArrayList<String> continentsParam, boolean includeFilterParam, boolean initializedParam ) {
        initialized = false;
        if (initializedParam) {
            initialized = true;
            includeFilter = includeFilterParam;

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
    }

    //  The below five methods are called from FiltersInterface.prepareToEditFilter()
    boolean getFilterInitialized() {  return initialized; }
    boolean getFilterIncludeFilter() {  return includeFilter;  }
    ArrayList<String> getFilterPrefixCallsigns() {
        return prefixCallsigns;
    }
    ArrayList<String> getFilterContinents() {
        return continents;
    }
    ArrayList<String> getFilterCountries() {
        return countries;
    }

    //  This method is called from FiltersInterface.java when a previous filter is recalled.  It calls checkRemovedCountries() immediately below.  The
    //      purpose is to check if the filter contains countries that have been removed from the countries<> and substitute the new names.
    void updateCountryNames( Context thisContext ) {
        if (checkRemovedCountries( thisContext, countries )) {
            logInfo("Filters.updateCountryNames() ERROR - no match for all countries in filter!  *******");
        }
    }

    //  This method does most of the work of removing old countries.  It was made into a static method so it could be also called from Alerts.java.
    //      It returns true unless an error occurs (which should never occur).  It only returns the boolean because I want to call logInfo() if things
    //      fail and I can't call logInfo() from static method.
    static boolean checkRemovedCountries( Context thisContext, ArrayList<String> countriesList ) {
        if (countriesList == null) {        //   if no countries in the alert or filter then there is nothing to do.
            return true;
        }

        boolean returnValue = false;
        String[] removedCountries =                         //  List of countries that have been removed from countries list
                thisContext.getResources().getStringArray(R.array.update_country_names);
        ArrayList<String> inList = new ArrayList<>();       //  ArrayList of removedCountries[] items that are in this Filter or Alert

        //  For each country in this filter/alert search through the list of removed countries and look for matches.
        for (int iii = 0; iii < countriesList.size(); iii++) {
            for (int jjj = 0; jjj < removedCountries.length; jjj++) {
                if (countriesList.get(iii).equals( removedCountries[jjj] )) {
                    inList.add( removedCountries[jjj] );                    //  If found a match then add country name to ArrayList<> inList.
                    break;
                }
            }
        }

        //  Now remove the old country names from this filter/alert.  This can't be done in the above loop, where they are being searched for because it would
        //      change the number of iterations while the loop is progressing.  Usually not a problem but not good practice.
        for (int kkk = 0; kkk < inList.size(); kkk++) {
            //  First remove the old country from the filter/alerts
            String countryToRemove = inList.get(kkk);
            countriesList.remove( countryToRemove );            //  remove based on name, not index

            //  Now make additions to countries list based on which country was just removed.  The changes are specific for each country.
            String[] countriesToAdd = null;
            if (countryToRemove.equals( removedCountries[0] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Great_Britain_countries);
            }
            else if (countryToRemove.equals( removedCountries[1] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Russia_countries);
            }
            else if (countryToRemove.equals( removedCountries[2] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Bouvet_countries);
            }
            else if (countryToRemove.equals( removedCountries[3] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Malaysia_countries);
            }
            else if (countryToRemove.equals( removedCountries[4] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Congo_countries);
            }
            else if (countryToRemove.equals( removedCountries[5] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Malpelo_countries);
            }
            else if (countryToRemove.equals( removedCountries[6] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Minami_countries);
            }
            else if (countryToRemove.equals( removedCountries[7] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Netherlands_Caribbean_countries);
            }
            else if (countryToRemove.equals( removedCountries[8] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Kiribati_countries);
            }
            else if (countryToRemove.equals( removedCountries[9] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Heard_countries);
            }
            else if (countryToRemove.equals( removedCountries[10] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Turkey_countries);
            }
            else if (countryToRemove.equals( removedCountries[11] )) {
                countriesToAdd = thisContext.getResources().getStringArray(R.array.Pitcairn_countries);
            }
            else {
                returnValue = true;
            }

            if (countriesToAdd != null) {       //  should never be null but this is to avoid a compiler warning
                countriesList.addAll(Arrays.asList(countriesToAdd));
            }
        }
        return returnValue;
    }

    String describeFilter( Context thisContext ) {
        final int MAX_FILTER_DESCRIPTION_LENGTH = 50;

        SpannableStringBuilder ssb = new SpannableStringBuilder("");
        if (includeFilter) {
            ssb.append( thisContext.getResources().getString(R.string.filter_description_include) );
        }
        else {
            ssb.append( thisContext.getResources().getString(R.string.filter_description_exclude) );
        }
        ssb.append(" ");
        //ssb.append("filter: ");
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
                if (ssb.length() > MAX_FILTER_DESCRIPTION_LENGTH) {                 //  if length is too long ...
                    if (iii+1 < countries.size()) {                                 //  ... and if there are more countries to add
                        String extraString = String.format(Locale.US,"...+%d %s, ",
                                countries.size()-(iii+1),thisContext.getResources().getString(R.string.more));
                        int extraLength = extraString.length();
                        if ( ( MAX_FILTER_DESCRIPTION_LENGTH - extraLength ) > 5 ) {        // make sure that MAX_FILTER_DESCRIPTION_LENGTH - extraLength leaves something to print.
                            ssb.delete(MAX_FILTER_DESCRIPTION_LENGTH - extraLength, ssb.length());
                            ssb.append(extraString);
                            break;
                        }
                    }
                }
            }
        }
        ssb.delete(ssb.length()-2,ssb.length());    //  remove last comma
        if (ssb.length() > MAX_FILTER_DESCRIPTION_LENGTH) {
            ssb.delete( MAX_FILTER_DESCRIPTION_LENGTH-3, ssb.length());
            ssb.append("...");
        }
        return ssb.toString();
    }

    Filters copyOf() {
        return new Filters( prefixCallsigns, countries, continents, includeFilter, initialized );
    }

    boolean equals( Filters otherFilter ) {
        if (initialized != otherFilter.initialized) {
            return false;
        }
        if (includeFilter != otherFilter.includeFilter) {
            return false;
        }

        if ( (continents != null) && (otherFilter.continents != null) ) {       // if both are not null ...
            if (!continents.equals(otherFilter.continents)) {                   //  ... then use .equals() method
                return false;
            }
        }
        else if ( (continents != null) || (otherFilter.continents != null) ) {  // One or both are null.  It only matches if both are null.  If either are not ...
            return false;                                                       //  ... then return false.
        }

        if ( (countries != null) && (otherFilter.countries != null) ) {
            if (!countries.equals(otherFilter.countries)) {
                return false;
            }
        }
        else if ( (countries != null) || (otherFilter.countries != null) ) {
            return false;
        }

        if ( (prefixCallsigns != null) && (otherFilter.prefixCallsigns != null) ) {
            if (!sortArrayList(prefixCallsigns).equals(sortArrayList(otherFilter.prefixCallsigns))) {
                return false;
            }
        }
        else if ( (prefixCallsigns != null) || (otherFilter.prefixCallsigns != null) ) {
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

    //  Returns true if the decoded message should be kept, false if it should be discarded.
    boolean doFilter( String callsign, String country, String continent ) {
        boolean filterMatches = false;

        if (!initialized) {     //  should never happen but if it does then ...
            return true;        //  ... display everything.
        }

        if (prefixCallsigns != null) {
            for (int iii = 0; iii < prefixCallsigns.size(); iii++) {
                String search = prefixCallsigns.get(iii);
                if (callsign.startsWith(search)) {
                    filterMatches = true;
                }
            }
        }

        if (continents != null) {
            for (int iii = 0; iii < continents.size(); iii++) {
                String search = continents.get(iii);
                if (continent.contains(search)) {       // have to use .contains() because some continent strings have more than one, "EU,AS" for Russia.
                    filterMatches = true;
                }
            }
        }

        if ( (countries != null) && (!filterMatches) ) {
            for (int iii = 0; iii < countries.size(); iii++) {
                String search = countries.get(iii);
                if (country.equals(search) ) {          //  for countries use .equals() cuz they have to match exactly, distinguish between "New Zealand" and "New Zealand - Niue"
                    filterMatches = true;
                }
            }
        }

        if (includeFilter) {        //  If include filter then only display what matches.  So if it matches return true (print).  Otherwise return false (don't print).
            return filterMatches;
        }
        else {                      //  If exclude filter then display everything that doesn't match.  So if match return false (don't print).  Otherwise return true (print).
            return !filterMatches;
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

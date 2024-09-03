package com.feotec.wsjt_xmonitor;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

class HelperCountriesList {

    private static final String TAG = HelperCountriesList.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingHelperCountriesList;

    private static class Country {

        private String prefix;
        private String name;
        private String continent;

        Country( String ppp, String nnn, String ccc ) {
            prefix = ppp;
            name = nnn;
            continent = ccc;
        }
    }

    private static class SpecialCalls {

        private final String callsign;
        private final String countryName;
        private final String continent;

        SpecialCalls( String sss, String nnn, String ccc ) {
            callsign = sss;
            countryName = nnn;
            continent = ccc;
        }
    }

    private static ArrayList<Country> countriesArrayList;
    private static ArrayList<SpecialCalls> specialCallsArrayList;
    private static String[] countriesArrayForFiltersAlerts;

    HelperCountriesList( Context thisContext ) {    // Constructor used to overwrite countriesList[].name if Spanish version.

        //  Create and populate countriesArrayList<> from string_array_init.xml
        countriesArrayList = new ArrayList<>();
        String[] tempCountriesArray = thisContext.getResources().getStringArray(R.array.helper_countries_list_values);
        for (int iii = 0; iii < tempCountriesArray.length; iii++) {
            //  A typical XML line from helper_countries_list_values looks like:
            //      <item>3B6                         $Agalega &amp; St. Brandon   $AF</item>
            //  The first element of splits[] is the prefix, second is the country name, and third continent.
            //  This form of splits should trim all trailing spaces.  The delimiter is '$'.  But it seems to always leave one space character left.  So I
            //      still have to invoke trim().
            String[] splits = tempCountriesArray[iii].split("\\$");
            splits[0] = splits[0].trim();  splits[1] = splits[1].trim();  splits[2] = splits[2].trim();     // doesn't actually trim trailing spaces.
            Country tempCountry = new Country( splits[0], splits[1], splits[2]);
            countriesArrayList.add( tempCountry );
            /*  Add this code to check that I haven't mucked up the English names when I do a translation.
            if (!countriesList[iii].name.equals(tempCountries[iii])) {
                logInfo(tempCountries.length+" "+tempCountries[iii]+" "+countriesList[iii].name+" *****++");
            } */
        }

        //  Create and populate specialCallsArrayList<> from string_array_init.xml
        specialCallsArrayList = new ArrayList<>();
        String[] tempCallsArray = thisContext.getResources().getStringArray(R.array.helper_countries_list_special_calls);
        for (int iii = 0; iii < tempCallsArray.length; iii++) {
            //  A typical XML line from helper_countries_list_special_calls looks like:
            //      <item>2E0GWD/P/LH                 $England                     $EU</item>
            //  The first element of splits[] is the callsign, second is the country name, and third continent.
            String[] splits = tempCallsArray[iii].split("\\$");
            splits[0] = splits[0].trim();  splits[1] = splits[1].trim();  splits[2] = splits[2].trim();     // doesn't actually trim trailing spaces.
            SpecialCalls tempCall = new SpecialCalls( splits[0], splits[1], splits[2] );
            specialCallsArrayList.add( tempCall );
        }

        //  Create and populate countriesArrayForFiltersAlerts[] from string_array_init.xml.  It is used below in getAllCountriesList().  Has to be built here
        //      because getAllCountriesList is static.
        countriesArrayForFiltersAlerts = thisContext.getResources().getStringArray(R.array.helper_countries_list_sorted_no_dups);
    }

    //  This method returns a string[] containing the country and continent corresponding to the callsign.  If it can't find a country then it returns two null strings.
    //      It is called from HelperStationList.java.  This is called from HelperStationList.checkStation() once for each station.
    String[] getCountry( String callsign ) {
        int iii;

        //  Before considering the callsign prefix loop through the "exceptions to the rule" list of callsigns.  If find a match then
        //      return right away.
        for (iii = 0; iii < specialCallsArrayList.size(); iii++) {
            SpecialCalls nextCall = specialCallsArrayList.get(iii);
            if ( nextCall.callsign.equals( callsign )) {
                logInfo("HelperCountriesList.getCountry()  callsign: " + callsign + "  special callsign: "
                        + nextCall.callsign + "  country: "+nextCall.countryName+", "+nextCall.continent+" *****");
                return new String[] { nextCall.countryName, nextCall.continent };
            }
        }

        //  If no match found in special calls then determine country using the prefix.  In this case don't quit early but rather loop through the
        //      entire ArrayList.  The reason is explained below the loop at the return statements.
        boolean foundIt = false;
        String[] returnValue = new String[0];
        String logString= "";
        for (iii = 0; iii < countriesArrayList.size(); iii++ ) {
            Country nextCountry = countriesArrayList.get( iii );
            String prefix = nextCountry.prefix;
            int prefixLength = prefix.length();

            //  Search for slash.  If slash is present and it is past the middle of the callsign then use the characters after the callsign for  callsignPrefix
            //      Not sure about this.  K1AB/KH0 has length 8 chars and would have the '/' at position 4
            //      OTOH, K1AB/2 is length 6 and slash is at position 4.
            //      Perhaps under these cases I could search for what is after the slash.  If I don't find anything then search the beginning.  I could
            //      also look for common things like /MM and /P.  Still a K1AB/2 would yield a call from Britain, although maybe the '2' could be eliminated
            //      since there isn't any such calls in the ARRL DXCC list.
            //int lastIndex = callsign.lastIndexOf('/');
            //if (lastIndex >= (callsign.length() / 2) ) {
            //}

            if (callsign.length() >= prefixLength) {
                String callsignPrefix = callsign.substring(0, prefixLength);
                if (callsignPrefix.equals(prefix)) {
                    //  Special case of Guantanamo Bay.  Note from WSJT-X code, AD1CCty.cpp, AD1CCty::lookup_entity(), Gitmo apparently only has 2x2 calls.  Rest are US.
                    if (callsign.startsWith("KG4")) {
                        if (callsign.length() != 5) {    //  WSJT-X source code also checked for lenght of 3, not sure why.
                            nextCountry.name = "USA";
                        }
                    }
                    logString = "HelperCountriesList.getCountry()  prefix: " + prefix + "  callsign: " + callsign + "  callsignPrefix: "
                            + callsignPrefix + "  country: "+nextCountry.name+", "+nextCountry.continent+" *****";
                    foundIt = true;
                    returnValue = new String[] { nextCountry.name, nextCountry.continent };
                }
            }
        }

        //  I used to quit the above loop as soon as I had a match.  But after importing cty.dat from WSJT-X source I would have a call like 3H (China) followed by
        //      several more like 3H0, 3H2A, 3H2B, etc.  In this case they all refer to China but in case another nation is used I want it to search and return the
        //      last one found.
        if (foundIt) {
            logInfo(logString);
            return returnValue;
        } else {
            return new String[]{"", ""};
        }
    }

    //  This method is called from AlertsInterface.java and FiltersInterface.java to populate the drop down lists. It returns a CharSequence[] which
    //      contains the country names, sorting and eliminating duplicates.  This is only called once when the first FilterInterface is invoked and
    //      again with the first AlertsInterface.

    // HelperCountriesList.getAllCountriesList() returns a CharSequence[] which contains the country names, sorting and eliminating
    //  duplicates.  FiltersInterface then builds charSeqAllCountries[] and checkedItemsAllCountries[] from the country names provided by
    //  HelperCountriesList.getAllCountriesList().  FiltersInterface passes these two arrays to DialogFilterAlerts, allowing the the user to make
    //  a selection.  After DialogFilterAlerts closes FiltersInterface will create a Filter object, passing charSeqAllCountries[] and checkedItemsAllCountries[].
    //  The Filter object constructor will go through the two charSequences and read the country names from them as it constructs the filter.  In
    //  Filters:doFilter() it will compare country names.

    //  In the old version helper_countries_list_values are read in HelperCountriesList constructor.  For English they don't have to be read since they are already
    //  in countriesList[] but for Spanish those names must be overwritten.  I read them in for both languages.  countriesList is an array of Country class.  There
    //  was no special callsign list and HelperCountriesList.getAllCountriesList() constructed all countries list, sorted and duplicates removed, when it was called.

    //  In the new version the HelperCountriesList constructor reads in helper_countries_list_values and constructs countriesArrayList ArrayList, all of it, not
    //  just the county name.  countriesArrayList is an ArrayList of Country class.  The same constructor also reads in helper_countries_list_special_calls from
    //  string_array_init.xml into another ArrayList specialCallsArrayList.  It also reads helper_countries_list_sorted_no_dups into countriesArrayForFiltersAlerts
    //  (a String[] array).

    static CharSequence[] getAllCountriesList() {
        return countriesArrayForFiltersAlerts;
    }


    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

package com.feotec.wsjt_xmonitor;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

class HelperStationList {

    private static final String TAG = HelperStationList.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingHelperStationList;

    private static int MAP_NUMBER_OFF = -999;

    /*
        There are three similar data structures involved here.

        1) StationData, a private static structure defined immediately below.  A linked list of StationData structures stores all the received station info.
           The linked list is named stationList, which is also a private variable.
        2) ReturnStationData, defined in ReturnStationData.java.  This is the return value from checkStation() method below.  checkStation() is called from
           UDPService.convertWSPRDecodeMessage() and UDPService.convertDecodeMessage().
        3) DecodeDataStruct, defined in DecodeDataStruct.java.  It is used only in UDPService.  It is the primary data structure that holds all the decoded
           data for each burst.  It is filled in immediately after the call to checkStation(), within UDPService.convertWSPRDecodeMessage() and
           UDPService.convertDecodeMessage().

        If any one of the structures are changed then they all need to be considered.
    */

    private static class StationData implements Comparable<StationData> {
        String callsign;
        String country;
        String continent;
        String gridSquare = null;
        String distance = "";               // in Class GridData below there are doubles with the same name.  Set values to "" to indicate that it has not been
        String azimuth = "";                //      assigned. It's possible to collect grid data before msg1 arrives to fill in myGrid.
        double distanceInKmNum;             //  number, not a string.
        double azimuthNum;                  //  number, not a string.
        int mapNumber;                      //  used only for mapping
        boolean replyWorkedAlready;         //  used for reply
        String distanceMi;

        StationData() {
            callsign = "";
            country = "";
            continent = "";
            gridSquare = null;              //  gridSquare is exprected to be null, not "", in the way it is used below.
            distance = "";
            azimuth = "";
            distanceInKmNum = -1.0;
            azimuthNum = -1.0;
            mapNumber = MAP_NUMBER_OFF;     // MAP_NUMBER_OFF indicates two things, 1) grid square is unknown or 2) grid known but station filtered out.
            replyWorkedAlready = false;
            distanceMi = "";
        }

        @Override
        public int compareTo(StationData ddd) {
            return ddd.mapNumber - this.mapNumber;    //  descending order, highest to lowest
            //return this.mapNumber - ddd.mapNumber;    //  ascending order, lowest to highest
        }
    }

    private LinkedList<StationData> stationList = new LinkedList<>();
    private StationData lastStationUsed;
    private int mapCounter = 0;
    private HelperCountriesList countries = null;

    //  The next four elements (GridData and LatLon class plus myGrid and myLatLon) are all for computing
    //      the distance and azimuth of the station's grid square.
    private static class GridData {
        double distance;
        double azimuth;

        GridData() {
            distance = 0;
            azimuth = 0;
        }
    }

    private static class LatLon {
        double latitude;
        double longitude;
    }

    private String myGrid = null;       //  if myGrid has not been assigned then we don't want to compute distance to another grid square.
                                        //  myGrid is now always a 6 digit value with the last two digits being lower case.  If the user inputs a 4 char grid then 'mm' will be appended
                                        //      to the end.  Although this is a private variable getMyGrid() exist to access it from MapsActivity.  Once assigned, its value is not used
                                        //      for anything other than a check to see if it is null.  myLatLon is what is used in the ongoing calculations except for azdist() below.
                                        //      In azdist() the myGrid is compared against the station's grid to see if they are the same.  The stations grid is always 4 characters
                                        //      (see assignment for checkStation.gridSquare) and in azdist() I'm truncating myGrid to 4 characters.
    private LatLon myLatLon;

    HelperStationList( Context thisContext ) {    // Constructor just for passing context on to HelperCountriesList.
        countries = new HelperCountriesList( thisContext );
    }

    void clearHistory() {
        stationList.clear();
    }

    //
    //  This method checks inputString (the decoded message for msg 2 or the callsign for msg 10).  For msg 2 it attempts to extract the callsign
    //      from the message and makes sure it is a valid callsign.  If it is not then it returns with returnValue.isNewStation = false.
    //  If it is a valid callsign then it looks to see if it is already in stationList (a LinkedList).  If it is then it returns:
    //          returnValue.isNewStation = false;
    //          returnValue.country = <the country read from list>
    //          returnValue.gridSquare = grid if known, null otherwise
    //  If it is a valid callsign and the callsign is not already in list then it returns:
    //          returnValue.isNewStation = true;
    //          returnValue.country = <the country determined by call to countries.getCountry() >
    //          returnValue.gridSquare = grid if known, null otherwise
    //  The second parameter determines if it is msg 2 or msg 10.  If msg 10 then the grid square is parsed from the packet and is in the second
    //      parameter.  If msg 2 then the second parameter is a null string (the grid is pulled from inputString).
    //
    //  Notes regarding grid squares.  In WSPR they sometimes post a six digit grid square.  I'm rejecting that.  Also, I don't have any provisions
    //      for replacing a grid square once it has been placed in the list.
    //  A further note regarding WSPR and grids.  WSPR expects the grid parameter to not be null.  If it is then it will return without determining
    //      the country or setting .isNewStation.  This could be fixed but I'm not going to do that now.
    //
    //  The return value is of type ReturnStationData which is the same as the private structure StationData above with the addition of the
    //      isNewStation boolean.  StationData also has two mapping elements that are not in ReturnStationData.
    //
    ReturnStationData checkStation(String inputString, String grid, boolean isMyCall ) {

        String gridSquare = null;
        String callsign;
        ReturnStationData returnValue = new ReturnStationData();    //  default is returnValue.isNewStation == false, returnValue.callsign = ""

        logInfo("HelperStationList.checkStation: "+inputString+" *******");

        //  Extract callsign from message
        if (grid.length() > 0) {
            callsign = inputString;
            if (callsign.charAt(0) == '<') {
                if (callsign.charAt( callsign.length()-1 ) == '>') {
                    callsign = callsign.substring(1, callsign.length() - 1);
                    logInfo("    HelperStationList.checkStation: <> "+callsign+" *******");
                }
                else {
                    return returnValue;       // malformed, opening < but not closing >
                }
            }
            if (verifyGridSquare( grid ) ) {
                gridSquare = grid.substring(0,4);   // sometimes the grid is passed as a six digit value.  It is valid but I'm only recording and using the 4 digit grid.
            }
        }
        else {
            boolean potentialGrid = true;                               // grid will always be the last substring.  If callsign is the last substring then this is false
            String[] splits = inputString.split("\\s+");          //   Split string into an array of strings based on whitespace.
            int numSubstrings = splits.length;
            if (numSubstrings < 2) {                                    //  if inputString is like "READMYQRZPAGE"
                return returnValue;
            }
            //  Note - past this point numSubstrings is equal to 2 or greater.
            callsign = splits[numSubstrings - 2];                       //  start by assuming the callsign is the second to last substring
            //if (callsign.equals("CQ")) {                                //  if inputString is like "CQ 6W/IV3FSG"
            //    callsign = splits[numSubstrings - 1];
            //    potentialGrid = false;
            //}
            if (numSubstrings == 2) {                                   //  if only two substrings then callsign should be the second
                callsign = splits[numSubstrings - 1];                   //  input string like "SV1CIF W5PR".  This will also catch "SV1CIF TU", callsign = "TU" and will be thrown out.
                potentialGrid = false;                                  //  This also handles inputString like "CQ 6W/IV3FSG"
            }
            if (callsign.charAt(0) == 'Q') {                           //  No callsign begins with Q
                return returnValue;
            }
            if (callsign.length() < 3) {                               //  no callsign has 2 or fewer characters.
                return returnValue;
            }
            //  Note - past this point numSubstrings is equal to 2 or greater AND callsign has at least 3 characters in it.
            if (callsign.charAt(0) == '<') {                            //  inputString like "<6W/IV3FSG> N4CAP" or "K9DN <W2/JR1AQN> +06"
                if (callsign.charAt( callsign.length()-1 ) == '>') {
                    if (numSubstrings == 3) {                                   // inputString like "K9DN <W2/JR1AQN> +06"
                        callsign = callsign.substring(1, callsign.length() - 1);
                    }
                    else {                                                      // inputString like "<6W/IV3FSG> N4CAP"
                        callsign = splits[numSubstrings - 1];
                        potentialGrid = false;
                    }
                    logInfo("    HelperStationList.checkStation: <> "+callsign+" *******");
                }
                else {
                    return returnValue;       // malformed, opening < but not closing >
                }
            }
            if (potentialGrid) {
                if (verifyGridSquare( splits[numSubstrings - 1] ) ) {
                    gridSquare = splits[numSubstrings - 1].substring(0,4); // sometimes the grid is passed as a six digit value.  It is valid but I'm only recording and using the 4 digit grid.
                }
            }
        }

        if (!stringContainsNumberAndLetter(callsign)) {                         //  all callsigns contain at least one number and one letter.
            return returnValue;
        }

        //
        //  If gotten this far then the callsign is valid.
        //
        returnValue.callsign = callsign;

        //  So now see if callsign already in list.  If so then fill in returnValue.country now.
        Iterator iter = stationList.iterator();
        boolean notInList = true;
        while (iter.hasNext()) {
            StationData nextStation = (StationData)iter.next();
            if (callsign.equals(nextStation.callsign)) {
                //
                //  Already in list ....
                //
                notInList = false;
                if (isMyCall) {     //  If worked this station then mark replyWorkedAlready true
                    nextStation.replyWorkedAlready = true;
                }
                returnValue.country = nextStation.country;
                returnValue.continent = nextStation.continent;
                if (nextStation.gridSquare == null) {       // if in the list but grid square hasn't been found yet.
                    if (gridSquare != null) {               //  .... and the current grid square seems valid.
                        nextStation.gridSquare = gridSquare;
                        if (myGrid != null) {
                            GridData gd = azdist( nextStation.gridSquare );
                            nextStation.distance = String.valueOf( doubleToInt(gd.distance) );
                            nextStation.distanceMi = String.valueOf( doubleToInt(gd.distance * 0.621371192237 ));
                            nextStation.distanceInKmNum = gd.distance;
                            nextStation.azimuth = String.valueOf( doubleToInt(gd.azimuth) );
                            nextStation.azimuthNum = gd.azimuth;
                        }
                        logInfo("    HelperStationList.checkStation: assigned "+gridSquare+", "+nextStation.distance+
                                                                                "km, "+nextStation.azimuth+"deg to IN LIST *******");
                    }
                }
                else {
                    if (nextStation.azimuth.equals("")) {    // this handles the case where station grid arrived before the users grid in msg1.
                        if (myGrid != null) {
                            GridData gd = azdist( nextStation.gridSquare );
                            nextStation.distance = String.valueOf( doubleToInt(gd.distance) );
                            nextStation.distanceMi = String.valueOf( doubleToInt(gd.distance * 0.621371192237 ));
                            nextStation.distanceInKmNum = gd.distance;
                            nextStation.azimuth = String.valueOf( doubleToInt(gd.azimuth) );
                            nextStation.azimuthNum = gd.azimuth;
                            logInfo("    HelperStationList.checkStation: "+gridSquare+" already assigned, added "+nextStation.distance+
                                                                                "km, "+nextStation.azimuth+"deg to IN LIST *******");
                        }
                    }
                }
                returnValue.gridSquare = nextStation.gridSquare;    //  if not assigned it will be null
                returnValue.distance = nextStation.distance;
                returnValue.distanceMi = nextStation.distanceMi;
                returnValue.distanceInKmNum = nextStation.distanceInKmNum;
                returnValue.azimuth = nextStation.azimuth;
                returnValue.azimuthNum = nextStation.azimuthNum;
                lastStationUsed = nextStation;
                break;
            }
        }

        //  If not in list then add to the list and fill in returnValue.country.
        if (notInList) {
            //
            //  Not in list .....
            //
            StationData station = new StationData();
            station.callsign = callsign;
            String[] tempString = countries.getCountry( callsign );
            station.country = tempString[0];
            station.continent = tempString[1];
            if (gridSquare != null) {
                station.gridSquare = gridSquare;
                if (myGrid != null) {
                    GridData gd = azdist( station.gridSquare );
                    station.distance = String.valueOf( doubleToInt(gd.distance));
                    station.distanceMi = String.valueOf( doubleToInt(gd.distance * 0.621371192237 ));
                    station.distanceInKmNum = gd.distance;
                    station.azimuth = String.valueOf( doubleToInt(gd.azimuth));
                    station.azimuthNum = gd.azimuth;
                }
                logInfo("    HelperStationList.checkStation: assigned "+gridSquare+", "+station.distance+
                                                                    "km, "+station.azimuth+"deg  to NOT IN LIST *******");
            }
            station.replyWorkedAlready = isMyCall;      //  could be true if a station heard me and then called without me ever hearing them.
            stationList.add( station );
            returnValue.country = station.country;
            returnValue.continent = station.continent;
            returnValue.gridSquare = station.gridSquare;
            returnValue.distance = station.distance;
            returnValue.distanceMi = station.distanceMi;
            returnValue.distanceInKmNum = station.distanceInKmNum;
            returnValue.azimuth = station.azimuth;
            returnValue.azimuthNum = station.azimuthNum;
            logInfo("    HelperStationList.checkStation: NOT IN LIST - size: "+ stationList.size()+" *******");
            lastStationUsed = station;
        }
        returnValue.isNewStation = notInList;

        return returnValue;
    }

    private boolean stringContainsNumberAndLetter( String inputString ) {
        boolean hasNumber = false;
        boolean hasLetter = false;

        for (int iii = 0; iii < inputString.length(); iii++) {
            boolean numberOrLetter = false;
            char ccc = inputString.charAt( iii );
            if ( (ccc >= '0') && (ccc <= '9') ) {
                hasNumber = true;
                numberOrLetter = true;
            }
            else if ( (ccc >= 'A') && (ccc <= 'Z') )  {         //   If I just say 'else' then a value like "-17" can sneak by.
                hasLetter = true;
                numberOrLetter = true;
            }

            if (!numberOrLetter) {                              //   if not letter or number then only other allowed character are the two slashes.
                if ( (ccc != '\\') && (ccc != '/') ) {
                    return false;
                }
            }
        }

        return ((hasLetter) && (hasNumber));
    }

    private boolean verifyGridSquare( String inputString ) {
        if ( (inputString.length() != 4) && (inputString.length() != 6) ){            //  a valid grid square is always length 4
            return false;
        }
        if (inputString.equals("RR73")) {           //  reject RR73, although it is a potential grid square, it's deep in the Arctic.
            return false;
        }
        char ccc = inputString.charAt(0);           //  the first two characters must be letters between A-R
        ccc = Character.toUpperCase(ccc);
        if ( (ccc < 'A') || (ccc > 'R') ) {
            return false;
        }
        ccc = inputString.charAt(1);
        ccc = Character.toUpperCase(ccc);
        if ( (ccc < 'A') || (ccc > 'R') ) {
            return false;
        }
        ccc = inputString.charAt(2);                //  the last two characters must be numbers between 0-9
        if ( (ccc < '0') || (ccc > '9') ) {
            return false;
        }
        ccc = inputString.charAt(3);
        if ( (ccc < '0') || (ccc > '9') ) {
            return false;
        }
        if (inputString.length() == 6) {
            ccc = inputString.charAt(4);           //  the last two characters must be letters between A-Z
            ccc = Character.toUpperCase(ccc);
            if ( (ccc < 'A') || (ccc > 'Z') ) {
                return false;
            }
            ccc = inputString.charAt(5);
            ccc = Character.toUpperCase(ccc);
            if ( (ccc < 'A') || (ccc > 'Z') ) {
                return false;
            }
        }
        return true;
    }

    void setMyGrid( String myGridx ) {
        if (verifyGridSquare( myGridx )) {      //  make sure it is a valid grid square before assigning anything.
            //  if verifyGridSquare() returns true then myGridx is either 4 or 6 characters AND the first two characters are uppercase letters AND
            //      the next two are numbers AND the last two (if present) are uppercase characters.
            String myGrid6;
            if (myGridx.length() == 4) {
                myGrid = myGrid6 = myGridx + "mm";
            } else {
                String temp = myGridx.substring(4,6).toLowerCase();
                myGrid = myGrid6 = myGridx.substring(0,4)+temp;
            }
            myLatLon = grid2deg(myGrid6);
            logInfo(myGrid6 + "  " + myLatLon.latitude + "  " + myLatLon.longitude+" *******");
        }
    }

    //
    //  The following three methods are used for mapping
    //

    //  Used for mapping
    String getMyGrid() {
        return myGrid;
    }

    //  Used for mapping.  It retrieves the list of grid squares to be mapped.  Called from MainActivity when the user requests mapping.  It is to
    //      return the grid squares of the MAX_NUMBER_OF_STATIONS most recent stations heard from.  This is not the most efficient code but since it is
    //      called from MainActivity it doesn't really need to be.  The idea of using mapNumber was to make doFilterForMapping() (below) which is
    //      called from UDPService as efficient as possible.
    //  There is a possibility that the linked list will change as it is being processed here.  It will happen if a burst comes in while this code
    //      is running.  But it's not that critical.  What is important is that the background thread (in UDPService) writes to the linked list and
    //      this code, from the other thread, only reads from it.
    String[] getRxGridsForMapping() {
        String[] returnValue = new String[ MapsActivity.MAX_NUMBER_OF_STATIONS ];
        int iii = 0;

        //debugDumpHistory();

        //  run through linked list.  Find all stations with mapNumber != MAP_NUMBER_OFF.  MAP_NUMBER_OFF indicates filtered out or grid unknown.
        //      Copy them to new linked list.
        LinkedList<StationData> tempStationList = new LinkedList<>();
        Iterator iter = stationList.iterator();
        while (iter.hasNext()) {
            StationData nextStation = (StationData) iter.next();
            if (nextStation.mapNumber != MAP_NUMBER_OFF) {
                tempStationList.add(nextStation);
            }
        }

        //  change this new linked list into an array.
        StationData[] tempStationArray = tempStationList.toArray( new StationData[0] );

        //  sort the array in descending order, based on mapNumber.
        Arrays.sort( tempStationArray );

        //  copy grid squares to returnValue[], but only the first MapsActivity.MAX_NUMBER_OF_STATIONS.
        int endValue = tempStationArray.length;
        if (endValue > MapsActivity.MAX_NUMBER_OF_STATIONS) {
            endValue = MapsActivity.MAX_NUMBER_OF_STATIONS;
        }
        for (iii = 0; iii < endValue; iii++) {
            returnValue[iii] = tempStationArray[iii].gridSquare;
        }

        //
        //for (int jjj = 0; jjj < iii; jjj++) {
        //    Log.i(TAG,"- "+jjj+" "+returnValue[jjj]+" *****");
        //}
        //
        return returnValue;
    }

    //  Used for mapping.  Called from the background thread in UDPService.  I did this in a cryptic manner, using mapNumber as both a flag and a counter.  But
    //      I was trying to make this as efficient as possible.  This is called from each decoded msg 2 or 10 just after the decoded message is written to the
    //      DecodeDataStruct linked list.
    void doFilterForMapping( boolean notFiltered ) {
        if (lastStationUsed != null) {                          //  It's possible for this to be called before any station has been written to the linked list, in which case lastStationUsed == null
            if (lastStationUsed.gridSquare != null) {           //  don't bother until the grid square is known.
                if (notFiltered) {
                    lastStationUsed.mapNumber = mapCounter;
                    mapCounter++;
                } else {
                    lastStationUsed.mapNumber = MAP_NUMBER_OFF; //  set to 0ff, indicating the station is now filtered out.
                }
            }
        }
    }

    //
    //  End of mapping methods
    //

    //  Used for reply.  Returns a boolean indicating if the station has been worked already.
    boolean stationHasBeenWorked() {
        if (lastStationUsed == null) {
            return false;
        } else {
            return lastStationUsed.replyWorkedAlready;
        }
    }

    private int doubleToInt( double xxx ) {
        int returnValue = (int)xxx;
        double fract = xxx % 1;
        if (fract >= 0.5) {
            returnValue++;
        }
        return returnValue;
    }

    //  Called from several places above.  It is passed the sending station's grid.  It returns GridData (defined above as two elements, distance and azimuth, both doubles).
    //      This represents the distance and azimuth between the user's grid and the sending station's grid.
    //  The calling routine MUST CHECK to see that myGrid is not null before calling this routine.
    private GridData azdist( String hisGrid ) {
        GridData returnValue = new GridData();

        if (hisGrid.equals((String)myGrid.substring(0,4))) {
            return returnValue;         //  returns value of 0,0
        }

        //String hisGrid6 = new String(hisGrid+"mm");
        String hisGrid6 = hisGrid+"mm";
        LatLon hisLatLon = grid2deg(hisGrid6);

        //  myLatLon has the user's latitude and longitude
        returnValue = geodist( myLatLon.latitude, myLatLon.longitude, hisLatLon.latitude, hisLatLon.longitude );

        return returnValue;
    }


    //  This grid should be a six digit grid square.  The returnValue contains the latitude and longitude of that grid location.  In this case north latitude and west
    //      longitude are positive.  It is called from azdist() immediately above (with each received message) and from setMyGrid() above (once when the user's call is received).
    private LatLon grid2deg( String grid6 ) {
        LatLon returnValue = new LatLon();

        // Converts Maidenhead grid locator to degrees of West longitude
        // and North latitude.

        double dlong, dlat;
        char g1,g2,g3,g4,g5,g6;
        int nlong,n20d,nlat;
        double xminlong,xminlat;

        g1=grid6.charAt(0);
        g2=grid6.charAt(1);
        g3=grid6.charAt(2);
        g4=grid6.charAt(3);
        g5=grid6.charAt(4);
        g6=grid6.charAt(5);

        // Regarding undeclared variables a Fortran tutorial says "all variables starting with the letters i-n are integers and all others are real".
        nlong = 180 - 20*((int)(g1-'A'));
        n20d = 2*((int)(g3-'0'));
        xminlong = 5*(double)(g5-'a')+0.5;
        dlong = ((double)(nlong - n20d)) - xminlong/60.0;
        nlat = -90+10*(int)(g2-'A') + (int)(g4-'0');
        xminlat = 2.5*((double)(g6-'a')+0.5);
        dlat = (double)nlat + xminlat/60.0;

        returnValue.latitude = dlat;
        returnValue.longitude = dlong;
        return returnValue;
    }

    //  Called only from azdist() above.  It returns GridData (defined above as two elements, distance and azimuth, both doubles) which represents the
    //      distance and azimuth between two stations.
    private GridData geodist( double Eplat, double Eplon, double Stlat, double Stlon ) {
        //
        //    JHT: In actual fact, I use the first two arguments for "My Location",
        //        the second two for "His location"; West longitude is positive.
        //
        //         Taken directly from:
        //         Thomas, P.D., 1970, Spheroidal geodesics, reference systems,
        //         & local geometry, U.S. Naval Oceanographi!Office SP-138,
        //         165 pp.
        //         assumes North Latitude and East Longitude are positive
        //
        //         EpLat, EpLon = End point Lat/Long
        //         Stlat, Stlon = Start point lat/long
        //         Az, BAz = direct & reverse azimuith
        //         Dist = Dist (km); Deg = central angle, discarded
        //
        GridData returnValue = new GridData();

        double Az, Dist; // Baz

        double BOA, F, P1R, P2R, L1R, L2R, DLR, T1R, T2R, TM;
        double DTM, STM, CTM, SDTM,CDTM, KL, KK, SDLMR, L;
        double CD, DL, SD, T, U, V, D, X, E, Y, A, FF64, TDLPM;
        double HAPBR, HAMBR, A1M2; //, A2M1;

        double AL = 6378206.4;              // Clarke 1866 ellipsoid
        double BL = 6356583.8;
        double D2R = 0.01745329251994;      // degrees to radians conversion factor
        double Pi2 = 6.28318530718;

        BOA = BL/AL;
        F = 1.0 - BOA;
        // Convert st/end pts to radians
        P1R = (Eplat) * D2R;
        P2R = (Stlat) * D2R;
        L1R = (Eplon) * D2R;
        L2R = (Stlon) * D2R;
        DLR = L2R - L1R;                 // DLR = Delta Long in Rads
        T1R = Math.atan(BOA * Math.tan(P1R));
        T2R = Math.atan(BOA * Math.tan(P2R));
        TM = (T1R + T2R) / 2.0;
        DTM = (T2R - T1R) / 2.0;
        STM = Math.sin(TM);
        CTM = Math.cos(TM);
        SDTM = Math.sin(DTM);
        CDTM = Math.cos(DTM);
        KL = STM * CDTM;
        KK = SDTM * CTM;
        SDLMR = Math.sin(DLR/2.0);
        L = SDTM * SDTM + SDLMR * SDLMR * (CDTM * CDTM - STM * STM);
        CD = 1.0 - 2.0 * L;
        DL = Math.acos(CD);
        SD = Math.sin(DL);
        T = DL/SD;
        U = 2.0 * KL * KL / (1.0 - L);
        V = 2.0 * KK * KK / L;
        D = 4.0 * T * T;
        X = U + V;
        E = -2.0 * CD;
        Y = U - V;
        A = -D * E;
        FF64 = F * F / 64.0;
        Dist = AL*SD*(T -(F/4.0)*(T*X-Y)+FF64*(X*(A+(T-(A+E)/2.0)*X)+Y*(-2.0*D+E*Y)+D*X*Y))/1000.0;
        TDLPM = Math.tan((DLR+(-((E*(4.0-X)+2.0*Y)*((F/2.0)*T+FF64*(32.0*T+(A-20.0*T)*X-2.0*(D+2.0)*Y))/4.0)*Math.tan(DLR)))/2.0);
        HAPBR = Math.atan2(SDTM,(CTM*TDLPM));
        HAMBR = Math.atan2(CDTM,(STM*TDLPM));
        A1M2 = Pi2 + HAMBR - HAPBR;
        //A2M1 = Pi2 - HAMBR - HAPBR;

        int counter = 0;
        while (true) {
            counter++;
            if (counter > 10) { break; }    // my paranoia.  I don't want it to get stuck here.  It usually takes no more than two passes.
            if ((A1M2 >= 0.0) && (A1M2 < Pi2)) { break; }
            if (A1M2 < Pi2) {
                A1M2 = A1M2 + Pi2;
            }
            else {
                A1M2 = A1M2 - Pi2;
            }
        }
        logInfo("HelperStationList.geodist() counter = "+counter+" *****");

        // All of this gens the proper az, baz (forward and back azimuth)
        /*
        while (true) {
            if ((A2M1 >= 0.0) && (A2M1 < Pi2)) { break; }
            if (A2M1 < Pi2) {
                A2M1 = A2M1 + Pi2;
            }
            else {
                A2M1 = A2M1 - Pi2;
            }
        }
        */
        Az = A1M2 / D2R;
        //Baz = A2M1 / D2R;

        // Fix the mirrored coords here.

        Az = 360.0 - (Az);
        //Baz = 360.0 - (Baz);

        returnValue.distance = Dist;
        returnValue.azimuth = Az;

        return returnValue;
    }

    //  This function is called from UDPService.stopUDPService().  It is called only if DebugUtils.loggingUDPService is true.
    void debugDumpHistory() {
        int iii = 0;
        Log.i(TAG,"mapCounter = "+mapCounter+" *****");
        Iterator iter = stationList.iterator();
        while (iter.hasNext()) {
            StationData nextStation = (StationData)iter.next();
            //  Since this is only called from UDPService.stopUDPService() I don't want to condition this printout on HelperStationList.DO_LOGGING.
            //      The function isn't called unless UDPService.DO_LOGGING is true, effectively conditioning these two lines on that constant.
            Log.i(TAG,"   #"+iii+" "+nextStation.callsign+", "+nextStation.country+" "+nextStation.gridSquare+" "+nextStation.mapNumber+" *****");
            DebugUtils.writeToAndroidLogFile(TAG,"   #"+iii+" "+nextStation.callsign+", "+nextStation.country+" *****");
            iii++;
        }
    }

    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

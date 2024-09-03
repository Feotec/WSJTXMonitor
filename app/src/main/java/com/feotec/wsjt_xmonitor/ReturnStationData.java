package com.feotec.wsjt_xmonitor;

class ReturnStationData {
    boolean isNewStation;
    String callsign;
    String country;
    String continent;
    String gridSquare;
    String distance;
    String azimuth;
    double distanceInKmNum;     //  number, not a string.
    double azimuthNum;          //  number, not a string.
    String distanceMi;

    ReturnStationData() {
        isNewStation = false;
        callsign = "";
        country = "";
        continent = "";
        gridSquare = null;      //  gridSquare is exprected to be null, not "", in the way it is used in HelperStationList
        distance = "";
        azimuth = "";
        distanceInKmNum = -1.0;
        azimuthNum = -1.0;
        distanceMi = "";
    }
}

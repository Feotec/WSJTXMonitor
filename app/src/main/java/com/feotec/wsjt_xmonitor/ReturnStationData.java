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

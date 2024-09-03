package com.feotec.wsjt_xmonitor;


import java.util.Comparator;

class DecodeDataStruct implements Comparable<DecodeDataStruct> {

    boolean isWSPR;

    //  Items used for msg 2 (all decodes except WSPR)
    String qtimeStr2;
    int SNR;
    int dFreq;
    String mode;
    String xmessage;
    boolean isCQ;
    boolean isMyCall;
    boolean isNewStation;
    String country;
    String continent;
    String grid;
    String distanceInKm;
    String distanceInMi;
    String azimuth;
    String callsign;
    boolean isAlert;
    double distanceInKmNum;     //  number, not a string.
    double azimuthNum;          //  number, not a string.

    //  Items used for msg 10 (WSPR decode).  Common elements are commented out.
    // String qtimeStr2;
    // int SNR;
    long dFreqWSPR;
    int drift;
    // String callsign;
    // String grid;
    // boolean isNewStation;
    int power;
    // String country;
    // String continent;
    // String distanceInKm;
    // String azimuth;
    // double distanceInKmNum;
    // double azimuthNum;

    byte[] msg4Buffer;
    int msg4Length;

    //  Constructor
    DecodeDataStruct() {
        msg4Buffer = new byte[UDPService.DATAGRAM_BUFFER_SIZE];
    }

    @Override
    public int compareTo(DecodeDataStruct ddd) {
        return ddd.SNR - this.SNR;
    }

    public static class FreqComparator implements Comparator<DecodeDataStruct> {
        @Override
        public int compare(DecodeDataStruct c1, DecodeDataStruct c2) {
            return c1.dFreq - c2.dFreq;
        }
    }

    public static class DistanceComparator implements Comparator<DecodeDataStruct> {
        @Override
        public int compare(DecodeDataStruct c1, DecodeDataStruct c2) {
            return (int)(c1.distanceInKmNum - c2.distanceInKmNum);
        }
    }

    public static class AzimuthComparator implements Comparator<DecodeDataStruct> {
        @Override
        public int compare(DecodeDataStruct c1, DecodeDataStruct c2) {
            return (int)(c1.azimuthNum - c2.azimuthNum);
        }
    }

}

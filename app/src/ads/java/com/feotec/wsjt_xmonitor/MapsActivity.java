package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.Locale;


public class MapsActivity extends AppCompatActivity {
    //  Three values not used here.  They are just here to satisfy the linker.
    public static final String MAP_EXTRA_MY_GRID = "map_extra_my_grid";
    public static final String MAP_EXTRA_RX_GRIDS = "map_extra_rx_grids";
    public static final int MAX_NUMBER_OF_STATIONS = 30;

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingMapsActivity;

    public static final String MAP_SCREEN_ALWAYS_ON = "map_screen_always_on";

    //  Timer for checking whether msg4 (setting Rx Frequency, not alerts) got through successfully.  setRxFreqSend() below initiates the timer by calling
    //          timerHandler.postDelayed(timerRunnable, timerRunnableTimeout);
    //  This run() will then execute when the time (set by timerRunnableTimeout) has expired.  Within this routine I can do this again (substituting 'this' for
    //  the first parameter) and the timer will run forever, even after the app has terminated.  If the timer is running and I want to terminate it early I can do
    //          timerHandler.removeCallbacks(timerRunnable);
    //  This will take the timer out of the queue.  This is done with the screen timers in BaseActivity but there isn't any need here.
    private static final int timerRunnableTimeout = 800;        // in mS
    private Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {		// Runnable is an Interface
        @Override
        public void run() {			// Runnable has one required method, run()

            logInfo("Timer MapsActivity *****");

            FragmentManager fragMan = getSupportFragmentManager();          // Get FragmentManager
            DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("Mapping Dialog");
            if (testFrag == null) {
                testFrag = new MappingDialog();
                testFrag.show(getSupportFragmentManager(), "Mapping Dialog");
                testFrag.setCancelable(false);
            }

            //timerHandler.postDelayed(this, timerRunnableTimeout);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        logInfo( "onCreate() called *****" +
                String.format(Locale.US," PID %d Tid %d Uid %d Thread %d",
                        android.os.Process.myPid(),
                        android.os.Process.myTid(),
                        android.os.Process.myUid(),
                        Thread.currentThread().getId()
                ));

        Intent intent = getIntent();
        boolean screenAlwaysOnForDisplay = intent.getBooleanExtra(MAP_SCREEN_ALWAYS_ON, false );
        logInfo("MapsActivity - screenAlwaysOnForDisplay == "+screenAlwaysOnForDisplay+" *****");
        if (screenAlwaysOnForDisplay) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        ImageView imageView = findViewById(R.id.noMapsImageView);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            imageView.setImageResource(R.drawable.map_italy);
        } else {
            imageView.setImageResource(R.drawable.map_la);
        }

        timerHandler.postDelayed(timerRunnable, timerRunnableTimeout);  //  initiate timer
    }

    public static class MappingDialog extends DialogFragment {

        @Override
        public void onStart() {         //   This is done so that the dialog can have a link in it.
            super.onStart();
            ((TextView) getDialog().findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getText(R.string.activity_maps_no_maps))
                    .setTitle(getString(R.string.activity_maps_help_title))
                    .setPositiveButton(getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            try {
                                getActivity().finish();
                            } catch (Exception ex){
                                Log.i(TAG,"MappingDialog - Dismiss Exception " + ex.getMessage());
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
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

/*
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingMapsActivity;

    public static final String MAP_EXTRA_MY_GRID = "map_extra_my_grid";
    public static final String MAP_EXTRA_RX_GRIDS = "map_extra_rx_grids";
    public static final String MAP_SCREEN_ALWAYS_ON = "map_screen_always_on";
    public static final int MAX_NUMBER_OF_STATIONS = 30;

    private GoogleMap mMap;

    private LatLng myLatLng;
    private LatLng[] rxLatLng = new LatLng[MAX_NUMBER_OF_STATIONS];
    private int numberOfStations;
    private static CharSequence errorMessage;
    private boolean okToPlot = false;
    private boolean screenAlwaysOnForDisplay = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        numberOfStations = 0;
        // Get the intent that started this activity and extract the string which contains the filename
        Intent intent = getIntent();
        String myGrid = intent.getStringExtra(MAP_EXTRA_MY_GRID);
        if (myGrid == null) {
            logInfo("Error - myGrid is null *****");
            displayErrorMessage(getText(R.string.activity_maps_error1));
        } else {
            myLatLng = grid2deg( myGrid+"mm" );
            logInfo("myGrid = "+myGrid+" "+myLatLng.latitude+" "+myLatLng.longitude+" *****");
            String[] rxGrids = intent.getStringArrayExtra(MAP_EXTRA_RX_GRIDS);
            if (rxGrids[0] == null) {       //  must be at least one entry.
                //  Note - this array is initialized to a given size.  So the length will be that size even if all the entries are null.
                logInfo("Error - rxGrids[] has no entries *****");
                displayErrorMessage(getText(R.string.activity_maps_error2));
            } else {
                for (int iii = 0; iii < MAX_NUMBER_OF_STATIONS; iii++) {
                    if (rxGrids[iii] == null) {
                        break;          //  in case there are not the MAX_NUMBER_OF_STATIONS in the array.
                    }
                    rxLatLng[iii] = grid2deg( rxGrids[iii]+"mm" );
                    numberOfStations++;
                    logInfo("rxGrid["+iii+"] = "+rxGrids[iii]+" "+rxLatLng[iii].latitude+" "+rxLatLng[iii].longitude+" *****");
                }
                okToPlot = true;
            }
            screenAlwaysOnForDisplay = intent.getBooleanExtra(MAP_SCREEN_ALWAYS_ON, false );
            logInfo("MapsActivity - screenAlwaysOnForDisplay == "+screenAlwaysOnForDisplay+" *****");
            if (screenAlwaysOnForDisplay) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        try {
            mapFragment.getMapAsync(this);
        } catch (Exception ex) {
            logInfo("thread Exception 2 " + ex.getMessage()+" *****");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        logInfo("onCreateOptionsMenu() called *****");

        // Inflate our menu from the resources by using the menu inflater.
        getMenuInflater().inflate(R.menu.menu_maps, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_help:
                logInfo("onOptionsItemSelected() help *****");
                FragmentManager fragMan = getSupportFragmentManager();
                DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("Mapping Help Fragment");
                if (testFrag == null) {
                    testFrag = new MappingHelpFragment();
                    testFrag.show(getSupportFragmentManager(), "Mapping Help Fragment");
                    testFrag.setCancelable(false);
                }
                return true;
            default:
                logInfo("onOptionsItemSelected() default *****");
                return super.onOptionsItemSelected(item);
        }
    }

    //  This grid should be a six digit grid square.  The returnValue contains the latitude and longitude of that grid location.  In this case north latitude and east
    //      longitude are positive.
    //  This was stolen from HelperStationList.  It seemed just easier to duplicate it here since I needed to return LatLng instead of the private class HelperStationData.LatLon.
    //      Also that method calls west longitude positive when it needs to be negative.
    private LatLng grid2deg( String grid6 ) {

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

        return new LatLng( dlat, -dlong );
    }


    //private static final int COLOR_GREEN_ARGB = 0xff388E3C;
    //private static final int COLOR_ORANGE_ARGB = 0xffF57F17;

    private void doOneLine( int index ) { //double latStation, double lonStation ) {
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.clickable(false);
        polylineOptions.add( myLatLng ); //new LatLng( latUser, lonUser ));
        polylineOptions.add( rxLatLng[index] ); //new LatLng( latStation, lonStation ));
        Polyline polyline = mMap.addPolyline( polylineOptions );
        //polyline.setColor(COLOR_GREEN_ARGB);
        polyline.setWidth(4);       //  default width is 10 doesn't look good on tablet
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        //GoogleMap mMap;

        if (!okToPlot) { return; }

        mMap = googleMap;

        for (int iii = 0; iii < numberOfStations; iii++) {
            doOneLine(iii);
        }


        //  Create LatLngBounds.Builder object
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        //  Include these data points within the bounds
        builder.include( myLatLng );
        for (int iii = 0; iii < numberOfStations; iii++) {
            builder.include( rxLatLng[iii] );
        }

        //  Now build the LanLngBounds
        LatLngBounds centerBounds = builder.build();

        //  The first method in the try / catch block works well.  However, on my KitKat it crashes.  It's a know Android bug.  The work-around is
        //      to use the code in the catch block.  However, it doesn't seem to always get the data on the screen.  So try the first and only use
        //      the second if necessary.  Actually both seem to do that sometimes, I think because Google Maps has problems showing the entire earth.
        //      The first method works more often.
        try {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(centerBounds, 100));
            logInfo("Using preferred CameraUpdateFactory method *****");
        } catch (Exception ex) {
            logInfo("thread Exception 3 " + ex.getMessage()+" *****");
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int padding = (int)(width * 0.12);
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(centerBounds, width, height, padding));
        }

        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom( myLatLng, 1));
    }

    private void displayErrorMessage(CharSequence errorMessagePassed ) {
        FragmentManager fragMan = getSupportFragmentManager();          // Get FragmentManager
        DialogFragment testFrag = (DialogFragment) fragMan.findFragmentByTag("Mapping Error Message Fragment");
        if (testFrag == null) {
            errorMessage = errorMessagePassed;
            testFrag = new ErrorMessageFragment();
            testFrag.show(getSupportFragmentManager(), "Mapping Error Message Fragment");
            testFrag.setCancelable(false);
        }
    }

    public static class ErrorMessageFragment extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(errorMessage)
                    .setTitle(getString(R.string.activity_maps_error_title))
                    .setPositiveButton(getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.i(TAG, "Mapping Error Message Fragment OK *****");
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class MappingHelpFragment extends DialogFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getText(R.string.activity_maps_help))
                    .setTitle(getString(R.string.activity_maps_help_title))
                    .setPositiveButton(getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //Log.i(TAG, "Mapping Error Message Fragment OK *****");
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
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
*/

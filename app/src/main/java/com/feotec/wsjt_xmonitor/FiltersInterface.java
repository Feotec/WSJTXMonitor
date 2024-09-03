package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

//  The interface for creating a filter.  Once the user has tried to create a filter this class is created (constructor is called).  If ths user
//      aborts or finishes the class remains.  It is not destroyed by Java garbage collector.  When the screen is rotated this object is preserved
//      in a mRetainedFragment.  There is a note in the menu code, where filters are selected, that if I always create a new filter object the old
//      one will be destroyed.  I chose not to do that.  It seems safer.
class FiltersInterface {
    private static final String TAG = FiltersInterface.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingFiltersInterface;

    private static final String RECENT_FILTERS_FILENAME = "RecentFiltersFile";
    private static final int MAX_NUM_RECENT_FILTERS = 10;

    //  This static class is used for the help popups within this class.  It has only one button, 'Dismiss'.
    public static class BasicClassHelp extends DialogFragment {
        private static final String TAG = BasicClassHelp.class.getSimpleName();
        private static final String MSG_RES = "message_resource";

        protected int messageResource;

        public static BasicClassHelp newInstance( int messageParameter ) {
            BasicClassHelp fragment = new BasicClassHelp();
            Bundle args = new Bundle();
            args.putInt( MSG_RES, messageParameter );
            fragment.setArguments(args);
            return fragment;
        }

        private boolean readBundle(Bundle bundle) {
            if (bundle != null) {
                messageResource = bundle.getInt(MSG_RES);
                return true;
            }
            else {
                return false;       //  should only happen if default constructor called.
            }
        }

        @Override
        public void onStart() {         //   This is done so that the dialog can have a link in it.
            super.onStart();
            ((TextView) getDialog().findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            if (readBundle(getArguments())) {
                logInfo("BasicClass.onCreateDialog readBundle() ok  *****");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            //builder.setTitle(R.string.no_wifi_title);
            builder.setMessage(messageResource);
            builder.setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    logInfo( "BasicClassHelp OK *****");
                }
            });

            return builder.create();
        }

        //  Logging convenience method
        private void logInfo( String message ) {
            if (DO_LOGGING) {
                Log.i(TAG,message);
            }
        }
    }


    //
    //
    //  End of static classes
    //
    //  Beginning of FiltersInterface methods
    //
    //

    //  Six dialogs enumerated here.  These values are assigned to dialogName and used to keep track of where the user is in the filter selection process.
    //      DIALOG_INCLUDE_EXCLUDE is first presented.  If the user cancels then doCancel() is called and filter is done (dialogName set to DIALOG_NONE).
    //          If the user makes a selection then doNext() is called and the user transitions to DIALOG_REGION_OR_PREFIX or DIALOG_PREVIOUS_FILTERS.
    //      DIALOG_REGION_OR_PREFIX is sort of a central point.  If the user makes a selection doNext() is called and one of the remaining four dialogs
    //          are brought up.  When the user leaves those dialogs, either when Cancel or OK button, then DIALOG_REGION_OR_PREFIX is again displayed.
    //          If the user cancels from DIALOG_REGION_OR_PREFIX then doCancel() is called and filter is done, just like DIALOG_INCLUDE_EXCLUDE.
    //      DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, DIALOG_ALL_COUNTRIES, and DIALOG_PREFIX_CALLSIGN all behave the same.  When the user is finished
    //          either with Cancel (doCancel() called) or OK (doNext() called) then DIALOG_REGION_OR_PREFIX is again displayed.  All but DIALOG_PREFIX_CALLSIGN
    //          are select-from-list dialogs.  DIALOG_PREFIX_CALLSIGN has the user input data into a field.
    //      DIALOG_PREVIOUS_FILTERS displays previous filters.  If user cancels then the interface quits.  If the user makes a selection then that selection
    //          is read from linked list recentFilters and is copied into filterObject.  It transitions to DIALOG_REGION_OR_PREFIX with a different title
    //          indicating that the user can modify the previous filter.
    private enum DialogName {
        DIALOG_NONE,
        DIALOG_INCLUDE_EXCLUDE, DIALOG_REGION_OR_PREFIX, DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, DIALOG_ALL_COUNTRIES,
        DIALOG_PREFIX_CALLSIGN, DIALOG_PREVIOUS_FILTERS
    }

    //  private variables
    private AppCompatActivity thisContext;
    private DialogName dialogName = DialogName.DIALOG_NONE;

    //  The following private variable are used for the dialogs used in this class.  All except PrefixCallsign dialog are list dialogs.  The items in the list
    //      are contained in charSeq*[] arrays and the results (checked or not) are in checkedItems*[] arrays.  For PrefixCallsign dialog results are stored in
    //      prefisCallsignItems[].  Note that the arrays for all countries and previous filters are not pre-initialized but are built up when the user selects
    //      that item from the RegionPrefix dialog.
    //  When it came to translation, I had to move all the initializers into res/ values/ string_array_init.xml.
    private CharSequence[] charSeqFilterType = null; //{ "New Include Filter", "New Exclude Filter", "Reuse Previous Filters", "Edit Previous Filters" };
    private boolean[] checkedItemsFilterType = { false, false, false, false  };
    private CharSequence[] charSeqRegionPrefix = null; //{ "Input Prefix or Callsign", "List of Continents", "List of Major Countries", "List of All Countries" };
    private boolean[] checkedItemsRegionPrefix = { false, false, false, false };
    private CharSequence[] charSeqContinent = null; //{ "Africa", "Asia", "Europe", "North America", "South America", "Oceania" };
    private boolean[] checkedItemsContinent = { false, false, false, false, false, false };
    private CharSequence[] charSeqMajorCountries = null; //{ "Argentina", "Australia", "Austria", "Brazil", "Britain", "Canada", "China", "Chile", "Denmark", "Finland", ...
    private boolean[] checkedItemsMajorCountries;
    private CharSequence[] charSeqAllCountries = null;
    private boolean[] checkedItemsAllCountries = null;
    private ArrayList<String> prefixCallsignItems = new ArrayList<>();
    private CharSequence[] charSeqPreviousFilters = null;   //  can be a local variable
    private boolean[] checkedItemsPreviousFilters = null;

    //  This is the output of thie entire interface.  It is initially null.  Since it is static then it always exists.  I can set the reference (filterObject)
    //      to null to invoke garbage collector.  However, I can't think of any place I want to do that.  A Filters object may be created once.  If the user
    //      then goes in to create a different filter then I don't want to remove the current filter until the user completes the process.  Setting filterObject
    //      to null if the user cancels out would remove an existing filter, something that I don't want.
    static Filters filterObject = null;
    //private Filters filterUDPService = null;
    private UDPService mService = null;

    private LinkedList<Filters> recentFilters = new LinkedList<>();     // I had set this to null and did the "new LinkedList<>()" in the constructor but doing it that
                                                                        //      way wouldn't let me make an iterator in doNext() below.  Doing it this was is equivalent.
    //  constructor
    FiltersInterface(AppCompatActivity context, UDPService service) {
        logInfo("FiltersInterface constructor called *****");
        setContext( context );
        mService = service;
        charSeqFilterType = thisContext.getResources().getStringArray(R.array.filter_type_values);  //  initialized from string_array_init.xml
        charSeqRegionPrefix = thisContext.getResources().getStringArray(R.array.filter_region_prefix_values);
        charSeqContinent = thisContext.getResources().getStringArray(R.array.continent_list_values);
        charSeqMajorCountries = thisContext.getResources().getStringArray(R.array.major_countries_list_values);
        checkedItemsMajorCountries = new boolean[charSeqMajorCountries.length];
        Arrays.fill(checkedItemsMajorCountries, false);
        startUI();
    }

    //  This is called from the constructor above.  It is also called from onResumeFragments() when the screen is rotated or app moves to background.  This second
    //      call is necessary because the context disappears and is created again duing screen rotations or app in background.
    void setContext(AppCompatActivity context) {
        thisContext = context;
    }

    //  Called from doCancel() and from clearAll() below.
    private void clearContinents() {
        /*
        for (int iii = 0; iii < checkedItemsContinent.length; iii++) {
            checkedItemsContinent[iii] = false;
        } */
        Arrays.fill(checkedItemsContinent, false);
    }

    //  Called from doCancel() and from clearAll() below.
    private void clearMajorCountries() {
        /*
        for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
            checkedItemsMajorCountries[iii] = false;
        } */
        Arrays.fill(checkedItemsMajorCountries, false);
    }

    //  Called from doCancel() and from clearAll() below.
    private void clearAllCountries() {
        if (checkedItemsAllCountries != null) {
            /*
            for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                checkedItemsAllCountries[iii] = false;
            } */
            Arrays.fill(checkedItemsAllCountries, false);
        }
    }

    //  Called from two places.  1) From doCancel() when the user selects Cancel button from DIALOG_INCLUDE_EXCLUDE or DIALOG_REGION_OR_PREFIX dialogs.  At
    //      this point the filter is aborted and it switches to DIALOG_NONE.  2) from doNext() when the user selects Finish button.  At this point the user has
    //      finished creating a filter, a Filter object has been created and it is necessary to clear all checked items so the user can create another filter
    //      if desired.
    private void clearAll() {
        int iii;
        for (iii = 0; iii < checkedItemsFilterType.length; iii++) {         //  cleared here because there was no need to clear them anywhere else.  Others
            checkedItemsFilterType[iii] = false;                            //      are cleared from more than one place.
        }
        for (iii = 0; iii < checkedItemsRegionPrefix.length; iii++) {
            checkedItemsRegionPrefix[iii] = false;
        }
        if (checkedItemsPreviousFilters != null) {
            for (iii = 0; iii < checkedItemsPreviousFilters.length; iii++) {
                checkedItemsPreviousFilters[iii] = false;
            }
        }
        prefixCallsignItems.clear();
        clearContinents();
        clearMajorCountries();
        clearAllCountries();
        logInfo("FiltersInterface.clearAll() *****");
    }

    //  Called from the constructor above.  It is also called from the menu system if FiltersInterface has already been created.  It is the only
    //      place where DIALOG_INCLUDE_EXCLUDE is brought up.
    void startUI() {
        dialogName = DialogName.DIALOG_INCLUDE_EXCLUDE;
        readLinkedList();
        int endOfLargeFont = thisContext.getResources().getInteger(R.integer.filter_type_end_of_large_font);
        if (recentFilters.size() < 1) {
            //  If the LinkedList recentFilters has no objects in it then there are no recent filters so don't offer the option to reuse a recent filter.
            CharSequence[] charSeqFilterType2 = thisContext.getResources().getStringArray(R.array.filter_type_values_short);  //  initialized from string_array_init.xml
            boolean[] checkedItemsFilterType2 = { false, false  };
            bringUpDialog(charSeqFilterType2, checkedItemsFilterType2, R.string.title_include_exclude,endOfLargeFont,
                    "Filter Type Class 2", R.string.cancel, R.string.next);
        }
        else {
            bringUpDialog(charSeqFilterType, checkedItemsFilterType, R.string.title_include_exclude, endOfLargeFont,
                    "Filter Type Class", R.string.cancel, R.string.next);
        }
    }

    //  This is a utility function, called when DialogFilterAlerts class is invoked.  (That is, when one of the select-item-from-list dialog is shown).
    //      It is called from startUI() above to bring up DIALOG_INCLUDE_EXCLUDE, the first dialog.
    //      It is called from doCancel() below when the user cancels out of DIALOG_PREFIX_CALLSIGN, DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, or DIALOG_ALL_COUNTRIES.
    //          In that case it is necessary to again show DIALOG_REGION_OR_PREFIX, the next one up in the hierarchy.
    //      It is called from doNext() below when the user has selected Next, OK, or Finish button.  In all but DIALOG_REGION_OR_PREFIX dialog the next dialog
    //          is DIALOG_REGION_OR_PREFIX.  That is, when DIALOG_PREFIX_CALLSIGN, DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, or DIALOG_ALL_COUNTRIES are completed
    //          the DIALOG_REGION_OR_PREFIX is again displayed.
    //      It is called from doSelection() below when the DIALOG_REGION_OR_PREFIX dialog is up and user selected DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, or
    //          DIALOG_ALL_COUNTRIES.  (If user selected DIALOG_PREFIX_CALLSIGN then this is not called because that uses DialogPrefixCallsign class).
    private void bringUpDialog( CharSequence[] charSeqParam, boolean[] checkedItemsParam,
                                int titleParameter, int endOfLargeFontParameter, String dialogTag,
                                int middleText, int rightText ) {
        boolean selectAllParam = false;
        if ( (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) || (dialogName == DialogName.DIALOG_ALL_COUNTRIES)) {
            selectAllParam = true;
        }
        DialogFragment myListY;
        FragmentManager fragMan = thisContext.getSupportFragmentManager();
        myListY = (DialogFragment)fragMan.findFragmentByTag(dialogTag);
        if (myListY == null) {
            myListY = DialogFilterAlerts.newInstance( selectAllParam, charSeqParam, checkedItemsParam,
                    thisContext.getResources().getString(titleParameter), endOfLargeFontParameter,
                    middleText, rightText, true );
            myListY.show( fragMan, dialogTag);
            myListY.setCancelable(false);
        }
    }

    //  This was made into a method because it needed to be called from doCancel() and doBackButton() immediately below.
    private void doCancelHelp() {
        if (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) {
            prefixCallsignItems.clear();
            checkedItemsRegionPrefix[0] = false;        // uncheck "Input Prefix or Callsign"
        } else if (dialogName == DialogName.DIALOG_CONTINENT) {
            clearContinents();
            checkedItemsRegionPrefix[1] = false;        //  uncheck "List of Continents"
        } else if (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) {
            clearMajorCountries();
            checkedItemsRegionPrefix[2] = false;        //  uncheck "List of Major Countries"
            syncTheTwoCountryLists();
        } else if (dialogName == DialogName.DIALOG_ALL_COUNTRIES) {
            clearAllCountries();
            checkedItemsRegionPrefix[3] = false;        //  uncheck "List of All Countries"
            syncTheTwoCountryLists();
        }
        dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
        int endOfLargeFont = thisContext.getResources().getInteger(R.integer.filter_region_prefix_end_of_large_font);
        bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_region_prefix, endOfLargeFont,
                "Filter How To Class", R.string.cancel, R.string.finish);
    }

    //  This is called from the listeners of DialogFilterAlerts and DialogPrefixCallsign dialogs.  The button is called CANCEL or CLEAR-RETURN.  If
    //      the dialog was DIALOG_INCLUDE_EXCLUDE, DIALOG_PREVIOUS_FILTERS, or DIALOG_REGION_OR_PREFIX then it clears all selections and terminates the
    //      display.  If it is one of the other dialogs then it clears only that dialog's selections and unchecks that selection from DIALOG_REGION_OR_PREFIX.
    void doCancel() {
        logInfo("FiltersInterface.doCancel() *****");
        if ( (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) ||
                (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) || (dialogName == DialogName.DIALOG_PREVIOUS_FILTERS) ){
            //  One of four exit points (two are in in doNext(), one in doBackButton() ....
            clearAll();
            dialogName = DialogName.DIALOG_NONE;
        }
        else {
            doCancelHelp();
        }
    }

    //  This is called when the user presses the back button while any of the dialogs are up.  The behavior differs depending on which dialog is up.
    void doBackButton() {
        logInfo("FiltersInterface.doBackButton() *****");
        if (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) {          //  if back button pressed while first dialog is up then just quit all.
            //  One of four exit points (two are in in doNext(), one in doCancel() ....
            clearAll();
            dialogName = DialogName.DIALOG_NONE;
        }
        else if ( (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) || (dialogName == DialogName.DIALOG_PREVIOUS_FILTERS) ) {
            clearAll();
            startUI();
        }
        else {
            doCancelHelp();
        }
    }


    //  Called from the DialogFilterAlerts listeners.  It is called when the user makes a selection within the dialog box.  It only handles three dialogs,
    //      DIALOG_INCLUDE_EXCLUDE, DIALOG_PREVIOUS_FILTERS, and DIALOG_REGION_OR_PREFIX.  If called from one of the other four dialogs then it does nothing.
    //  If called from DIALOG_INCLUDE_EXCLUDE then it notes which item is selected and unchecks the others (this dialog only allows one selection).
    //  If called from DIALOG_REGION_OR_PREFIX then it will dismiss DIALOG_REGION_OR_PREFIX and bring up the appropriate dialog based on user selection.
    //      Note that if DIALOG_ALL_COUNTRIES is the selection then it will check if charSeqAllCountries[] and checkedItemsAllCountries[] have been
    //      initialized and, if not, initialize them.
    void doSelection(DialogFragment dialog, int which) {
        logInfo("FiltersInterface.doSelection() *****");

        //  cast DialogFragment as DialogFilterAlerts DialogFragment so it can access DialogFilterAlerts internal members
        DialogFilterAlerts thisDialogClass = (DialogFilterAlerts) dialog;
        if ( (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) || (dialogName == DialogName.DIALOG_PREVIOUS_FILTERS) ) {
            //  These two dialogs are mutually exclusive, only one selection can be valid.
            Dialog dialog3 = thisDialogClass.getDialog();    //  invoke DialogFragment.getDialog() to return a Dialog class
            AlertDialog dialog4 = (AlertDialog) dialog3;     //  cast as AlertDialog.  AlertDialog is a descendent of Dialog class.
            if (thisDialogClass.checkedItems[which]) {       //  if the one that was selected (parameter which) is now checked then uncheck all the others.
                ListView listView = dialog4.getListView();
                for (int iii = 0; iii < thisDialogClass.sizeOfArrays; iii++) {
                    if (iii == which) {
                        continue;       // don't uncheck the item just checked.
                    }
                    thisDialogClass.checkedItems[iii] = thisDialogClass.checkedItemsUsed[iii] = false;
                    listView.setItemChecked(iii, false);
                }
            }
            if (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) {
                //  If there are no previous filters stored in the LinkedList recentFilters then a substitute set of checkedItems[] was used.  Transfer any selection
                //      from that to checkedItemsFilterType.  If there are previous filters then this code sets a boolean that is already set, so no harm.
                if (thisDialogClass.checkedItems[0]) {
                    checkedItemsFilterType[0] = true;
                }
                if (thisDialogClass.checkedItems[1]) {
                    checkedItemsFilterType[1] = true;
                }
            }

        }
        else if (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) {
            thisDialogClass.dismiss();
            if (which == 0) {                           //  If the user selected prefix or callsign.
                //  if checkedItemsRegionPrefix[0] (available in this method as thisDialogClass.checkedItems[0]) is false then it was true before the user selected it.
                //      If there are any items in prefixCallsignItems[] that are true then set checkedItemsRegionPrefix[0] back to true.
                if (!thisDialogClass.checkedItems[0]) {
                    if (prefixCallsignItems.size() > 0) {
                        checkedItemsRegionPrefix[0] = true;
                    }
                }
                dialogName = DialogName.DIALOG_PREFIX_CALLSIGN;
                DialogFragment myListY = DialogPrefixCallsign.newInstance( prefixCallsignItems, true );
                myListY.show( thisContext.getSupportFragmentManager(), "DialogPrefixCallsign Class");
                myListY.setCancelable(false);
            }
            else if (which == 1) {                      //  ... or if user selected list of Continents
                //  if checkedItemsRegionPrefix[1] is clear then set it again if any elements of checkedItemsContinent[] are set true.
                if (!thisDialogClass.checkedItems[1]) {
                    for (int iii = 0; iii < checkedItemsContinent.length; iii++) {
                        if (checkedItemsContinent[iii]) {
                            checkedItemsRegionPrefix[1] = true;
                            break;
                        }
                    }
                }
                dialogName = DialogName.DIALOG_CONTINENT;
                int endOfLargeFont = thisContext.getResources().getInteger(R.integer.continent_end_of_large_font);
                bringUpDialog(charSeqContinent, checkedItemsContinent, R.string.title_continent, endOfLargeFont,
                        "Filter Continent Class", R.string.clearall, R.string.okay);
            }
            else if (which == 2) {                      //  ... or if user selected major countries.
                //  if checkedItemsRegionPrefix[2] is clear then set it again if any elements of checkedItemsMajorCountries[] are set true.
                if (!thisDialogClass.checkedItems[2]) {
                    for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                        if (checkedItemsMajorCountries[iii]) {
                            checkedItemsRegionPrefix[2] = true;
                            break;
                        }
                    }
                }
                dialogName = DialogName.DIALOG_MAJOR_COUNTRIES;
                int endOfLargeFont = thisContext.getResources().getInteger(R.integer.countries_end_of_large_font);
                bringUpDialog(charSeqMajorCountries, checkedItemsMajorCountries, R.string.title_countries, endOfLargeFont,
                        "Filter Major Countries Class", R.string.clearall, R.string.okay);
            }
            else if (which == 3) {                      //  ... or if user selected all countries.
                dialogName = DialogName.DIALOG_ALL_COUNTRIES;
                //  Unlike other windows, this list must be built here.  charSeqAllCountries could be a local variable but it would have to be built each time
                //      this code is entered.  checkedItemsAllCountries cannot be a local variable because its values need to be preserved.
                if (charSeqAllCountries == null) {
                    charSeqAllCountries = HelperCountriesList.getAllCountriesList();
                }
                if ((checkedItemsAllCountries == null)) { // && (charSeqAllCountries != null) ) {
                    checkedItemsAllCountries = new boolean[charSeqAllCountries.length];
                    for (int iii = 0; iii < charSeqAllCountries.length; iii++) {
                        checkedItemsAllCountries[iii] = false;
                    }
                }
                else {
                    //  if checkedItemsRegionPrefix[3] is clear then set it again if any elements of checkedItemsAllCountries[] are set true.
                    if (!thisDialogClass.checkedItems[3]) {
                        for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                            if (checkedItemsAllCountries[iii]) {
                                checkedItemsRegionPrefix[3] = true;
                                break;
                            }
                        }
                    }
                }
                int endOfLargeFont = thisContext.getResources().getInteger(R.integer.countries_end_of_large_font);
                bringUpDialog(charSeqAllCountries, checkedItemsAllCountries, R.string.title_countries, endOfLargeFont,
                        "Filter All Countries Class", R.string.clearall, R.string.okay);
            }
        }
    }


    //  This is called from the listeners of DialogFilterAlerts and DialogPrefixCallsign dialogs when the user chooses the Next, OK, or Finish.  (OK is used in
    //      DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, DIALOG_ALL_COUNTRIES, and DIALOG_PREFIX_CALLSIGN dialogs.  Next is used in DIALOG_INCLUDE_EXCLUDE and
    //      Finish is used in DIALOG_REGION_OR_PREFIX).
    //  In all but DIALOG_REGION_OR_PREFIX the action is to bring up DIALOG_REGION_OR_PREFIX dialog (it is the next state from DIALOG_INCLUDE_EXCLUDE and the
    //      other four dialogs all loop back to DIALOG_REGION_OR_PREFIX.  The one exception to this is if DIALOG_INCLUDE_EXCLUDE is up and the user selected
    //      to reuse previous filters.  It then builds the list of previous filters and displays them in a DIALOG_PREVIOUS_FILTERS dialog.
    //  In DIALOG_REGION_OR_PREFIX then the button is labelled Finish and the action is to create the filter and, after that is done, clear all selectins.
    void doNext() {
        //  The user selection should already be in the appropriate checkedItems*[] array.
        if ( (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) || (dialogName == DialogName.DIALOG_CONTINENT) ||
                (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) || (dialogName == DialogName.DIALOG_ALL_COUNTRIES) ||
                (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) ) {
            if ( (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) && ( checkedItemsFilterType[2] || checkedItemsFilterType[3] ) ) {

                //  Special case if user chose REUSE or EDIT Previous Filters from DIALOG_INCLUDE_EXCLUDE
                logInfo("FiltersInterface.doNext() Previous Filters Selected  *****");
                if ( recentFilters.size() > 0 ) {       //  should never happen that this is called when recentFilters.size() == 0
                    dialogName = DialogName.DIALOG_PREVIOUS_FILTERS;
                    charSeqPreviousFilters = null;                              //  clear out any elements in the array
                    charSeqPreviousFilters = new String[recentFilters.size()];  //  create a new array
                    int iii = 0;
                    Iterator iter1 = recentFilters.iterator();                  //  loop through all the filters in the LinkedList recentFilters
                    while (iter1.hasNext()) {
                        Filters tempFilter = (Filters) iter1.next();
                        String description = tempFilter.describeFilter( thisContext );  //  generate a description for this filter ...
                        charSeqPreviousFilters[iii] = description;                      //  ... and store it in the array.
                        iii++;
                    }
                    checkedItemsPreviousFilters = null;                         //  clear out any elements of checkedItems array, create a new one, and initialize.
                    checkedItemsPreviousFilters = new boolean[recentFilters.size()];
                    for (iii = 0; iii < recentFilters.size(); iii++) {
                        checkedItemsPreviousFilters[iii] = false;
                    }
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.previous_end_of_large_font);
                    bringUpDialog(charSeqPreviousFilters, checkedItemsPreviousFilters, R.string.title_previous_filters, endOfLargeFont,
                            "Filter Prevous Class", R.string.cancel, R.string.finish);
                }
            }
            else {
                //  If just left DIALOG_MAJOR_COUNTRIES or DIALOG_ALL_COUNTRIES then sync the lists.
                syncTheTwoCountryLists();

                dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
                int endOfLargeFont = thisContext.getResources().getInteger(R.integer.filter_region_prefix_end_of_large_font);
                bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_region_prefix, endOfLargeFont,
                        "Filter How To Class", R.string.cancel, R.string.finish);
            }
        }
        else if ( dialogName == DialogName.DIALOG_REGION_OR_PREFIX ) {
            //  Here is one of four exit points (one in doCancel(), one in doBackButton(), the third is below else-if statement).  This and the below
            //      exit point are the only ones where a filter will be applied ....
            //  .... FILTER SELECTION IS COMPLETE, create the Filters object, write linked list to file, and clear all data.
            logInfo("FiltersInterface.doNext()  creating filterObject  *****");
            filterObject = new Filters(checkedItemsFilterType, checkedItemsContinent, charSeqMajorCountries, checkedItemsMajorCountries,
                    charSeqAllCountries, checkedItemsAllCountries, prefixCallsignItems );
            finishWithNewFilter();
        }
        else if ( dialogName == DialogName.DIALOG_PREVIOUS_FILTERS ) {
            //  Find the filter that the user selected, get the index into selectionIndex and load it into filterObject.
            int selectionIndex;
            for (selectionIndex = 0; selectionIndex < checkedItemsPreviousFilters.length; selectionIndex++) {
                if (checkedItemsPreviousFilters[selectionIndex]) {
                    break;
                }
            }
            //  selectionIndex represents the index of the filter selected.  Move that to front of list, assigning filterObject to the selected filter.
            filterObject = recentFilters.get(selectionIndex);       //  get the selected filter.
            filterObject.updateCountryNames( thisContext );

            if (checkedItemsFilterType[2]) {
                //  If user is choosing to REUSE a previous filter
                //
                //  Here is one of four exit points (one in doCancel(), one in doBackButton(), the other is in above else-if statement).  This and the above
                //      exit point are the only ones where a filter will be applied ....
                //  .... FILTER SELECTION IS COMPLETE, recall the Filters object from LinkedList, remove old entry from LinkedList, write recalled filter
                //      object to the front of LinkedList (done in writeLinkedList() and clear all data.
                logInfo("FiltersInterface.doNext() reusing filterObject " + selectionIndex + " *****");
                recentFilters.remove(selectionIndex);                   //  remove it from list.
                finishWithNewFilter();                                  //  before writing to file this will add filterObject to front of linkedlist
            } else if (checkedItemsFilterType[3]) {
                //  If user is choosing to EDIT a previous filter
                //  .... remove old entry from LinkedList, write recalled filter object to the front of LinkedList (done in writeLinkedList() wich is called
                //       from finishWithNewFiler()) and clear all data.
                if (prepareToEditFilter()) {
                    dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.filter_region_prefix_end_of_large_font);
                    bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_region_prefix, endOfLargeFont,
                            "Filter How To Class", R.string.cancel, R.string.finish);
                    logInfo("FiltersInterface.doNext() editing filterObject " + selectionIndex + " *****");
                } else {                //   shouldn't ever happen
                    //  If prepareToEditFilter() returned an error (shouldn't happen).
                    String toastString = String.format(Locale.US, "%s %s",
                            thisContext.getResources().getString(R.string.filter_edit_error), filterObject.describeFilter(thisContext));
                    Toast.makeText(thisContext, toastString, Toast.LENGTH_LONG).show();
                    clearAll();
                    dialogName = DialogName.DIALOG_NONE;
                    logInfo("FiltersInterface.doNext() editing - error in prepareToEditFilter() *****");
                }
            }
        }
    }

    //  This is nearly identical to a corresponding method in AlertsInterface.
    void syncTheTwoCountryLists() {
        if ( (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) || (dialogName == DialogName.DIALOG_ALL_COUNTRIES) ) {
            logInfo("FiltersInterface.syncTheTwoCountryLists() *****");
            //  First check to see if charSeqAllCountries and checkedItemsAllCountries are created.  They will be needed.
            if (charSeqAllCountries == null) {
                charSeqAllCountries = HelperCountriesList.getAllCountriesList();
            }
            if ((checkedItemsAllCountries == null)) { // && (charSeqAllCountries != null) ) {
                checkedItemsAllCountries = new boolean[charSeqAllCountries.length];
                for (int iii = 0; iii < charSeqAllCountries.length; iii++) {
                    checkedItemsAllCountries[iii] = false;
                }
            }

            //
            //  If just left major countries ...
            if (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) {
                //  Go through each item in major countries
                for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                    //  Find the corresponding item in all countries and check or uncheck it based on what is in major countries.
                    for (int jjj = 0; jjj < charSeqAllCountries.length; jjj++) {
                        if ( charSeqMajorCountries[iii].equals( charSeqAllCountries[jjj] )) {
                            checkedItemsAllCountries[jjj] = checkedItemsMajorCountries[iii];
                            break;
                        }
                    }
                }
                //  Now independently go through all countries and see if any items are checked ...
                checkedItemsRegionPrefix[3] = false;
                for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                    if (checkedItemsAllCountries[iii]) {
                        checkedItemsRegionPrefix[3] = true;
                        break;
                    }
                }
            }

            //
            //  If just left all countries
            if (dialogName == DialogName.DIALOG_ALL_COUNTRIES) {
                //  Go through each item in all countries
                for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                    //  See if there is a corresponding item in major countries and check or uncheck it based on what is in all countries.
                    for (int jjj = 0; jjj < charSeqMajorCountries.length; jjj++) {
                        if ( charSeqAllCountries[iii].equals( charSeqMajorCountries[jjj] )) {
                            checkedItemsMajorCountries[jjj] = checkedItemsAllCountries[iii];
                            break;
                        }
                    }
                }
                //  Now independently go through major countries and see if any items are checked ...
                checkedItemsRegionPrefix[2] = false;
                for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                    if (checkedItemsMajorCountries[iii]) {
                        checkedItemsRegionPrefix[2] = true;
                        break;
                    }
                }

            }
        }
    }

    //  This block of code was common to the two places immediately above where it says ALERT SELECTION IS COMPLETE so I moved it here.
    private void finishWithNewFilter() {
        writeLinkedList();
        mService.filterObject = filterObject.copyOf();          //  make a copy of the Filters class for UDPService.

        clearAll();
        dialogName = DialogName.DIALOG_NONE;

        int numFilters = WSJTXUtils.getAndBumpNumberOfFilterUses( thisContext );
        String filterDescription = filterObject.describeFilter( thisContext );

        String toastString = String.format(Locale.US, "%s %s",
                thisContext.getResources().getString(R.string.filter_toast), filterDescription );
        Toast.makeText(thisContext, toastString, Toast.LENGTH_LONG).show();
        logInfo("     Filter use number "+numFilters+" *****");
    }

    //  This method is called when the user wants to edit a previously used filter.  Its purpose is to setup all the checkedItems*[] arrays.
    //      It reads private variables from the recalled Filters object.
    //  It returns false on error or true on success.
    private boolean prepareToEditFilter() {
        if (filterObject == null) {
            return false;
        }
        if (!filterObject.getFilterInitialized()) {
            return false;
        }

        checkedItemsFilterType[0] = checkedItemsFilterType[1] = checkedItemsFilterType[2] = checkedItemsFilterType[3] = false;
        if (filterObject.getFilterIncludeFilter()) {
            checkedItemsFilterType[0] = true;
        } else {
            checkedItemsFilterType[1] = true;
        }

        //  Retrieve the important elements from the selected filter.
        ArrayList<String> prefixCallsigns = filterObject.getFilterPrefixCallsigns();
        ArrayList<String> continents = filterObject.getFilterContinents();
        ArrayList<String> countries = filterObject.getFilterCountries();

        //  Set the DIALOG_REGION_OR_PREFIX items to be null
        checkedItemsRegionPrefix[0] = checkedItemsRegionPrefix[1] = checkedItemsRegionPrefix[2] = checkedItemsRegionPrefix[3] = false;

        //  Copy any prefix-callsign items to prefixCallsignItems<>.  If any items then set checkedItemsRegionPrefix[0]
        prefixCallsignItems.clear();
        if (prefixCallsigns != null) {
            prefixCallsignItems = new ArrayList<>(prefixCallsigns);
            checkedItemsRegionPrefix[0] = true;
        }

        //  Set checkedItemsContinent[].  If any indices are true then set checkedItemsRegionPrefix[1]
        //for (int iii = 0; iii < checkedItemsContinent.length; iii++) { checkedItemsContinent[iii] = false; }
        Arrays.fill(checkedItemsContinent, false);
        if (continents != null) {
            String[] continentAbbreviations = {"AF", "AS", "EU", "NA", "SA", "OC"};
            for (int iii = 0; iii < continents.size(); iii++) {
                for (int jjj = 0; jjj < continentAbbreviations.length; jjj++) {
                    if (continentAbbreviations[jjj].equals(continents.get(iii))) {
                        checkedItemsContinent[jjj] = true;
                        checkedItemsRegionPrefix[1] = true;
                        break;
                    }
                }
            }
        }

        //for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) { checkedItemsMajorCountries[iii] = false; }
        Arrays.fill(checkedItemsMajorCountries, false);
        if (countries != null) {
            //  Go through each item in countries<>, set corresponding checkedItemsMajorCountries[] item.  If any item is checked then set
            //      checkedItemsRegionPrefix[3].
            for (int iii = 0; iii < countries.size(); iii++) {
                int jjj;
                //  ... and see if it matches any country name in charSeqMajorCountries[]
                for (jjj = 0; jjj < charSeqMajorCountries.length; jjj++) {
                    if ( countries.get(iii).contentEquals( charSeqMajorCountries[jjj] )) {
                        //  ... and if it does match then ...
                        checkedItemsMajorCountries[jjj] = true;     //  ... set the corresponding checkedItemsMajorCountries[] element
                        checkedItemsRegionPrefix[2] = true;         //  ... set the upper level checkedItemsRegionPrefix[2] (the checked item for Major Countries)
                        break;                                      //  ... and quit searching through charSeqMajorCountries[] since we've found it.
                    }
                }
            }

            if (charSeqAllCountries == null) {
                charSeqAllCountries = HelperCountriesList.getAllCountriesList();
            }
            if ((checkedItemsAllCountries == null)) { // && (charSeqAllCountries != null) ) {
                checkedItemsAllCountries = new boolean[charSeqAllCountries.length];
                for (int iii = 0; iii < charSeqAllCountries.length; iii++) {
                    checkedItemsAllCountries[iii] = false;
                }
            }

            //  Now go through each item in countries<> and look for it in charSeqAllCountries[].  Note that this will result in country name in
            //      charSeqMajorCountries[] will also get checked in checkedItemsAllCountries[].
            for (int iii = 0; iii < countries.size(); iii++) {
                //  ... and see if it matches any country name in charSeqMajorCountries[]
                for (int jjj = 0; jjj < charSeqAllCountries.length; jjj++) {
                    if ( countries.get(iii).contentEquals( charSeqAllCountries[jjj] )) {
                        //  ... and if it does match then ...
                        checkedItemsAllCountries[jjj] = true;     //  ... set the corresponding checkedItemsAllCountries[] element
                        checkedItemsRegionPrefix[3] = true;         //  ... set the upper level checkedItemsRegionPrefix[2] (the checked item for All Countries)
                        break;                                      //  ... and quit searching through charSeqAllCountries[] since we've found it.
                    }
                }
            }
        }

        return true;
    }

    private boolean writeLinkedList() {
        FileOutputStream outputStream;
        boolean returnValue = false;

        try {
            //  Check that the new filter isn't a duplicate of an already existing filter.  If so, delete the older one.
            Iterator iter1 = recentFilters.iterator();                  //  loop through all the filters in the LinkedList recentFilters
            while (iter1.hasNext()) {
                Filters tempFilter = (Filters) iter1.next();
                if (filterObject.equals( tempFilter )) {
                    iter1.remove();
                }
            }
            //  Add most recent filter to beginning of linked list before writing to file (method .add() adds to end of linked list).
            recentFilters.addFirst(filterObject);
            //  Limit the size of the linked list.
            if (recentFilters.size() > MAX_NUM_RECENT_FILTERS) {
                recentFilters.removeLast();
            }

            //  Now write to file.
            outputStream = thisContext.openFileOutput( RECENT_FILTERS_FILENAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);
            oos.writeObject(recentFilters);
            oos.close();
            outputStream.close();
            returnValue = true;
            logInfo("FiltersInterface.writeLinkedList() - File Write success *****");
        } catch (Exception ex) {
            logInfo("FiltersInterface.writeLinkedList() - Exception "+ex.getMessage()+" *****");
        }

        recentFilters.clear();
        return returnValue;
    }


    @SuppressWarnings("unchecked")      // Warning on line "recentFilters = (LinkedList<Filters>)ois.readObject();"  The compiler can't guarantee that
    //      what is in the file is really LinkedList<Filters>.
    private boolean readLinkedList() {
        FileInputStream inputStream;
        boolean returnValue = false;

        recentFilters.clear();      //  before reading in filters, clear out the linked list.  (Ex - if file is deleted then without this clear the LinkedList will still have the
        //      contents of last read.  An exception will occur before it has a chance to read from file so LinkedList will not change.
        try {
            inputStream = thisContext.openFileInput(RECENT_FILTERS_FILENAME);
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            recentFilters = (LinkedList<Filters>)ois.readObject();
            ois.close();
            inputStream.close();
            returnValue = true;
            logInfo("FiltersInterface.readLinkedList() - File Read success.  # of filters "+recentFilters.size()+" *****");
        } catch (Exception ex) {
            logInfo("FiltersInterface.readLinkedList() - Exception "+ex.getMessage()+" *****");
        }

        return returnValue;
    }

    //  This is called from the listeners of DialogFilterAlerts and DialogPrefixCallsign dialogs when the user chooses the Help button.
    void doHelp() {
        logInfo("FiltersInterface.doHelp() *****");
        int resource;
        if (dialogName == DialogName.DIALOG_INCLUDE_EXCLUDE) {
            resource = R.string.help_include_exclude;
        }
        else if (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) {
            resource = R.string.help_region_prefix;
        }
        else if (dialogName == DialogName.DIALOG_PREVIOUS_FILTERS) {
            resource = R.string.help_previous_filters;
        }
        else if (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) {
            resource = R.string.help_prefix_callsign;
        }
        else if (dialogName == DialogName.DIALOG_CONTINENT) {
            resource = R.string.help_continent;
        }
        else if (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) {
            resource = R.string.help_major_countries;
        }
        else if (dialogName == DialogName.DIALOG_ALL_COUNTRIES) {
            resource = R.string.help_all_countries;
        }
        else {
            return;
        }
        DialogFragment helpY = BasicClassHelp.newInstance( resource );
        helpY.show( thisContext.getSupportFragmentManager(), "Help class");
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }
}

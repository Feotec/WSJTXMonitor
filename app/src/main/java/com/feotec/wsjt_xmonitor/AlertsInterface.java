package com.feotec.wsjt_xmonitor;

import android.app.Dialog;
import android.content.Context;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ListView;
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

class AlertsInterface {
    private static final String TAG = AlertsInterface.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingAlertsInterface;

    private static final String RECENT_ALERTS_FILENAME = "RecentAlertsFile";
    private static final int MAX_NUM_RECENT_ALERTS = 10;

    //  private variables
    private enum DialogName {
        DIALOG_NONE,
        DIALOG_NEW_OR_REUSE, DIALOG_REGION_OR_PREFIX, DIALOG_CONTINENT, DIALOG_MAJOR_COUNTRIES, DIALOG_ALL_COUNTRIES,
        DIALOG_PREFIX_CALLSIGN, DIALOG_PREVIOUS_ALERTS, DIALOG_ALERT_SETTINGS, DIALOG_MIN_SIGNAL
    }

    private AppCompatActivity thisContext;
    private Menu mainMenu;
    private DialogName dialogName = DialogName.DIALOG_NONE;

    private CharSequence[] charSeqNewReuse = null;
    private boolean[] checkedItemsNewReuse = { false, false, false };
    private CharSequence[] charSeqRegionPrefix = null; //{ "Alert on My Callsign", "Alert on All Stations", "Prefix or Callsign", "List of Continents", "List of Major Countries", "List of All Countries" };
    private boolean[] checkedItemsRegionPrefix = { false, false, false, false, false, false };
    private CharSequence[] charSeqContinent = null; //{ "Africa", "Asia", "Europe", "North America", "South America", "Oceania" };
    private boolean[] checkedItemsContinent = { false, false, false, false, false, false };
    private CharSequence[] charSeqMajorCountries = null; //{ "Argentina", "Australia", "Austria", "Brazil", "Britain", "Canada", "China", "Chile", "Denmark", "Finland", ...
    private boolean[] checkedItemsMajorCountries;
    private CharSequence[] charSeqAllCountries = null;
    private boolean[] checkedItemsAllCountries = null;
    private ArrayList<String> prefixCallsignItems = new ArrayList<>();
    private CharSequence[] charSeqPreviousAlerts = null;   //  can be a local variable
    private boolean[] checkedItemsPreviousAlerts = null;

    private boolean[] checkedItemsSoundVibration = { false, false, false, false  };     //  "Sound and Vibrate", "Sound only", "Vibrate only", "Silence"
    private int minSignal = Alerts.NO_MIN_SIGNAL;
    private boolean rememberStation = true;

    static Alerts alertObject = null;
    private UDPService mService = null;

    private LinkedList<Alerts> recentAlerts = new LinkedList<>();

    //  constructor
    AlertsInterface(AppCompatActivity context, UDPService service, Menu mainMenuParam ) {
        logInfo("AlertsInterface constructor called *****");
        setContext( context );
        setMainMenu( mainMenuParam );
        mService = service;
        charSeqNewReuse = thisContext.getResources().getStringArray(R.array.alert_type_values);
        charSeqRegionPrefix = thisContext.getResources().getStringArray(R.array.alert_region_prefix_values);
        charSeqContinent = thisContext.getResources().getStringArray(R.array.continent_list_values);
        charSeqMajorCountries = thisContext.getResources().getStringArray(R.array.major_countries_list_values);
        checkedItemsMajorCountries = new boolean[charSeqMajorCountries.length];
        Arrays.fill(checkedItemsMajorCountries, false);
        clearAll();
        startUI();
    }

    //  This is called from the constructor above.  It is also called from onResumeFragments() when the screen is rotated or app moves to background.  This second
    //      call is necessary because the context disappears and is created again during screen rotations or app in background.
    void setContext(AppCompatActivity context) {
        thisContext = context;
    }

    //  Similar to the setContext() method, this is called from the constructor above and from MainActivity.onPrepareOptionsMenu().
    void setMainMenu( Menu mainMenuParam ) { mainMenu = mainMenuParam; }

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
        }  */
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

    private void clearAlertOptions() {
        /*
        for (int iii = 0; iii < checkedItemsSoundVibration.length; iii++) {
            checkedItemsSoundVibration[iii] = false;
        } */
        Arrays.fill(checkedItemsSoundVibration, false);
        if (mService.deviceHasVibrator()) {
            checkedItemsSoundVibration[0] = true;               // default for most devices.
        } else {
            checkedItemsSoundVibration[1] = true;               // default for devices that do not vibrate
        }
        minSignal = Alerts.NO_MIN_SIGNAL;
        rememberStation = true;
    }

    private void clearAll() {
        int iii;
        for (iii = 0; iii < checkedItemsNewReuse.length; iii++) {
            checkedItemsNewReuse[iii] = false;
        }
        for (iii = 0; iii < checkedItemsRegionPrefix.length; iii++) {
            checkedItemsRegionPrefix[iii] = false;
        }
        prefixCallsignItems.clear();
        clearContinents();
        clearMajorCountries();
        clearAllCountries();
        clearAlertOptions();
        logInfo("AlertsInterface.clearAll() *****");
    }

    void startUI() {
        if ( (readLinkedList()) && (recentAlerts.size() > 0) ) {
            dialogName = DialogName.DIALOG_NEW_OR_REUSE;
            int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_new_reuse_end_of_large_font);
            bringUpDialog(charSeqNewReuse, checkedItemsNewReuse, R.string.title_alert_new_reuse, endOfLargeFont,
                    "New Alert", R.string.cancel, R.string.next);
        }
        else {
            dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
            int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_region_prefix_end_of_large_font);
            bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_alerts_region_prefix, endOfLargeFont,
                    "Alert on what", R.string.cancel, R.string.next);
        }
    }

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
                    middleText, rightText, false );
            myListY.show( fragMan, dialogTag);
            myListY.setCancelable(false);
        }
    }

    private void bringUpAlertsSettings() {
        dialogName = DialogName.DIALOG_ALERT_SETTINGS;
        DialogFragment alertSettingsDialog;
        FragmentManager fragMan = thisContext.getSupportFragmentManager();
        alertSettingsDialog = (DialogFragment)fragMan.findFragmentByTag("Alert Setting Tag");
        if (alertSettingsDialog == null) {
            alertSettingsDialog = DialogAlertSettings.newInstance( checkedItemsSoundVibration, minSignal, rememberStation, mService.deviceHasVibrator() );
            alertSettingsDialog.show( fragMan, "Alert Setting Tag");
            alertSettingsDialog.setCancelable(false);
        }
    }

    //  This was made into a method because it needed to be called from doCancel() and doBackButton() immediately below.
    private void doCancelHelp() {
        if (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) {
            prefixCallsignItems.clear();
            checkedItemsRegionPrefix[2] = false;        // uncheck "Input Prefix or Callsign"
        } else if (dialogName == DialogName.DIALOG_CONTINENT) {
            clearContinents();
            checkedItemsRegionPrefix[3] = false;        //  uncheck "List of Continents"
        } else if (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) {
            clearMajorCountries();
            checkedItemsRegionPrefix[4] = false;        //  uncheck "List of Major Countries"
            syncTheTwoCountryLists();
        } else if (dialogName == DialogName.DIALOG_ALL_COUNTRIES) {
            clearAllCountries();
            checkedItemsRegionPrefix[5] = false;        //  uncheck "List of All Countries"
            syncTheTwoCountryLists();
        }
        dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
        int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_region_prefix_end_of_large_font);
        bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_alerts_region_prefix, endOfLargeFont,
                "Alert on what", R.string.cancel, R.string.next);
    }

    void doCancel() {
        logInfo("AlertsInterface.doCancel() *****");
        if ( (dialogName == DialogName.DIALOG_NEW_OR_REUSE) || (dialogName == DialogName.DIALOG_REGION_OR_PREFIX)
                    || (dialogName == DialogName.DIALOG_PREVIOUS_ALERTS) || (dialogName == DialogName.DIALOG_ALERT_SETTINGS) ){
            //  One of four exit points (two are in in doNext(), one in doBackButton() ....
            clearAll();
            dialogName = DialogName.DIALOG_NONE;
            doReplyIcon( false );
        }
        else if ( dialogName == DialogName.DIALOG_MIN_SIGNAL ) {
            bringUpAlertsSettings();
        }
        else {
            doCancelHelp();
        }
    }

    void doBackButton() {
        logInfo("AlertsInterface.doBackButton() *****");
        //  if back button pressed while first dialog is up then just quit all.
        if ( (dialogName == DialogName.DIALOG_NEW_OR_REUSE) ||
                ( (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) && (recentAlerts.size() == 0) ) ) {
            //  One of four exit points (two are in in doNext(), one in doCancel() ....
            clearAll();
            dialogName = DialogName.DIALOG_NONE;
            doReplyIcon( false );
        }
        //  if back button pressed while second dialog is up then go back to first dialog.  This is easy to do by just clearing and calling startUI().
        else if ( (dialogName == DialogName.DIALOG_PREVIOUS_ALERTS) ||
                  (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) ) {  // && (recentAlerts.size() > 0) ) ) {  The recentAlerts.size() > 0 is implied.  It wouldn't get here otherwise.
            clearAll();
            startUI();
        }
        //  if DIALOG_ALERT_SETTINGS is up while back button is pressed then go back to DIALOG_REGION_OR_PREFIX
        else if ( dialogName == DialogName.DIALOG_ALERT_SETTINGS ) {
            clearAlertOptions();
            dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
            int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_region_prefix_end_of_large_font);
            bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_alerts_region_prefix, endOfLargeFont,
                    "Alert on what", R.string.cancel, R.string.next);
        }
        else if ( dialogName == DialogName.DIALOG_MIN_SIGNAL ) {
            bringUpAlertsSettings();
        }
        else {
            doCancelHelp();
        }
    }

    void doSelection(DialogFragment dialog, int which) {
        logInfo("AlertsInterface.doSelection() *****");

        //  Dialogs that are mutually exclusive, only one selection can be valid.
        if ( (dialogName == DialogName.DIALOG_NEW_OR_REUSE) || (dialogName == DialogName.DIALOG_PREVIOUS_ALERTS) ){
            DialogFilterAlerts thisDialogClass = (DialogFilterAlerts) dialog;       //  cast DialogFragment as DialogFilterAlerts so it can access DialogFilterAlerts internal members
            Dialog dialog3 = thisDialogClass.getDialog();                           //  invoke DialogFragment.getDialog() to return a Dialog class
            AlertDialog dialog4 = (AlertDialog) dialog3;
            if (thisDialogClass.checkedItems[which]) {                              //  if the one that was selected (parameter which) is now checked then uncheck all the others.
                ListView listView = dialog4.getListView();
                for (int iii = 0; iii < thisDialogClass.sizeOfArrays; iii++) {
                    if (iii == which) {
                        continue;       // don't uncheck the item just checked.
                    }
                    thisDialogClass.checkedItems[iii] = thisDialogClass.checkedItemsUsed[iii] = false;
                    listView.setItemChecked(iii, false);
                }
            }
        }
        else if (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) {
            DialogFilterAlerts thisDialogClass = (DialogFilterAlerts) dialog;       //  cast DialogFragment as DialogFilterAlerts so it can access DialogFilterAlerts internal members
            Dialog dialog3 = thisDialogClass.getDialog();                           //  invoke DialogFragment.getDialog() to return a Dialog class
            AlertDialog dialog4 = (AlertDialog) dialog3;                            //  cast as AlertDialog.  AlertDialog is a descendent of Dialog class.
            ListView listView = dialog4.getListView();
            if (which == 1) {                                                   //  if "Alert on all" checked then uncheck all the others.
                if (thisDialogClass.checkedItems[1]) {
                    for (int iii = 2; iii < thisDialogClass.sizeOfArrays; iii++) {
                        thisDialogClass.checkedItems[iii] = thisDialogClass.checkedItemsUsed[iii] = false;
                        listView.setItemChecked(iii, false);
                    }
                }
                prefixCallsignItems.clear();                                    //  ... and clear all the elements associated with the other four options (locations and callsigns).
                clearContinents();
                clearMajorCountries();
                clearAllCountries();
            }
            else if (which != 0) {                                               //  If items 2-5 selected ...
                //  The if/else is structured like this because selections 2-5 will result in this Dialog being dismissed and another dialog
                //      (callsigns/prefix, continents, major countries, all countries) brought up.
                thisDialogClass.checkedItems[1] = thisDialogClass.checkedItemsUsed[1] = false;                        //  ... then "Alert on all" must be unchecked.
                listView.setItemChecked(1, false);

                thisDialogClass.dismiss();
                if (which == 2) {                           //  If the user selected prefix or callsign.
                    //  if checkedItemsRegionPrefix[2] (available in this method as thisDialogClass.checkedItems[2]) is false then it was true before the user selected it.
                    //      If there are any items in prefixCallsignItems[] that are true then set checkedItemsRegionPrefix[2] back to true.
                    //  The idea is that if the user wants to add more callsigns/prefixes I don't want to clear this checkbox.
                    if (!thisDialogClass.checkedItems[which]) {
                        if (prefixCallsignItems.size() > 0) {
                            checkedItemsRegionPrefix[which] = true;
                        }
                    }
                    dialogName = DialogName.DIALOG_PREFIX_CALLSIGN;
                    DialogFragment myListY = DialogPrefixCallsign.newInstance( prefixCallsignItems, false );
                    myListY.show( thisContext.getSupportFragmentManager(), "DialogPrefixCallsign Class");
                    myListY.setCancelable(false);
                }
                else if (which == 3) {                      //  ... or if user selected list of Continents
                    //  if checkedItemsRegionPrefix[3] is clear then set it again if any elements of checkedItemsContinent[] are set true.
                    //      The idea is that if the user wants to add more continents then this checkbox shouldn't be clear.
                    if (!thisDialogClass.checkedItems[which]) {
                        for (int iii = 0; iii < checkedItemsContinent.length; iii++) {
                            if (checkedItemsContinent[iii]) {
                                checkedItemsRegionPrefix[which] = true;
                                break;
                            }
                        }
                    }
                    dialogName = DialogName.DIALOG_CONTINENT;
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.continent_end_of_large_font);
                    bringUpDialog(charSeqContinent, checkedItemsContinent, R.string.title_continent, endOfLargeFont,
                            "Alert Continent Class", R.string.clearall, R.string.okay);
                }
                else if (which == 4) {                      //  ... or if user selected major countries.
                    //  if checkedItemsRegionPrefix[2] is clear then set it again if any elements of checkedItemsMajorCountries[] are set true.
                    if (!thisDialogClass.checkedItems[which]) {
                        for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                            if (checkedItemsMajorCountries[iii]) {
                                checkedItemsRegionPrefix[which] = true;
                                break;
                            }
                        }
                    }
                    dialogName = DialogName.DIALOG_MAJOR_COUNTRIES;
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.countries_end_of_large_font);
                    bringUpDialog(charSeqMajorCountries, checkedItemsMajorCountries, R.string.title_countries, endOfLargeFont,
                            "Alert Major Countries Class", R.string.clearall, R.string.okay);
                }
                else if (which == 5) {                      //  ... or if user selected all countries.
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
                    //  if checkedItemsRegionPrefix[which] is clear then set it again if any elements of checkedItemsAllCountries[] are set true.
                    if (!thisDialogClass.checkedItems[which]) {
                        for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                            if (checkedItemsAllCountries[iii]) {
                                checkedItemsRegionPrefix[which] = true;
                                break;
                            }
                        }
                    }
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.countries_end_of_large_font);
                    bringUpDialog(charSeqAllCountries, checkedItemsAllCountries, R.string.title_countries, endOfLargeFont,
                            "Alert All Countries Class", R.string.clearall, R.string.okay);
                }
            }
        }
        else if (dialogName == DialogName.DIALOG_ALERT_SETTINGS) {
            //  The only time this will be called when dialogName == DialogName.DIALOG_ALERT_SETTINGS is when the min signal level button is pressed.
            logInfo("AlertsInterface.doSelection() DIALOG_ALERT_SETTINGS *****");
            DialogAlertSettings thisDialogClass = (DialogAlertSettings) dialog;
            rememberStation = thisDialogClass.rememberStation;
            thisDialogClass.dismiss();
            //  Bring up DIALOG_MIN_SIGNAL.
            dialogName = DialogName.DIALOG_MIN_SIGNAL;
            DialogFragment minSignalDialog;
            FragmentManager fragMan = thisContext.getSupportFragmentManager();
            minSignalDialog = (DialogFragment)fragMan.findFragmentByTag("Min Signal Tag");
            if (minSignalDialog == null) {
                minSignalDialog = DialogMinSignal.newInstance( minSignal );
                minSignalDialog.show( fragMan, "Min Signal Tag");
                minSignalDialog.setCancelable(false);
            }
        }
    }

    void doNext(DialogFragment dialog) {
        logInfo("AlertsInterface.doNext() *****");
        if ( (dialogName == DialogName.DIALOG_NEW_OR_REUSE) || (dialogName == DialogName.DIALOG_CONTINENT) ||
                (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) || (dialogName == DialogName.DIALOG_ALL_COUNTRIES) ||
                (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) ) {
            if ( (dialogName == DialogName.DIALOG_NEW_OR_REUSE) && ( (checkedItemsNewReuse[1]) || (checkedItemsNewReuse[2]) ) ) {

                //  Special case if user chose Previous Alerts from DIALOG_NEW_OR_REUSE
                logInfo("AlertsInterface.doNext() Previous Alerts Selected  *****");
                if ( recentAlerts.size() > 0 ) {       //  should never happen that this is called when recentFilters.size() == 0
                    dialogName = DialogName.DIALOG_PREVIOUS_ALERTS;
                    charSeqPreviousAlerts = null;                                   //  clear out any elements in the array
                    charSeqPreviousAlerts = new String[recentAlerts.size()];        //  create a new array
                    int iii = 0;
                    Iterator iter1 = recentAlerts.iterator();                       //  loop through all the filters in the LinkedList recentFilters
                    while (iter1.hasNext()) {
                        Alerts tempAlert = (Alerts) iter1.next();
                        String description = tempAlert.describeAlert( thisContext );    //  generate a description for this filter ...
                        charSeqPreviousAlerts[iii] = description;                       //  ... and store it in the array.
                        iii++;
                    }
                    checkedItemsPreviousAlerts = null;                              //  clear out any elements of checkedItems array, create a new one, and initialize.
                    checkedItemsPreviousAlerts = new boolean[recentAlerts.size()];
                    for (iii = 0; iii < recentAlerts.size(); iii++) {
                        checkedItemsPreviousAlerts[iii] = false;
                    }
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_previous_alerts_end_of_large_font);
                    bringUpDialog(charSeqPreviousAlerts, checkedItemsPreviousAlerts, R.string.title_previous_alerts, endOfLargeFont,
                            "Alerts Prevous Class", R.string.cancel, R.string.finish);
                }
            }
            else {
                //  If just left DIALOG_MAJOR_COUNTRIES or DIALOG_ALL_COUNTRIES then sync the lists.
                syncTheTwoCountryLists();

                //  Transition to DIALOG_REGION_OR_PREFIX
                dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
                int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_region_prefix_end_of_large_font);
                bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_alerts_region_prefix, endOfLargeFont,
                        "Alert on what", R.string.cancel, R.string.next);
            }
        }
        else if (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) {
            bringUpAlertsSettings();
        }
        else if (dialogName == DialogName.DIALOG_MIN_SIGNAL) {
            DialogMinSignal thisDialogClass = (DialogMinSignal) dialog;
            minSignal = thisDialogClass.minSignal;
            bringUpAlertsSettings();
        }
        else if ( dialogName == DialogName.DIALOG_ALERT_SETTINGS ) {
            DialogAlertSettings thisDialogClass = (DialogAlertSettings) dialog;
            rememberStation = thisDialogClass.rememberStation;
            //  Here is one of four exit points (one in doCancel(), one in doBackButton(), one in below else-if statement).  This and the below
            //      exit point are the only ones where an Alert will be applied ....
            //  .... ALERT SELECTION IS COMPLETE, create the Alerts object, write linked list to file, and clear all data.
            logInfo("AlertsInterface.doNext()  creating alertObject  *****");
            alertObject = new Alerts( checkedItemsRegionPrefix[0], checkedItemsRegionPrefix[1], checkedItemsContinent, charSeqMajorCountries, checkedItemsMajorCountries,
                    charSeqAllCountries, checkedItemsAllCountries, prefixCallsignItems, checkedItemsSoundVibration, minSignal, rememberStation);
            finishWithNewAlert();
        }
        else if ( dialogName == DialogName.DIALOG_PREVIOUS_ALERTS ) {
            //  Here is one of four exit points (one in doCancel(), one in doBackButton(), one in above else-if statement).  This and the above
            //      exit point are the only ones where an Alert will be applied ....
            //  .... ALERT SELECTION IS COMPLETE, recall the Alerts object from LinkedList, remove old entry from LinkedList, write recalled alert
            //      object to the front of LinkedList (done in writeLinkedList() and clear all data.
            int selectionIndex;
            for (selectionIndex = 0; selectionIndex < checkedItemsPreviousAlerts.length; selectionIndex++) {
                if (checkedItemsPreviousAlerts[selectionIndex]) {
                    break;
                }
            }
            //  selectionIndex represents the index of the filter selected.  Move that to front of list, assigning filterObject to the selected filter.
            alertObject = recentAlerts.get(selectionIndex);         //  get the selected filter.
            alertObject.updateCountryNames( thisContext );

            if (checkedItemsNewReuse[1]) {
                //  If user is choosing to REUSE a previous alert
                //  .... remove old entry from LinkedList, write recalled alert object to the front of LinkedList (done in writeLinkedList() called from
                //        finishWIthNewAlert()) and clear all data.
                logInfo("AlertsInterface.doNext() reusing alertObject "+selectionIndex+" *****");
                recentAlerts.remove(selectionIndex);                    //  remove it from list before writing the new one to file (writeLinkedList() (called from
                finishWithNewAlert();                                   //      finishWithNewAlert()) will write this alert to the fron of the linked list.
            } else if (checkedItemsNewReuse[2]) {
                //  If user is choosing to EDIT a previous alert.
                if (prepareToEditAlert()) {
                    dialogName = DialogName.DIALOG_REGION_OR_PREFIX;
                    int endOfLargeFont = thisContext.getResources().getInteger(R.integer.alert_region_prefix_end_of_large_font);
                    bringUpDialog(charSeqRegionPrefix, checkedItemsRegionPrefix, R.string.title_alerts_region_prefix, endOfLargeFont,
                            "Alert on what", R.string.cancel, R.string.next);
                    logInfo("AlertsInterface.doNext() editing alertObject "+selectionIndex+" *****");
                } else {                //   shouldn't ever happen
                    //  If prepareToEditAlert() returned an error (shouldn't happen).
                    String toastString = String.format(Locale.US, "%s %s",
                            thisContext.getResources().getString(R.string.filter_edit_error), alertObject.describeAlert(thisContext));
                    Toast.makeText(thisContext, toastString, Toast.LENGTH_LONG).show();
                    clearAll();
                    dialogName = DialogName.DIALOG_NONE;
                    logInfo("AlertsInterface.doNext() editing - error in prepareToEditAlert() *****");
                }
            }
        }
    }

    void syncTheTwoCountryLists() {
        if ( (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) || (dialogName == DialogName.DIALOG_ALL_COUNTRIES) ) {
            logInfo("AlertsInterface.syncTheTwoCountryLists() *****");
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
                checkedItemsRegionPrefix[5] = false;
                for (int iii = 0; iii < checkedItemsAllCountries.length; iii++) {
                    if (checkedItemsAllCountries[iii]) {
                        checkedItemsRegionPrefix[5] = true;
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
                checkedItemsRegionPrefix[4] = false;
                for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) {
                    if (checkedItemsMajorCountries[iii]) {
                        checkedItemsRegionPrefix[4] = true;
                        break;
                    }
                }

            }
        }
    }

    //  This block of code was common to the two places immediately above where it says ALERT SELECTION IS COMPLETE so I moved it here.
    private void finishWithNewAlert() {
        writeLinkedList();                              //  write new alert to linked list and then to file
        mService.alertObject = alertObject.copyOf();    //  make a copy of the Alerts class for UDPService.
        mService.newAlertSetRingtone();

        clearAll();                                     //  clear out all the data structures in this class, get ready for next alert
        dialogName = DialogName.DIALOG_NONE;            //  reset current dialog, gat ready for next alert

        int numAlerts = WSJTXUtils.getAndBumpNumberOfAlertUses( thisContext );      //  do the Firebase message
        String alertDescription = alertObject.describeAlert( thisContext );

        doReplyIcon( true );                    //  display reply icon on screen
        doReplyHelpDialog();

        logInfo("Alert: "+alertDescription+", use number "+numAlerts+" *****");
        String toastString = String.format(Locale.US, "%s %s", thisContext.getResources().getString(R.string.alert_toast),alertDescription );
        Toast.makeText(thisContext, toastString, Toast.LENGTH_LONG).show();
    }

    //  This method is called when the user wants to edit a previously used alert.  Its purpose is to setup all the checkedItems*[] arrays.
    //      It reads private variables from the recalled Alerts object.
    //  It returns false on error or true on success.
    private boolean prepareToEditAlert() {
        if (alertObject == null) {
            return false;
        }
        if (!alertObject.getAlertInitialized()) {
            return false;
        }

        ArrayList<String> prefixCallsigns = alertObject.getAlertPrefixCallsigns();
        ArrayList<String> continents = alertObject.getAlertContinents();
        ArrayList<String> countries = alertObject.getAlertCountries();

        //  Set the DIALOG_REGION_OR_PREFIX items to be null
        checkedItemsRegionPrefix[0] = alertObject.getAlertOnMyCallsign();
        checkedItemsRegionPrefix[1] = alertObject.getAlertAlertOnAll();
        checkedItemsRegionPrefix[2] = checkedItemsRegionPrefix[3] = checkedItemsRegionPrefix[4] = checkedItemsRegionPrefix[5] = false;

        //  clear all the checkedItems* that are used in the following IF block, beginning with "if (!checkedItemsRegionPrefix[1]) {"
        prefixCallsignItems.clear();
        //for (int iii = 0; iii < checkedItemsContinent.length; iii++) { checkedItemsContinent[iii] = false; }
        Arrays.fill(checkedItemsContinent, false);
        //for (int iii = 0; iii < checkedItemsMajorCountries.length; iii++) { checkedItemsMajorCountries[iii] = false; }
        Arrays.fill(checkedItemsMajorCountries, false);
        if (charSeqAllCountries != null) {
            for (int iii = 0; iii < charSeqAllCountries.length; iii++) {
                checkedItemsAllCountries[iii] = false;
            }
        }

        //  If alert on all is set then the region-prefix, continents, countries will all be blank.
        if (!checkedItemsRegionPrefix[1]) {

            if (prefixCallsigns != null) {
                prefixCallsignItems = new ArrayList<>(prefixCallsigns);
                checkedItemsRegionPrefix[2] = true;
            }

            if (continents != null) {
                String[] continentAbbreviations = {"AF", "AS", "EU", "NA", "SA", "OC"};
                for (int iii = 0; iii < continents.size(); iii++) {
                    for (int jjj = 0; jjj < continentAbbreviations.length; jjj++) {
                        if (continentAbbreviations[jjj].equals(continents.get(iii))) {
                            checkedItemsContinent[jjj] = true;
                            checkedItemsRegionPrefix[3] = true;
                            break;
                        }
                    }
                }
            }

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
                            checkedItemsRegionPrefix[4] = true;         //  ... set the upper level checkedItemsRegionPrefix[4] (the checked item for Major Countries)
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
                            checkedItemsRegionPrefix[5] = true;         //  ... set the upper level checkedItemsRegionPrefix[5] (the checked item for All Countries)
                            break;                                      //  ... and quit searching through charSeqAllCountries[] since we've found it.
                        }
                    }
                }
            }

        }

        //  Now the settings stuff.
        //for (int iii = 0; iii < checkedItemsSoundVibration.length; iii++) { checkedItemsSoundVibration[iii] = false; }
        Arrays.fill(checkedItemsSoundVibration, false);
        boolean doingVibrate = alertObject.getAlertDoingVibrate();
        boolean doingSound = alertObject.getAlertDoingSound();
        if (doingSound && doingVibrate) {
            checkedItemsSoundVibration[0] = true;
        } else if (doingSound) {
            checkedItemsSoundVibration[1] = true;
        } else if (doingVibrate) {
            checkedItemsSoundVibration[2] = true;
        } else {
            checkedItemsSoundVibration[3] = true;
        }

        minSignal = alertObject.getAlertMinSignal();
        rememberStation = alertObject.getAlertRememberStation();

        return true;
    }

    private void doReplyHelpDialog() {
        if (!WSJTXUtils.getReplyHelpDontShowAgain(thisContext)) {
            FragmentManager fragMan = thisContext.getSupportFragmentManager();
            DialogFragment replyHelp;
            replyHelp = (DialogFragment) fragMan.findFragmentByTag("Reply Pre Help Tag");
            if (replyHelp == null) {
                replyHelp = new DialogReplyPreHelp();
                replyHelp.show(fragMan, "Reply Pre Help Tag");
                replyHelp.setCancelable(false);
            }
        }
    }

    private void doReplyIcon( boolean enabled ) {
        MenuItem replyIcon = mainMenu.findItem( R.id.action_reply );
        replyIcon.setEnabled( enabled );
        replyIcon.setVisible( enabled );
        mService.setDoingReply(false);      //  turn this off regardless of whether the alerts have just been turned off or on
        ImageView replyIconView = (ImageView)replyIcon.getActionView();
        replyIconView.setImageResource(R.drawable.baseline_reply_black_36);
        replyIcon.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private boolean writeLinkedList() {
        FileOutputStream outputStream;
        boolean returnValue = false;

        try {
            //  Check that the new alert isn't a duplicate of an already existing alert.  If so, delete the older one.
            Iterator iter1 = recentAlerts.iterator();                  //  loop through all the alerts in the LinkedList recentAlerts
            while (iter1.hasNext()) {
                Alerts tempAlert = (Alerts) iter1.next();
                if (alertObject.equals( tempAlert )) {
                    iter1.remove();
                }
            }
            //  Add most recent alert to beginning of linked list before writing to file (method .add() adds to end of linked list).
            recentAlerts.addFirst(alertObject);
            //  Limit the size of the linked list.
            if (recentAlerts.size() > MAX_NUM_RECENT_ALERTS) {
                recentAlerts.removeLast();
            }

            //  Now write to file.
            outputStream = thisContext.openFileOutput( RECENT_ALERTS_FILENAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);
            oos.writeObject(recentAlerts);
            oos.close();
            outputStream.close();
            returnValue = true;
            logInfo("AlertsInterface.writeLinkedList() - File Write success *****");
        } catch (Exception ex) {
            logInfo("AlertsInterface.writeLinkedList() - Exception "+ex.getMessage()+" *****");
        }

        recentAlerts.clear();
        return returnValue;
    }


    @SuppressWarnings("unchecked")      // Warning on line "recentAlerts = (LinkedList<Alerts>)ois.readObject();"  The compiler can't guarantee that
                                        //      what is in the file is really LinkedList<Alerts>.
    private boolean readLinkedList() {
        FileInputStream inputStream;
        boolean returnValue = false;

        recentAlerts.clear();       //  before reading in alerts, clear out the linked list.  (Ex - if file is deleted then without this clear the LinkedList will still have the
                                    //      contents of last read.  An exception will occur before it has a chance to read from file so LinkedList will not change.
        try {
            inputStream = thisContext.openFileInput(RECENT_ALERTS_FILENAME);
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            recentAlerts = (LinkedList<Alerts>)ois.readObject();
            ois.close();
            inputStream.close();
            returnValue = true;
            logInfo("AlertsInterface.readLinkedList() - File Read success.  # of Alerts "+recentAlerts.size()+" *****");
        } catch (Exception ex) {
            logInfo("AlertsInterface.readLinkedList() - Exception "+ex.getMessage()+" *****");
        }

        return returnValue;
    }

    void doHelp() {
        logInfo("AlertsInterface.doHelp() *****");
        int resource;
        if (dialogName == DialogName.DIALOG_NEW_OR_REUSE) {
            resource = R.string.help_new_reuse;
        }
        else if (dialogName == DialogName.DIALOG_PREVIOUS_ALERTS) {
            resource = R.string.help_previous_alerts;
        }
        else if (dialogName == DialogName.DIALOG_REGION_OR_PREFIX) {
            resource = R.string.help_region_prefix_alert;
        }
        else if (dialogName == DialogName.DIALOG_PREFIX_CALLSIGN) {
            resource = R.string.help_prefix_callsign_alert;
        }
        else if (dialogName == DialogName.DIALOG_CONTINENT) {
            resource = R.string.help_continent_alert;
        }
        else if (dialogName == DialogName.DIALOG_MAJOR_COUNTRIES) {
            resource = R.string.help_major_countries_alert;
        }
        else if (dialogName == DialogName.DIALOG_ALL_COUNTRIES) {
            resource = R.string.help_all_countries_alert;
        }
        else if (dialogName == DialogName.DIALOG_ALERT_SETTINGS) {
            resource = R.string.help_alert_settings;
        }
        else if (dialogName == DialogName.DIALOG_MIN_SIGNAL) {
            resource = R.string.help_min_signal;
        }
        else {
            return;
        }
        DialogFragment helpY = FiltersInterface.BasicClassHelp.newInstance( resource );
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

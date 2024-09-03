package com.feotec.wsjt_xmonitor;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.Log;

public class TouchableTextView extends AppCompatTextView {
    private static final String TAG = TouchableTextView.class.getSimpleName();
    private static final boolean DO_LOGGING = DebugUtils.loggingTouchableTextView;

    public TouchableTextView(Context context) {
        super(context);
    }

    public TouchableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // Because we call this from onTouchEvent, this code will be executed for both
    // normal touch events and for when the system calls this using Accessibility
    @Override
    public boolean performClick() {
        logInfo("performClick() *****");
        super.performClick();
        return true;
    }

    //  Logging convenience method
    private void logInfo( String message ) {
        if (DO_LOGGING) {
            Log.i(TAG,message);
            DebugUtils.writeToAndroidLogFile(TAG,message);
        }
    }


}

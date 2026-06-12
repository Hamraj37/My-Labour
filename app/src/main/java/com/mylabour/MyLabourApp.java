package com.mylabour;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class MyLabourApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dynamic colors (Monet) to all activities in the app
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

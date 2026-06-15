package com.mylabour;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MyLabourApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dynamic colors (Monet) to all activities in the app
        DynamicColors.applyToActivitiesIfAvailable(this);
        
        scheduleAttendanceReminder();
    }

    private void scheduleAttendanceReminder() {
        Calendar calendar = Calendar.getInstance();
        long now = calendar.getTimeInMillis();
        
        // Schedule for 9:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        if (calendar.getTimeInMillis() <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        long delay = calendar.getTimeInMillis() - now;

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AttendanceReminderWorker.class,
                24, TimeUnit.HOURS
        )
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AttendanceReminder",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
    }
}

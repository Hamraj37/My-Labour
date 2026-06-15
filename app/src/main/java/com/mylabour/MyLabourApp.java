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
        WorkManager workManager = WorkManager.getInstance(this);
        // Cancel old single reminder if it exists
        workManager.cancelUniqueWork("AttendanceReminder");

        // Schedule 3 reminders per day: 9:00 AM, 2:00 PM, and 8:00 PM
        schedulePeriodicReminder(workManager, "AttendanceReminder_Morning", 9, 0);
        schedulePeriodicReminder(workManager, "AttendanceReminder_Afternoon", 14, 0);
        schedulePeriodicReminder(workManager, "AttendanceReminder_Evening", 20, 0);
    }

    private void schedulePeriodicReminder(WorkManager workManager, String uniqueName, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        long now = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
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

        workManager.enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
    }
}

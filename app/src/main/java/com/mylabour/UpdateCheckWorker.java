package com.mylabour;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateCheckWorker extends Worker {

    private static final String UPDATE_CHANNEL_ID = "app_update_channel";

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        checkForUpdates();
        return Result.success();
    }

    private void checkForUpdates() {
        try {
            URL url = new URL("https://api.github.com/repos/Hamraj37/My-Labour/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "My-Labour-App");
            connection.connect();

            if (connection.getResponseCode() == 200) {
                InputStream responseBody = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject jsonObject = new JSONObject(result.toString());
                String latestVersion = jsonObject.getString("tag_name");
                
                String downloadUrl = null;
                JSONArray assets = jsonObject.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        break;
                    }
                }

                if (downloadUrl == null) {
                    downloadUrl = jsonObject.getString("html_url");
                }

                String latestClean = latestVersion.replace("v", "");
                String currentVersion = getApplicationContext().getPackageManager()
                        .getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;

                if (currentVersion != null) {
                    String currentClean = currentVersion.replace("v", "").trim();
                    if (!latestClean.equals(currentClean)) {
                        showUpdateNotification(latestVersion, downloadUrl);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("UpdateCheckWorker", "Error checking for updates", e);
        }
    }

    private void showUpdateNotification(String version, String downloadUrl) {
        Context context = getApplicationContext();
        createUpdateNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(context.getString(R.string.new_update_available))
                .setContentText(context.getString(R.string.update_notification_content, version))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(2, builder.build());
        } catch (SecurityException e) {
            Log.e("UpdateCheckWorker", "Notification permission not granted", e);
        }
    }

    private void createUpdateNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new app versions");
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

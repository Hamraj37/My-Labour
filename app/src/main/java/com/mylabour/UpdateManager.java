package com.mylabour;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;

public class UpdateManager {

    public interface OnProgressListener {
        void onProgress(int progress);
        void onComplete();
        void onError(String message);
    }

    private final Context context;
    private long downloadId;
    private OnProgressListener progressListener;

    public UpdateManager(Context context) {
        this.context = context;
    }

    public void downloadAndInstall(String url) {
        downloadAndInstall(url, null);
    }

    public void downloadAndInstall(String url, OnProgressListener listener) {
        this.progressListener = listener;

        // Clean up previous download if it exists
        File oldFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MyLabourUpdate.apk");
        if (oldFile.exists()) {
            oldFile.delete();
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Downloading My Labour Update");
        request.setDescription("Please wait...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "MyLabourUpdate.apk");
        request.setMimeType("application/vnd.android.package-archive");

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            downloadId = manager.enqueue(request);
            if (progressListener != null) {
                startProgressTracker();
            } else {
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show();
            }

            BroadcastReceiver onComplete = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (downloadId == id) {
                        if (progressListener != null) {
                            progressListener.onComplete();
                        }
                        installApk();
                        context.unregisterReceiver(this);
                    }
                }
            };

            ContextCompat.registerReceiver(context, onComplete, 
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
                    ContextCompat.RECEIVER_EXPORTED);
        }
    }

    private void startProgressTracker() {
        new Thread(() -> {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                try (android.database.Cursor cursor = manager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1 && statusIndex != -1) {
                            long bytesDownloaded = cursor.getLong(bytesDownloadedIndex);
                            long bytesTotal = cursor.getLong(bytesTotalIndex);
                            int status = cursor.getInt(statusIndex);

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloading = false;
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                downloading = false;
                                if (progressListener != null) {
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                                        progressListener.onError("Download failed"));
                                }
                            } else if (bytesTotal > 0) {
                                int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                                if (progressListener != null) {
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                                        progressListener.onProgress(progress));
                                }
                            }
                        }
                    } else {
                        downloading = false;
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void installApk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Toast.makeText(context, "Please enable 'Install unknown apps' for My Labour", Toast.LENGTH_LONG).show();
                return;
            }
        }

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return;

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        try (android.database.Cursor cursor = manager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri downloadFileUri = manager.getUriForDownloadedFile(downloadId);
                    if (downloadFileUri != null) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(downloadFileUri, "application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, "Failed to open APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(context, "Download failed: URI is null", Toast.LENGTH_SHORT).show();
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int reason = cursor.getInt(reasonIndex);
                    Toast.makeText(context, "Download failed: " + getFailureReason(reason), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private String getFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME: return "Cannot resume";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return "Device not found";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return "File already exists";
            case DownloadManager.ERROR_FILE_ERROR: return "File error";
            case DownloadManager.ERROR_HTTP_DATA_ERROR: return "HTTP data error";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return "Insufficient space";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return "Too many redirects";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return "Unhandled HTTP code";
            default: return "Error code: " + reason;
        }
    }
}

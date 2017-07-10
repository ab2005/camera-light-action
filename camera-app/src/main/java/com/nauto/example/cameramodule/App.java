package com.nauto.example.cameramodule;

import java.util.concurrent.ScheduledExecutorService;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.nauto.camera.Utils;
import com.nauto.camera.base.CameraModule;

/**
 * Created by ab on 12/15/16.
 */

public class App extends Application {
    public static final String TAG = App.class.getName();
    private static final int MAX_TRIES = 5;
    ScheduledExecutorService mExec;
    private BroadcastReceiver mExternalStorageListener;

    @Override
    public synchronized void onCreate() {
        super.onCreate();

        // Skip application context initialization for sub-processes
        String processName = Utils.getProcessName();
        if (!getPackageName().equals(processName)) {
            Log.d(TAG, "Skip app context initialization for subprocess " + processName);
            return;
        }

        // When sdcard is removed we should stop camera services.
        // When sdcard is mounted we'll start camera services.
        IntentFilter extFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        extFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        registerReceiver(mExternalStorageListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Storage state changed: " + intent.getData());
                if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)) {
                    // TODO: check if sdcard available
                    CameraModule.stopAll(getApplicationContext());
                } else if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    CameraModule.startAll(getApplicationContext());
                }
            }
        }, extFilter);
    }
}

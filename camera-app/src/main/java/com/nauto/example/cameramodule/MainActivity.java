package com.nauto.example.cameramodule;

import com.nauto.camera.CameraStore;
import com.nauto.camera.base.CameraModule;
import com.nauto.example.cameramodule.camera.Camera0;
import com.nauto.example.cameramodule.camera.Camera1;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ToggleButton;

import static com.nauto.camera.base.CameraModule.dbg;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MainActivity extends Activity {
    public static final String TAG = MainActivity.class.getName();
    private static String mDeviceUid;

    private ArrayAdapter mAdapter;
    private BroadcastReceiver mServiceListener;
    private BroadcastReceiver mExternalStorageListener;
    private ScheduledExecutorService mThreadPool;
    private ScheduledFuture<?> mCaptureRunner;
    private int snapshotCount;
    private String mFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ListView listview = (ListView) findViewById(R.id.listview);
        final ArrayList<String> list = new ArrayList<String>();
        mAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        listview.setAdapter(mAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String str = mAdapter.getItem(position).toString();
                int pos = str.indexOf("content://");
                if (pos > 0) {
                    Uri contentUri = Uri.parse(str.substring(pos));
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(contentUri, "video/mp4");
                    startActivity(intent);
                }
            }
        });

        if (mServiceListener != null) {
            throw new RuntimeException("Camera service listener leaked!");
        }

        registerReceiver(mServiceListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String id = intent.getStringExtra(CameraModule.EXTRA_ID);
                String msg = intent.getStringExtra(CameraModule.EXTRA_MESSAGE);
                if (msg == null) {
                    return;
                }
                int note = intent.getIntExtra(CameraModule.EXTRA_NOTICE_CODE, -1);
                int info = intent.getIntExtra(CameraModule.EXTRA_INFO_CODE, -1);
                int err = intent.getIntExtra(CameraModule.EXTRA_ERROR_CODE, -1);
                String s = "Camera" + id + ": " + msg;
                Parcelable md = intent.getParcelableExtra(CameraModule.EXTRA_METADATA);
                if (md != null && md instanceof CameraStore.VideoMetadata) {
                    CameraStore.VideoMetadata vmd = (CameraStore.VideoMetadata) md;
                    int p = vmd.getFileName().lastIndexOf("/");
                    String name = vmd.getFileName().substring(p + 1);
                    long len = vmd.getEndTime() - vmd.getStartTime();
                    s = name + ", " + len + "(" + vmd.getGapLength() + ")";
                    if (name.startsWith("1_")) {
                        Log.d(TAG, "mFileName = " + mFileName);
                        mFileName = vmd.getFileName();
                    }
                }
                if (err != -1) {
                    s = ":boom: " + "*Camera" + id + "* : " + msg;
                    final String ms = s;
                    // TODO:
                }
                mAdapter.add(s);
            }
        }, CameraModule.getIntentFilter(this));

        if (mExternalStorageListener != null) {
            throw new RuntimeException("External storage listener leaked!");
        }

        // When sdcard is removed we should stop camera services.
        // If sdcard is mounted we start camera services again.
        IntentFilter extFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        extFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        getApplication().registerReceiver(mExternalStorageListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Storage state changed: " + intent.getData());
                if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)) {
                    CameraModule.stopAll(getApplication());
                } else if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    CameraModule.startAll(getApplication());
                }
            }
        }, extFilter);
        dbg(TAG, "Registered external storage listener");

        if (CameraModule.isAnyRunning(this)) {
            ToggleButton btn = (ToggleButton) findViewById(R.id.toggleButton);
            btn.setChecked(true);
            String msg = "Camera module service is running...";
            dbg(TAG, msg);
            mAdapter.add(msg);
        }

        mThreadPool = Executors.newScheduledThreadPool(1);

    }

    int count = 0;

    @Override
    protected void onResume() {
        super.onResume();
        final long SCHEDULE_INTERVAL_MS = -1;//10 * 1000;
        final long TIME_BEFORE_MS = 30 * 1000;
        final long EXTRACT_VIDEO_LEN_MS = 5 * 1000;

        if (SCHEDULE_INTERVAL_MS > 0) {
            mCaptureRunner = mThreadPool.scheduleAtFixedRate(new Runnable() {
                public void run() {
//                    extractVideoAsFile(TIME_BEFORE_MS, EXTRACT_VIDEO_LEN_MS);
                    saveSnaphotAsFile();
                }
            }, 0, SCHEDULE_INTERVAL_MS, MILLISECONDS);
        }
    }

    private void extractVideoAsFile(long timeBeforeMs, long extractVideoLenMs) {
        Log.d(TAG, "scheduled capture task");
        long t = System.currentTimeMillis();
        try {
            final String path = new File(getCacheDir(), "v_" + count).getAbsolutePath() + ".mp4";
            long start = t - timeBeforeMs;
            long end = start + extractVideoLenMs;
            Log.d(TAG, "extractVideoAsFile " + count + ": " + start + ", " + end + " to " + path);
            boolean b = CameraStore.extractVideoAsFile(getApplicationContext(), "0", start, end, path);
            long dt = System.currentTimeMillis() - t;
            if (b) {
                Log.d(TAG, "extracted file " + path + " in" + dt + " ms");
                count++;
            } else {
                Log.d(TAG, "failed to extract file " + path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveSnaphotAsFile() {
        Log.d(TAG, "scheduled capture task");
        long t = System.currentTimeMillis();
        try {
            final String path = new File(getCacheDir(), "img_" + snapshotCount).getAbsolutePath() + ".jpg";
            Log.d(TAG, "saveSnapshotAsFile " + snapshotCount + " to " + path);
            CameraModule.snapshot(getApplicationContext(), "1", path);
            long dt = System.currentTimeMillis() - t;
            Log.d(TAG, "saved snapshot to file " + path + " in" + dt + " ms");
            snapshotCount++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCaptureRunner != null) {
            mCaptureRunner.cancel(true);
            mCaptureRunner = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mThreadPool != null) {
            mThreadPool.shutdown();
            mThreadPool = null;
        }

        if (mServiceListener != null) {
            getApplication().unregisterReceiver(mServiceListener);
            mServiceListener = null;
        }
        if (mExternalStorageListener != null) {
            getApplication().unregisterReceiver(mExternalStorageListener);
            mExternalStorageListener = null;
        }
    }

    public void startModule(View v) {
        boolean on = ((ToggleButton) v).isChecked();
        if (on) {
            String startParams = CameraModule.PARAM_VIDEO_LENGTH_SEC + "=30" + "&" + CameraModule.PARAM_PLAY_SOUND + "=true";
            CameraModule.start(this, Camera0.class, startParams);
            CameraModule.start(this, Camera1.class, startParams);
        } else {
            CameraModule.stopAll(this);
        }
    }

    int captureCount0 = 0;
    int captureCount1 = 0;

    public void capture(View v) throws IOException {
        dbg(TAG, "capture button pressed");
        File ext = Environment.getExternalStorageDirectory();
        CameraModule.snapshot(this, "0", new File(ext, "camera0_" + captureCount0++ + ".jpg").getAbsolutePath());
        CameraModule.snapshot(this, "1", new File(ext, "camera1_" + captureCount1++ + ".jpg").getAbsolutePath());
    }

    public void onCutOff(View v) {
        CameraModule.scheduleCutOff(this, "0", 0);
        CameraModule.scheduleCutOff(this, "1", 1000);
    }
}

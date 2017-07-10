package com.nauto.camera.base;

import android.content.Intent;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Created by ab on 1/16/17.
 */
public abstract class RepeatCaptureModule extends CaptureModule {
    private static final String TAG = RepeatCaptureModule.class.getSimpleName();
    private static final boolean DEBUG = false;
    private ScheduledExecutorService mThreadPool;
    private ScheduledFuture<?> mScheduler;

    @Override
    @CallSuper
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate();
        mThreadPool = Executors.newScheduledThreadPool(1);
    }

    @Override
    @CallSuper
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand()" + intent);
        if (getInterval() > 0 && mScheduler == null) {
            final long delay = getInterval();
            mScheduler = mThreadPool.scheduleAtFixedRate(new Runnable() {
                long t0 = SystemClock.elapsedRealtime();
                public void run() {
                    long t1 = SystemClock.elapsedRealtime();
                    int id = capture();
                    if (DEBUG) Log.d(TAG, (t1 - t0) + " caling capture() " + id + ", delay " + delay);
                    t0 = t1;
                }
            }, delay, delay, MILLISECONDS);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        if (mScheduler != null) {
            mScheduler.cancel(true);
            mScheduler = null;
        }
        if (mThreadPool != null) {
            mThreadPool.shutdown();
            mThreadPool = null;
        }
        super.onDestroy();
    }

    @Override
    @CallSuper
    protected void onCaptureRequestStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
        if (DEBUG) Log.d(TAG, "onCaptureRequestStarted()" + frameNumber);
    }

    @Override
    @CallSuper
    public void onCaptureRequestCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        if (DEBUG) Log.d(TAG, "onCaptureRequestCompleted()" + request);
    }
}

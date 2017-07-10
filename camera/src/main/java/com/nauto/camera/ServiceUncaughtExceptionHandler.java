package com.nauto.camera;

import com.nauto.camera.base.CameraModule;

import android.util.Log;


/**
 * Handles uncaught exceptions.
 */
public class ServiceUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = ServiceUncaughtExceptionHandler.class.getName();

    private static final String CAMERA_DUMPSYS = "dumpsys media.camera";

    public static void install(CameraModule context) {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(defaultHandler instanceof ServiceUncaughtExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new ServiceUncaughtExceptionHandler(defaultHandler, context));
        }
    }

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final CameraModule context;

    public ServiceUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultHandler, CameraModule context) {
        this.defaultHandler = defaultHandler;
        this.context = context;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.d(TAG, "Unhandled exception " + ex + " in thread " + thread);
        ex.printStackTrace();
        try {
            context.reportErrorAndStopService(CameraModule.UNHANDLED_EXCEPTION_ERROR, ex);
        } finally {
            defaultHandler.uncaughtException(thread, ex);
        }
    }
}
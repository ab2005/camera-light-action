package com.nauto.example.cameramodule.camera;

import com.nauto.camera.base.VideoPipelineCaptureModule;

import java.nio.ByteBuffer;

import android.util.Log;

/**
 * Vehicle detection module placeholder.
 */
public class VehicleDetectionModule extends VideoPipelineCaptureModule {
    private static String TAG = VehicleDetectionModule.class.getSimpleName();
    private long t0;

    @Override
    public void onCaptured(byte[] bytes) {
        Log.d(TAG, "onCaptured()");
    }

    @Override
    public void onConfigurationChanged() {
        Log.d(TAG, "onConfigurationChanged()");
    }

    @Override
    protected void onCaptured(ByteBuffer y, ByteBuffer u, ByteBuffer v) {
        Log.d(TAG, "onCaptured():");
    }
}

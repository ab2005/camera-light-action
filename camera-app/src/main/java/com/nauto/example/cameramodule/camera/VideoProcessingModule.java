package com.nauto.example.cameramodule.camera;

import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;

import com.nauto.camera.base.VideoPipelineCaptureModule;

/**
 * Created by ab on 4/4/17.
 */

public class VideoProcessingModule extends VideoPipelineCaptureModule {
    private static final String TAG = "VideoProcessingModule";

    @Override
    public void onCaptured(@NonNull byte[] frame) {
        
    }

    @Override
    public void onConfigurationChanged() {
        Log.d(TAG, "onConfigurationChanged()");
    }

    @Override
    protected void onCaptured(ByteBuffer y, ByteBuffer u, ByteBuffer v) {
        Log.d(TAG, "onCaptured");
    }

}

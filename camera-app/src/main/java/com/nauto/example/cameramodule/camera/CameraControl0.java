package com.nauto.example.cameramodule.camera;

import com.nauto.camera.CameraStore;
import com.nauto.camera.base.CameraControl;
import com.nauto.camera.base.CameraModule;

import android.content.Context;
import android.content.Intent;

/**
 * Created by ab on 6/30/17.
 */

public class CameraControl0 extends CameraControl {
    public final static long TIME_TO_CHECK_IF_CAMERA_RUNNING = 10000;
    public final static String CAMERA_START_PARAMS = "reboot";
    public final static String CAMERA_RESTART_PARAMS = "restart";

    public void startCamera(Context ctx) {
        CameraModule.start(ctx, CAMERA_ID, CAMERA_START_PARAMS);
        super.startCameraWatchDog(ctx, TIME_TO_CHECK_IF_CAMERA_RUNNING, CAMERA_RESTART_PARAMS);
    }

    public void stopCamera(Context ctx) {
        CameraModule.stop(ctx, CAMERA_ID);
        super.stopCameraWatchDog();
    }

    @Override
    protected void onError(String message) {

    }

    @Override
    protected void onStarted() {

    }

    @Override
    protected void onStopped() {

    }

    @Override
    protected void onCapture(CameraStore.SnapshotMetadata snapshotMetadata) {

    }

    @Override
    protected void onCutOff(CameraStore.VideoMetadata videoMetadata) {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }

    @Override
    protected void onRestart(Context context) {
        super.onRestart(context);
    }

    @Override
    protected void onReboot(Context context) {
        super.onReboot(context);
    }

    @Override
    protected boolean isRebootNeeded(int err) {
        return super.isRebootNeeded(err);
    }
}

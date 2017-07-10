package com.nauto.camera.base;

import com.nauto.camera.CameraStore;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * CameraPipeline creates and controls camera sessions, saves media files, trims media store
 * and reports errors to the service.
 */
public abstract class CameraPipeline extends CameraCaptureSession.StateCallback {
    private static final String TAG = CameraPipeline.class.getSimpleName();
    protected final CameraModule mService;
    protected final CameraPipelineConfig mConfig;
    protected CameraDevice mCamera;

    public CameraPipeline(CameraModule service, CameraDevice camera, CameraPipelineConfig config) {
        mService = service;
        mCamera = camera;
        mConfig = config;
    }

    public abstract void init(List<CaptureModule> captureModules) throws IOException, CameraAccessException;

    public abstract void cutOff(long timeMs);

    public abstract void pause();

    public abstract void resume();

    public abstract void snapshot(CameraStore.SnapshotMetadata metadata);

    public void destroy() {
        Log.d(TAG, "destroy(), camera = " + (mCamera != null ? mCamera.getId() : "null"));
        if (mCamera != null) {
            try {
                mCamera.close();
            } catch (Exception ex) {
               // ignore
            } finally {
                mCamera = null;
            }
        }
    }

    @Override
    public void onConfigureFailed(CameraCaptureSession session) {
        Log.d(TAG, "onConfigureFailed():" + session);
        destroy();
        mService.reportErrorAndStopService(CameraModule.CAPTURE_SESSION_CONFIGURE_FAILED,
                new Exception("CameraCaptureSession.onConfigureFailed(): config = " + mConfig));
    }
}

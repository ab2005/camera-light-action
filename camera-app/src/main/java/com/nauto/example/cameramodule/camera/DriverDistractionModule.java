package com.nauto.example.cameramodule.camera;

import com.nauto.camera.base.VideoPipelineCaptureModule;

import java.nio.ByteBuffer;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;

/**
 * Driver distraction CNN library placeholder.
 */
public class DriverDistractionModule extends VideoPipelineCaptureModule {
    private static String TAG = DriverDistractionModule.class.getSimpleName();

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
//        int n = getCameraService().getNumFaces();
//        Log.d(TAG, "num faces: " + n);
//        if (n > 0) {
//            Face[] faces = getCameraService().getFaces();
//            for (int i = 0; i < n; i++) {
//                if (faces[i] != null) {
//                    Rect bounds = faces[i].getBounds();
//                    int score = faces[i].getScore();
//                    Log.d(TAG, "\tscore:" + score + ", bounds:" + bounds);
//                }
//            }
//        }
//        Log.d(TAG, "onCaptured(), face count = " + n);
    }

    @Override
    protected void onCaptureRequestCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//        android.hardware.camera2.params.Face[] camFaces = result.get(CaptureResult.STATISTICS_FACES);
//        Log.d(TAG, "face count = " + (camFaces != null ? camFaces.length : 0));
    }
}

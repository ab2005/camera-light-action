package com.nauto.camera.base;

import com.nauto.camera.CameraStore;
import com.nauto.camera.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

/**
 * A class to take still captures.
 */
public class SnapshotHandler {
    private static final String TAG = SnapshotHandler.class.getName();
    private final CameraCaptureSession.CaptureCallback mCaptureCallback;
    private final ImageReader.OnImageAvailableListener mReaderListener;
    private CameraCaptureSession mSession;
    private final CameraModule mService;
    private final File mMediaRoot;
    private final Handler mHandler;
    private final CameraDevice mCamera;
    private final CaptureRequest.Builder mRequestBuilder;
    private ImageReader mImageReader;
    private CameraStore.SnapshotMetadata mSnapshotMetadata;

    /**
     * @param service      Camera module service
     * @param camera       Camera device
     * @param size         The size of this ImageReader to be created.
     * @param format       The format of this ImageReader to be created
     * @param maxNumImages The max number of images that can be acquired simultaneously.
     */
    public SnapshotHandler(CameraModule service, CameraDevice camera, Size size, int format, int maxNumImages) throws CameraAccessException {
        mService = service;
        mCamera = camera;
        mMediaRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, maxNumImages);
        HandlerThread thread = new HandlerThread(TAG + ".HandlerThread", Process.THREAD_PRIORITY_DEFAULT);
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mReaderListener = new ImageReader.OnImageAvailableListener() {
            int count = 0;
            long t;

            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "onImageAvailable()");
                Image image = reader.acquireNextImage();
                if (image != null) {
                    try {
                        long t1 = SystemClock.elapsedRealtime();
                        Log.d(TAG, count++ + ": onImageAvailable() " + mService.TAG + ", " + (t1 - t) + " ms");
                        t = t1;
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        // if snapshot saving path is not identified by the user, generate one.
                        if (mSnapshotMetadata == null) {
                            mSnapshotMetadata = new CameraStore.SnapshotMetadata();
                        }
                        if (mSnapshotMetadata.getFilename() == null) {
                            mSnapshotMetadata.setFilename(new File(mMediaRoot, Utils.generateTimestamp() + ".jpg").getAbsolutePath());
                        }
                        if (mSnapshotMetadata.getTakenTime() == 0) {
                            mSnapshotMetadata.setTakenTime(System.currentTimeMillis());
                        }

                        // TODO: wait until (mSnapshotMetadata.getFaceDetected != -1)
                        mHandler.post(new ImageSaver(mService, bytes, mSnapshotMetadata));
                    } finally {
                        image.close();
                    }
                }
            }
        };

        mImageReader.setOnImageAvailableListener(mReaderListener, mHandler);
        mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
        mRequestBuilder.addTarget(mImageReader.getSurface());
        // we do not use face detection
//        mRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);

        if (mService.mConfig.mJpegQUality > 0) {
            mRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) mService.mConfig.mJpegQUality);
        }

        Utils.setAeRect(mService.getCameraPipelineConfig(), mRequestBuilder);

        mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                Log.d(TAG, "onCaptureStarted()");
                if (request.getTag() != null && request.getTag() instanceof CameraStore.SnapshotMetadata) {
                    mSnapshotMetadata = (CameraStore.SnapshotMetadata) request.getTag();
                    Log.d(TAG, "capture started " + mService.CAMERA_ID + " to " + mSnapshotMetadata.getFilename());
                } else {
                    mSnapshotMetadata = new CameraStore.SnapshotMetadata();
                }
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                Log.d(TAG, "onCaptureRequestCompleted().");
                mSnapshotMetadata.setTakenTime(System.currentTimeMillis());
//                Integer mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
//                if (mode != null) {
//                    Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
//                    // if (faces != null && faces.length > 0) {
//                    //     Log.d(TAG, "faces : " + faces.length + " , mode : " + mode);
//                    //     String fs = "";
//                    //     for (Face face : faces) {
//                    //         Rect bounds = face.getBounds();
//                    //         Point leftEye = face.getLeftEyePosition();
//                    //         Point rightEye = face.getRightEyePosition();
//                    //         Point mouth = face.getMouthPosition();
//                    //         fs = String.format("%dx%d, l:%s, r:%s, m:%s", bounds.width(), bounds.height(), leftEye, rightEye, mouth);
//                    //         // broadcast("face", s);
//                    //         Log.d(TAG, "Face detected: " + fs);
//                    //     }
//                    // }
//
//                    // FIXME: depending on the assumption that onCaptureCompleted() will be called before onImageAvailable().
//                    if (faces != null && mSnapshotMetadata != null) {
//                        Log.d(TAG, "faces : " + faces.length + " , mode : " + mode);
//                        mSnapshotMetadata.setFaceDetected(faces.length);
//                    }
//                }
            }
        };
    }

    public void onSessionCreated(CameraCaptureSession session) {
        mSession = session;
    }

    public Surface getSurface() {
        return mImageReader != null ? mImageReader.getSurface() : null;
    }

    public void capture(CameraStore.SnapshotMetadata metadata) throws CameraAccessException {
        if (mSession != null) {
            if (metadata.getTakenTime() == 0) {
                metadata.setTakenTime(System.currentTimeMillis());
            }
            mRequestBuilder.setTag(metadata);
            mSession.capture(mRequestBuilder.build(), mCaptureCallback, mHandler);
        }
    }

    public void capture(String filePath) throws CameraAccessException {
        capture(new CameraStore.SnapshotMetadata(filePath));
    }

    public void destroy() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mHandler != null) {
            mHandler.getLooper().quitSafely();
        }
    }

    /**
     * Runnable that saves an {@link Image} into the specified {@link File}, and updates
     * {@link android.provider.MediaStore} to include the resulting file.
     */
    private static class ImageSaver implements Runnable {
        private final byte[] mBytes;
        private final File mFile;
        private final CameraStore.SnapshotMetadata mMetadata;
        private final CameraModule mContext;

        private ImageSaver(CameraModule service, byte[] bytes, CameraStore.SnapshotMetadata metadata) {
            mContext = service;
            mBytes = bytes;
            mFile = new File(metadata.getFilename());
            mMetadata = metadata;
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(mBytes);
                mContext.registerMediaFile(mFile.getAbsolutePath(), mMetadata);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Utils.close(output);
            }
        }
    }

}

package com.nauto.camera.base;

import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for a camera pipeline module .
 */
public abstract class CaptureModule extends Service {

    private boolean mPrepared;

    public CaptureModule() {
        this(null);
    }

    /**
     * Send capture request. The caller should always check if result is not -1.
     *
     * @return unique request id or -1 if request fails.
     */
    @CallSuper @CheckResult final protected int capture() {
        CameraCaptureSession session = mSession.get();
        if (session != null) {
            try {
                return session.capture(mCaptureRequest, mCaptureCallback, mHandler);
            } catch (Exception e) {
                Log.e(TAG, "Capture request failed " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "CaptureSession is null");
        }
        return -1;
    }

    /**
     * A callback when the image data is available.
     *
     * @param frame image data
     */
    abstract public void onCaptured(@NonNull byte[] frame);

    abstract public void onConfigurationChanged();

    /**
     * For subclasses to override.
     *
     * @param session
     * @param request
     * @param timestamp
     * @param frameNumber
     */
    protected void onCaptureRequestStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
        // nothing
    }

    /**
     * For subclasses to override to get capture request/result esettins such as face detection.
     *
     *
     * @param session
     * @param request
     * @param result
     */
    protected void onCaptureRequestCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        // can be use to get face detection at the highest rate
     }

    final protected Size getSize() {
        return mSize;
    }

    final protected int getFormat() {
        return mFormat;
    }

    final protected Rect getCropRect() {
        return mCropRect;
    }

    final protected int getInterval() {
        return mInterval;
    }

    @Nullable final protected CameraModule getCameraService() {
        return mService;
    }

    @Nullable final protected Handler getHandler() {
        return mHandler;
    }


    // =========== Implementation section ===============

    private final String TAG;
    private CameraModule mService;
    private final AtomicReference<CameraCaptureSession> mSession;

    protected Handler mHandler;

    private Size mSize;
    private Rect mCropRect;
    private int mFormat;
    private int mInterval;

    private CameraDevice mCamera;
    private ImageReader.OnImageAvailableListener mReaderListener;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private CameraCaptureSession.CaptureCallback mCaptureCallback;
    private String size;
    private final AtomicInteger mStartResult;

    /* package */ CaptureModule(@NonNull CameraModule service) {
        mService = service;
        TAG = getClass().getName();
        mSession = new AtomicReference<>();
        mStartResult = new AtomicInteger(-1);
    }

    /* package */ void setService(@NonNull CameraModule service) {
        mService = service;
    }

    /**
     * A callback from the CameraModule to configure.
     * @param camera
     */
    @CallSuper
    protected void onCameraReady(@NonNull CameraDevice camera) {
        try {
            mCamera = camera;
            Size size = getSize();
            int format = getFormat();
            HandlerThread thread = new HandlerThread(getClass().getSimpleName() + ".HandlerThread",
                    Process.THREAD_PRIORITY_URGENT_DISPLAY);
            thread.start();
            mHandler = new Handler(thread.getLooper());
            mReaderListener = new ImageReader.OnImageAvailableListener() {
                long mLastTimeInvoked;
                // throttle invocation
                final long interval = getInterval() - 1000 / 15;
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        try {
                            if (mFormat == ImageFormat.YUV_420_888) {
                                long t = System.currentTimeMillis();
                                long dt = t - mLastTimeInvoked;
                                if (dt > interval) {
                                    onCaptured(image.getPlanes()[0].getBuffer(),
                                            image.getPlanes()[1].getBuffer(),
                                            image.getPlanes()[2].getBuffer());
                                    mLastTimeInvoked = t;
                                }
                            }
                        } finally {
                            image.close();
                        }
                    }
                }
            };

            mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), format, 10);
            mImageReader.setOnImageAvailableListener(mReaderListener, mHandler);

            mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            mRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequest = mRequestBuilder.build();
            mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    onCaptureRequestStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    onCaptureRequestCompleted(session, request, result);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void onCaptured(ByteBuffer y, ByteBuffer u, ByteBuffer v);

    /* package */ Surface getSurface() {
        return mImageReader.getSurface();
    }

    /*
     * A callback from the CameraModule to notify that it is safe to start capture.
     * A onStartCommand is called on the handler thread.
     * Note: onStartCommand will be called multiple times.
     */
    /* package */ void onSessionConfigured(@NonNull CameraCaptureSession session) {
        if (mSession.get() == null) {
            mSession.set(session);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    int result = onStartCommand(null, 0, 0);
                    mStartResult.set(result);
                }
            });
        }
    }

    /* package */ void onSessionClosed(@NonNull CameraCaptureSession session) {
        mSession.set(null);
    }

    @Override
    final public
    @Nullable
    IBinder onBind(Intent intent) {
        return null;
    }

    /* package */  void setSize(String size) {
        switch (size) {
            case CameraModule.METADATA_VALUE_SIZE_1080:
                mSize = new Size(1920, 1080);
                break;
            case CameraModule.METADATA_VALUE_SIZE_720:
                mSize = new Size(1280, 720);
                break;
            case CameraModule.METADATA_VALUE_SIZE_480:
                mSize = new Size(640, 480);
                break;
            default:
                throw new IllegalArgumentException("invalid size " + size);
        }
    }

    /* package */  void setFormat(String format) {
        switch (format) {
            case CameraModule.METADATA_VALUE_FORMAT_YUV_420:
                mFormat = ImageFormat.YUV_420_888;
                break;
            case CameraModule.METADATA_VALUE_FORMAT_JPEG:
                mFormat = ImageFormat.JPEG;
                break;
            default:
                throw new IllegalArgumentException("invalid format " + format);
        }
    }

    /* package */  void setCropRect(String crop) {
        try {
            if (crop != null) {
                String[] ss = crop.split(",");
                int x = Integer.parseInt(ss[0].trim());
                int y = Integer.parseInt(ss[1].trim());
                int w = Integer.parseInt(ss[2].trim());
                int h = Integer.parseInt(ss[3].trim());
                mCropRect = new Rect(x, y, w, h);
            } else {
                mCropRect = new Rect(0, 0, mSize.getWidth(), mSize.getHeight());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("invalid crop rect " + crop);
        }
    }

    /* package */  void setInterval(int timeMs) {
        if (timeMs < 0) {
            throw new IllegalArgumentException("invalid repeat interval " + timeMs);
        }
        mInterval = timeMs;
    }

    @Override @CallSuper public void onDestroy() {
        if (mHandler != null) {
            mHandler.getLooper().quitSafely();
            mHandler = null;
        }
        mSession.set(null);
        mImageReader.close();
        super.onDestroy();
    }

    /*package*/ void surfacePrepared() {
        mPrepared = true;
    }

    /*package*/ boolean isSurfacePrepared() {
        return mPrepared;
    }

    /*package*/ boolean isVideoPipelineRequest() {
        return false;
    }
}

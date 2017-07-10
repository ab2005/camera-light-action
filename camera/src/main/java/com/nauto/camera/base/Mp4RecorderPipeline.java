package com.nauto.camera.base;

import com.nauto.camera.CameraStore;
import com.nauto.camera.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Media recorder running
 */
public class Mp4RecorderPipeline extends CameraPipeline {

    // TODO: choose the value accordingly based on FD perfomance
    private static final long DELTA_T = 1000;

    private static final long MINIMUM_FREE_SPACE = 1024 * 1024 * 1024L;
    private static final long SPACE_AFTER_TRIM = MINIMUM_FREE_SPACE * 2;
    final static SimpleDateFormat HH_MM_SS_TTT = new SimpleDateFormat("00:mm:ss.SSS");
    private static final boolean DO_FACE_REPORT = true;
    private static final boolean DO_FACE_STATS_REPORT = true;
    private static final boolean DO_FACE_STATS_COLLECT = false;

    private final MediaRecorder mRecorder;
    private final ScheduledExecutorService mThreadPool;
    private SnapshotHandler mSnapshotHandler;
    private String mNextVideoAbsolutePath;
    private String TAG = Mp4RecorderPipeline.class.getSimpleName();
    private long mRecordingStartTime;
    ScheduledFuture<?> mVideoSaveHandler;
    ScheduledFuture<?> mStoreTrimHandler;
    private ScheduledFuture<?> mStateReportHandler;

    private long mStopRecordingTime;

    double mPwr;
    double mCpuTemp;
    private CameraCaptureSession mSession;
    private List<CaptureModule> mCaptureModules;
    private long mCaptureStopTimeMs;
    private long mCaptureStartedTimeMs;
    private long mFrames;
    private File mReportFpsFile;
    private AtomicBoolean mPaused = new AtomicBoolean();
    private CameraPipelineConfig mPrevConfig;
    private PrintStream mStatsReport;
    private PrintStream mFaceReport;
    private PrintStream mFaceStatsReport;

    private Map<Long, String> mFaceStats;

    public Mp4RecorderPipeline(CameraModule service, CameraDevice camera, CameraPipelineConfig config) {
        super(service, camera, config);
        mPrevConfig = config.clone();
        mService.assertHandlerThread();
        TAG += "-" + camera.getId();
        mRecorder = new MediaRecorder();
        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            public void onError(MediaRecorder mr, int what, int extra) {
                mService.reportErrorAndStopService(CameraModule.MEDIA_RECORDER_ERROR,
                        new Exception("Media recorder reported error:" + what + ", extra:" + extra));
            }
        });
        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            public void onInfo(MediaRecorder mr, int what, int extra) {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                        mService.broadcast("Media recorder reported info MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED, extra: " + extra);
                        cutOff(0);
                        break;
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                        mService.broadcast("Media recorder reported info MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, extra: " + extra);
                        cutOff(0);
                        break;
                    case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                        mService.broadcast("Media recorder reported info MEDIA_RECORDER_INFO_UNKNOWN, extra: " + extra);
                        break;
                    default:
                        mService.broadcast("Media recorder reported info:" + what + ", extra:" + extra);
                        break;
                }
            }
        });


        mThreadPool = Executors.newScheduledThreadPool(1);

        try {
            Size size = new Size(config.mJpegWidth, config.mJpegHeight);
            mSnapshotHandler = new SnapshotHandler(service, camera, size, ImageFormat.JPEG, 3);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create SnapshotHandler");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(List<CaptureModule> captureModules) throws IOException, CameraAccessException {
        mCaptureModules = captureModules;

        for (CaptureModule cm : mCaptureModules) {
            cm.onCameraReady(mCamera);
        }

        startRecording();

        // pace of trying to trim equals to pace of generating new videos
        long dt = mConfig.mVideoLengthSec * 1000L;
        mVideoSaveHandler = createCutOffScheduler(dt, dt);

        if (mConfig.mSelfTrimming) {
            mStoreTrimHandler = mThreadPool.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    Log.d(TAG, "scheduled store trim");
                    // Trim original recorded video
                    CameraStore.trimOriginalVideoIfFreeSpaceIsShort(mService, MINIMUM_FREE_SPACE, SPACE_AFTER_TRIM);
                }
            }, dt / 2, dt, MILLISECONDS);
        }

        mStateReportHandler = mThreadPool.scheduleAtFixedRate(new Runnable() {
            long reportedFrames;

            public void run() {
                double fps = (mFrames - reportedFrames) / 30.;
                reportedFrames = mFrames;
                double pwr = Utils.getPowerNow();
                double temp = Utils.getCpuTemperature();
                String info = String.format("%.2f,%.1f,%.2f", fps, pwr, temp);
                mService.broadcastInfo(CameraModule.INFO_CODE_NUM_CAPTURE_REQUESTS_IN_30_SEC, info);
                mStatsReport.println(System.currentTimeMillis() + info);
                if (fps < mConfig.mVideoFrameRate / 2) {
                    mService.reportErrorAndStopService(CameraModule.ERROR_LOW_FPS, new RuntimeException("Frame rate is low: " + fps));
                }
            }
        }, 30, 30, SECONDS);

        if (Utils.doFpsRecording(mService)) {
            mReportFpsFile = new File(mService.getApplicationInfo().dataDir, "recorder_fps" + mService.CAMERA_ID);
        }

        mStatsReport = new PrintStream(new FileOutputStream(new File(mService.getApplicationInfo().dataDir, "cam_stats" + mService.CAMERA_ID), true));
        
        if (mService.isFrontCamera() && DO_FACE_STATS_COLLECT) {
            mFaceStats = new TreeMap<>();
        }
    }

    private ScheduledFuture<?> createCutOffScheduler(long startTime, long repeatTime) {
        return mThreadPool.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    if (mSession == null) {
                        Log.d(TAG, "start session");
                        startRecording();
                    } else {
                        Log.d(TAG, mPaused.get() ? "stop session" : "scheduled video cut off");
                        stopRecording();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, startTime, repeatTime, MILLISECONDS);
    }

    @Override
    public void cutOff(long timeMs) {
        if (mVideoSaveHandler != null) {
            mVideoSaveHandler.cancel(true);
        }
        mVideoSaveHandler = createCutOffScheduler(timeMs, mConfig.mVideoLengthSec * 1000);
    }

    @Override
    public void pause() {
        if (!mPaused.get()) {
            mPaused.set(true);
            cutOff(0);
        }
    }

    @Override
    public void resume() {
        if (mPaused.get()) {
            mPaused.set(false);
            cutOff(0);
        }
    }

    @Override
    public void snapshot(CameraStore.SnapshotMetadata metadata) {
        if (mSnapshotHandler != null) {
            try {
                mSnapshotHandler.capture(metadata);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        Log.d(TAG, "destroy(), session = " + mSession);
        try {
            if (mSnapshotHandler != null) {
                mSnapshotHandler.destroy();
                mSnapshotHandler = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            mThreadPool.shutdown();
            if (mVideoSaveHandler != null) {
                mVideoSaveHandler.cancel(true);
                mVideoSaveHandler = null;
            }
            if (mStoreTrimHandler != null) {
                mStoreTrimHandler.cancel(true);
                mStoreTrimHandler = null;
            }
            if (mStateReportHandler != null) {
                mStateReportHandler.cancel(true);
                mStateReportHandler = null;
            }
            if (mSession != null) {
                mSession.close();
                mSession = null;
            }
            onClosed(mSession);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // just to be sure recorder is released in case something went wrong when onClose is called
            try {
                mRecorder.release();
            } catch (Exception ex) {
                // ignore
            }
            mService.unbind();
            super.destroy();
        }
    }

    @Override
    public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
        super.onSurfacePrepared(session, surface);
        mSession = session;
        for (CaptureModule cm : mCaptureModules) {
            if (cm.getSurface() == surface) {
                Log.d(TAG, "prepared surface for " + cm.getClass().getName());
                cm.surfacePrepared();
            }
        }
        for (CaptureModule cm : mCaptureModules) {
            if (!cm.isSurfacePrepared()) return;
        }
        begin();
    }

    @Override
    public void onConfigured(CameraCaptureSession session) {
        mSession = session;
        boolean configured = true;

        if (mCaptureModules != null) {
            for (CaptureModule cm : mCaptureModules) {
                if (!cm.isSurfacePrepared()) {
                    configured = false;
                    break;
                }
            }
        }

        if (configured) {
            begin();
            return;
        }

        try {
            if (mCaptureModules != null) {
                for (CaptureModule cm : mCaptureModules) {
                    Log.d(TAG, "preparing surface for " + cm.getClass().getName() + "...");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        session.prepare(cm.getSurface());
                    } else {
                        begin();
                        return;
                    }
                }
            } else {
                begin();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void begin() {
        try {
            Log.d(TAG, "onConfigured() session = " + mSession + ", camera = " + mCamera);
            CaptureRequest.Builder rb = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            rb.addTarget(mRecorder.getSurface());
            rb.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            rb.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(mConfig.mVideoFrameRate, mConfig.mVideoFrameRate));
            rb.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            if (mService.isFrontCamera()) {
                rb.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
                rb.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
            }
            Utils.setAeRect(mService.getCameraPipelineConfig(), rb);
            if (mConfig.mSceneMode != 0) {
                rb.set(CaptureRequest.CONTROL_SCENE_MODE, mConfig.mSceneMode);
            }
            if (mConfig.mEffectMode != 0) {
                rb.set(CaptureRequest.CONTROL_EFFECT_MODE, mConfig.mEffectMode);
            }

            if (mCaptureModules != null) {
                for (CaptureModule cm : mCaptureModules) {
                    if (cm.isVideoPipelineRequest()) {
                        rb.addTarget(cm.getSurface());
                    }
                }
            }

            mSnapshotHandler.onSessionCreated(mSession);
            if (mCaptureModules != null) {
                for (CaptureModule cm : mCaptureModules) {
                    cm.onSessionConfigured(mSession);
                }
            }

            mRecordingStartTime = 0;
            mCaptureStartedTimeMs = 0;
            mSession.setRepeatingRequest(rb.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    if (mCaptureModules != null) {
                        for (CaptureModule cm : mCaptureModules) {
                            if (cm.isVideoPipelineRequest()) {
                                cm.onCaptureRequestStarted(session, request, timestamp, frameNumber);
                            }
                        }
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    long t = System.currentTimeMillis();
                    if (mCaptureStartedTimeMs == 0) {
                        mCaptureStartedTimeMs = t;
                        mRecordingStartTime = System.currentTimeMillis();
                        mRecorder.start();
                        // Face reporting
                        if (mFaceReport !=null) {
                            mFaceReport.println("WEBVTT");
                            mFaceReport.println();
                            mFaceReport.print("00:00:00.000");
                        }
                        if (mFaceStatsReport != null) {
                            mFaceStatsReport.println("WEBVTT");
                            mFaceStatsReport.println();
                        }
                        if (mFaceStats != null) {
                            mFaceStats.clear();
                        }
                        long gap = mStopRecordingTime > 0 ? mRecordingStartTime - mStopRecordingTime : 0;
                        Log.d(TAG, "Started media recorder with gap " + gap + "ms");
                        mService.broadcast("Recording with gap " + gap + " ms");
                    }
                    mCaptureStopTimeMs = t;
                    mFrames++;
                    if (mService.isFrontCamera()) {
                        Face[] faces = mService.mFaces.get();
                        android.hardware.camera2.params.Face[] camFaces = result.get(CaptureResult.STATISTICS_FACES);
                        int n = camFaces != null ? camFaces.length : 0;
                        int nBefore = mService.mNumFaces.get();
                        long time = System.currentTimeMillis() - mRecordingStartTime - DELTA_T;
                        if (nBefore != n) {
                            Log.d(TAG, "num faces changed: " + mService.mNumFaces.get() + " -> " + n);
                            mService.speakNumber(n);
                            if (mFaceReport != null && time > 0) {
                                // write vtt
                                String startTime = HH_MM_SS_TTT.format(new Date(time));
                                mFaceReport.println(" --> " + startTime);
                                mFaceReport.println(nBefore);
                                mFaceReport.println();
                                mFaceReport.print(startTime);
                            }
                        }
                        Face.copy(camFaces, faces);
                        mService.mFaces.set(faces);
                        mService.mNumFaces.set(n);
                        if (mFaceStatsReport != null && time > 0) {
                            String startTime = HH_MM_SS_TTT.format(new Date(time));
                            String endTime = HH_MM_SS_TTT.format(new Date(time + 1000 / mConfig.mVideoFrameRate));
                            mFaceStatsReport.println(startTime + " --> " + endTime);
                            mFaceStatsReport.println(getFaceInfo());
                            mFaceStatsReport.println();
                        }
                        if (mFaceStats != null && n > 0) {
                            mFaceStats.put(time, getFaceInfo());
                        }
                    }
                    if (mCaptureModules != null) {
                        for (CaptureModule cm : mCaptureModules) {
                            if (cm.isVideoPipelineRequest()) {
                               cm.onCaptureRequestCompleted(session, request, result);
                            }
                        }
                    }
                }
            }, mService.mCameraHandler);
        } catch (Exception e) {
            mService.reportErrorAndStopService(CameraModule.CAPTURE_SESSION_CONFIGURE_EXCEPTION, e);
        }
    }

    private String getFaceInfo() {
        Face[] faces = mService.mFaces.get();
        int numFaces = mService.mNumFaces.get();
        if (numFaces == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < numFaces; i++) {
            sb.append(faces[i]);
            if (i < numFaces - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void printFaceStats() {
        mFaceStatsReport.println("WEBVTT\n");
        final long deltaT = 1500;
        for (Map.Entry<Long, String> entry: mFaceStats.entrySet()) {
            Long t = entry.getKey() - deltaT;
            if (t > 0) {
                String val = entry.getValue();
                String startTime = HH_MM_SS_TTT.format(new Date(t));
                String endTime = HH_MM_SS_TTT.format(new Date(t + 1000 / mConfig.mVideoFrameRate));
                mFaceStatsReport.println(startTime + " --> " + endTime);
                mFaceStatsReport.println(val);
                mFaceStatsReport.println();
            }
        }
        mFaceStatsReport.close();
        mFaceStats.clear();
    }

    /**
     * Session is closed when a new session is created by the parent camera device,
     * or when the parent camera device is closed (either by the user closing the device,
     * or due to a camera device disconnection or fatal error).
     */
    @Override
    public void onClosed(@NonNull CameraCaptureSession session) {
        Log.d(TAG, "session onClosed()");
        if (mRecordingStartTime > 0) {
            try {
                mStopRecordingTime = System.currentTimeMillis();
                mRecorder.stop();
                mRecorder.reset();
                // get time from file name
                int p_filename_start = mNextVideoAbsolutePath.lastIndexOf("/") + 1;
                String fileName = mNextVideoAbsolutePath.substring(p_filename_start);
                String cameraId = String.valueOf(mNextVideoAbsolutePath.charAt(p_filename_start));
                CameraStore.VideoMetadata metadata = new CameraStore.VideoMetadata(mRecordingStartTime, mStopRecordingTime, fileName, cameraId);
                Log.d(TAG, "New video created: " + metadata.toString());
                mService.registerMediaFile(mNextVideoAbsolutePath, metadata);
                if (mFaceReport != null) {
                    // write vtt
                    int nBefore = mService.mNumFaces.get();
                    long time = System.currentTimeMillis() - mRecordingStartTime - DELTA_T;
                    String startTime = HH_MM_SS_TTT.format(new Date(time));
                    mFaceReport.println(" --> " + startTime);
                    mFaceReport.println(nBefore);
                    mFaceReport.println();

                    mFaceReport.close();
                    mFaceReport = null;
                }
                if (mFaceStatsReport != null) {
                    mFaceStatsReport.close();
                    mFaceStatsReport = null;
                }
                if (mFaceStats != null) {
                    printFaceStats();
                }
            } catch (RuntimeException ex) {
                // no output created, delete empty file
                //mService.broadcast("Video creation failed " + mNextVideoAbsolutePath);
                File file = new File(mNextVideoAbsolutePath);
                if (file.exists()) {
                    file.delete();
                }
            }
        } else {
            Log.d(TAG, "Recorder was not started!");
        }

        Throwable ex = null;
        // create a new session or release media recorder
        if (mSession != null) {
            try {
                mSession = null;
                startRecording();
                return;
            } catch (Exception e) {
                ex = e;
            }
        }
        try {
            Log.d(TAG, "releasing recorder...");
            mRecorder.release();
            Log.d(TAG, "release recorder done");
        } catch (Exception e) {
            ex = e;
        }
        mService.unbind();
        if (ex != null) {
            mService.reportErrorAndStopService(CameraModule.START_RECORDING_EXCEPTION, ex);
        }
    }

    /*
     * If startRecording is called with wrong config it will not create a session
     */
    private void startRecording() throws CameraAccessException, IOException, IllegalStateException {
        if (mPaused.get()) {
            Log.d(TAG, "Ignoring start recording, camera module is paused!");
            return;
        }

        if (mSession != null) {
            throw new RuntimeException("Session should be null!");
        }

        notifyConfigChange();

        mNextVideoAbsolutePath = mService.getVideoFilePath();
        if (mService.isFrontCamera() && DO_FACE_REPORT) {
            mFaceReport = new PrintStream(new FileOutputStream(new File(mNextVideoAbsolutePath + ".vtt")));
        }
        if (mService.isFrontCamera() && DO_FACE_STATS_REPORT) {
            mFaceStatsReport = new PrintStream(new FileOutputStream(new File(mNextVideoAbsolutePath + ".stats.vtt")));
        }
        if (mNextVideoAbsolutePath == null) {
            throw new RuntimeException("Failed to allocate file for media recorder!");
        }
        List<Surface> surfaces = new ArrayList<>();
        if (mConfig.mRecordAudio) {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setOutputFile(mNextVideoAbsolutePath);
        mRecorder.setVideoEncodingBitRate(mConfig.mVideoBitRate);
        mRecorder.setVideoFrameRate(mConfig.mVideoFrameRate);
        mRecorder.setVideoSize(mConfig.mVideoWidth, mConfig.mVideoHeight);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (mConfig.mRecordAudio) {
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }
        mRecorder.setMaxDuration(0);
        mRecorder.setMaxFileSize(0);
        mRecorder.prepare();
        Log.d(TAG, "Prepared video recorder writing to " + mNextVideoAbsolutePath);


        surfaces.add(mRecorder.getSurface());
        surfaces.add(mSnapshotHandler.getSurface());
        if (mCaptureModules != null) {
            for (CaptureModule cm : mCaptureModules) {
                surfaces.add(cm.getSurface());
            }
        }

        surfaces.add(mService.bind());
        // this will cause currently running session (if any) to close
        mCamera.createCaptureSession(surfaces, this, mService.mCameraHandler);
    }

    /*
     *  Calling this will stop recording current video and start a new one as soon as possible.
     */
    private void stopRecording() {
        Log.d(TAG, "stopRecording()");
        try {
            if (mCaptureModules != null) {
                for (CaptureModule cm : mCaptureModules) {
                    cm.onSessionClosed(mSession);
                }
            }
            mSession.abortCaptures();
            mSession.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void notifyConfigChange() {
        // before start camera, notify configuration changed if needed.
        if (!mConfig.equals(mPrevConfig)) {
            for (CaptureModule captureModule : mCaptureModules) {
                Log.d(TAG, "Notify mConfig Change: " + captureModule.getClass().getSimpleName());
                captureModule.onConfigurationChanged();
                mPrevConfig = mConfig.clone();
            }
        } else {
            Log.d(TAG, "mConfig not changed: " + getClass().getSimpleName());
        }
    }
}

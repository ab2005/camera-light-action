package com.nauto.camera.base;

import com.nauto.camera.CameraStore;
import com.nauto.camera.R;
import com.nauto.camera.ServiceUncaughtExceptionHandler;
import com.nauto.camera.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.MediaActionSound;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.SoundPool;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;

import static java.lang.annotation.RetentionPolicy.SOURCE;

abstract public class CameraModule extends Service {
    private static final String STATIC_TAG = CameraModule.class.getSimpleName();

    /**
     * Uri scheme to communicate with the service.
     * example:  camera://start?fps=30&videoLength=300
     */
    public static final String SCHEME = "camera";
    /**
     * Start camera pipeline command
     */
    public static final String START_COMMAND = "start";
    /**
     * Cut off video command
     */
    public static final String CUT_OFF_COMMAND = "cutOff";
    /**
     * Do snapshot command
     */
    public static final String SNAPSHOT_COMMAND = "snapshot";
    /**
     * Close capture session
     */
    public static final String PAUSE_COMMAND = "pause";
    /**
     * Start capture session
     */
    public static final String RESUME_COMMAND = "resume";

    /**
     * Start parameters
     */
    public static final String PARAM_DO_FACE_DETECTION = "doFaceDetection";
    public static final String PARAM_RUN_STICKY = "runSticky";
    public static final String PARAM_BIT_RATE = "bitRate";
    public static final String PARAM_FPS = "fps";
    public static final String PARAM_VIDEO_LENGTH_SEC = "videoLength";
    public static final String PARAM_RECORD_AUDIO = "recordAudio";
    public static final String PARAM_VIDEO_WIDTH = "videoWidth";
    public static final String PARAM_VIDEO_HEIGHT = "videoHeight";
    public static final String PARAM_PLAY_SOUND = "playSound";
    public static final String PARAM_AE_RECT = "aeRect";
    public static final String PARAM_VIDEO_SIZE = "videoSize";
    public static final String PARAM_JPEG_SIZE = "jpegSize";
    public static final String PARAM_CUT_OFF_TIME = "t";
    public static final String PARAM_SNAPSHOT_FILE_NAME = "snapshotFileName";
    public static final String PARAM_SCENE_MODE = "sceneMode";
    public static final String PARAM_EFFECT_MODE = "effectMode";
    public static final String PARAM_NIGHT_MODE = "nightMode";
    public static final String PARAM_SELF_TRIMMING = "selfTrimming";
    public static final String PARAM_JPEG_QUALITY = "jpegQuality";

    public static final String EXTRA_SNAPSHOT_META = "snapshotMetadata";

    public static final String EXTRA_INFO_CODE = CameraModule.class.getSimpleName() + ".INFO";
    /**
     * Intent key to retrieve a message from the camera service intent.
     */
    public static final String EXTRA_MESSAGE = CameraModule.class.getSimpleName() + ".MESSAGE";
    /**
     * Intent key to retrieve a camera id from the camera service intent.
     */
    public static final String EXTRA_ID = CameraModule.class.getSimpleName() + ".ID";
    /**
     * Intent key to retrieve a file metadata from the camera service intent.
     */
    public static final String EXTRA_METADATA = CameraModule.class.getSimpleName() + ".METADATA";
    /**
     * Intent key to retrieve a error code from the camera service intent.
     */
    public static final String EXTRA_ERROR_CODE = CameraModule.class.getSimpleName() + ".ERROR_CODE";
    /**
     * Intent key to retrieve a notice code from the camera service intent.
     */
    public static final String EXTRA_NOTICE_CODE = CameraModule.class.getSimpleName() + ".NOTICE_CODE";
    /**
     * Intent key to retrieve night mode boolean from the camera service intent.
     */
    public static final String EXTRA_NIGHT_MODE = CameraModule.class.getSimpleName() + ".NIGHT_MODE";

    public static final String LOG_FORMAT = "{id:%s, root:%s}";

    /**
     * Error codes
     */
    public static final int CAMERA_ACCESS_EXCEPTION = 0;
    public static final int CAMERA_PERMISSION_NOT_GRANTED = 1;
    public static final int CAMERA_ERROR = 2;
    public static final int CAMERA_STATE_ERROR = 3;
    public static final int CAPTURE_SESSION_CONFIGURE_FAILED = 4;
    public static final int CAPTURE_SESSION_CONFIGURE_EXCEPTION = 5;
    public static final int SERVICE_START_ERROR = 6;
    public static final int MEDIA_STORAGE_ERROR = 7;
    public static final int CAMERA_DISCONNECTED = 8;
    public static final int MEDIA_RECORDER_ERROR = 9;
    public static final int START_RECORDING_EXCEPTION = 10;
    public static final int ERROR_SYSTEM_CAMERA_SERVICE = 11;
    public static final int CAPTURE_MODULE_ERROR = 12;
    public static final int VIDEO_IS_BLACK_ERROR = 13;
    public static final int UNHANDLED_EXCEPTION_ERROR = 14;
    public static final int ERROR_SAVING_CREATION_TIME = 15;
    public static final int ERROR_LOW_FPS = 16;

    final AtomicReference<Face[]> mFaces;
    final AtomicInteger mNumFaces;

    private List<CaptureModule> mCaptureModules;

    private static final String INTENT_URI_KEY = "intent.uri";
    private static final int SOUND_NUMBER_OFFSET = 1;

    public SoundPool mSoundPool;
    private int mSounds[] = new int[10];

    private void playSound(int id) {
        mSoundPool.play(mSounds[id], 1.0f, 1.0f, 100, 0, 1);
    }

    public void speakNumber(int n) {
        // sound[0] is a
        mSoundPool.play(mSounds[n + SOUND_NUMBER_OFFSET], 1.0f, 1.0f, 100, 0, 1);
    }

    @Retention(SOURCE)
    @IntDef({CAMERA_ACCESS_EXCEPTION, CAMERA_PERMISSION_NOT_GRANTED, CAMERA_ERROR,
            CAMERA_STATE_ERROR, CAPTURE_SESSION_CONFIGURE_FAILED, CAPTURE_SESSION_CONFIGURE_EXCEPTION,
            SERVICE_START_ERROR, MEDIA_STORAGE_ERROR, CAMERA_DISCONNECTED, MEDIA_RECORDER_ERROR,
            START_RECORDING_EXCEPTION, ERROR_SYSTEM_CAMERA_SERVICE, CAPTURE_MODULE_ERROR,
            VIDEO_IS_BLACK_ERROR, UNHANDLED_EXCEPTION_ERROR, ERROR_SAVING_CREATION_TIME, ERROR_LOW_FPS})
    public @interface CameraError {
    }

    /**
     * Info codes
     */
    public static final int INFO_CODE_NUM_CAPTURE_REQUESTS_IN_30_SEC = 0;

    @Retention(SOURCE)
    @IntDef({INFO_CODE_NUM_CAPTURE_REQUESTS_IN_30_SEC})
    public @interface CameraInfo {
    }

    /**
     * Notice codes
     */
    public static final int SCHEDULED_CUT_OFF_COMPLETED = 0;
    public static final int SCHEDULED_CAPTURE_COMPLETED = 1;
    public static final int CAMERA_SERVICE_STARTED = 2;
    public static final int CAMERA_SERVICE_STOPPED = 3;
    public static final int CAMERA_SERVICE_IS_TRIMMING = 4;

    @Retention(SOURCE)
    @IntDef({SCHEDULED_CUT_OFF_COMPLETED, SCHEDULED_CAPTURE_COMPLETED,
            CAMERA_SERVICE_STARTED, CAMERA_SERVICE_STOPPED, CAMERA_SERVICE_IS_TRIMMING})
    public @interface CameraNotice {
    }

    public static final String METADATA_NAME_CAMERA_ID = "camera_id";
    public static final String METADATA_NAME_SIZE = "size";
    public static final String METADATA_NAME_CROP_RECT = "crop_rect";
    public static final String METADATA_NAME_FORMAT = "format";
    public static final String METADATA_NAME_INTERVAL = "interval";

    public static final String METADATA_VALUE_SIZE_1080 = "1920x1080";
    public static final String METADATA_VALUE_SIZE_720 = "1280x720";
    public static final String METADATA_VALUE_SIZE_480 = "640x480";
    public static final String METADATA_VALUE_FORMAT_YUV_420 = "YUV_420";
    public static final String METADATA_VALUE_FORMAT_JPEG = "JPEG";

    public final Handler mCameraHandler;
    private CameraPipeline mCameraPipeline;

    private int mServiceStartMode = START_NOT_STICKY;
    private boolean mIsFrontCamera;
    public int mFaceDetectionMode;
    public File mMediaRoot;
    // current video frame rate
    private int mVideoFrameRate;

    protected Intent mIntent;

    public void registerMediaFile(final String absolutePath, final Parcelable metadata) {
        final Context ctx = getApplicationContext();
        MediaScannerConnection.scanFile(ctx, new String[]{absolutePath}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String s, Uri uri) {
                        ContentValues values = new ContentValues();
                        String desc = null;
                        if (metadata instanceof CameraStore.VideoMetadata) {
                            CameraStore.VideoMetadata videoMetadata = (CameraStore.VideoMetadata) metadata;
                            Log.d(TAG, "Scheduled cut off completed: " + videoMetadata.toString());
                            try {
                                desc = Long.toString(videoMetadata.getStartTime());
                                if (desc != null) {
                                    values.put(MediaStore.Video.Media.DESCRIPTION, desc);
                                    int n = ctx.getContentResolver().update(uri, values, null, null);
                                    if (n != 1) {
                                        Log.d(TAG, "Failed to save description " + desc);
                                        broadcastError("Failed to save description ", ERROR_SAVING_CREATION_TIME);
                                    }
                                }
                                Log.d(TAG, "Saving video start time " + desc);
                            } finally {
                                broadcastNotice(videoMetadata, CameraModule.SCHEDULED_CUT_OFF_COMPLETED);
                            }

                            playShutterClick();
                        } else if (metadata instanceof CameraStore.SnapshotMetadata) {
                            CameraStore.SnapshotMetadata snapshotMetadata = (CameraStore.SnapshotMetadata) metadata;
                            Log.d(TAG, "Live snapshot completed: " + snapshotMetadata);
                            try {
                                desc = Long.toString(snapshotMetadata.getTakenTime());
                                if (desc != null) {
                                    values.put(MediaStore.Video.Media.DESCRIPTION, desc);
                                    int n = ctx.getContentResolver().update(uri, values, null, null);
                                    if (n != 1) {
                                        Log.d(TAG, "Failed to save description " + desc);
                                        broadcastError("Failed to save description ", ERROR_SAVING_CREATION_TIME);
                                    }
                                }
                                Log.d(TAG, "Saving snapshot taken time " + desc);
                            } finally {
                                broadcastNotice(snapshotMetadata, CameraModule.SCHEDULED_CAPTURE_COMPLETED);
                            }

                        } else {
                            Log.i(TAG, "Unknown type of file.");
                        }
                    }
                });
    }

    protected final String TAG;
    protected final String CAMERA_ID;
    protected final Uri BASE_URI;
    protected CameraPipelineConfig mConfig;

    public CameraPipelineConfig getConfig() {
        return mConfig.clone();
    }

    public static boolean isValidCameraServiceClassName(String className) {
        boolean valid = false;
        try {
            Class clazz = Class.forName(className);
            valid = (className.endsWith("0") || className.endsWith("1"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return valid;
    }

    /**
     * Start all camera services declared in the manifest.
     *
     * @param ctx
     * @return
     */
    public static IntentFilter startAll(Context ctx) {
        IntentFilter filter = new IntentFilter();
        try {
            String packageName = ctx.getPackageName();
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);

            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name)) {
                    filter.addAction(name);
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        Uri data = Uri.parse("camera://start");
                        Log.d(STATIC_TAG, "Starting service " + name);
                        Intent intent = new Intent("start", data, ctx, clazz);
                        ctx.startService(intent);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return filter;
    }

    /**
     * Start camera service by camera id.
     *
     * @param ctx
     * @param cameraId
     * @return
     */
    public static IntentFilter start(Context ctx, int cameraId, String params) {
        IntentFilter filter = new IntentFilter();
        try {
            String packageName = ctx.getPackageName();
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);

            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && name.endsWith(cameraId + "")) {
                    filter.addAction(name);
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        Uri data = Uri.parse("camera://start?" + params);
                        Log.d(STATIC_TAG, "Starting service " + name);
                        Intent intent = new Intent("start", data, ctx, clazz);
                        ctx.startService(intent);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return filter;
    }

    public static IntentFilter getIntentFilter(Context ctx) {
        IntentFilter filter = new IntentFilter();
        try {
            String packageName = ctx.getPackageName();
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name)) {
                    filter.addAction(name);
                    Log.d(STATIC_TAG, "Adding filter action " + name);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return filter;
    }

    public static void stopAll(Context ctx) {
        String packageName = ctx.getPackageName();
        try {
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name)) {
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        Log.d(STATIC_TAG, "Stopping service " + name);
                        ctx.stopService(new Intent(ctx, clazz));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop camera service by camera id.
     *
     * @param ctx
     * @param cameraId
     * @return
     */
    public static void stop(Context ctx, int cameraId) {
        String packageName = ctx.getPackageName();
        try {
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && name.endsWith(cameraId + "")) {
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        Log.d(STATIC_TAG, "Stopping service " + name);
                        ctx.stopService(new Intent(ctx, clazz));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void start(Context ctx, @NonNull String className, @Nullable String params) throws ClassNotFoundException {
        Class clazz = ctx.getClassLoader().loadClass(className);
        start(ctx, clazz, params);
    }

    public static void start(Context ctx, @NonNull Class clazz, @Nullable String params) {
        Uri uri = Uri.parse("camera://start?" + params);
        Intent intent = new Intent("start", uri, ctx, clazz);
        ctx.startService(intent);
    }

    public static void stop(@NonNull Context ctx, @NonNull Class clazz) {
        ctx.stopService(new Intent(ctx, clazz));
    }

    /**
     * Schedule video cut off, resume camera pipeline with the current config.
     *
     * @param ctx
     * @param cameraId null to cut video on all cameras.
     * @param delayMs  time in milliseconds to wait before executing video cut off.
     */
    public static void scheduleCutOff(Context ctx, @Nullable String cameraId, long delayMs) {
        scheduleCutOff(ctx, cameraId, delayMs, null);
    }

    /**
     * Schedule video cut off, resume camera pipeline with params overriding current config.
     *
     * @param ctx
     * @param cameraId null to cut video on all cameras.
     * @param delayMs  time in milliseconds to wait before executing video cut off.
     * @param params   new params changing based on default config.
     */
    public static void scheduleCutOff(Context ctx, @Nullable String cameraId, long delayMs, String params) {
        sendCommand(ctx, cameraId, CUT_OFF_COMMAND, PARAM_CUT_OFF_TIME + "=" + delayMs + (params != null ? "&" + params : ""));
    }

    /**
     * Stop camera pipeline capture session.
     *
     * @param ctx
     * @param cameraId null to stop session on all cameras.
     */
    public static void pause(Context ctx, @Nullable String cameraId) {
        sendCommand(ctx, cameraId, PAUSE_COMMAND, null);
    }

    /**
     * Start camera pipeline capture session.
     *
     * @param ctx
     * @param cameraId null to start video on all cameras.
     */
    public static void resume(Context ctx, @Nullable String cameraId) {
        sendCommand(ctx, cameraId, RESUME_COMMAND, null);
    }

    public static void snapshot(Context ctx, CameraStore.SnapshotMetadata snapshotMetadata) throws IOException {
        String packageName = ctx.getPackageName();
        try {
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;

                String cameraId = snapshotMetadata.getCameraId();
                if (isValidCameraServiceClassName(name) && (cameraId == null || name.endsWith(cameraId))) {
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        String cmd = "camera://" + SNAPSHOT_COMMAND;
                        Uri uri = Uri.parse(cmd);
                        Log.d(STATIC_TAG, "sending command to do snapshot:" + uri);
                        Intent intent = new Intent(SNAPSHOT_COMMAND, uri, ctx, clazz);
                        intent.putExtra(EXTRA_SNAPSHOT_META, snapshotMetadata);
                        ctx.startService(intent);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void snapshot(Context ctx, @Nullable String cameraId, String fileName) throws IOException {
        String packageName = ctx.getPackageName();
        try {
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && (cameraId == null || name.endsWith(cameraId))) {
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        String cmd = "camera://" + SNAPSHOT_COMMAND;
                        if (fileName != null) {
                            cmd += "?" + PARAM_SNAPSHOT_FILE_NAME + "=" + fileName;
                        }
                        Uri uri = Uri.parse(cmd);
                        Log.d(STATIC_TAG, "sending command to do snapshot:" + uri);
                        Intent intent = new Intent(SNAPSHOT_COMMAND, uri, ctx, clazz);
                        ctx.startService(intent);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static boolean isAnyRunning(@NonNull Context ctx) {
        try {
            String packageName = ctx.getPackageName();
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && isServiceRunning(ctx, name)) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isServiceRunning(@NonNull Context ctx, int cameraId) {
        try {
            String packageName = ctx.getPackageName();
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && name.endsWith("" + cameraId) && isServiceRunning(ctx, name)) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isServiceRunning(@NonNull Context ctx, @NonNull String className) {
        try {
            Class clazz = ctx.getClassLoader().loadClass(className);
            return isServiceRunning(ctx, clazz);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isServiceRunning(Context ctx, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /*
     *
     */
    private static void sendCommand(Context ctx, @Nullable String cameraId, String command, String params) {
        String packageName = ctx.getPackageName();
        try {
            PackageInfo packageInfo = ctx.getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (isValidCameraServiceClassName(name) && (cameraId == null || name.endsWith(cameraId))) {
                    try {
                        Class clazz = ctx.getClassLoader().loadClass(name);
                        Uri uri = Uri.parse("camera://" + command + (params != null ? ("?" + params) : ""));
                        Log.d(STATIC_TAG, "sending command: " + uri);
                        Intent intent = new Intent(command, uri, ctx, clazz);
                        ctx.startService(intent);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructs CameraService initializing camera id, main handler thread and global static instance.
     */
    public CameraModule() {
        TAG = getClass().getSimpleName();
        if (TAG.endsWith("0")) {
            CAMERA_ID = "0";
        } else if (TAG.endsWith("1")) {
            CAMERA_ID = "1";
        } else {
            throw new RuntimeException("Invalid class name " + TAG
                    + ". Use <Camera>0, <Camera>1 where <Camera> is any valid name.");
        }
        BASE_URI = new Uri.Builder().scheme(SCHEME).authority(CAMERA_ID).build();
        HandlerThread thread = new HandlerThread(TAG + ".HandlerThread", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        thread.start();
        mCameraHandler = new Handler(thread.getLooper());
        dbg(TAG, "Constructed " + this);

        mConfig = getCameraPipelineConfig();
        mFaces = new AtomicReference<Face[]>(new Face[10]);
        Face[] ff = mFaces.get();
        for (int i = 0; i < ff.length; i++) {
            ff[i] = new Face(new Rect(), 2);
        }
        mFaces.set(ff);
        mNumFaces = new AtomicInteger(0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCaptureModules = getCaptureModules();
        for (CaptureModule cm : mCaptureModules) {
            try {
                cm.onCreate();
            } catch (Exception ex) {
                ex.printStackTrace();
                broadcastError("onCreate() for " + cm.getClass() + " failed!" + ex, CAPTURE_MODULE_ERROR);
            }
        }
        updatePipelineConfigFromMetadata();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        // TODO: add six and more ?
        int[] soundIds = {
                R.raw.camera_error_sound,
                R.raw.zero,
                R.raw.one,
                R.raw.two,
                R.raw.three,
                R.raw.four,
                R.raw.five,
                R.raw.five, // TODO
                R.raw.five, // TODO
                R.raw.five};// TODO
        for (int i = 0; i < soundIds.length; i++) {
            mSounds[i] = mSoundPool.load(getApplicationContext(), soundIds[i], 1);
        }
    }

    @Override
    @MainThread
    public void onDestroy() {
        dbg(TAG, "onDestroy() " + this);
        playStopRecording();

        if (mCaptureModules != null) {
            for (CaptureModule cm : mCaptureModules) {
                try {
                    cm.onDestroy();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.e(TAG, "onDestroy() for " + cm.getClass() + " failed!");
                }
            }
            mCaptureModules = null;
        }
        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCameraPipeline != null) {
                    try {
                        mCameraPipeline.destroy();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        mCameraPipeline = null;
                    }
                    releaseGpu();
                }
            }
        });

        mCameraHandler.getLooper().quitSafely();
        try {
            long t = System.currentTimeMillis();
            mCameraHandler.getLooper().getThread().join(2000);
            long dt = System.currentTimeMillis() - t;
            if (dt > 2000) {
                broadcast("handler timeout");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        broadcastNotice(null, CAMERA_SERVICE_STOPPED);
        super.onDestroy();
    }

    @Override
    @MainThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // The only one case when this happens if Android restarted crashed service
            String data = getSharedPreferences(getClass().getName(), Context.MODE_PRIVATE)
                    .getString(INTENT_URI_KEY, null);
            Intent restartIntent = ((data == null) ? null : new Intent("start", Uri.parse(data)));
            return startModule(restartIntent);
        }

        String command = intent.getData() == null ? START_COMMAND : intent.getData().getHost();

        if (START_COMMAND.equals(command) && mCameraPipeline == null) {
            return startModule(intent);
        }

        if (CUT_OFF_COMMAND.equals(command) && mCameraPipeline != null) {
            String s = intent.getData().getQueryParameter(PARAM_CUT_OFF_TIME);
            // time is optional
            int time = s == null ? 0 : Integer.parseInt(s);
            updateConfigFromIntent(mConfig, intent);
            broadcast("executing cutoff command in " + time + " ms");
            mCameraPipeline.cutOff(time);
            return mServiceStartMode;
        }

        if (SNAPSHOT_COMMAND.equals(command) && mCameraPipeline != null) {
            try {
                CameraStore.SnapshotMetadata metadata = intent.getExtras().getParcelable(EXTRA_SNAPSHOT_META);
                if (metadata == null || metadata.getFilename() == null) {
                    String fileName = intent.getData().getQueryParameter(PARAM_SNAPSHOT_FILE_NAME);
                    broadcast("executing snapshot to " + fileName);
                    mCameraPipeline.snapshot(new CameraStore.SnapshotMetadata(fileName, CAMERA_ID, 0));
                } else {
                    broadcast("executing snapshot: " + metadata.toString());
                    mCameraPipeline.snapshot(metadata);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return mServiceStartMode;
        }

        if (PAUSE_COMMAND.equals(command) && mCameraPipeline != null) {
            mCameraPipeline.pause();
            return mServiceStartMode;
        }
        if (RESUME_COMMAND.equals(command) && mCameraPipeline != null) {
            mCameraPipeline.resume();
            return mServiceStartMode;
        }

        if (START_COMMAND.equals(command) && mCameraPipeline != null) {
            broadcast("ignore start, module already started ");
        } else if (CUT_OFF_COMMAND.equals(command) && mCameraPipeline == null) {
            broadcast("ignore cutoff, module not started ");
        } else if (SNAPSHOT_COMMAND.equals(command) && mCameraPipeline == null) {
            broadcast("ignore snapshot, module not started ");
        } else {
            broadcast("wrong command: " + intent.getData());
        }

        return mServiceStartMode;
    }

    private int startModule(Intent intent) {
        try {
            mMediaRoot = getMediaRootOrStopService();
            dbg(TAG, "Media root " + mMediaRoot);
            ServiceUncaughtExceptionHandler.install(this);
            // if intent is null we'll update config with parameters
            updateConfigFromIntent(mConfig, intent);
            dbg(TAG, "onStartCommand() executing with parameters " + getParamsString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    reportErrorAndStopService(CAMERA_PERMISSION_NOT_GRANTED,
                            new IllegalAccessException("Need CAMERA permission to run camera module"));
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }
            final CameraManager cm = (CameraManager) getApplication().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics chars = cm.getCameraCharacteristics(CAMERA_ID);
            mIsFrontCamera = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
            dbg(TAG, "Camera " + CAMERA_ID + " is " + (mIsFrontCamera ? "front" : "back") + " camera");
            int[] fd = chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
            int maxFd = chars.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
            if (maxFd > 0) {
                Arrays.sort(fd);
                mFaceDetectionMode = fd[fd.length - 1];
            }
            dbg(TAG, "Face detection mode = " + mFaceDetectionMode + ", " + fd.length);
            mServiceStartMode = mConfig.mRunSticky ? START_STICKY : START_NOT_STICKY;
            dbg(TAG, "Service run sticky = " + mConfig.mRunSticky);

            mCameraHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        initGpu();
                        startCamera(cm, CAMERA_ID);
                    } catch (Exception ex) {
                        reportErrorAndStopService(SERVICE_START_ERROR, ex);
                    }
                }
            }, CAMERA_ID.equals("0") ? 1000 : 0);

            notification();

            broadcastNotice(null, CAMERA_SERVICE_STARTED);
            playStartRecording();
            mIntent = intent;
            return mServiceStartMode;
        } catch (CameraAccessException e) {
            reportErrorAndStopService(SERVICE_START_ERROR, e);
        }

        return START_NOT_STICKY;

    }

    public boolean isFrontCamera() {
        return mIsFrontCamera;
    }

    private void startCamera(CameraManager cm, String cameraId) {
        assertHandlerThread();
        try {
            dbg(TAG, "opening camera " + cameraId + " ...");
            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    dbg(TAG, "onOpened() " + camera);
                    try {
                        mCameraPipeline = getCameraPipeline(camera);
                        mCameraPipeline.init(mCaptureModules);
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (mCameraPipeline != null) {
                            mCameraPipeline.destroy();
                            mCameraPipeline = null;
                        }
                        reportErrorAndStopService(CAMERA_ACCESS_EXCEPTION, e);
                    }
                }

                @Override
                public void onClosed(CameraDevice camera) {
                    super.onClosed(camera);
                    dbg(TAG, "onClosed() " + camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    dbg(TAG, "onDisconnected() " + camera);
                    reportErrorAndStopService(CAMERA_ACCESS_EXCEPTION, new Exception("camera disconnected"));
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    dbg(TAG, "onError(" + error + ") from camera " + CAMERA_ID);
                    switch (error) {
                        case ERROR_CAMERA_IN_USE:
                        case ERROR_MAX_CAMERAS_IN_USE:
                        case ERROR_CAMERA_DEVICE:
                            // restart service
                            reportErrorAndStopService(CAMERA_ERROR, new Exception("Camera error " + error));
                            break;
                        case ERROR_CAMERA_SERVICE:
                            // restart service, and notify the application to reboot device
                            reportErrorAndStopService(ERROR_SYSTEM_CAMERA_SERVICE, new Exception(
                                    "The Android camera service has encountered a fatal error! Reboot device is needed to recover! Error " + error));
                            break;
                    }
                }
            }, null);
        } catch (CameraAccessException e) {
            reportErrorAndStopService(CAMERA_ACCESS_EXCEPTION, e);
        }
    }

    protected CameraPipelineConfig getCameraPipelineConfig() {
        return (CAMERA_ID.equals("0") ? CameraPipelineConfig.DEFAULT_0 : CameraPipelineConfig.DEFAULT_1).clone();
    }

    protected CameraPipeline getCameraPipeline(CameraDevice camera) throws IOException, CameraAccessException {
        return new Mp4RecorderPipeline(CameraModule.this, camera, mConfig);
    }

    @Override
    public String toString() {
        return String.format(LOG_FORMAT, CAMERA_ID, mMediaRoot);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    protected String getParamsString() {
        return getCameraPipelineConfig().toString();
    }

    protected void updateConfigFromIntent(CameraPipelineConfig cfg, Intent intent) {
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null) {
                String query = uri.getQuery();
                if ("restart".equals(query)) {
                    String data = getSharedPreferences(getClass().getName(), Context.MODE_PRIVATE)
                            .getString(INTENT_URI_KEY, null);
                    if (data == null) {
                        uri = Uri.parse(data);
                    }
                }
                cfg.mDoFaceDetection = uri.getBooleanQueryParameter(PARAM_DO_FACE_DETECTION, cfg.mDoFaceDetection);
                cfg.mRunSticky = uri.getBooleanQueryParameter(PARAM_RUN_STICKY, cfg.mRunSticky);
                cfg.mPlaySound = uri.getBooleanQueryParameter(PARAM_PLAY_SOUND, cfg.mPlaySound);
                cfg.mRecordAudio = uri.getBooleanQueryParameter(PARAM_RECORD_AUDIO, cfg.mRecordAudio);
                cfg.mSelfTrimming = uri.getBooleanQueryParameter(PARAM_SELF_TRIMMING, cfg.mSelfTrimming);
                cfg.mNightMode = uri.getBooleanQueryParameter(PARAM_NIGHT_MODE, cfg.mNightMode);
                try {
                    cfg.mJpegQUality = Integer.parseInt(uri.getQueryParameter(PARAM_JPEG_QUALITY));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mVideoFrameRate = Integer.parseInt(uri.getQueryParameter(PARAM_FPS));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mVideoBitRate = Integer.parseInt(uri.getQueryParameter(PARAM_BIT_RATE));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mVideoLengthSec = Integer.parseInt(uri.getQueryParameter(PARAM_VIDEO_LENGTH_SEC));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mVideoWidth = Integer.parseInt(uri.getQueryParameter(PARAM_VIDEO_WIDTH));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mVideoHeight = Integer.parseInt(uri.getQueryParameter(PARAM_VIDEO_HEIGHT));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mSceneMode = Integer.parseInt(uri.getQueryParameter(PARAM_SCENE_MODE));
                } catch (Exception e) {/* ignore */}
                try {
                    cfg.mEffectMode = Integer.parseInt(uri.getQueryParameter(PARAM_EFFECT_MODE));
                } catch (Exception e) {/* ignore */}
                String s = uri.getQueryParameter(PARAM_AE_RECT);
                if (s != null) {
                    try {
                        String[] ss = s.split(",");
                        cfg.mAeRects = new float[]{
                                Float.parseFloat(ss[0]),
                                Float.parseFloat(ss[1]),
                                Float.parseFloat(ss[2]),
                                Float.parseFloat(ss[3])
                        };
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // save config
                SharedPreferences sharedPrefs = getSharedPreferences(getClass().getName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                String data = "camera://start?" + cfg.toString();
                editor.putString(INTENT_URI_KEY, data);
                editor.commit();
            }
        }
        Log.d(TAG, CAMERA_ID + " params = " + getParamsString());
    }

    public void reportErrorAndStopService(@CameraError int error, @NonNull Throwable ex) {
        String s = null;
        switch (error) {
            case CAMERA_ACCESS_EXCEPTION:
                s = "CAMERA_ACCESS_EXCEPTION";
                break;
            case CAMERA_PERMISSION_NOT_GRANTED:
                s = "CAMERA_PERMISSION_NOT_GRANTED";
                break;
            case CAMERA_ERROR:
                s = "CAMERA_ERROR";
                break;
            case CAMERA_STATE_ERROR:
                s = "CAMERA_STATE_ERROR";
                break;
            case SERVICE_START_ERROR:
                s = "SERVICE_START_ERROR";
                break;
            case CAPTURE_SESSION_CONFIGURE_FAILED:
                s = "CAPTURE_SESSION_CONFIGURE_FAILED";
                break;
            case CAPTURE_SESSION_CONFIGURE_EXCEPTION:
                s = "CAPTURE_SESSION_CONFIGURE_EXEPTION";
                break;
            case CAMERA_DISCONNECTED:
                s = "CAMERA_DISCONNECTED";
                break;
            case MEDIA_RECORDER_ERROR:
                s = "MEDIA_RECORDER_ERROR";
                break;
            case START_RECORDING_EXCEPTION:
                s = "START_RECORDING_EXCEPTION";
                break;
            default:
                s = "UNKNOWN ERROR";
                break;
        }
        String errMsg = s + ": " + ex.getMessage();
        err(TAG, errMsg);
        broadcastError(errMsg, error);
        playAlarm();
        stopSelf();
    }

    public void broadcastError(String msg, @CameraError int error) {
        String action = getClass().getName();
        Intent intent = new Intent(action).putExtra(EXTRA_ID, CAMERA_ID).putExtra(EXTRA_MESSAGE, msg)
                .putExtra(EXTRA_ERROR_CODE, error);
        Log.d(TAG, "broadcast error: " + TAG + ", " + CAMERA_ID + ", " + error + ", " + msg);
        sendBroadcast(intent);
    }

    public void broadcastError(Parcelable metadata, @CameraError int error) {
        String action = getClass().getName();
        Intent intent = new Intent(action).putExtra(EXTRA_ID, CAMERA_ID).putExtra(EXTRA_METADATA, metadata)
                .putExtra(EXTRA_ERROR_CODE, error);
        Log.d(TAG, "broadcast error: " + TAG + ", " + CAMERA_ID + ", " + error + ", " + metadata.toString());
        sendBroadcast(intent);
    }

    public void broadcastNotice(Parcelable metadata, @CameraNotice int notice) {
        String action = getClass().getName();
        Intent intent = new Intent(action)
                .putExtra(EXTRA_ID, CAMERA_ID)
                .putExtra(EXTRA_METADATA, metadata)
                .putExtra(EXTRA_NOTICE_CODE, notice)
                .putExtra(EXTRA_NIGHT_MODE, mConfig.mNightMode);
        if (metadata == null) {
            Log.d(TAG, "broadcast notice: " + TAG + ", " + CAMERA_ID + ", " + notice);
        } else {
            Log.d(TAG, "broadcast notice: " + TAG + ", " + CAMERA_ID + ", " + notice + ", " + metadata.toString());
        }
        sendBroadcast(intent);
    }

    public void broadcast(String msg) {
        String action = getClass().getName();
        Intent intent = new Intent(action).putExtra(EXTRA_ID, CAMERA_ID).putExtra(EXTRA_MESSAGE, msg);
        Log.d(TAG, "broadcast: " + TAG + ", " + CAMERA_ID + ", " + msg);
        sendBroadcast(intent);
    }

    public void broadcastInfo(@CameraInfo int code, String msg) {
        String action = getClass().getName();
        Intent intent = new Intent(action).putExtra(EXTRA_ID, CAMERA_ID).putExtra(EXTRA_INFO_CODE, code).putExtra(EXTRA_MESSAGE, msg);
        Log.d(TAG, "broadcast info: " + TAG + ", " + CAMERA_ID + ", " + msg);
        sendBroadcast(intent);
    }

    protected File getMediaRootOrStopService() {
        File mediaRoot = Utils.getMediaRoot(getApplicationContext());
        if (mediaRoot == null) {
            broadcastError("No media storage available ", MEDIA_STORAGE_ERROR);
            throw new RuntimeException();
        }
        return mediaRoot;
    }

    public static final MediaActionSound SOUND = new MediaActionSound();

    protected String getVideoFilePath() {
        if (mMediaRoot == null) {
            mMediaRoot = getMediaRootOrStopService();
        }
        String originalVideoDirectory = mMediaRoot + "/originalVideo";
        if (!new File(originalVideoDirectory).exists()) {
            Utils.makeDirectory(originalVideoDirectory);
        }
        long currentTimeSec = System.currentTimeMillis();
        String fname = CAMERA_ID + "_" + currentTimeSec + ".mp4";
        return new File(originalVideoDirectory, fname).getAbsolutePath();
    }

    public void playStartRecording() {
        if (mConfig != null && mConfig.mPlaySound) {
            SOUND.play(MediaActionSound.START_VIDEO_RECORDING);
        }
    }

    public void playStopRecording() {
        if (mConfig != null && mConfig.mPlaySound) {
            SOUND.play(MediaActionSound.STOP_VIDEO_RECORDING);
        }
    }

    public void playShutterClick() {
        if (mConfig != null && mConfig.mPlaySound) {
            SOUND.play(MediaActionSound.SHUTTER_CLICK);
        }
    }

    private static Ringtone alarm;

    public void playAlarm() {
        if (mConfig != null && mConfig.mPlaySound) {
            playSound(0);
        }
    }

    public void assertHandlerThread() {
        Looper looper = Looper.myLooper();
        if (mCameraHandler == null || mCameraHandler.getLooper() != looper) {
            throw new RuntimeException("Current looper " + looper + " is not a handler looper " + mCameraHandler);
        }
    }

    public static void dbg(String TAG, String msg) {
        Log.d(TAG, msg);
    }

    public static void err(String TAG, String msg) {
        Log.e(TAG, msg);
    }

    private void notification() {
        //First time
        Intent notificationIntent = Intent.makeMainActivity(
                new ComponentName("com.nauto.example.camera",
                        "com.nauto.example.camera.MainActivity"));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(getApplicationContext())
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setUsesChronometer(true)
                .setTicker("CameraModule" + CAMERA_ID)
                .setContentTitle("CameraModule" + CAMERA_ID)
                .setContentText(getParamsString())
                .setPriority(Notification.PRIORITY_MAX)
//                .setLights(0xff00ff00, 300, 100)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(7777 + (CAMERA_ID.equals("0") ? 0 : 1), notification);
    }

    protected List<CaptureModule> getCaptureModules() {
        List<CaptureModule> modules = new LinkedList();
        String packageName = getPackageName();
        String processName = getProcessName();
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                try {
                    Class clazz = getClassLoader().loadClass(name);
                    if (CaptureModule.class.isAssignableFrom(clazz)) {
                        Bundle bundle = serviceInfo.metaData;
                        if (bundle != null) {
                            String camId = "" + bundle.getInt(METADATA_NAME_CAMERA_ID);
                            if (CAMERA_ID.equals(camId)) {
                                Log.d(STATIC_TAG, "Adding capture module " + name);
                                String size = bundle.getString(METADATA_NAME_SIZE);
                                String format = bundle.getString(METADATA_NAME_FORMAT);
                                int intervalMs = bundle.getInt(METADATA_NAME_INTERVAL);
                                String cropRect = bundle.getString(METADATA_NAME_CROP_RECT);
                                if (processName.equals(serviceInfo.processName)
                                        || serviceInfo.applicationInfo.processName.equals(serviceInfo.processName)) {
                                    CaptureModule captureModule = (CaptureModule) clazz.getConstructor().newInstance();
                                    captureModule.setService(this);
                                    captureModule.setSize(size);
                                    captureModule.setFormat(format);
                                    captureModule.setCropRect(cropRect);
                                    captureModule.setInterval(intervalMs);
                                    modules.add(captureModule);
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException
                        | NoSuchMethodException
                        | InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return modules;
    }

    protected void updatePipelineConfigFromMetadata() {
        List<CaptureModule> modules = new LinkedList();
        String packageName = getPackageName();
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                try {
                    if (name.equals(getClass().getName())) {
                        Bundle bundle = serviceInfo.metaData;
                        if (bundle != null) {
                            CameraPipelineConfig cfg = getCameraPipelineConfig();
                            String s = bundle.getString(PARAM_AE_RECT);
                            if (s != null) {
                                try {
                                    String[] ss = s.split(",");
                                    cfg.mAeRects = new float[]{
                                            Float.parseFloat(ss[0]),
                                            Float.parseFloat(ss[1]),
                                            Float.parseFloat(ss[2]),
                                            Float.parseFloat(ss[3])
                                    };
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            s = bundle.getString(PARAM_VIDEO_SIZE);
                            if (s != null) {
                                try {
                                    String[] ss = s.split(",");
                                    cfg.mVideoWidth = Integer.parseInt(ss[0]);
                                    cfg.mVideoHeight = Integer.parseInt(ss[1]);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            s = bundle.getString(PARAM_JPEG_SIZE);
                            if (s != null) {
                                try {
                                    String[] ss = s.split(",");
                                    cfg.mJpegWidth = Integer.parseInt(ss[0]);
                                    cfg.mJpegHeight = Integer.parseInt(ss[1]);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            cfg.mVideoFrameRate = bundle.getInt(PARAM_FPS, cfg.mVideoFrameRate);
                            cfg.mDoFaceDetection = bundle.getBoolean(PARAM_DO_FACE_DETECTION, cfg.mDoFaceDetection);
                            cfg.mRunSticky = bundle.getBoolean(PARAM_RUN_STICKY, cfg.mRunSticky);
                            cfg.mSelfTrimming = bundle.getBoolean(PARAM_SELF_TRIMMING, cfg.mSelfTrimming);
                            cfg.mPlaySound = bundle.getBoolean(PARAM_PLAY_SOUND, cfg.mPlaySound);
                            cfg.mRecordAudio = bundle.getBoolean(PARAM_RECORD_AUDIO, cfg.mRecordAudio);
                            cfg.mVideoBitRate = bundle.getInt(PARAM_BIT_RATE, cfg.mVideoBitRate);
                            cfg.mVideoLengthSec = bundle.getInt(PARAM_VIDEO_LENGTH_SEC, cfg.mVideoLengthSec);
                            cfg.mVideoWidth = bundle.getInt(PARAM_VIDEO_WIDTH, cfg.mVideoWidth);
                            cfg.mVideoHeight = bundle.getInt(PARAM_VIDEO_HEIGHT, cfg.mVideoHeight);
                            cfg.mPlaySound = bundle.getBoolean(PARAM_PLAY_SOUND, cfg.mPlaySound);
                            cfg.mJpegQUality = bundle.getInt(PARAM_JPEG_QUALITY, cfg.mVideoWidth);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getProcessName() {
        String packageName = getPackageName();
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                String name = serviceInfo.name;
                if (name.equals(getClass().getName())) {
                    return serviceInfo.processName;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        throw new RuntimeException();
    }

    public int getNumFaces() {
        return mNumFaces.get();
    }

    public Face[] getFaces() {
        return mFaces.get();
    }

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLConfig mConf;
    private EGLSurface mEglSurface;
    private SurfaceTexture mTexture;
    private Surface mSurface;

    private void initGpu() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException("unable to initialize");
        }
        int[] attribList = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142, 1, EGL14.EGL_NONE};
        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);
        checkEglError("failed eglCreateContext RGB888+recordable ES2");
        int[] attrib_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        checkEglError("failed eglCreateContext");
        mConf = configs[0];
        int[] surfaceAttribs = {EGL14.EGL_WIDTH, 1920, EGL14.EGL_HEIGHT, 1080, EGL14.EGL_NONE};
        mEglSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mConf, surfaceAttribs, 0);
        checkEglError("failed eglCreatePbufferSurface");
        if (mEglSurface == null) {
            throw new RuntimeException("surface is null");
        }
        EGL14.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEGLContext);
    }

    private void releaseGpu() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        if (mEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEGLDisplay, mEglSurface);
        }
        mEglSurface = EGL14.EGL_NO_SURFACE;
        unbind();
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": error: 0x" + Integer.toHexString(error));
        }
    }

    public Surface bind() {
        if (mTexture == null) {
            mTexture = new SurfaceTexture(-1234);
            mSurface = new Surface(mTexture);
        }
        return mSurface;
    }

    public void unbind() {
        if (mTexture != null) {
            mTexture.release();
            mTexture = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }
}

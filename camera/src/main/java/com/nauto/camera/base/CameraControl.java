package com.nauto.camera.base;

import com.nauto.camera.CameraStore;
import com.nauto.camera.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.util.Log;

import static com.nauto.camera.base.CameraModule.CAMERA_ACCESS_EXCEPTION;
import static com.nauto.camera.base.CameraModule.CAMERA_DISCONNECTED;
import static com.nauto.camera.base.CameraModule.CAMERA_ERROR;
import static com.nauto.camera.base.CameraModule.CAMERA_PERMISSION_NOT_GRANTED;
import static com.nauto.camera.base.CameraModule.CAMERA_STATE_ERROR;
import static com.nauto.camera.base.CameraModule.CAPTURE_MODULE_ERROR;
import static com.nauto.camera.base.CameraModule.CAPTURE_SESSION_CONFIGURE_EXCEPTION;
import static com.nauto.camera.base.CameraModule.CAPTURE_SESSION_CONFIGURE_FAILED;
import static com.nauto.camera.base.CameraModule.ERROR_SAVING_CREATION_TIME;
import static com.nauto.camera.base.CameraModule.ERROR_SYSTEM_CAMERA_SERVICE;
import static com.nauto.camera.base.CameraModule.EXTRA_ERROR_CODE;
import static com.nauto.camera.base.CameraModule.EXTRA_INFO_CODE;
import static com.nauto.camera.base.CameraModule.EXTRA_MESSAGE;
import static com.nauto.camera.base.CameraModule.EXTRA_METADATA;
import static com.nauto.camera.base.CameraModule.EXTRA_NOTICE_CODE;
import static com.nauto.camera.base.CameraModule.MEDIA_RECORDER_ERROR;
import static com.nauto.camera.base.CameraModule.MEDIA_STORAGE_ERROR;
import static com.nauto.camera.base.CameraModule.SERVICE_START_ERROR;
import static com.nauto.camera.base.CameraModule.START_RECORDING_EXCEPTION;
import static com.nauto.camera.base.CameraModule.UNHANDLED_EXCEPTION_ERROR;
import static com.nauto.camera.base.CameraModule.VIDEO_IS_BLACK_ERROR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

abstract public class CameraControl extends BroadcastReceiver {
    private static final int MAX_TIME_ERROR_LIVES_MS = 30 * 1000;
    private static final int MAX_TIME_TO_WAIT_FOR_STOP_NOTIFICATION_MS = 5 * 1000;
    private static final int MAX_ERRORS = 10;
    private static final long TIME_TO_WAIT_BEFORE_RESTART_MS = 2000;
    protected final int CAMERA_ID;

    private static boolean mDoNotLogStats;
    private static PrintStream mStatsReport;

    private final String mTag;
    final private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    final private static SimpleDateFormat mDf = new SimpleDateFormat();
    final public static long[] mLastInfoTime = {0, 0};
    final private static List<CameraError>[] mCameraErrorList = new List[]{new LinkedList<>(), new LinkedList<>()};
    final public static CameraStore.SnapshotMetadata[] mCaptureMetadata = {null, null};
    final public static CameraStore.VideoMetadata[] mVideoMetadata = {null, null};
    final private static ScheduledFuture<?>[] mRestartHandler = {null, null};
    final private static ScheduledFuture<?>[] mRebootHandler = {null, null};
    final private static ScheduledFuture<?>[] mStartHandler = {null, null};


    protected abstract void onError(String message);

    protected abstract void onStarted();

    protected abstract void onStopped();

    protected abstract void onCapture(CameraStore.SnapshotMetadata snapshotMetadata);

    protected abstract void onCutOff(CameraStore.VideoMetadata videoMetadata);

    public void startCameraWatchDog(final Context ctx, final long timeInterval, final String params) {
        stopCameraWatchDog();
        mStartHandler[CAMERA_ID] = scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (mRebootHandler[CAMERA_ID] == null && mRestartHandler[CAMERA_ID] == null) {
                    if (!CameraModule.isServiceRunning(ctx, CAMERA_ID)) {
                        logd(mTag, "Staring camera " + CAMERA_ID + " from watchdog ...");
                        CameraModule.start(ctx, CAMERA_ID, null);
                    }
                }
            }
        }, timeInterval, timeInterval, MILLISECONDS);
    }

    public void stopCameraWatchDog()  {
        if (mStartHandler[CAMERA_ID] != null) {
            logd(mTag, "Canceling camera " + CAMERA_ID +  " runtime handler");
            mStartHandler[CAMERA_ID].cancel(true);
            mStartHandler[CAMERA_ID] = null;
        }
    }

    public CameraControl() {
        mTag = getClass().getSimpleName();
        if (mTag.endsWith("0")) {
            CAMERA_ID = 0;
        } else if (mTag.endsWith("1")) {
            CAMERA_ID = 1;
        } else {
            throw new RuntimeException("Invalid class name " + mTag);
        }
    }

    @Override
    @CallSuper
    public void onReceive(final Context context, Intent intent) {
        final String id = intent.getStringExtra(CameraModule.EXTRA_ID);

        if (!mDoNotLogStats && mStatsReport == null) {
            try {
                File fstats = new File(context.getApplicationInfo().dataDir, "cam_control_log");
                mStatsReport = new PrintStream(new FileOutputStream(fstats), true);
                Log.d(mTag, "stats report at: " + fstats.getAbsolutePath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                mDoNotLogStats = true;
            }
        }

        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Log.d(mTag, "BOOT_COMPLETED: starting camera " + CAMERA_ID + " ...");
            startCameraWatchDog(context, 10 * 1000, "reboot");
//            CameraModule.start(context, CAMERA_ID, "reboot");
            mStatsReport.println("BOOT_COMPLETED: starting camera " + CAMERA_ID + " ...");
            return;
        }

        if (!getClass().getName().endsWith(id)) {
            throw new RuntimeException(mTag + " : Class name does not match camera id" + id);
        }


        final String msg = intent.getStringExtra(EXTRA_MESSAGE);
        final Parcelable metadata = intent.getParcelableExtra(EXTRA_METADATA);
        final int err = intent.getIntExtra(EXTRA_ERROR_CODE, -1);
        final int notice = intent.getIntExtra(EXTRA_NOTICE_CODE, -1);
        final int info = intent.getIntExtra(EXTRA_INFO_CODE, -1);

        if (err != -1) {
            loge(mTag, "errorCode: " + err + ": " + msg);
            CameraError cameraError = new CameraError(id, err, msg);
            mCameraErrorList[CAMERA_ID].add(cameraError);
            logd(mTag, this + "error list size: " + mCameraErrorList[CAMERA_ID].size());
            // if error list contains a fatal error or max num errors reached then schedule reboot
            if (mRebootHandler[CAMERA_ID] == null && isRebootNeeded(err)) {
                logd(mTag, "Scheduling reboot...");
                mRebootHandler[CAMERA_ID] = scheduler.schedule(new Runnable() {
                    public void run() {
                        logd(mTag, "Rebooting...");
                        onReboot(context);
                    }
                }, MAX_TIME_TO_WAIT_FOR_STOP_NOTIFICATION_MS, MILLISECONDS);
            } else if (mRestartHandler[CAMERA_ID] == null) {
                logd(mTag, "Scheduling restart...");
                mRestartHandler[CAMERA_ID] = scheduler.schedule(new Runnable() {
                    public void run() {
                        logd(mTag, "Restarting...");
                        onRestart(context);
                    }
                }, MAX_TIME_TO_WAIT_FOR_STOP_NOTIFICATION_MS, MILLISECONDS);
            }
            switch (err) {
                case CAMERA_ACCESS_EXCEPTION:
                    loge(mTag, "CAMERA_ACCESS_EXCEPTION:" + msg);
                    break;
                case CAMERA_PERMISSION_NOT_GRANTED:
                    loge(mTag, "CAMERA_PERMISSION_NOT_GRANTED:" + msg);
                    break;
                case CAMERA_ERROR:
                    loge(mTag, "CAMERA_ERROR:" + msg);
                    break;
                case CAMERA_STATE_ERROR:
                    loge(mTag, "CAMERA_STATE_ERROR:" + msg);
                    break;
                case CAPTURE_SESSION_CONFIGURE_FAILED:
                    loge(mTag, "CAPTURE_SESSION_CONFIGURE_FAILED:" + msg);
                    break;
                case CAPTURE_SESSION_CONFIGURE_EXCEPTION:
                    loge(mTag, "CAPTURE_SESSION_CONFIGURE_EXCEPTION:" + msg);
                    break;
                case SERVICE_START_ERROR:
                    loge(mTag, "SERVICE_START_ERROR:" + msg);
                    break;
                case MEDIA_STORAGE_ERROR:
                    loge(mTag, "MEDIA_STORAGE_ERROR:" + msg);
                    break;
                case CAMERA_DISCONNECTED:
                    loge(mTag, "CAMERA_DISCONNECTED:" + msg);
                    break;
                case MEDIA_RECORDER_ERROR:
                    loge(mTag, "MEDIA_RECORDER_ERROR:" + msg);
                    break;
                case START_RECORDING_EXCEPTION:
                    loge(mTag, "START_RECORDING_EXCEPTION:" + msg);
                    break;
                case ERROR_SYSTEM_CAMERA_SERVICE:
                    loge(mTag, "ERROR_SYSTEM_CAMERA_SERVICE:" + msg);
                    break;
                case CAPTURE_MODULE_ERROR:
                    loge(mTag, "CAPTURE_MODULE_ERROR:" + msg);
                    break;
                case VIDEO_IS_BLACK_ERROR:
                    loge(mTag, "VIDEO_IS_BLACK_ERROR:" + msg);
                    break;
                case UNHANDLED_EXCEPTION_ERROR:
                    loge(mTag, "UNHANDLED_EXCEPTION_ERROR:" + msg);
                    break;
                case ERROR_SAVING_CREATION_TIME:
                    loge(mTag, "ERROR_SAVING_CREATION_TIME:" + msg);
                    break;
                default:
                    loge(mTag, "Undefined error code " + err + ":" + msg);
                    break;
            }
        } else if (notice != -1) {
            switch (notice) {
                case CameraModule.CAMERA_SERVICE_STARTED:
                    onStarted();
                    logi(mTag, "CAMERA_SERVICE_STARTED");
                    break;
                case CameraModule.SCHEDULED_CUT_OFF_COMPLETED:
                    onCutOff(mVideoMetadata[CAMERA_ID] = (CameraStore.VideoMetadata) metadata);
                    logi(mTag, "SCHEDULED_CUT_OFF_COMPLETED:" + metadata.toString());
                    break;
                case CameraModule.SCHEDULED_CAPTURE_COMPLETED:
                    onCapture(mCaptureMetadata[CAMERA_ID] = (CameraStore.SnapshotMetadata) metadata);
                    logi(mTag, "SCHEDULED_CAPTURE_COMPLETED" + metadata.toString());
                    break;
                case CameraModule.CAMERA_SERVICE_IS_TRIMMING:
                    logw(mTag, "CAMERA_SERVICE_IS_TRIMMING");
                    break;
                case CameraModule.CAMERA_SERVICE_STOPPED:
                    onStopped();
                    logi(mTag, "CAMERA_SERVICE_STOPPED");
                    logd(mTag, this + "error list size: " + mCameraErrorList[CAMERA_ID].size() + ":" + mCameraErrorList.hashCode());
                    if (mRebootHandler[CAMERA_ID] != null) {
                        logi(mTag, "Rebooting...");
                        mRebootHandler[CAMERA_ID].cancel(true);
                        mRebootHandler[CAMERA_ID] = null;
                        onReboot(context);
                    } else if (mRestartHandler[CAMERA_ID] != null) {
                        mRestartHandler[CAMERA_ID].cancel(true);
                        mRestartHandler[CAMERA_ID] = null;
                        onRestart(context);
                    }
                    break;
                default:
                    logw(mTag, "Undefined notice code " + notice);
                    break;
            }
        } else if (info != -1) {
            switch (info) {
                case CameraModule.INFO_CODE_NUM_CAPTURE_REQUESTS_IN_30_SEC:
                    // we expect this info every 30 sec
                    logi(mTag, "INFO:" + msg);
                    String[] ss = msg.split(",");
                    float fps = Float.parseFloat(ss[0]);
                    if (fps < 10) {
                        logw(mTag, "low fps detected " + fps + " fps");
                    }
                    mLastInfoTime[CAMERA_ID] = System.currentTimeMillis();
                    break;
                default:
                    logw(mTag, "Undefined info code " + notice);
                    break;
            }
        } else {
            if (msg != null) {
                logi(mTag, "MESSAGE:" + msg);
            }
        }
    }

    private void loge(String tag, String s) {
        Log.e(tag, s);
        onError(s);
        if (mStatsReport != null)
            mStatsReport.println(tag + " E " + mDf.format(System.currentTimeMillis()) + " " + s);
    }

    private void logd(String tag, String s) {
        Log.d(tag, s);
        if (mStatsReport != null)
            mStatsReport.println(tag + " D " + mDf.format(System.currentTimeMillis()) + " " + s);
    }

    private void logw(String tag, String s) {
        Log.w(tag, s);
        if (mStatsReport != null)
            mStatsReport.println(tag + " W " + mDf.format(System.currentTimeMillis()) + " " + s);
    }

    private void logi(String tag, String s) {
        Log.i(tag, s);
        if (mStatsReport != null)
            mStatsReport.println(tag + " I " + mDf.format(System.currentTimeMillis()) + " " + s);
    }

    @CallSuper
    protected void onRestart(final Context context) {
        logi(mTag, "Scheduling restart...");
        scheduler.schedule(new Runnable() {
            public void run() {
                logd(mTag, "Restarting...");
                CameraModule.start(context, CAMERA_ID, "restart");
            }
        }, TIME_TO_WAIT_BEFORE_RESTART_MS, MILLISECONDS);

    }

    @CallSuper
    protected void onReboot(Context context) {
        logd(mTag, "onReboot");
        Utils.killCameraDaemon();
        Utils.killMediaServer();
        onRestart(context);
        mCameraErrorList[CAMERA_ID].clear();
    }

    @CallSuper
    protected boolean isRebootNeeded(int err) {
        if (err == ERROR_SYSTEM_CAMERA_SERVICE) {
            return true;
        }

        long t = System.currentTimeMillis();
        while (mCameraErrorList[CAMERA_ID].size() > 0 && ((t - mCameraErrorList[CAMERA_ID].get(0).timestamp) > MAX_TIME_ERROR_LIVES_MS)) {
            logd(mTag, "Removing expired error " + mCameraErrorList[CAMERA_ID].get(0));
            mCameraErrorList[CAMERA_ID].remove(0);
        }

        return mCameraErrorList[CAMERA_ID].size() > MAX_ERRORS;
    }

    static public class CameraError {
        String cameraId;
        int errorCode;
        long timestamp;
        String errorDetail;

        CameraError(String cameraId, int errorCode, String errorDetail) {
            this.cameraId = cameraId;
            this.errorCode = errorCode;
            this.timestamp = System.currentTimeMillis();
            this.errorDetail = errorDetail;
        }

        public String toString() {
            return mDf.format(timestamp) + " camera:" + cameraId + " code:" + errorCode + " details:" + errorDetail;
        }
    }
}


/*
adb shell "echo "userspace" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
adb shell "echo "702000" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed"
adb shell "echo "384000" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"
adb shell "echo "384000" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
adb shell "cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq"
*/
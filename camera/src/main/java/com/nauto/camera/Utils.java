package com.nauto.camera;

import com.nauto.camera.base.CameraModule;
import com.nauto.camera.base.CameraPipelineConfig;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by ab on 11/15/16.
 */

public final class Utils {
    private static final String TAG = "Utils";

    /**
     * Get location of media files
     *
     * @param app
     * @return
     */
    @Nullable
    public static File getMediaRoot(Context app) {
        File[] mediaDirs = app.getExternalMediaDirs();
        File mediaRoot = null;
        loop:
        for (File dir : mediaDirs) {
            if (dir == null) {
                continue;
            }
            if (Environment.isExternalStorageRemovable(dir)) {
                String state = Environment.getExternalStorageState(dir);
                switch (state) {
                    case Environment.MEDIA_MOUNTED:
                    case Environment.MEDIA_SHARED:
                        return dir;
                    default:
                        // ignore: MEDIA_UNKNOWN, MEDIA_REMOVED, MEDIA_UNMOUNTED, MEDIA_CHECKING,
                        // MEDIA_NOFS, MEDIA_MOUNTED_READ_ONLY, MEDIA_BAD_REMOVAL, MEDIA_UNMOUNTABLE
                        Log.d(TAG, "Warning! " + dir + " is removable external storalge but it is not mounted or shared!");
                        break loop;
                }
            }
            // see NAUTO-5941
            // mediaRoot = dir;
        }
        return mediaRoot;
    }

    /**
     * Dump camera service. Needs android.manifest.DUMP permission.
     *
     * @param ctx
     * @param fileName
     */
    public static void dumpCameraService(Context ctx, String fileName) {
        FileOutputStream out = null;
        try {
            File sd = getMediaRoot(ctx);
            File fileDump = new File(sd, fileName);
            if (fileDump.exists()) {
                fileDump.delete();
            }

            out = new FileOutputStream(fileDump);
            boolean ok = Debug.dumpService("media.camera", out.getFD(), null);
            if (!ok) {
                Log.e(CameraModule.class.getName(), "dumpCameraService() failed to dump state to " + fileDump.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @return power consumption in milliwatts
     */
    public static double getPowerNow() {
        try {
            double powerW = getCurrentNow() * getVoltageNow() / 1000.;
            Log.d(TAG, "power = " + powerW + " mW");
            return powerW;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public static double getCurrentNow() {
        try {
            String s = exec("cat /sys/class/power_supply/battery/current_now");
            double currentMa = Double.parseDouble(s) / 1000.;
            Log.d(TAG, "current = " + currentMa + "mA");
            return currentMa;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public static double getVoltageNow() {
        try {
            String s = exec("cat /sys/class/power_supply/battery/voltage_now");
            double voltageMv = Double.parseDouble(s) / 1000.;
            Log.d(TAG, "voltage = " + voltageMv + "mV");
            return voltageMv;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public static double getCpuTemperature() {
        try {
            String s3 = exec("cat /sys/devices/virtual/thermal/thermal_zone3/temp").replace('\n', ' ').trim();
            String s4 = exec("cat /sys/devices/virtual/thermal/thermal_zone4/temp").replace('\n', ' ').trim();
            double t = (Long.decode(s3) + Long.decode(s4)) / 20.;
            Log.d(TAG, "cpuTemperature = " + t + "C");
            return t;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void killMediaServer() {
        exec("pkill /system/bin/mediaserver");
    }

    public static void killCameraDaemon() {
        exec("pkill /system/bin/mm-qcamera-daemon");
    }

    public static String exec(String cmd) {
        StringBuffer output = new StringBuffer();
        BufferedReader reader = null;
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
                p.waitFor();
            }
            reader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            close(reader);
        }

        String response = output.toString();
        return response;
    }

    public static void assertHandlerThread(Handler handler) {
        Looper looper = Looper.myLooper();
        if (handler.getLooper() != looper) {
            throw new RuntimeException("Current looper " + looper + " is not a handler looper " + handler.getLooper());
        }
    }

    /**
     * Generate a string containing a formatted timestamp with the current date and time.
     *
     * @return a {@link String} representing a time.
     */
    public static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * A helper to do safe close
     *
     * @param cls Closeable
     */
    public static void close(Closeable cls) {
        if (null != cls) {
            try {
                cls.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void setAeRect(CameraPipelineConfig cfg, CaptureRequest.Builder requestBuilder) {
        if (cfg.mAeRects != null) {
            int weight = cfg.mAeRects[0] == 0 && cfg.mAeRects[1] == 0 && cfg.mAeRects[2] == 1920 && cfg.mAeRects[2] == 1080 ?
                    0 : MeteringRectangle.METERING_WEIGHT_MAX;
            MeteringRectangle[] aeRegions = {
                    new MeteringRectangle((int) (1920 * cfg.mAeRects[0]), (int) (1080 * cfg.mAeRects[1]),
                            (int) (1920 * cfg.mAeRects[2]), (int) (1080 * cfg.mAeRects[3]), weight)
            };
            requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
        } else {
            MeteringRectangle[] aeRegions = {
                    new MeteringRectangle(0, 0, 1920, 1080, 0)
            };
            requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
        }
    }

    public static byte[] loadRaw(int rawId, Context context) throws IOException {
        InputStream is = context.getResources().openRawResource(rawId);
        byte[] buffer = new byte[is.available()];
        while (is.read(buffer) != -1) ;
        is.close();
        return buffer;
    }

    public static boolean doFpsRecording(CameraModule cameraModule) {
        Cursor cursor = null;
        String propName = cameraModule.isFrontCamera() ? "CameraModule_record_interior_fps" : "CameraModule_record_exterior_fps";
        try {
            cursor = cameraModule.getApplicationContext().getContentResolver()
                    .query(Uri.parse("content://com.nauto.provider.configuration/settings_map"),
                            new String[]{"name", "current_value"},
                            "name=?",
                            new String[]{propName},
                            null);
            Log.d(TAG, ", cursor = " + cursor);
            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, propName + " == " + cursor.getInt(1));
                return cursor.getInt(1) == 1;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, propName + " column is not defined");
        return true;
    }

    public static boolean makeDirectory(String directory) {
        String msg;

        if (directory == null) {
            return false;
        }

        File dir = new File(directory);
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) return false;
        }

        // verify directory exists
        long now = System.currentTimeMillis();
        long timeout = now + 5000;
        boolean directoryExists = false;
        while (!directoryExists && (now < timeout)) {
            File verifyDir = new File(directory);
            String verifyPath = null;
            try {
                verifyPath = verifyDir.getCanonicalPath();
            } catch (IOException ex) {
                verifyPath = "INVALID_PATH";
                msg = String.format(Locale.US, "directory [%s] still does not exist", directory);
                Log.e(TAG, msg, ex);
            }

            if (!verifyDir.isDirectory()) {
                msg = String.format(Locale.US, "directory [%s] does not exist...", verifyPath);
                Log.d(TAG, msg);
            } else {
                directoryExists = true;
                msg = String.format(Locale.US, "directory [%s] verified at time = %d", verifyPath, now);
                Log.d(TAG, msg);
                break;
            }

            // catch our breath after all this heavy lifting
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            now = System.currentTimeMillis();
        }

        if (!directoryExists) {
            msg = String.format(Locale.US, "unable to create directory [%s]", directory);
            Log.d(TAG, msg);
            return false;
        }

        return true;
    }

    public static String getProcessName() {
        try {
            BufferedReader cmdlineReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/proc/" + android.os.Process.myPid() + "/cmdline"), "iso-8859-1"));
            int ch;
            StringBuilder processName = new StringBuilder();
            while ((ch = cmdlineReader.read()) > 0) {
                processName.append((char) ch);
            }
            cmdlineReader.close();
            return processName.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "unknown";
    }

    public static String getDeviceUid() {
        try {
            return (String) Build.class.getField("SERIAL").get(null);
        } catch (Exception ex) {
            // handle exception
        }
        return "unknown";
    }


}

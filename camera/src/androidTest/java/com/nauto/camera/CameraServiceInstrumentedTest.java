package com.nauto.camera;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import com.nauto.camera.base.CameraModule;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Instrumentation test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class CameraServiceInstrumentedTest {
    private static final String TAG = "Test";

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        Log.d(TAG, appContext.getPackageName());
        assertEquals("com.nauto.camera.test", appContext.getPackageName());
    }

    @Test
    public void dumpCameraModule() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        Utils.dumpCameraService(appContext, "dump.out");
        try {
            File sd = Utils.getMediaRoot(appContext);
            File fileDump = new File(sd, "dump.out");
            Scanner scanner = new Scanner(fileDump);
            while (scanner.hasNextLine()) {
                Log.d(TAG, scanner.nextLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals("com.nauto.camera.test", appContext.getPackageName());
    }

    @Test
    public void testDumpsysMediaCamera() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        Process p = Runtime.getRuntime().exec("/system/bin/dumpsys media.camera");
        InputStream fin = p.getInputStream();
        p.waitFor();
        int siz = fin.available();
        byte[] bytes = new byte[siz];
        int len = fin.read(bytes);
        fin.close();
        assertEquals("sizes should be equal ", siz, len);
        FileOutputStream fout = appContext.openFileOutput("dumpsys.media.camera.txt", Context.MODE_PRIVATE);
        fout.write(bytes);
        fout.close();
    }

    @Test
    public void testMediaRootWrite() {
        Context app = InstrumentationRegistry.getTargetContext();
        File root = Utils.getMediaRoot(app);
        Log.d(TAG, "root = " + root);
        File test = new File(root, "test.txt");
        try {
            BufferedWriter bout = new BufferedWriter(new FileWriter(test));
            bout.write("test");
            bout.close();
            BufferedReader bin = new BufferedReader(new FileReader(test));
            String s = bin.readLine();
            assertEquals(s, "test");
            bin.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void printCurrentPowerConsumption() {
        double power = Utils.getPowerNow();
        Log.d(TAG, "power = " + power + " Watts");
    }


    @Test
    public void testStartStopServices() {
        try {
            testStopSevice();
            Thread.sleep(2000);
            testStartSevice();
            Thread.sleep(2000);
            testStopSevice();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStartSevice() {
        Context app = InstrumentationRegistry.getTargetContext();
        CameraModule.startAll(app);
        try {
            Thread.sleep(2000);
//            CameraModule cam0 = CameraModuleTest.sInstances[0];
//            CameraModule cam1 = CameraModule.sInstances[1];
//            assertNotNull(cam0);
//            assertNotNull(cam1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStopSevice() {
        Context app = InstrumentationRegistry.getTargetContext();
        CameraModule.stopAll(app);
        try {
            Thread.sleep(2000);
//            CameraModule cam0 = CameraModule.sInstances[0];
//            CameraModule cam1 = CameraModule.sInstances[1];
//            Assert.assertNull(cam0);
//            Assert.assertNull(cam1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void getVideos() {
        Context app = InstrumentationRegistry.getTargetContext();
        List<String> videos = CameraStore.getAllVideosAsList(app);
        Assert.assertNotNull(videos);
    }

    @Test
    public void getVideosInTimeRange() {
        Context app = InstrumentationRegistry.getTargetContext();
        List<String> list = new ArrayList<>();
        long[] cuts = {0, 0};
        // YOUR NUMBERS HERE:
        long start =  1493350500000L;
        long end =   1493350520000L;
        cuts = CameraStore.getOriginalVideosListInTimeRange(app, "1", list, start, end);
        Assert.assertNotNull(cuts[0] * cuts[1]);
    }

    @Test
    public void extractVideo() {
        Context app = InstrumentationRegistry.getTargetContext();
        // Compute start/stop time in ms

        CameraStore.removeDeadMediaStoreRecord(app);

        long start = 1493872125000L;
        long end = 1493872130000L;
        Log.d(TAG, new Date(start) + " -- " + new Date(end));
        try {
            CameraStore.extractVideoAsFile(app, "0", start, end, "/sdcard/test-10.mp4");
//            CameraStore.extractVideoAsFile(app, "1", start - 3000, end, "/sdcard/test-07.mp4");
//            CameraStore.extractVideoAsFile(app, "1", start + 3000, end, "/sdcard/test-13.mp4");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void trimVideo() {
        Context app = InstrumentationRegistry.getTargetContext();

        long max = (long)600 * CameraStore.GB_TO_BYTE;
        long free = (long)200 * CameraStore.GB_TO_BYTE;
//        CameraStore.trimVideoByPattern(app, CameraStore.ORIGINAL_VIDEO_FILE_PATTERN, max, free);
//        CameraStore.trimImageByPattern(app, CameraStore.SNAPSHOT_FILE_PATTERN, max, free);

        CameraStore.trimOriginalVideoIfFreeSpaceIsShort(app, max, max + free);
    }

    /**
     *
     1_1484923942790.mp4, start = 1484923943416, end = 1484924242000, len = 298584
     1_1484924242490.mp4, start = 1484924242690, end = 1484924542000, len = 299310
     1_1484924542527.mp4, start = 1484924542756, end = 1484924842000, len = 299244
     1_1484924842514.mp4, start = 1484924842756, end = 1484925142000, len = 299244
     1_1484925142493.mp4, start = 1484925142690, end = 1484925442000, len = 299310
     1_1484925442511.mp4, start = 1484925442690, end = 1484925742000, len = 299310
     1_1484925742539.mp4, start = 1484925742756, end = 1484926042000, len = 299244
     1_1484926042543.mp4, start = 1484926043548, end = 1484926342000, len = 298452
     1_1484926342478.mp4, start = 1484926342690, end = 1484926642000, len = 299310
     1_1484926642469.mp4, start = 1484926643350, end = 1484926942000, len = 298650
     1_1484926942498.mp4, start = 1484926942690, end = 1484927242000, len = 299310
     1_1484927242496.mp4, start = 1484927242690, end = 1484927542000, len = 299310
     1_1484927542505.mp4, start = 1484927543086, end = 1484927842000, len = 298914
     */
    @Test
    public void getMediaServerPid() {
        String s = Utils.exec("ps");
        String[] ss = s.split("\n");
        int count = 0;
        for (String line : ss) {
            Log.d(TAG, count++ + ": " + line);
//            if (line.contains("mediaserver")) {
//                Log.d(TAG, line);
//            }
        }
//        Log.d(TAG, s);
    }

    @Test
    public void extractVideoInTimeRange() {
        Context app = InstrumentationRegistry.getTargetContext();
        // Compute start/stop time in ms
        long start  = 1481669101598L - 15 * 1000;
        long end  = 1481669131697L + 5 * 1000;
        List<String> list = new ArrayList<>();
        long[] cuts = {0, 0};
        cuts = CameraStore.getOriginalVideosListInTimeRange(app, null, list, start, end);
        Assert.assertNotNull(cuts[0] * cuts[1]);
        List<String> list0 = new ArrayList<>();
        List<String> list1 = new ArrayList<>();
        for (String s : list) {
            if (s.contains("/0_")) {
                Log.d(TAG, "Camera 0:" + s);
                list0.add(s);
            } else if (s.contains("/1_")) {
                list1.add(s);
                Log.d(TAG, "Camera 1:" + s);
            }
        }

        long t = System.currentTimeMillis();
        try {
            CameraStore.extractVideoAsFile(list0, cuts, "/sdcard/0_out.mp4");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            CameraStore.extractVideoAsFile(list1, cuts, "/sdcard/1_out.mp4");
        } catch (IOException e) {
            e.printStackTrace();
        }
        long dt = System.currentTimeMillis() - t;
        Log.d(TAG, "Extract time = " + dt + "ms");
        }

    // Calendar c_start= Calendar.getInstance();
    // c_start.set(2013,2,4,0,0);

    @Test
    public void extractSnapshotFromVideo() {
        Context app = InstrumentationRegistry.getTargetContext();
        long timestamp = 1495648729066L;
        int quality = 80;
        int width = 640;
        String cameraId;
        String filename;

        while (timestamp <= 1495648739066L) {
            try {
                cameraId = "0";
                filename = Utils.getMediaRoot(app) + File.separator + cameraId + "_" + quality + "_" + width + "_" + timestamp + ".jpg";
                CameraStore.SnapshotMetadata metadata = new CameraStore.SnapshotMetadata(filename, cameraId, timestamp);
                CameraStore.extractSnapshotAsFile(app, metadata);

//                cameraId = "1";
//                filename = Utils.getMediaRoot(app) + File.separator + cameraId + "_" + quality + "_" + width + "_" + timestamp + ".jpg";
//                CameraStore.extractSnapshotAsFile(app, cameraId, timestamp, quality, width, filename);
                timestamp += 500;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    @Test
    public void cloneMediaUsingMuxer() throws IOException {
//        private void cloneMediaUsingMuxer(int srcMedia, String dstMediaPath, int expectedTrackCount, int degrees) throws IOException {
        final int MAX_SAMPLE_SIZE = 256 * 1024;
        final boolean VERBOSE = true;
        final int degrees = 0;
        final int expectedTrackCount = 2;
        Context app = InstrumentationRegistry.getTargetContext();
        File root = Utils.getMediaRoot(app);
        File extRoot = new File(Environment.getExternalStorageDirectory(), "CameraModule");
        FileInputStream fin = new FileInputStream(new File(extRoot, "0_96878.mp4"));
        FileDescriptor fd = fin.getFD();
        File fout = new File(extRoot, "output.mp4");

        // Set up MediaExtractor to read from the source.
//        AssetFileDescriptor srcFd = mResources.openRawResourceFd(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fd, 0, fin.available());
        int trackCount = extractor.getTrackCount();
        assertEquals("wrong number of tracks", expectedTrackCount, trackCount);
        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(fout.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
        }
        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int frameCount = 0;
        int offset = 100;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if (degrees >= 0) {//
            muxer.setOrientationHint(degrees);
        }
        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                int flags = extractor.getSampleFlags();
                bufferInfo.flags = flags;
                int trackIndex = extractor.getSampleTrackIndex();
                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                extractor.advance();
                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }
        muxer.stop();
        muxer.release();
        fin.close();
        return;
    }

    @Test
    public void testExtractVideo() {
        try {
            long t0 = System.currentTimeMillis();
            extractVideo("/sdcard/CameraModule/1_18522.mp4", 8*1000*1000, 13*1000*1000, "/sdcard/CameraModule/1_18522-8-13.mp4");
            long t1 = System.currentTimeMillis();
            Log.d(TAG, "Extraction time = " + (t1 - t0) + " ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//    /storage/CA28-41BC/Android/data/com.example.sensorx/files
    @Test
    public void testExtractVideoTs() {
        try {
            long t0 = System.currentTimeMillis();
            extractVideo("/storage/CA28-41BC/Android/data/com.example.sensorx/files/19700101T070248Z_0.ts", 8 * 1000 * 1000, 13 * 1000 * 1000, "/sdcard/CameraModule/ts-test.mp4");
            long t1 = System.currentTimeMillis();
            Log.d(TAG, "Extraction time = " + (t1 - t0) + " ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void extractVideo(List<String> inVideos, long startUs, long endUs, String outVideo) throws IOException {
        final boolean VERBOSE = true;
        boolean sawEOS = false;
        final int BUFFER_SIZE = 256 * 1024;
        ByteBuffer dstBuf = ByteBuffer.allocate(BUFFER_SIZE);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        final MediaMuxer muxer = new MediaMuxer(outVideo, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>();
        int lastVideoIndex = inVideos.size() - 1;

        long globalTime = 0;
        for (int i = 0; i <= lastVideoIndex; i++) {
            sawEOS = false;
            String inVideo = inVideos.get(i);
            Log.d(TAG, i + " : extracting " + inVideo);
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inVideo);
            if (i == 0) {
                int trackCount = extractor.getTrackCount();
                for (int j = 0; j < trackCount; j++) {
                    extractor.selectTrack(j);
                    MediaFormat format = extractor.getTrackFormat(j);
                    int dstIndex = muxer.addTrack(format);
                    indexMap.put(j, dstIndex);
                }
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                muxer.start();
            } else {
                int trackCount = extractor.getTrackCount();
                for (int j = 0; j < trackCount; j++) {
                    extractor.selectTrack(j);
//                    MediaFormat format = extractor.getTrackFormat(j);
//                    int dstIndex = muxer.addTrack(format);
//                    indexMap.put(j, dstIndex);
                }
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            int frameCount = 0;
            int offset = 100;

            while (!sawEOS) {
                long currentTime = extractor.getSampleTime();
                if (i == lastVideoIndex && currentTime >= endUs) {
                    break;
                }
                bufferInfo.offset = offset;
                bufferInfo.size = extractor.readSampleData(dstBuf, offset);
                if (bufferInfo.size < 0) {
                    if (VERBOSE) {
                        Log.d(TAG, "saw input EOS.");
                    }
                    sawEOS = true;
                    bufferInfo.size = 0;
                    globalTime = bufferInfo.presentationTimeUs;
                } else {
                    bufferInfo.presentationTimeUs = currentTime + globalTime;
                    int flags = extractor.getSampleFlags();
                    bufferInfo.flags = flags;
                    int trackIndex = extractor.getSampleTrackIndex();
                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    extractor.advance();
                    frameCount++;
                    if (VERBOSE) {
                        Log.d(TAG, "Frame (" + frameCount + ") " + "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                                " Flags:" + bufferInfo.flags + " TrackIndex:" + trackIndex + " Size(KB) " + bufferInfo.size / 1024);
                    }
                }
            }
            extractor.release();
        }

        muxer.stop();
        muxer.release();
    }

    public static void extractVideo(String inVideo, long startUs, long endUs, String outVideo) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(inVideo);
        MediaMuxer muxer = new MediaMuxer(outVideo, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Set up the tracks.
        int trackCount = extractor.getTrackCount();
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
        }

        final boolean VERBOSE = true;
        boolean sawEOS = false;
        int bufferSize = 256 * 1024;
        int frameCount = 0;
        int offset = 100;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        muxer.start();
        long currentTime;
        while (!sawEOS) {
            currentTime = extractor.getSampleTime();
            if (currentTime >= endUs) {
                break;
            }
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                int flags = extractor.getSampleFlags();
                bufferInfo.flags = flags;
                int trackIndex = extractor.getSampleTrackIndex();
                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                extractor.advance();
                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }
        muxer.stop();
        muxer.release();
        extractor.release();
    }

    @Test
    public void testFrameRetrieval() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource("/sdcard/CameraModule/1_18522-8-13.mp4");
        Bitmap thumbnail = retriever.getFrameAtTime();
        Bitmap frame2 = retriever.getFrameAtTime(2 * 1000 * 1000);
        saveBitmap(thumbnail, "/sdcard/CameraModule/1_18522-8-13.jpeg");
        saveBitmap(frame2, "/sdcard/CameraModule/1_18522-8-13(2).jpeg");
    }

    private void saveBitmap(Bitmap img, String path) {
        try {
            FileOutputStream fout = new FileOutputStream(path);
            img.compress(Bitmap.CompressFormat.JPEG, 50, fout);
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Test
//    /**
//     * Uses 2 MediaExtractor, seeking to the same position, reads the sample and
//     * makes sure the samples match.
//     */
//    public  void verifySamplesMatch() throws IOException {
//        File extRoot = new File(Environment.getExternalStorageDirectory(), "CameraModule");
//        FileInputStream fin1 = new FileInputStream(new File(extRoot, "0_96878.mp4"));
//        FileDescriptor fd1 = fin1.getFD();
//
//        MediaExtractor extractorSrc = new MediaExtractor();
//        extractorSrc.setDataSource(fd1, 0, fin1.available());
//        int trackCount = extractorSrc.getTrackCount();
//
//        MediaExtractor extractorTest = new MediaExtractor();
//        extractorTest.setDataSource(testMediaPath);
//
//        assertEquals("wrong number of tracks", trackCount, extractorTest.getTrackCount());
//
//        // Make sure the format is the same and select them
//        for (int i = 0; i < trackCount; i++) {
//            MediaFormat formatSrc = extractorSrc.getTrackFormat(i);
//            MediaFormat formatTest = extractorTest.getTrackFormat(i);
//            String mimeIn = formatSrc.getString(MediaFormat.KEY_MIME);
//            String mimeOut = formatTest.getString(MediaFormat.KEY_MIME);
//            if (!(mimeIn.equals(mimeOut))) {
//                fail("format didn't match on track No." + i +
//                        formatSrc.toString() + "\n" + formatTest.toString());
//            }
//            extractorSrc.selectTrack(i);
//            extractorTest.selectTrack(i);
//        }
//        // Pick a time and try to compare the frame.
//        extractorSrc.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//        extractorTest.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
//
//        int bufferSize = MAX_SAMPLE_SIZE;
//        ByteBuffer byteBufSrc = ByteBuffer.allocate(bufferSize);
//        ByteBuffer byteBufTest = ByteBuffer.allocate(bufferSize);
//
//        extractorSrc.readSampleData(byteBufSrc, 0);
//        extractorTest.readSampleData(byteBufTest, 0);
//
//        if (!(byteBufSrc.equals(byteBufTest))) {
//            fail("byteBuffer didn't match");
//        }
//    }
//

    public void verifyLocationInFile(String fileName) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fileName);
        String location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        assertNotNull("No location information found in file " + fileName, location);

        // parsing String location and recover the location information in floats
        // Make sure the tolerance is very small - due to rounding errors.

        // Get the position of the -/+ sign in location String, which indicates
        // the beginning of the longitude.
        int minusIndex = location.lastIndexOf('-');
        int plusIndex = location.lastIndexOf('+');

        assertTrue("+ or - is not found or found only at the beginning [" + location + "]",
                (minusIndex > 0 || plusIndex > 0));
        int index = Math.max(minusIndex, plusIndex);

        float latitude = Float.parseFloat(location.substring(0, index - 1));
        float longitude = Float.parseFloat(location.substring(index));
//        assertTrue("Incorrect latitude: " + latitude + " [" + location + "]",
//                Math.abs(latitude - LATITUDE) <= TOLERANCE);
//        assertTrue("Incorrect longitude: " + longitude + " [" + location + "]",
//                Math.abs(longitude - LONGITUDE) <= TOLERANCE);
        retriever.release();
    }

    @Test
    public void killMediaServer() {
        Utils.killMediaServer();
    }

    @Test
    public void killCameraDaemon() {
        Utils.killCameraDaemon();
    }

    @Test
    public void listCodecs() {
        Log.d(TAG, "ListCodecs...");
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                String name = codecInfo.getName();
                Log.d(TAG, "Codec: " + name);
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    String type = types[j];
                    Log.d(TAG, "\ttype: " + type);
                    MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        int nInstances = caps.getMaxSupportedInstances();
                    }
                }
            }
        }
    }

    private final static String[] mCameraIds = {"0", "1"};
    private MediaRecorder mMediaRecorder;

    private static final int[] mCamcorderProfileList = {
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_QVGA,
            CamcorderProfile.QUALITY_LOW,
    };

}

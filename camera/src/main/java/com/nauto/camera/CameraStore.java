package com.nauto.camera;

import com.nauto.camera.base.CameraModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Camera module store.
 */
public class CameraStore {
    private static final String TAG = CameraStore.class.getName();

    public static final long GB_TO_BYTE = 1024L * 1024 * 1024;
    public static final long MB_TO_BYTE = 1024L * 1024;

    public static final String ORIGINAL_VIDEO_FILE_PATTERN = "*/[01]_*.mp4";
    public static final String INTERNAL_ORIGINAL_VIDEO_FILE_PATTERN = "*/1_*.mp4";
    public static final String EXTERNAL_ORIGINAL_VIDEO_FILE_PATTERN = "*/0_*.mp4";
    public static final String EXTRACTED_VIDEO_FILE_PATTERN = "*/ex_[01]_*.mp4";
    public static final String SNAPSHOT_FILE_PATTERN = "*/[01]_*.jpg";

    private static AtomicBoolean isTrimmingOriginal = new AtomicBoolean(false);

    final static String[] VIDEO_FIELDS = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.MIME_TYPE,
            // Path to the file on disk.
            MediaStore.Video.Media.DATA,
            // The size of the file in bytes
            MediaStore.Video.Media.SIZE,
            // The date & time that the video was taken in units of milliseconds since jan 1, 1970.
            MediaStore.Video.Media.DATE_TAKEN,
            // The duration of the video file, in ms
            MediaStore.Video.Media.DURATION,
            // The resolution of the video file, formatted as "XxY"
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.LATITUDE,
            MediaStore.Video.Media.LONGITUDE,
            // The time the file was added to the store. Units are seconds since 1970.
            MediaStore.Video.Media.DATE_ADDED,
            // The bookmark for the video. Time in ms. Represents the location in the video that the
            // video should start playing at the time it is opened.
            MediaStore.Video.Media.BOOKMARK,
            // The description of the video recording
            MediaStore.Video.Media.DESCRIPTION,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.CATEGORY,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MINI_THUMB_MAGIC
    };

    static class FileWithSize {
        String filepath;
        long fileSize;

        public FileWithSize(String filepath, long fileSize) {
            this.filepath = filepath;
            this.fileSize = fileSize;
        }
    }

    /**
     * Compose a video from available image chunks in the given time range.
     * Warning: it is a blocking call.
     *
     * @param ctx      app context
     * @param cameraId camera id
     * @param start    start time in milliseconds
     * @param end      end time in milliseconds
     * @param outVideo output file name
     * @throws IOException
     */
    public static boolean extractVideoAsFile(Context ctx, String cameraId, long start, long end, String outVideo) throws IOException {
        List<String> items = new LinkedList<>();
        long[] cuts = getOriginalVideosListInTimeRange(ctx, cameraId, items, start, end);
        if (items.size() > 0) {
            boolean result = extractVideoAsFile(items, cuts, outVideo);
            VideoMetadata metadata = new VideoMetadata(start, end, outVideo, cameraId);
            registerExtractedMedia(ctx, new File(outVideo), metadata, null);
            return result;
        } else {
            Log.e(TAG, "no videos found for time interval " + start + "-" + end);
        }
        return false;
    }

    /**
     * Compose a video from available image chunks in the given time range.
     * Warning: it is a blocking call.
     *
     * @param ctx      app context
     * @param cameraId camera id
     * @param start    start time in milliseconds
     * @param end      end time in milliseconds
     * @param outVideo output file name
     * @param listener media store scan completed listener
     * @throws IOException
     */
    public static boolean extractVideoAsFileAndNotify(Context ctx, String cameraId, long start, long end, String outVideo,
                                                      MediaScannerConnection.OnScanCompletedListener listener) throws IOException {
        List<String> items = new LinkedList<>();
        long[] cuts = getOriginalVideosListInTimeRange(ctx, cameraId, items, start, end);
        if (items.size() > 0) {
            boolean result = extractVideoAsFile(items, cuts, outVideo);
            VideoMetadata metadata = new VideoMetadata(start, end, outVideo, cameraId);
            registerExtractedMedia(ctx, new File(outVideo), metadata, listener);
            return result;
        } else {
            Log.e(TAG, "no videos found for time interval " + start + "-" + end);
        }
        return false;
    }

    // TODO take snapshotMetadata as param instead of listing them one by one.
    public static boolean extractSnapshotAsFile(Context ctx, SnapshotMetadata metadata) throws IOException {
        List<String> items = new LinkedList<>();
        String cameraId = metadata.getCameraId();
        long time = metadata.getTakenTime();
        String outVideo = metadata.getFilename();
        int width = metadata.getWidth();
        int quality = metadata.getQuality();
        long[] cuts = getOriginalVideosListInTimeRange(ctx, cameraId, items, time, time);
        if (items.size() == 0) {
            Log.i(TAG, "No file found for extracting snapshot: " + outVideo);
            return false;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        // getFrameAtTime() takes microSecond, so need to convert.
        long timestampInUs = cuts[0] * 1000;
        Log.v(TAG, "Extract frame at " + timestampInUs + " us");
        try {
            retriever.setDataSource(items.get(0));
            Bitmap bitmap = retriever.getFrameAtTime(timestampInUs);
            // getFrameAtTime could return null. If bitmap is null, it means extraction snapshot failed.
            if (bitmap == null) {
                return false;
            }
            if (width > 0) {
                bitmap = BitmapScaler.scaleToFitWidth(bitmap, width);
            }
            boolean result = saveBitmap(bitmap, quality, outVideo);
            if (result) {
                registerExtractedMedia(ctx, new File(outVideo), metadata, null);
            } else {
                Log.e(TAG, "Failed to save bitmap into file: " + outVideo);
            }
            return result;
        } finally {
            retriever.release();
        }

    }

    public static Bitmap[] extractSnapshotAsBitmap(Context ctx, String cameraId, long time) {
        List<String> items = new LinkedList();
        long[] cuts = getOriginalVideosListInTimeRange(ctx, cameraId, items, time, time);
        if (items.size() == 0) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(items.get(0));
            Bitmap thumbnail = retriever.getFrameAtTime();
            Bitmap snapshot = retriever.getFrameAtTime(cuts[0]);
            return new Bitmap[]{thumbnail, snapshot};
        } finally {
            retriever.release();
        }
    }

    public static void getFileListByPattern(Uri uri, Context ctx, String match, List<FileWithSize> items) {

        final boolean VERBOSE = false;
        final String DESCRIPTION = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.equals(uri)
                ? MediaStore.Images.Media.DESCRIPTION
                : MediaStore.Video.Media.DESCRIPTION;
        String[] proj = {MediaStore.MediaColumns.DATA, DESCRIPTION, MediaStore.MediaColumns.SIZE};
        String sel = MediaStore.MediaColumns.DATA + " GLOB ?";
        String[] selArgs = new String[]{match};
        String sort = DESCRIPTION + " DESC";
        Cursor cursor = ctx.getContentResolver().query(uri, proj, sel, selArgs, sort);

        Log.d(TAG, "Number of files found (" + match + "): " + (cursor == null ? 0 : cursor.getCount()));
        int count = 0;
        while (cursor != null && cursor.moveToNext()) {
            String filePath = cursor.getString(0);
            if ((new File(filePath)).exists()) {
                items.add(new FileWithSize(filePath, cursor.getLong(2)));
                if (VERBOSE) {
                    Log.d(TAG, "cursor[" + count + "]: " + cursor.getString(0) + ";\t" + new Date(cursor.getLong(1)) + ";\t" + cursor.getLong(2));
                }
                count++;
            } else {
                ContentResolver resolver = ctx.getContentResolver();
                resolver.delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{filePath});
            }
        }
    }

    public static long[] getOriginalVideosListInTimeRange(Context ctx, String cameraId, List<String> items, long start, long end) {

        final boolean VERBOSE = false;
        long[] cuts = {0, 0, 0};
        String[] proj = {MediaStore.Video.Media.DATA, MediaStore.Video.Media.DESCRIPTION, "duration"};
        String match;
        switch (cameraId) {
            case "0":
                match = EXTERNAL_ORIGINAL_VIDEO_FILE_PATTERN;
                break;
            case "1":
                match = INTERNAL_ORIGINAL_VIDEO_FILE_PATTERN;
                break;
            default:
                match = ORIGINAL_VIDEO_FILE_PATTERN;
        }
        String sel = "((CAST(" + MediaStore.Video.Media.DESCRIPTION + " AS INTEGER) + duration) > " + String.valueOf(start)
                + ") AND (CAST(" + MediaStore.Video.Media.DESCRIPTION + " AS INTEGER) < " + end
                + ") AND " + MediaStore.Video.Media.DATA + " GLOB ?";
        String[] selArgs = new String[]{match};
        String sort = MediaStore.Video.Media.DESCRIPTION + " ASC";
        Cursor cursor;
        if (VERBOSE) Log.d(TAG, "run query: " + sel);
        cursor = ctx.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, selArgs, sort);

        Log.d(TAG, (cursor == null ? 0 : cursor.getCount()) + " items found");
        int count = 0;
        long endTime = 0;
        long len = 0;
        long startTime = 0;
        long totalLength = 0;

        try {
            while (cursor != null && cursor.moveToNext()) {
                String filePath = cursor.getString(0);
                if ((new File(filePath)).exists()) {
                    String fname = filePath.substring(filePath.lastIndexOf("/") + 1);
                    // ignore short videos
                    if (cursor.getLong(2) < 1000) continue;
                    // skip if cut is close to the end
                    //if (cursor.getLong(1) - start < 1000) continue;

                    len = cursor.getLong(2);
                    startTime = cursor.getLong(1);
                    endTime = startTime + len;
                    //if (VERBOSE)
                    Log.d(TAG, fname + ", start = " + startTime + ", end = " + endTime + ", len = " + len);
                    if (count == 0) {
                        cuts[0] = start - startTime;
                    }

                    cuts[1] = end - startTime;

                    items.add(filePath);
                    totalLength += len;
                    count++;
                } else {
                    ContentResolver resolver = ctx.getContentResolver();
                    resolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.MediaColumns.DATA + "=?", new String[]{filePath});
                }

            }

            if (cuts[0] < 0) {
                // cut should never be negative
                cuts[0] = 0;
            }
            if (cuts[1] > len) {
                // cut should never be greater then video length
                cuts[1] = len;
            }
            if (count == 1 && (cuts[1] < cuts[0])) {
                throw new RuntimeException();
            }
            totalLength -= cuts[0];
            totalLength -= (len - cuts[1]);
            long missedTime = end - start - totalLength;
            cuts[2] = missedTime;
            if (VERBOSE) {
                Log.d(TAG, count + " videos, content length: " + totalLength + ", missing: " + missedTime + ", 0: " + cuts[0] + ",  1:" + cuts[1] + ", len = " + len);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            cursor.close();
        }
        return cuts;
    }

    public static boolean extractVideoAsFile(List<String> inVideos, long[] cuts, String outVideo) throws IOException {
        Log.d(TAG, "inVideos size: " + inVideos.size());
        if (inVideos.size() == 0) {
            return false;
        }

        long gapMs = cuts[2] < 3000 ? cuts[2] : 0;
        long startMs = cuts[0];
        long endMs = cuts[1] + gapMs;

        // for a single video start time should be positive and smaller then the end time
        if (inVideos.size() == 1 && (startMs < 0 || startMs > endMs)) {
            throw new IllegalArgumentException("invalid time range:[" + startMs + "," + endMs + "]");
        }

        final boolean VERBOSE = false;
        boolean sawEOS = false;
        // Make it 512kb for now. If we need to extract from higher resolution/bitrate videos, we may need larger buffer.
        final int BUFFER_SIZE = 512 * 1024;
        ByteBuffer dstBuf = ByteBuffer.allocate(BUFFER_SIZE);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        final MediaMuxer muxer = new MediaMuxer(outVideo, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>();
        int lastVideoIndex = inVideos.size() - 1;
        Log.d(TAG, "start = " + startMs + "ms, end = " + endMs + "ms");

        // global time accumulated over multiple video chunks
        long globalTime = 0;

        for (int i = 0; i <= lastVideoIndex; i++) {
            sawEOS = false;
            String inVideo = inVideos.get(i);
            Log.d(TAG, i + " : extracting " + inVideo);
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inVideo);

            // first video, starting extracting from startMs
            if (i == 0) {
                int trackCount = extractor.getTrackCount();
                for (int j = 0; j < trackCount; j++) {
                    extractor.selectTrack(j);
                    MediaFormat format = extractor.getTrackFormat(j);
                    int dstIndex = muxer.addTrack(format);
                    indexMap.put(j, dstIndex);
                }
                extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_NEXT_SYNC);
                // adjust global time to be negative of video starting time
                // so video current time should start with 0
                globalTime = -extractor.getSampleTime();
                long dt = globalTime / 1000 + startMs;
                endMs -= dt;
                if (VERBOSE) {
                    Log.d(TAG, "seek to " + startMs + "ms, global time = " + globalTime / 1000 + "ms, delta = " + dt + "ms");
                }
                muxer.start();
            } else {
                int trackCount = extractor.getTrackCount();
                for (int j = 0; j < trackCount; j++) {
                    extractor.selectTrack(j);
                }
                //extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            int frameCount = 0;
            int offset = 100;
            long frameDuration = 0;
            while (!sawEOS) {
                long currentTime = extractor.getSampleTime();
                int trackIndex = 0;
                if (VERBOSE) Log.d(TAG, "current time = " + currentTime);

                // reach the endMs of last video
                if (i == lastVideoIndex && currentTime > endMs * 1000) {
                    globalTime = bufferInfo.presentationTimeUs;
                    long totalTimeMs = globalTime / 1000;
                    Log.d(TAG, "total time = " + totalTimeMs);
                    break;
                }
                bufferInfo.offset = offset;
                try {
                    // MediaExtractor.readSampleData() can throw IllegalArgumentException if ByteBuffer isn't adequate.
                    bufferInfo.size = extractor.readSampleData(dstBuf, offset);
                } catch (IllegalArgumentException ex) {
                    Log.e(TAG, "Failed readSampleData (buffer size not enough)", ex);
                    break;
                }
                if (bufferInfo.size < 0) {
                    if (VERBOSE) Log.d(TAG, "saw input EOS.");
                    sawEOS = true;
                    bufferInfo.size = 0;
                    globalTime = bufferInfo.presentationTimeUs;
                    if (i == lastVideoIndex) {
                        long totalTimeMs = globalTime / 1000;
                        Log.d(TAG, "saw EOS: total time = " + totalTimeMs);
                    }
                } else {
                    frameDuration = (currentTime + globalTime - bufferInfo.presentationTimeUs);
                    if (VERBOSE) Log.d(TAG, (currentTime + globalTime) / 1000 + ", " + frameDuration / 1000);
                    bufferInfo.presentationTimeUs = currentTime + globalTime;
                    bufferInfo.flags = extractor.getSampleFlags();
                    trackIndex = extractor.getSampleTrackIndex();
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
        return true;
    }

    final static private long BLACK = 10;

    public static boolean isSnapshotBlack(Context ctx, String cameraId, long time) {
        Bitmap[] bitmaps = extractSnapshotAsBitmap(ctx, cameraId, time);
        if (bitmaps == null) {
            return false;
        }
        Bitmap bmp = bitmaps[1];
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int[] pixels = new int[width * height];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        long sum = 0;
        for (int pix : pixels) {
            sum += (pix         & 0x000000FF); // B
            sum += ((pix >> 8)  & 0x000000FF); // G
            sum += ((pix >> 16) & 0x000000FF); // R
        }
        long avg = sum / pixels.length;
        boolean isBlack = avg < BLACK;
        if (isBlack) {
            Log.d(TAG, "Average pixel value: BLACK : " + avg);
        } else {
            Log.d(TAG, "Average pixel value:" + avg);
        }
        return isBlack;
    }

    private static void loadLocalUrls(List<String> items, Context ctx, Uri contentUri, String[] projection) {
        Cursor cursor = null;
        final int nFields = projection.length;
        try {
            cursor = ctx.getContentResolver().query(contentUri, projection, null, null, null);
            int[] idx = new int[nFields];
            for (int i = 0; i < nFields; i++) {
                idx[i] = cursor.getColumnIndexOrThrow(projection[i]);
                assert (i != idx[i]);
            }
            int counter = 0;
            while (cursor != null && cursor.moveToNext()) {
                Object[] item = new Object[nFields];
                StringBuffer sb = new StringBuffer();
                for (int i : idx) {
                    int type = cursor.getType(i);
                    switch (type) {
                        case Cursor.FIELD_TYPE_STRING:
                            item[i] = cursor.getString(i);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            item[i] = cursor.getLong(i);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            item[i] = cursor.getDouble(i);
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            item[i] = null;
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            item[i] = "blob";
                            break;
                        default:
                            item[i] = "Error: default";
                            break;
                    }
                    sb.append(item[i]).append(",");
//                    Log.d(TAG, "\t" + i + ":" + item[i]);
                }
                Log.d(TAG, counter + ":" + sb);
                items.add(sb.toString());
                counter++;
            }
        } catch (Throwable t) {
            Log.e(TAG, "" + t.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static boolean deleteMedia(Context ctx, Uri uri, String path) {
        File file = new File(path);
        if (!file.exists() || file.delete()) {
            if (path.endsWith(".mp4")) {
                // delete face stats
                File fVtt = new File(path + ".vtt");
                File fStatsVtt = new File(path + ".stats.vtt");
                if (fVtt.exists() && ! fVtt.delete()) {
                    Log.e(TAG, "IO error deleting vtt file " + fVtt);
                }
                if (fStatsVtt.exists() && ! fStatsVtt.delete()) {
                    Log.e(TAG, "IO error deleting stats.vtt file " + fStatsVtt);
                }
            }
            // remove from MediaStore if file not exists or file deleted successfully
            ContentResolver resolver = ctx.getContentResolver();
            resolver.delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{path});
            return true;
        } else {
            Log.e(TAG, SubTag.TRIMMING + "Unable to delete file " + path);
            return false;
        }
    }

    /**
     * Trimming from app (not from camera service itself)
     * @param ctx context
     * @param minFreeSpace min free space
     * @param freeSpaceAfterTrimming target free space after trimming
     */
    public static void trimOriginalVideoIfFreeSpaceIsShort(Context ctx, long minFreeSpace, long freeSpaceAfterTrimming) {
        File root = Utils.getMediaRoot(ctx);
        if (root != null) {
            String path = root.getAbsolutePath();
            StatFs statFs = new StatFs(path);
            long availableSize = statFs.getAvailableBytes();
            Log.d(TAG, SubTag.TRIMMING + "From app: availableSize/minFreeSpace: " + (availableSize / MB_TO_BYTE) + "MB / " + (minFreeSpace / MB_TO_BYTE) + "MB");
            if (availableSize < minFreeSpace) {
                trimOriginalVideo(ctx, freeSpaceAfterTrimming, availableSize);
            }
        }
    }

    /**
     * Camera service self trimming
     * @param cameraModule camera service
     * @param minFreeSpace min free space
     * @param freeSpaceAfterTrimming target free space after trimming
     */
    public static void trimOriginalVideoIfFreeSpaceIsShort(CameraModule cameraModule, long minFreeSpace, long freeSpaceAfterTrimming) {
        Context ctx = cameraModule.getApplicationContext();
        File root = Utils.getMediaRoot(ctx);
        if (root != null) {
            String path = root.getAbsolutePath();
            StatFs statFs = new StatFs(path);
            long availableSize = statFs.getAvailableBytes();
            Log.d(TAG, SubTag.TRIMMING + "From camera service: availableSize/minFreeSpace: " + (availableSize / MB_TO_BYTE) + "MB / " + (minFreeSpace / MB_TO_BYTE) + "MB");
            if (availableSize < minFreeSpace) {
                // notify that camera service is doing trimming work.
                cameraModule.broadcastNotice(null, CameraModule.CAMERA_SERVICE_IS_TRIMMING);

                trimOriginalVideo(ctx, freeSpaceAfterTrimming, availableSize);
            }
        }
    }

    private static void trimOriginalVideo(Context ctx, long freeSpaceAfterTrimming, long availableSize) {
        Log.d(TAG, SubTag.TRIMMING + "short in space, begin trimming.");
        if (isTrimmingOriginal.compareAndSet(false, true)) {
            try {
                long spaceNeedToFree = freeSpaceAfterTrimming - availableSize;
                long spaceTrimmed = 0;
                // trim
                List<FileWithSize> list = new LinkedList();
                getFileListByPattern(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ctx, ORIGINAL_VIDEO_FILE_PATTERN, list);

                for (int i = list.size() - 1; i >= 0; i--) {
                    FileWithSize file = list.get(i);
                    Log.d(TAG, SubTag.TRIMMING + "Trimming file: " + file.filepath + "; size: " + (file.fileSize / MB_TO_BYTE) + "MB");
                    if (deleteMedia(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, file.filepath)) {
                        spaceTrimmed += file.fileSize;
                        if (spaceTrimmed >= spaceNeedToFree) {
                            break;
                        }
                    }
                }
            } finally {
                isTrimmingOriginal.set(false);
            }
        } else {
            Log.i(TAG, "Trimming original video is on-going, skip.");
        }
    }

    public static void trimVideoByPattern(Context ctx, String pattern, long maxSizeBeforeTrimming, long freeSpaceAfterTrimming) {
        trimFileByPattern(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pattern, maxSizeBeforeTrimming, freeSpaceAfterTrimming);
    }

    public static void trimImageByPattern(Context ctx, String pattern, long maxSizeBeforeTrimming, long freeSpaceAfterTrimming) {
        trimFileByPattern(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pattern, maxSizeBeforeTrimming, freeSpaceAfterTrimming);
    }

    private static void trimFileByPattern(Context ctx, Uri uri, String pattern, long maxSizeBeforeTrimming, long freeSpaceAfterTrimming) {
        List<FileWithSize> fileList = new LinkedList<>();
        getFileListByPattern(uri, ctx, pattern, fileList);

        long targetSizeAfterTrimming = maxSizeBeforeTrimming - freeSpaceAfterTrimming;
        if (targetSizeAfterTrimming < 0) {
            Log.e(TAG, SubTag.TRIMMING + "Free space after trimming should not larger than max size before trimming.");
            return;
        }
        long totalSize = 0;
        int trimFromIndex = -1;
        boolean needTrimming = false;
        for (int i = 0; i < fileList.size(); i++) {
            totalSize += fileList.get(i).fileSize;
            if (trimFromIndex == -1) {
                if (totalSize > targetSizeAfterTrimming) {
                    trimFromIndex = i;
                } else {        // totalSize < targetSizeAfterTrimming, so it must be < maxSizeBeforeTrimming, continue
                    continue;
                }
            }
            if (totalSize >= maxSizeBeforeTrimming) {
                needTrimming = true;
                break;
            }
        }
        if (needTrimming) {
            Log.d(TAG, SubTag.TRIMMING + "Trimming pattern [" + pattern + "] from index " + trimFromIndex + " to " + (fileList.size() - 1));
            for (int i = trimFromIndex; i < fileList.size(); i++) {
                deleteMedia(ctx, uri, fileList.get(i).filepath);
            }
        } else {
            Log.d(TAG, SubTag.TRIMMING + "Pattern[" + pattern + "], no need to trim -- total size: " + ((double) totalSize / MB_TO_BYTE) + "MB");
        }
    }

    /**
     * Remove media store record for those media that has been deleted, but its record in media store is not been removed yet.
     */
    public static void removeDeadMediaStoreRecord(Context context) {
        // remove video records
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.MediaColumns.DATA};

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(uri, proj, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
            String filePath = cursor.getString(0);
            if (!(new File(filePath)).exists()) {
                Log.d(TAG, SubTag.TRIMMING + "Removing MediaStore record of: " + filePath);
                resolver.delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{filePath});
            }
        }

        // remove image records
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        cursor = resolver.query(uri, proj, null, null, null);
        while (cursor != null && cursor.moveToNext()) {
            String filePath = cursor.getString(0);
            if (!(new File(filePath)).exists()) {
                Log.d(TAG, SubTag.TRIMMING + "Removing MediaStore record of: " + filePath);
                resolver.delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{filePath});
            }
        }
    }

    private static boolean saveBitmap(Bitmap img, int quality, String path) {
        FileOutputStream fout = null;
        boolean result = false;
        try {
            fout = new FileOutputStream(path);
            result = img.compress(Bitmap.CompressFormat.JPEG, quality, fout);
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
    }

    private CameraStore() {
        // it's a library
    }

    @Deprecated
    public static List<String> getAllVideosAsList(Context ctx) {
        List<String> items = new LinkedList<>();
        loadLocalUrls(items, ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_FIELDS);
        loadLocalUrls(items, ctx, MediaStore.Video.Media.INTERNAL_CONTENT_URI, VIDEO_FIELDS);
        return items;
    }

    // TODO file should be part of metadata.
    public static void registerExtractedMedia(final Context context, File file, final Parcelable metadata,
                                              MediaScannerConnection.OnScanCompletedListener listener) {
        final String filePath = file.getAbsolutePath();
        if (listener == null) {
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {

                            ContentValues values = new ContentValues();
                            String desc = null;
                            if (metadata instanceof VideoMetadata) {
                                VideoMetadata videoMetadata = (VideoMetadata) metadata;
                                desc = Long.toString(videoMetadata.getStartTime());
                            } else if (metadata instanceof SnapshotMetadata) {
                                SnapshotMetadata snapshotMetadata = (SnapshotMetadata) metadata;
                                Log.d(TAG, "Extracted snapshot completed: " + snapshotMetadata);
                                desc = Long.toString(snapshotMetadata.getTakenTime());
                                Log.d(TAG, "Extracted snapshot taken time " + desc);
                            } else {
                                Log.i(TAG, "Unknown type of file.");
                            }
                            if (desc != null) {
                                values.put(MediaStore.Video.Media.DESCRIPTION, desc);
                                int n = context.getContentResolver().update(uri, values, null, null);
                                if (n != 1) {
                                    Log.e(TAG, "Failed to save description " + desc);
                                }
                            }
                            Log.d(TAG, "File has been registered to MediaStore:" + filePath);
                        }
                    });
        } else {
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, listener);
        }
    }

    public static boolean saveYuvAsFile(ByteBuffer y, ByteBuffer u, ByteBuffer v, int width, int height,
                                        String outFile, int quality, int targetWidth) {
        Log.d(TAG, "saveYuvAsFile(): " + outFile);

        int yRemain = y.remaining();
        int uRemain = u.remaining();
        int vRemain = v.remaining();

        byte[] data = new byte[yRemain + uRemain + vRemain];

        y.get(data, 0, yRemain);
        v.get(data, yRemain, vRemain);
        u.get(data, yRemain + vRemain, uRemain);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        try {
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            Log.e(TAG, "compressToJpeg() Exception" + ex);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        if (targetWidth > 0) {
            bitmap = BitmapScaler.scaleToFitWidth(bitmap, targetWidth);
        }
        boolean isSuccess = saveBitmap(bitmap, quality, outFile);
        // Log.d(TAG, "isSuccess: " + isSuccess);
        return isSuccess;
    }

    /**
     * Created by sduan on 5/2/17.
     */

    public static class BitmapScaler
    {
        // scale and keep aspect ratio
        public static Bitmap scaleToFitWidth(Bitmap b, int width)
        {
            float factor = width / (float) b.getWidth();
            return Bitmap.createScaledBitmap(b, width, (int) (b.getHeight() * factor), true);
        }


        // scale and keep aspect ratio
        public static Bitmap scaleToFitHeight(Bitmap b, int height)
        {
            float factor = height / (float) b.getHeight();
            return Bitmap.createScaledBitmap(b, (int) (b.getWidth() * factor), height, true);
        }


        // scale and keep aspect ratio
        public static Bitmap scaleToFill(Bitmap b, int width, int height)
        {
            float factorH = height / (float) b.getWidth();
            float factorW = width / (float) b.getWidth();
            float factorToUse = (factorH > factorW) ? factorW : factorH;
            return Bitmap.createScaledBitmap(b, (int) (b.getWidth() * factorToUse),
                    (int) (b.getHeight() * factorToUse), true);
        }


        // scale and don't keep aspect ratio
        public static Bitmap strechToFill(Bitmap b, int width, int height)
        {
            float factorH = height / (float) b.getHeight();
            float factorW = width / (float) b.getWidth();
            return Bitmap.createScaledBitmap(b, (int) (b.getWidth() * factorW),
                    (int) (b.getHeight() * factorH), true);
        }
    }

    public static class SubTag {

        public static final String TRIMMING = "{TRIMMING}";

    }

    public static class VideoMetadata implements Parcelable {
        private long startTime;
        private long endTime;
        private String fileName;
        private String cameraId;
        private long gapLength;


        public VideoMetadata(long startTime, long endTime, String fileName, String cameraId) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.fileName = fileName;
            this.cameraId = cameraId;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getCameraId() {
            return cameraId;
        }

        public long getGapLength() {
            return gapLength;
        }

        public void setGapLength(long gapLength) {
            this.gapLength = gapLength;
        }


        protected VideoMetadata(Parcel in) {
            startTime = in.readLong();
            endTime = in.readLong();
            fileName = in.readString();
            cameraId = in.readString();
            gapLength = in.readLong();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(startTime);
            dest.writeLong(endTime);
            dest.writeString(fileName);
            dest.writeString(cameraId);
            dest.writeLong(gapLength);
        }

        @SuppressWarnings("unused")
        public static final Creator<VideoMetadata> CREATOR = new Creator<VideoMetadata>() {
            @Override
            public VideoMetadata createFromParcel(Parcel in) {
                return new VideoMetadata(in);
            }

            @Override
            public VideoMetadata[] newArray(int size) {
                return new VideoMetadata[size];
            }
        };

        @Override
        public String toString() {
            return String.format(Locale.US, "filename:%s, cameraId:%s, startTime/endTime: %d/%d.", fileName, cameraId, startTime, endTime);
        }
    }

    public static class SnapshotMetadata implements Parcelable, Comparable<SnapshotMetadata>{

        private String cameraId;
        private String filename;
        private long takenTime;
        private int sequenceLength;
        private int sequenceIndex;
        private int faceDetected;
        private String triggeredBy;
        private long tripStartTime;
        private int quality;
        private int width;

        public String getCameraId() {
            return cameraId;
        }

        public String getFilename() {
            return filename;
        }

        public long getTakenTime() {
            return takenTime;
        }

        public int getSequenceLength() {
            return sequenceLength;
        }

        public int getSequenceIndex() {
            return sequenceIndex;
        }

        public String getTriggeredBy() {
            return triggeredBy;
        }

        public int getFaceDetected() {
            return faceDetected;
        }

        public long getTripStartTime() {
            return tripStartTime;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public void setTakenTime(long takenTime) {
            this.takenTime = takenTime;
        }

        public void setFaceDetected(int faceDetected) {
            this.faceDetected = faceDetected;
        }

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }


        public SnapshotMetadata(String filename, String cameraId, long takenTime, int sequenceLength, int sequenceIndex, String triggeredBy, long tripStartTime) {
            this.filename = filename;
            this.cameraId = cameraId;
            this.takenTime = takenTime;
            this.sequenceLength = sequenceLength;
            this.sequenceIndex = sequenceIndex;
            this.triggeredBy = triggeredBy;
            this.faceDetected = -1;     // faceDetected is -1 by default, means face detection hasn't been run on this snapshot.
            this.tripStartTime = tripStartTime;
            this.quality = 100;
            this.width = 0;
        }

        public SnapshotMetadata(String filename, String cameraId, long takenTime) {
            this.filename = filename;
            this.cameraId = cameraId;
            this.takenTime = takenTime;

            this.sequenceLength = 0;
            this.sequenceIndex = 0;
            this.triggeredBy = null;
            this.faceDetected = -1;     // faceDetected is -1 by default, means face detection hasn't been run on this snapshot.
            this.tripStartTime = 0;
            this.quality = 100;
            this.width = 0;
        }

        public SnapshotMetadata(String filename) {
            this.filename = filename;
        }

        public SnapshotMetadata() {
            faceDetected = -1;
        }

        protected SnapshotMetadata(Parcel in) {
            cameraId = in.readString();
            filename = in.readString();
            takenTime = in.readLong();
            sequenceLength = in.readInt();
            sequenceIndex = in.readInt();
            triggeredBy = in.readString();
            faceDetected = in.readInt();
            tripStartTime = in.readLong();
            quality = in.readInt();
            width = in.readInt();
        }

        @Override
        public String toString() {
            String st = "filename: %s, cameraId:%s, taken time %d, %d of %d, triggered by: %s, # of faces detected: %d, trip started at: %d, quality: %d, width: %d";
            return String.format(Locale.US, st, filename, cameraId, takenTime, sequenceIndex, sequenceLength, triggeredBy, faceDetected, tripStartTime, quality, width);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(cameraId);
            dest.writeString(filename);
            dest.writeLong(takenTime);
            dest.writeInt(sequenceLength);
            dest.writeInt(sequenceIndex);
            dest.writeString(triggeredBy);
            dest.writeInt(faceDetected);
            dest.writeLong(tripStartTime);
            dest.writeInt(quality);
            dest.writeInt(width);
        }

        @SuppressWarnings("unused")
        public static final Creator<SnapshotMetadata> CREATOR = new Creator<SnapshotMetadata>() {
            @Override
            public SnapshotMetadata createFromParcel(Parcel in) {
                return new SnapshotMetadata(in);
            }

            @Override
            public SnapshotMetadata[] newArray(int size) {
                return new SnapshotMetadata[size];
            }
        };

        @Override
        public int compareTo(@NonNull SnapshotMetadata another) {
            // TODO more compare.
            int result = Long.compare(takenTime, another.takenTime);
            return result != 0 ? result : cameraId.compareTo(another.cameraId);
        }
    }
}

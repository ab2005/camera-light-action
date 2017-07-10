package com.nauto.camera.base;

import static com.nauto.camera.base.CameraModule.PARAM_AE_RECT;
import static com.nauto.camera.base.CameraModule.PARAM_BIT_RATE;
import static com.nauto.camera.base.CameraModule.PARAM_DO_FACE_DETECTION;
import static com.nauto.camera.base.CameraModule.PARAM_EFFECT_MODE;
import static com.nauto.camera.base.CameraModule.PARAM_FPS;
import static com.nauto.camera.base.CameraModule.PARAM_JPEG_QUALITY;
import static com.nauto.camera.base.CameraModule.PARAM_JPEG_SIZE;
import static com.nauto.camera.base.CameraModule.PARAM_NIGHT_MODE;
import static com.nauto.camera.base.CameraModule.PARAM_PLAY_SOUND;
import static com.nauto.camera.base.CameraModule.PARAM_RECORD_AUDIO;
import static com.nauto.camera.base.CameraModule.PARAM_RUN_STICKY;
import static com.nauto.camera.base.CameraModule.PARAM_SCENE_MODE;
import static com.nauto.camera.base.CameraModule.PARAM_SELF_TRIMMING;
import static com.nauto.camera.base.CameraModule.PARAM_VIDEO_HEIGHT;
import static com.nauto.camera.base.CameraModule.PARAM_VIDEO_LENGTH_SEC;
import static com.nauto.camera.base.CameraModule.PARAM_VIDEO_WIDTH;

/**
 * Created by ab on 11/21/16.
 */
public class CameraPipelineConfig implements Cloneable {
    /**
     * Back camera default config
     */
    public final static CameraPipelineConfig DEFAULT_0_1080 =
            new CameraPipelineConfig(
                    1920, // width
                    1080, // height
                    15, // fps
                    60 * 5, // video chunk length in sec
                    8000000, // mbit rate
                    false, // record audio
                    false, // do face detection
                    false, // playSound
                    true, // run sticky
                    true // do self trimming
            );
    /**
     * Front Camera default config
     */
    public final static CameraPipelineConfig DEFAULT_1_1080 =
            new CameraPipelineConfig(
                    1920, // width
                    1080, // height
                    15, // fps
                    60 * 5, // video chunk length in sec
                    8000000, // mbit rate
                    false, // record audio
                    true, // do face detection
                    false, // playSound
                    true, // run sticky
                    true // do self trimming
            );
    /**
     * Back camera default config
     */
    public final static CameraPipelineConfig DEFAULT_0_720 =
            new CameraPipelineConfig(
                    1280, // width
                    720, // height
                    15, // fps
                    30,// 60 * 5, // video chunk length in sec
                    2500000, // mbit rate
                    false, // record audio
                    false, // do face detection
                    false, // playSound
                    true, // run sticky
                    true // do self trimming
            );
    /**
     * Front Camera default config
     */
    public final static CameraPipelineConfig DEFAULT_1_720 =
            new CameraPipelineConfig(
                    1280, // width
                    720, // height
                    15, // fps
                    30, // 60 * 5, // video chunk length in sec
                    2500 * 1000, // mbit rate
                    true, // record audio
                    true, // do face detection
                    false, // playSound
                    true, // run sticky
                    true // do self trimming
            );

    public final static CameraPipelineConfig DEFAULT_0 = DEFAULT_0_720;
    public final static CameraPipelineConfig DEFAULT_1 = DEFAULT_1_720;

    public int mVideoWidth = 1280;
    public int mVideoHeight = 720;
    public int mVideoFrameRate = 15;
    public int mVideoLengthSec = 60 * 5;
    public int mVideoBitRate = 2500 * 1000;
    public boolean mRecordAudio = false;
    public boolean mDoFaceDetection = false;
    public boolean mRunSticky = true;
    public boolean mPlaySound = false;
    public float[] mAeRects = null;
    public int mJpegWidth = 1280;
    public int mJpegHeight = 720;
    // Compression quality of the snapshot JPEG image.
    // Valid range 1-100; larger is higher quality
    public int mJpegQUality = 0;
    public int mSceneMode = 0;
    public int mEffectMode = 0;
    public boolean mNightMode = false;
    public boolean mSelfTrimming = true;

    public CameraPipelineConfig() {
        // defaults
    }


    public CameraPipelineConfig(int mVideoWidth,
                                int mVideoHeight,
                                int mVideoFrameRate,
                                int videoLengthSec,
                                int mBitRate,
                                boolean recordAudio,
                                boolean doFaceDetection,
                                boolean playSound,
                                boolean runSticky,
                                boolean selfTrimming) {
        this.mVideoWidth = mVideoWidth;
        this.mVideoHeight = mVideoHeight;
        this.mVideoFrameRate = mVideoFrameRate;
        this.mVideoBitRate = mBitRate;
        this.mRecordAudio = recordAudio;
        this.mVideoLengthSec = videoLengthSec;
        this.mDoFaceDetection = doFaceDetection;
        this.mRunSticky = runSticky;
        this.mPlaySound = playSound;
        this.mSelfTrimming = selfTrimming;
    }

    /**
     * Get parameters string to use with the service intent.
     * <p> Example: camera://start?doFaceDetection=0&runSticky=1&bitRate=8&fps=30&videoLength=300 </p>
     */
    @Override
    public String toString() {
        return PARAM_VIDEO_WIDTH + "=" + mVideoWidth +
                "&" + PARAM_VIDEO_HEIGHT + "=" + mVideoHeight +
                "&" + PARAM_FPS + "=" + mVideoFrameRate +
                "&" + PARAM_BIT_RATE + "=" + mVideoBitRate +
                "&" + PARAM_VIDEO_LENGTH_SEC + "=" + mVideoLengthSec +
                "&" + PARAM_DO_FACE_DETECTION + "=" + mDoFaceDetection +
                "&" + PARAM_RECORD_AUDIO + "=" + mRecordAudio +
                "&" + PARAM_PLAY_SOUND + "=" + mPlaySound +
                "&" + PARAM_AE_RECT + "=" + (mAeRects == null ? "0,0,1,1": "" + mAeRects[0] + "," + mAeRects[1] + "," + mAeRects[2] + "," + mAeRects[3]) +
                "&" + PARAM_JPEG_SIZE + "=" + mJpegWidth + "x" + mJpegHeight +
                "&" + PARAM_SCENE_MODE + "=" + mSceneMode +
                "&" + PARAM_EFFECT_MODE + "=" + mEffectMode +
                "&" + PARAM_NIGHT_MODE + "=" + mNightMode +
                "&" + PARAM_RUN_STICKY + "=" + mRunSticky +
                "&" + PARAM_SELF_TRIMMING + "=" + mSelfTrimming +
                "&" + PARAM_JPEG_QUALITY + "=" + mJpegQUality;
    }

    @Override
    public CameraPipelineConfig clone() {
        try {
            return (CameraPipelineConfig)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CameraPipelineConfig) {
            CameraPipelineConfig cfg = (CameraPipelineConfig) obj;
            return mVideoWidth == cfg.mVideoWidth
                    && mVideoHeight == cfg.mVideoHeight
                    && mVideoFrameRate == cfg.mVideoFrameRate
                    && mVideoLengthSec == cfg.mVideoLengthSec
                    && mVideoBitRate == cfg.mVideoBitRate
                    && mRecordAudio == cfg.mRecordAudio
                    && mDoFaceDetection == cfg.mDoFaceDetection
                    && mRunSticky == cfg.mRunSticky
                    && mPlaySound == cfg.mPlaySound
                    && mAeRects == cfg.mAeRects
                    && mJpegWidth == cfg.mJpegWidth
                    && mJpegHeight == cfg.mJpegHeight
                    && mJpegQUality == cfg.mJpegQUality
                    && mSceneMode == cfg.mSceneMode
                    && mEffectMode == cfg.mEffectMode
                    && mNightMode == cfg.mNightMode
                    && mSelfTrimming == cfg.mSelfTrimming;
        } else {
            return false;
        }

    }
}


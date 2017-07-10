package com.nauto.camera.base;

/**
 * A marker class for repeating video capture request.
 */

public abstract class VideoPipelineCaptureModule extends CaptureModule {
    /*package*/ final boolean isVideoPipelineRequest() {
        return true;
    }
}

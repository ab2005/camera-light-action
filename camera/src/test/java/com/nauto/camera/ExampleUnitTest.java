package com.nauto.camera;

import org.junit.Test;

import com.nauto.camera.base.CameraPipelineConfig;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testConfig() throws CloneNotSupportedException {
        CameraPipelineConfig conf = CameraPipelineConfig.DEFAULT_0.clone();
        System.out.println(conf.toString());
        conf.mAeRects = new float[] {1, 2, 3, 4};
        conf.mEffectMode = 3;
        System.out.println(conf.toString());
    }
}
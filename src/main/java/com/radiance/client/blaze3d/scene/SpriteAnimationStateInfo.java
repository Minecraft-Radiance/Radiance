package com.radiance.client.blaze3d.scene;

public interface SpriteAnimationStateInfo {
    int radiance$getCurrentFrameIndex();

    int radiance$getNextFrameIndex();

    int radiance$getCurrentFrameX();

    int radiance$getCurrentFrameY();

    int radiance$getNextFrameX();

    int radiance$getNextFrameY();

    float radiance$getFrameProgress();

    boolean radiance$isInterpolated();
}

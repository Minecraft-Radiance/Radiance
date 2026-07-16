package com.radiance.mixins.blaze3d_26;

import com.radiance.client.blaze3d.scene.SpriteAnimationStateInfo;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteContents.AnimationState.class)
public abstract class SpriteAnimationStateMixin implements SpriteAnimationStateInfo {
    @Shadow(remap = false)
    private int frame;

    @Shadow(remap = false)
    private int subFrame;

    @Override
    public int radiance$getCurrentFrameIndex() {
        return frameInfo(this.frame).radiance$getIndex();
    }

    @Override
    public int radiance$getNextFrameIndex() {
        List<?> frames = animationInfo().radiance$getFrames();
        return ((SpriteFrameInfoAccessor) frames.get((this.frame + 1) % frames.size()))
            .radiance$getIndex();
    }

    @Override
    public int radiance$getCurrentFrameX() {
        return animationInfo().radiance$invokeGetFrameX(this.radiance$getCurrentFrameIndex());
    }

    @Override
    public int radiance$getCurrentFrameY() {
        return animationInfo().radiance$invokeGetFrameY(this.radiance$getCurrentFrameIndex());
    }

    @Override
    public int radiance$getNextFrameX() {
        return animationInfo().radiance$invokeGetFrameX(this.radiance$getNextFrameIndex());
    }

    @Override
    public int radiance$getNextFrameY() {
        return animationInfo().radiance$invokeGetFrameY(this.radiance$getNextFrameIndex());
    }

    @Override
    public float radiance$getFrameProgress() {
        return this.subFrame / (float) frameInfo(this.frame).radiance$getTime();
    }

    @Override
    public boolean radiance$isInterpolated() {
        return animationInfo().radiance$getInterpolateFrames();
    }

    private SpriteAnimatedTextureAccessor animationInfo() {
        return (SpriteAnimatedTextureAccessor) AnimationStateFields.animationInfo(this);
    }

    private SpriteFrameInfoAccessor frameInfo(int index) {
        return (SpriteFrameInfoAccessor) animationInfo().radiance$getFrames().get(index);
    }

    private static final class AnimationStateFields {
        private static final Field ANIMATION_INFO = animationInfoField();

        private AnimationStateFields() {
        }

        static Object animationInfo(Object state) {
            try {
                return ANIMATION_INFO.get(state);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to access SpriteContents.AnimationState.animationInfo", exception);
            }
        }

        private static Field animationInfoField() {
            try {
                Field field = Class.forName("net.minecraft.client.renderer.texture.SpriteContents$AnimationState")
                    .getDeclaredField("animationInfo");
                field.setAccessible(true);
                return field;
            } catch (ClassNotFoundException | NoSuchFieldException exception) {
                throw new IllegalStateException("Unable to resolve SpriteContents.AnimationState.animationInfo", exception);
            }
        }
    }
}

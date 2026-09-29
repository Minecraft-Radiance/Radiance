package com.radiance.mixins.mod_support.flywheel;

import dev.engine_room.flywheel.backend.engine.uniform.UniformBuffer;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Uniforms.class, remap = false)
public interface UniformsAccessor {
    @Accessor("ALL_BUFFERS")
    static UniformBuffer[] radiance$getBuffers() { throw new AssertionError(); }
}

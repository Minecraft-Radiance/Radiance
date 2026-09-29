package com.radiance.mixins.mod_support.flywheel;

import dev.engine_room.flywheel.backend.engine.uniform.UniformBuffer;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UniformBuffer.class, remap = false)
public interface UniformBufferAccessor {
    @Accessor("clientBuffer")
    MemoryBlock radiance$getMemory();
}

package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelEngine;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.*;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VisualizationHelper.class, remap = false)
public class VisualizationHelperMixins {
    @Inject(method = "getVisualizer(Lnet/minecraft/world/level/block/entity/BlockEntity;)Ldev/engine_room/flywheel/api/visualization/BlockEntityVisualizer;",
        at = @At("RETURN"), cancellable = true)
    private static <T extends BlockEntity> void radiance$blockVisual(T object,
        CallbackInfoReturnable<BlockEntityVisualizer<? super T>> cir) {
        if (FlywheelSupport.vanillaRendering()) { cir.setReturnValue(null); return; }
        if (!FlywheelSupport.active()) return;
        BlockEntityVisualizer<? super T> original = cir.getReturnValue();
        if (original == null) return;
        cir.setReturnValue(new BlockEntityVisualizer<T>() {
            @Override
            public BlockEntityVisual<? super T> createVisual(VisualizationContext context, T block, float partialTick) {
                var parent = (FlywheelEngine.Context) context;
                var owned = parent.forObject(block);
                var visual = original.createVisual(owned, block, partialTick);
                return parent.isRoot() ? owned.wrap(visual) : visual;
            }
            @Override
            public boolean skipVanillaRender(T block) { return FlywheelSupport.rendered(block) && original.skipVanillaRender(block); }
        });
    }
    @Inject(method = "getVisualizer(Lnet/minecraft/world/entity/Entity;)Ldev/engine_room/flywheel/api/visualization/EntityVisualizer;",
        at = @At("RETURN"), cancellable = true)
    private static <T extends Entity> void radiance$entityVisual(T object,
        CallbackInfoReturnable<EntityVisualizer<? super T>> cir) {
        if (FlywheelSupport.vanillaRendering()) { cir.setReturnValue(null); return; }
        if (!FlywheelSupport.active()) return;
        EntityVisualizer<? super T> original = cir.getReturnValue();
        if (original == null) return;
        cir.setReturnValue(new EntityVisualizer<T>() {
            @Override
            public EntityVisual<? super T> createVisual(VisualizationContext context, T entity, float partialTick) {
                var parent = (FlywheelEngine.Context) context;
                var owned = parent.forObject(entity);
                var visual = original.createVisual(owned, entity, partialTick);
                return parent.isRoot() ? owned.wrap(visual) : visual;
            }
            @Override
            public boolean skipVanillaRender(T entity) { return FlywheelSupport.rendered(entity) && original.skipVanillaRender(entity); }
        });
    }
}

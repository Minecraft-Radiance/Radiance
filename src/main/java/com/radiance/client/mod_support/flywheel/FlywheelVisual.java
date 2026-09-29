package com.radiance.client.mod_support.flywheel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.*;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import java.util.function.Consumer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;

public final class FlywheelVisual implements BlockEntityVisual<BlockEntity>, EntityVisual<Entity>,
    EffectVisual<Effect>, DynamicVisual, TickableVisual, LightUpdatedVisual, ShaderLightVisual {
    private final Visual visual;
    private final Runnable deleted;
    FlywheelVisual(Visual visual, Runnable deleted) { this.visual = visual; this.deleted = deleted; }
    @Override
    public void update(float partialTick) { visual.update(partialTick); }
    @Override
    public void delete() { try { visual.delete(); } finally { deleted.run(); } }
    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return visual instanceof DynamicVisual dynamic ? dynamic.planFrame() : SimplePlan.of();
    }
    @Override
    public Plan<TickableVisual.Context> planTick() {
        return visual instanceof TickableVisual tickable ? tickable.planTick() : SimplePlan.of();
    }
    @Override
    public void updateLight(float partialTick) {
        if (visual instanceof LightUpdatedVisual light) light.updateLight(partialTick);
    }
    @Override
    public void setSectionCollector(SectionCollector collector) {
        if (visual instanceof SectionTrackedVisual tracked) tracked.setSectionCollector(collector);
    }
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (visual instanceof BlockEntityVisual<?> block) block.collectCrumblingInstances(consumer);
    }
}

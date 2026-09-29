package com.radiance.client.mod_support.sable;

import com.radiance.client.proxy.world.EntityProxy;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.util.math.BlockPos;

public final class SableBlockGeometry extends EntityProxy.BlockGeometrySource implements SubLevelRenderData {
    private final ClientSubLevel subLevel;

    public SableBlockGeometry(ClientSubLevel subLevel) {
        super(subLevel.getLevel());
        this.subLevel = subLevel;
        resize();
    }

    public void resize() {
        var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null || bounds.volume() <= 0) setBounds(null, null);
        else setBounds(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
            new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    @Override
    protected boolean ready() { return subLevel.isFinalized() && !subLevel.isRemoved(); }

    @Override
    protected void updateTransform(float partialTick) {
        var pose = subLevel.renderPose(partialTick);
        var center = pose.rotationPoint();
        localToWorld.translation(pose.position()).rotate(pose.orientation()).scale(pose.scale())
            .translate(-center.x(), -center.y(), -center.z());
    }

    @Override
    public ClientSubLevel getSubLevel() { return subLevel; }

    @Override
    public void compileSections(ChunkBuilderMode updates, ChunkRendererRegionBuilder regions, Camera camera) {
        compileSections(8);
    }
}

package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.render.renderer.RenderableBoxGroup;
import java.util.ArrayList;
import java.util.List;

public class DistantHorizonsDebug extends AbstractDebugWireframeRenderer {
    private final DistantHorizonsObjects objects = new DistantHorizonsObjects();
    private final RenderableBoxGroup group = new RenderableBoxGroup("radiance:debug",
        new DhApiVec3d(0, 0, 0), List.of(), false);
    private final List<DhApiRenderableBox> boxes = new ArrayList<>();

    public DistantHorizonsDebug() { objects.add(group); }

    @Override protected void beginRenderBatch() { boxes.clear(); }

    @Override
    public void renderBox(Box box) {
        double[] min = {box.minPos.x, box.minPos.y, box.minPos.z};
        double[] max = {box.maxPos.x, box.maxPos.y, box.maxPos.z};
        double radius = Math.max(0.025, Math.max(max[0] - min[0], max[2] - min[2]) / 2048.0);
        for (int axis = 0; axis < 3; axis++) {
            int a = (axis + 1) % 3, b = (axis + 2) % 3;
            for (int edge = 0; edge < 4; edge++) {
                double[] low = min.clone(), high = max.clone();
                low[a] = ((edge & 1) == 0 ? min[a] : max[a]) - radius;
                high[a] = low[a] + radius * 2;
                low[b] = ((edge & 2) == 0 ? min[b] : max[b]) - radius;
                high[b] = low[b] + radius * 2;
                boxes.add(new DhApiRenderableBox(new DhApiVec3d(low[0], low[1], low[2]),
                    new DhApiVec3d(high[0], high[1], high[2]), box.color, EDhApiBlockMaterial.ILLUMINATED));
            }
        }
    }

    @Override
    protected void endRenderBatch() {
        boolean changed = group.size() != boxes.size();
        for (int i = 0; !changed && i < boxes.size(); i++) {
            DhApiRenderableBox a = group.get(i), b = boxes.get(i);
            changed = a.minPos.x != b.minPos.x || a.minPos.y != b.minPos.y || a.minPos.z != b.minPos.z
                || a.maxPos.x != b.maxPos.x || a.maxPos.y != b.maxPos.y || a.maxPos.z != b.maxPos.z
                || !a.color.equals(b.color);
        }
        if (changed) { group.clear(); group.addAll(boxes); group.triggerBoxChange(); }
    }

    public void reset() {
        objects.reset();
        boxes.clear();
        particles.clear();
        group.clear();
        group.triggerBoxChange();
    }

    public long[] prepare(RenderParams params, long version) {
        if (!Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get()) {
            group.setActive(false);
        } else {
            group.setActive(true);
            render(params);
        }
        return objects.prepare(params, version);
    }
}

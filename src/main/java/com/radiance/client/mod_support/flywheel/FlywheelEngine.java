package com.radiance.client.mod_support.flywheel;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixins.mod_support.flywheel.UniformBufferAccessor;
import com.radiance.mixins.mod_support.flywheel.UniformsAccessor;
import dev.engine_room.flywheel.api.backend.Engine;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.instance.*;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

public final class FlywheelEngine implements Engine {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L << 32);
    private final Map<Model, FlywheelModel> models = new IdentityHashMap<>();
    private final Map<Model, String> modelFailures = new IdentityHashMap<>();
    private final Map<ProgramKey, FlywheelProgram> programs = new HashMap<>();
    private final Map<ProgramKey, String> programFailures = new HashMap<>();
    private final List<Handle> handles = new ArrayList<>();
    private final Set<Owner> owners = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> rendered = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> reported = new HashSet<>();
    private BlockPos origin = BlockPos.ZERO;
    private boolean deleted;
    private long frameSerial;
    private final LongSet failed = new LongOpenHashSet();
    private final List<Submission> submissions = new ArrayList<>();
    private final Set<Model> usedModels = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LongSet lastFailedOwners = new LongOpenHashSet();
    private boolean renderedDirty = true;
    private boolean modelsDirty = true;
    private ByteBuffer records;

    static final class Owner {
        final long id = NEXT_ID.getAndIncrement();
        final Object object;
        final Set<Object> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        volatile boolean deleted;
        Owner(Object object) { this.object = object; objects.add(object); }
    }

    private record ProgramKey(InstanceType<?> type, Material material, boolean embedded) {}
    private record Submission(Handle handle, FlywheelModel.Mesh mesh, int program, long key) {}
    private record PreparedMesh(FlywheelModel.Mesh mesh, int program) {}

    @Override
    public VisualizationContext createVisualizationContext() { return new Context(null); }
    @Override
    public Plan<RenderContext> createFramePlan() { return SimplePlan.of(); }
    @Override
    public Vec3i renderOrigin() { return origin; }
    @Override
    public boolean updateRenderOrigin(Camera camera) {
        BlockPos position = camera.getBlockPosition();
        if (origin.distSqr(position) < 256 * 256) return false;
        origin = position.immutable();
        return true;
    }
    @Override
    public void lightSections(LongSet sections) {}
    @Override
    public void onLightUpdate(SectionPos section, LightLayer layer) {}

    public boolean rendered(Object object) { return rendered.contains(object); }

    private void report(String reason) {
        if (reported.add(reason)) RadianceClient.LOGGER.warn("Flywheel Visual cannot use Entity geometry; its ordinary renderer remains active when available: {}", reason);
    }

    @Override
    public synchronized void render(RenderContext context) {
        if (deleted || !Pipeline.supportsSharedEntityGeometry()) {
            rendered.clear();
            renderedDirty = true;
            return;
        }
        if (Profiler.ENABLED) Profiler.enter("java.FlywheelEngine.render");
        try {
            ++frameSerial;
            Uniforms.update(context);
            var buffers = UniformsAccessor.radiance$getBuffers();
            ByteBuffer[] uniforms = new ByteBuffer[buffers.length];
            for (int i = 0; i < buffers.length; i++)
                uniforms[i] = ((UniformBufferAccessor) buffers[i]).radiance$getMemory().asBuffer();
            renderedDirty |= owners.removeIf(owner -> owner.deleted);
            modelsDirty |= handles.removeIf(handle -> handle.deleted || handle.group.context.owner.deleted);
            failed.clear();
            submissions.clear();
            if (modelsDirty) usedModels.clear();
            for (Handle handle : handles) {
                Group<?> group = handle.group;
                Owner owner = group.context.owner;
                if (modelsDirty) usedModels.add(group.model);
                if (owner.object instanceof BlockEntity block && FlywheelSupport.breaking(block)) {
                    failed.add(owner.id);
                    continue;
                }
                if (!handle.visible || failed.contains(owner.id)) continue;
                try {
                    if (modelFailures.containsKey(group.model)) throw new UnsupportedOperationException(modelFailures.get(group.model));
                    FlywheelModel model;
                    try { model = models.computeIfAbsent(group.model, FlywheelModel::new); }
                    catch (RuntimeException error) { modelFailures.put(group.model, error.getMessage()); throw error; }
                    if (group.preparedModel != model) {
                        List<PreparedMesh> prepared = new ArrayList<>(model.meshes.size());
                        for (var mesh : model.meshes) {
                            var key = new ProgramKey(group.type, mesh.material(), group.context instanceof Embedding || group.context instanceof SourceContext);
                            if (programFailures.containsKey(key)) throw new UnsupportedOperationException(programFailures.get(key));
                            FlywheelProgram program;
                            try { program = programs.computeIfAbsent(key, k -> new FlywheelProgram(k.type, k.material, k.embedded)); }
                            catch (RuntimeException error) { programFailures.put(key, error.getMessage()); throw error; }
                            prepared.add(new PreparedMesh(mesh, program.handle));
                        }
                        group.preparedMeshes = prepared;
                        group.preparedModel = model;
                    }
                    if (handle.preparedMeshes != group.preparedMeshes) {
                        handle.submissions.clear();
                        for (int i = 0; i < group.preparedMeshes.size(); i++) {
                            var mesh = group.preparedMeshes.get(i);
                            while (handle.keys.size() <= i) handle.keys.add(NEXT_ID.getAndIncrement());
                            handle.submissions.add(new Submission(handle, mesh.mesh, mesh.program, handle.keys.get(i)));
                        }
                        handle.preparedMeshes = group.preparedMeshes;
                    }
                    submissions.addAll(handle.submissions);
                } catch (RuntimeException error) {
                    failed.add(owner.id);
                    report(error.getMessage());
                }
            }
            if (!failed.isEmpty()) submissions.removeIf(s -> failed.contains(s.handle.group.context.owner.id));
            int bytes = 4;
            for (Submission submission : submissions)
                bytes = Math.addExact(bytes, 152 + submission.handle.group.dataSize());
            if (records == null || records.capacity() < bytes) {
                int capacity = Math.max(bytes, records == null ? 4096 : Math.multiplyExact(records.capacity(), 2));
                records = records == null ? MemoryUtil.memAlloc(capacity) : MemoryUtil.memRealloc(records, capacity);
            }
            records.clear();
            if (Profiler.ENABLED) Profiler.enter("java.FlywheelEngine.packInstances");
            try {
                records.putInt(submissions.size());
                for (Submission submission : submissions) {
                    Handle handle = submission.handle;
                    Context embedding = handle.group.context;
                    records.putLong(submission.key).putLong(embedding.owner.id)
                        .putInt(submission.mesh.handle()).putInt(submission.program)
                        .putDouble(origin.getX()).putDouble(origin.getY()).putDouble(origin.getZ());
                    Matrix4f pose = embedding.framePose();
                    Matrix3f normal = embedding.frameNormal();
                    pose.get(records.position(), records); records.position(records.position() + 64);
                    normal.get(records.position(), records); records.position(records.position() + 36);
                    int size = handle.group.dataSize();
                    records.putInt(size);
                    MemoryUtil.memSet(MemoryUtil.memAddress(records), 0, size);
                    handle.group.write(handle.instance, MemoryUtil.memAddress(records));
                    records.position(records.position() + size);
                }
                records.flip();
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
            if (Profiler.ENABLED) Profiler.enter("java.FlywheelEngine.submitInstances");
            try {
                for (String failure : EntityProxy.queueVertexShaderInstancesNative(records.slice(), uniforms)) {
                    int separator = failure.indexOf('\t');
                    failed.add(Long.parseLong(failure.substring(0, separator)));
                    report(failure.substring(separator + 1));
                }
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
            if (renderedDirty || !lastFailedOwners.equals(failed)) {
                rendered.clear();
                for (Owner owner : owners) if (!failed.contains(owner.id)) rendered.addAll(owner.objects);
                lastFailedOwners.clear();
                lastFailedOwners.addAll(failed);
                renderedDirty = false;
            }
            if (modelsDirty) {
                models.entrySet().removeIf(entry -> {
                    if (usedModels.contains(entry.getKey())) return false;
                    entry.getValue().close();
                    return true;
                });
                modelFailures.keySet().retainAll(usedModels);
                modelsDirty = false;
            }
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    @Override
    public void renderCrumbling(RenderContext context, List<CrumblingBlock> blocks) {
        for (CrumblingBlock block : blocks) FlywheelSupport.addBreaking(block.pos());
    }
    @Override
    public synchronized void delete() {
        deleted = true;
        handles.clear(); owners.clear(); rendered.clear();
        submissions.clear(); usedModels.clear(); failed.clear(); lastFailedOwners.clear();
        if (records != null) { MemoryUtil.memFree(records); records = null; }
        models.values().forEach(FlywheelModel::close); models.clear(); modelFailures.clear();
        programs.values().forEach(FlywheelProgram::close); programs.clear(); programFailures.clear();
    }

    public class Context implements VisualizationContext, InstancerProvider {
        final Owner owner;
        private long transformFrame = -1;
        private final Matrix4f cachedPose = new Matrix4f();
        private final Matrix3f cachedNormal = new Matrix3f();
        final Matrix4f framePose() { updateFrameTransform(); return cachedPose; }
        final Matrix3f frameNormal() { updateFrameTransform(); return cachedNormal; }
        private void updateFrameTransform() {
            if (transformFrame == frameSerial) return;
            updateTransform(cachedPose, cachedNormal);
            transformFrame = frameSerial;
        }
        void updateTransform(Matrix4f pose, Matrix3f normal) { pose.identity(); normal.identity(); }
        final Map<InstanceType<?>, Map<Model, Map<Integer, Group<?>>>> groups = new IdentityHashMap<>();
        Context(Owner owner) { this.owner = owner; }
        public Context forObject(Object object) {
            if (owner != null) { synchronized (FlywheelEngine.this) { renderedDirty |= owner.objects.add(object); } return this; }
            synchronized (FlywheelEngine.this) {
                Owner next = new Owner(object);
                owners.add(next);
                renderedDirty = true;
                if (object instanceof BlockEntity block) {
                    var source = EntityProxy.blockSource(block);
                    if (source != null) return new SourceContext(next, source, block.getBlockPos());
                }
                return new Context(next);
            }
        }
        public boolean isRoot() { return owner == null; }
        public FlywheelVisual wrap(dev.engine_room.flywheel.api.visual.Visual visual) {
            if (visual == null) { owner.deleted = true; return null; }
            return new FlywheelVisual(visual, () -> owner.deleted = true);
        }
        @Override
        public InstancerProvider instancerProvider() { return this; }
        @Override
        public Vec3i renderOrigin() { return origin; }
        @Override
        public VisualEmbedding createEmbedding(Vec3i renderOrigin) { return new Embedding(this, renderOrigin); }
        @Override
        @SuppressWarnings("unchecked")
        public synchronized <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
            if (owner == null) return forObject(this).instancer(type, model, bias);
            return (Instancer<I>) groups.computeIfAbsent(type, key -> new IdentityHashMap<>())
                .computeIfAbsent(model, key -> new HashMap<>()).computeIfAbsent(bias, key -> new Group<>(this, type, model));
        }
    }

    private final class SourceContext extends Context {
        final EntityProxy.BlockGeometrySource source;
        final net.minecraft.core.BlockPos localOrigin;
        SourceContext(Owner owner, EntityProxy.BlockGeometrySource source,
            net.minecraft.core.BlockPos localOrigin) {
            super(owner);
            this.source = source;
            this.localOrigin = localOrigin.immutable();
        }
        @Override
        public Vec3i renderOrigin() { return localOrigin; }
        @Override
        void updateTransform(Matrix4f pose, Matrix3f normal) {
            pose.set(source.transform(localOrigin, origin));
            pose.normal(normal);
        }
    }

    private final class Embedding extends Context implements VisualEmbedding {
        final Context parent;
        final Vec3i embeddingOrigin;
        final Matrix4f transform = new Matrix4f();
        final Matrix3f normalTransform = new Matrix3f();
        boolean closed;
        Embedding(Context parent, Vec3i origin) {
            super(parent.owner == null ? parent.forObject(parent).owner : parent.owner);
            this.parent = parent; this.embeddingOrigin = origin;
        }
        @Override
        public Vec3i renderOrigin() { return embeddingOrigin; }
        @Override
        void updateTransform(Matrix4f pose, Matrix3f normal) {
            pose.set(parent.framePose()).mul(transform);
            normal.set(parent.frameNormal()).mul(normalTransform);
        }
        @Override
        public void transforms(Matrix4fc pose, Matrix3fc normal) { transform.set(pose); normalTransform.set(normal); }
        @Override
        public void delete() { closed = true; }
        @Override
        public synchronized <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
            if (closed) throw new IllegalStateException("Deleted Flywheel embedding");
            return super.instancer(type, model, bias);
        }
    }

    private final class Group<I extends Instance> implements Instancer<I> {
        final Context context;
        final InstanceType<I> type;
        final Model model;
        FlywheelModel preparedModel;
        List<PreparedMesh> preparedMeshes;
        Group(Context context, InstanceType<I> type, Model model) { this.context = context; this.type = type; this.model = model; }
        int dataSize() { return (type.layout().byteSize() + 3) & ~3; }
        @SuppressWarnings("unchecked")
        void write(Instance instance, long ptr) { type.writer().write(ptr, (I) instance); }
        @Override
        public I createInstance() {
            synchronized (FlywheelEngine.this) {
                if (deleted) throw new IllegalStateException("Deleted Flywheel engine");
                Handle handle = new Handle(this);
                I instance = type.create(handle);
                handle.instance = instance; handles.add(handle); modelsDirty = true;
                return instance;
            }
        }
        @Override
        public void stealInstance(I instance) {
            if (instance == null) return;
            synchronized (FlywheelEngine.this) {
                if (!(instance.handle() instanceof Handle handle) || !handles.contains(handle) || instance.type() != type)
                    throw new IllegalArgumentException("Foreign Flywheel instance");
                handle.group = this;
                modelsDirty = true;
            }
        }
    }

    private final class Handle implements InstanceHandle {
        final List<Long> keys = new ArrayList<>();
        final List<Submission> submissions = new ArrayList<>();
        List<PreparedMesh> preparedMeshes;
        Group<?> group;
        Instance instance;
        volatile boolean visible = true;
        volatile boolean deleted;
        Handle(Group<?> group) { this.group = group; }
        @Override
        public void setChanged() {}
        @Override
        public void setDeleted() { deleted = true; }
        @Override
        public void setVisible(boolean visible) { this.visible = visible; }
        @Override
        public boolean isVisible() { return visible && !deleted; }
    }
}

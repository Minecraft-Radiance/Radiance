package com.radiance.client.mod_support.distant_horizons;

import com.radiance.client.vista.Vista;
import com.radiance.client.proxy.world.VistaProxy;
import com.radiance.client.vista.VistaTerrain;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper_neoforge;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterRenderEvent;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;

public class DistantHorizonsSupport implements Vista.Source {
    private static final ArrayList<LodRenderSection> sections = new ArrayList<>();
    private static ClientLevel currentWorld;
    private static long worldVersion;
    private static long[] previousSelection = new long[0];
    private static boolean renderingEnabled = true;
    private static volatile boolean bound;
    private static final RenderParams renderParams = new RenderParams();
    private static volatile boolean reloadPending;

    public static synchronized void bind() {
        if (bound) { return; }
        Config.Client.Advanced.Graphics.GenericRendering.dimensionEnabledCloudRenderingCsv.setApiValue("");
        Config.Client.Advanced.Graphics.Quality.lodShading.setApiValue(EDhApiLodShading.AUTO);
        new DistantHorizonsRenderApi().bindRenderers();
        bound = true;
        Vista.setSource(new DistantHorizonsSupport());
    }

    @Override
    public int distanceBlocks() {
        return bound && Config.Client.quickEnableRendering.get()
            && Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED
            ? Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * 16 : 0;
    }

    @Override public int textureId() { return DistantHorizonsTextures.id(); }

    @Override
    public int[] sideMaterials(BlockState state) {
        return DistantHorizonsMaterials.sides(state);
    }

    @Override
    public void reload() { reloadPending = true; }

    @Override
    public void setWorld(ClientLevel world) {
        if (!bound) { return; }
        if (currentWorld != world) {
            currentWorld = world;
            VistaProxy.reset(++worldVersion);
            previousSelection = new long[0];
            sections.clear();
            DistantHorizonsRenderApi.debug.reset();
        }
        if (world == null) { RenderThreadTaskHandler.INSTANCE.runRenderThreadTasks(); }
        if (world == null) { DistantHorizonsTextures.close(); }
    }

    @Override
    public void render(Camera camera, Matrix4f view, Matrix4f projection, float tickDelta) {
        if (!bound) { return; }
        Minecraft client = Minecraft.getInstance();
        setWorld(client.level);
        RenderThreadTaskHandler.INSTANCE.runRenderThreadTasks();
        boolean enabled = Config.Client.quickEnableRendering.get()
            && Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED;
        if (renderingEnabled != enabled) {
            renderingEnabled = enabled;
            VistaProxy.reset(++worldVersion);
            previousSelection = new long[0];
        }
        if (!enabled) { return; }
        var level = MinecraftClientWrapper_neoforge.INSTANCE.getWrappedClientLevel();
        if (level == null) { return; }
        ClientApi.RENDER_STATE.clientLevelWrapper = level;
        ClientApi.RENDER_STATE.mcModelViewMatrix.set(view);
        ClientApi.RENDER_STATE.mcProjectionMatrix.set(projection);
        ClientApi.RENDER_STATE.partialTickTime = tickDelta;
        var distantHorizonsWorld = SharedApi.tryGetDhClientWorld();
        if (distantHorizonsWorld == null) { return; }
        if (reloadPending) {
            reloadPending = false;
            VistaProxy.reset(++worldVersion);
            previousSelection = new long[0];
            com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry.INSTANCE.clear();
            DistantHorizonsTextures.reset();
            client.levelRenderer.allChanged();
            for (var loaded : distantHorizonsWorld.getAllLoadedLevels()) {
                if (loaded instanceof com.seibel.distanthorizons.core.level.IDhClientLevel clientLevel) {
                    clientLevel.clearRenderCache();
                }
            }
        }
        DistantHorizonsTextures.update();
        var distantHorizonsLevel = distantHorizonsWorld.getOrLoadClientLevel(level);
        if (distantHorizonsLevel == null) { return; }
        RenderBufferHandler handler = distantHorizonsLevel.getRenderBufferHandler();
        if (handler == null) { return; }
        renderParams.update(EDhApiRenderPass.OPAQUE, ClientApi.RENDER_STATE);
        if (ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderEvent.class, renderParams.apiCopy)) {
            VistaProxy.updateRenderList(worldVersion, new long[0]);
            VistaProxy.updateObjectListNative(worldVersion, new long[0]);
            previousSelection = new long[0];
            ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, null);
            return;
        }
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderSetupEvent.class, renderParams.apiCopy);
        long[] debugIds = DistantHorizonsRenderApi.debug.prepare(renderParams, worldVersion);
        if (distantHorizonsLevel.getGenericRenderer() instanceof DistantHorizonsObjects objects) {
            long[] objectIds = objects.prepare(renderParams, worldVersion);
            long[] selection = Arrays.copyOf(objectIds, objectIds.length + debugIds.length);
            System.arraycopy(debugIds, 0, selection, objectIds.length, debugIds.length);
            VistaProxy.updateObjectListNative(worldVersion, selection);
        }
        ArrayList<Long> ids = new ArrayList<>();
        boolean terrainReady = true;
        if (Config.Client.quickEnableRendering.get()) {
            sections.clear();
            handler.lodQuadTree.populateListWithEnabledRenderSections(sections);
            for (LodRenderSection section : sections) {
                if (section == null || !section.getRenderingEnabled()) { continue; }
                LodBufferContainer container = section.renderBufferContainer;
                if (container == null || !container.buffersUploaded) { continue; }
                int x = container.minCornerBlockPos.getX();
                int y = container.minCornerBlockPos.getY();
                int z = container.minCornerBlockPos.getZ();
                int width = DhSectionPos.getBlockWidth(container.pos);
                VistaTerrain terrain = ((IDistantHorizonsBufferContainerExt) container).radiance$getTerrain();
                if (terrain == null) {
                    terrainReady = false;
                    continue;
                }
                collect(container.vboOpaqueWrappers, x, y, z, width, terrain, ids);
                collect(container.vboTransparentWrappers, x, y, z, width, terrain, ids);
            }
        }
        long[] selection = ids.stream().mapToLong(Long::longValue).sorted().toArray();
        if (terrainReady && !Arrays.equals(selection, previousSelection)) {
            VistaProxy.updateRenderList(worldVersion, selection);
            previousSelection = selection;
        }
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderCleanupEvent.class, renderParams.apiCopy);
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, null);
    }

    private static void collect(IVertexBufferWrapper[] buffers, int x, int y, int z, int width,
        VistaTerrain terrain, ArrayList<Long> ids) {
        if (buffers == null) { return; }
        for (IVertexBufferWrapper buffer : buffers) {
            if (buffer instanceof DistantHorizonsVertexBuffer distantHorizonsBuffer) {
                distantHorizonsBuffer.submit(worldVersion, x, y, z, width, terrain);
                ids.add(distantHorizonsBuffer.id);
            }
        }
    }
}

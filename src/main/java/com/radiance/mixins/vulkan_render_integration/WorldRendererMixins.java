package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.UnsafeManager;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    private ClientWorld world;

    @Final
    @Shadow
    private MinecraftClient client;

    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private Frustum frustum;

    @Final
    @Shadow
    private List<Entity> renderedEntities;

    @Shadow
    private int renderedEntitiesCount;

    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraYaw;

    @Final
    @Shadow
    private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    @Final
    private WeatherRendering weatherRendering;

    @Shadow
    @Final
    private WorldBorderRendering worldBorderRendering;

    @Shadow
    private int ticks;
    @Shadow
    @Final
    private CloudRenderer cloudRenderer;
    // endregion

    // region <init>
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/render/SkyRendering"))
    private SkyRendering cancelNewSkyRendering() {
        return UnsafeManager.INSTANCE.allocateInstance(SkyRendering.class);
    }
    // endregion

    @Redirect(method = "scheduleTerrainUpdate()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;scheduleTerrainUpdate()V"))
    public void cancelTerrainUpdateWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance) {

    }

    // region <close>
    @Redirect(method = "close()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/SkyRendering;close()V"))
    public void cancelSkyRenderingClose(SkyRendering instance) {

    }

    @Redirect(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;loadEntityOutlinePostProcessor()V"))
    public void cancelReloadWithResourceManager(WorldRenderer instance) {

    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;setStorage"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelReloadWithChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    public boolean enablePlayerRendererInFirstPlayer(Camera instance) {
        return true;
    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    public <E extends Entity> boolean loosenEntityFiltering(EntityRenderDispatcher instance,
        E entity, Frustum frustum, double x, double y, double z) {
        Vec3d vec3d = entity.getPos().subtract(new Vec3d(x, y, z));
        double distance = vec3d.length();
        if (distance < 16 * 3) {
            return true;
        }
        return this.entityRenderDispatcher.shouldRender(entity, frustum, x, y, z);
    }

    // region <render>
    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean getEntitiesToRender(Camera camera, Frustum frustum,
        List<Entity> output);

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    protected abstract void applyFrustum(Frustum frustum);

    @Shadow
    protected abstract boolean isSkyDark(float tickDelta);

    @Shadow
    protected abstract boolean hasBlindnessOrDarkness(Camera camera);

    @Inject(method =
        "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;"
            + "ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(ObjectAllocator allocator, RenderTickCounter tickCounter,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PlayerProxy.setCameraPos(camera.getPos());

        float f = tickCounter.getTickDelta(false);
        RenderSystem.setShaderGameTime(this.world.getTime(), f);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Frustum frustum = this.frustum;

        Vec3d vec3d = camera.getPos();
        double x = vec3d.getX();
        double y = vec3d.getY();
        double z = vec3d.getZ();

        this.setupTerrain(camera, frustum, false, false);

        boolean renderEntityOutline = this.getEntitiesToRender(camera, frustum,
            this.renderedEntities);

        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);

        // fog
        float h = gameRenderer.getViewDistance();
        boolean bl2 = this.client.world.getDimensionEffects()
            .useThickFog(MathHelper.floor(x), MathHelper.floor(y))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        Vector4f vector4f = BackgroundRenderer.getFogColor(camera, f, this.client.world,
            this.client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(f));
        Fog fog = BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN,
            vector4f, h, bl2, f);

        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(EndPortalBlockEntityRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            EndPortalBlockEntityRenderer.PORTAL_TEXTURE).getGlId();
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, fog, world, endSkyTextureID, endPortalTextureID);

        // Sky
        float tickDelta = tickCounter.getTickDelta(false);
        float skyAngle = this.world.getSkyAngle(tickDelta);

        int baseColor = this.world.getSkyColor(this.client.gameRenderer.getCamera().getPos(),
            tickDelta);
        float baseColorR = ColorHelper.getRedFloat(baseColor);
        float baseColorG = ColorHelper.getGreenFloat(baseColor);
        float baseColorB = ColorHelper.getBlueFloat(baseColor);

        DimensionEffects dimensionEffects = this.world.getDimensionEffects();
        int horizontalColor = dimensionEffects.getSkyColor(skyAngle);
        float horizontalColorR = ColorHelper.getRedFloat(horizontalColor);
        float horizontalColorG = ColorHelper.getGreenFloat(horizontalColor);
        float horizontalColorB = ColorHelper.getBlueFloat(horizontalColor);
        float horizontalColorA = ColorHelper.getAlphaFloat(horizontalColor);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.peek().getPositionMatrix();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.pop();

        int skyType = dimensionEffects.getSkyType().ordinal();

        boolean sunRisingOrSetting = dimensionEffects.isSunRisingOrSetting(skyAngle);

        boolean skyDark = this.isSkyDark(tickDelta);

        boolean hasBlindnessOrDarkness = this.hasBlindnessOrDarkness(camera);

        int submersionType = camera.getSubmersionType().ordinal();

        int moonPhase = this.world.getMoonPhase();

        float rainGradient = this.world.getRainGradient(tickDelta);
        CloudRenderMode cloudRenderMode = this.client.options.getCloudRenderModeValue();
        float cloudLayerHeight = -16384.0F;
        if (cloudRenderMode != CloudRenderMode.OFF) {
            float cloudsHeight = this.world.getDimensionEffects().getCloudsHeight();
            if (!Float.isNaN(cloudsHeight)) {
                cloudLayerHeight = cloudsHeight + 0.33F;
            }
        }

        int sunTextureID = textureManager.getTexture(SkyRendering.SUN_TEXTURE).getGlId();

        int moonTextureID = textureManager.getTexture(SkyRendering.MOON_PHASES_TEXTURE).getGlId();

        BufferProxy.updateSkyUniform(baseColorR, baseColorG, baseColorB, horizontalColorR,
            horizontalColorG, horizontalColorB, horizontalColorA, sunDirection, skyType,
            sunRisingOrSetting, skyDark, hasBlindnessOrDarkness, submersionType, moonPhase,
            rainGradient, cloudLayerHeight, sunTextureID, moonTextureID);

        BufferProxy.updateMapping();

        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) (gameRenderer.getLightmapTextureManager());
        BufferProxy.updateLightMapUniform(lightMapManagerExt.radiance$getAmbientLightFactor(),
            lightMapManagerExt.radiance$getSkyFactor(),
            lightMapManagerExt.radiance$getBlockFactor(),
            lightMapManagerExt.radiance$isUseBrightLightmap(),
            lightMapManagerExt.radiance$getSkyLightColor(),
            lightMapManagerExt.radiance$getNightVisionFactor(),
            lightMapManagerExt.radiance$getDarknessScale(),
            lightMapManagerExt.radiance$getDarkenWorldFactor(),
            lightMapManagerExt.radiance$getBrightnessFactor());

        // Entities
        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickCounter, canDrawEntityOutlines());

        EntityProxy.BlockEntityQueueResult crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            this.builtChunks, this.noCullingBlockEntities, blockBreakingProgressions,
            blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, blockBreakingProgressions,
            this.client.getBlockRenderManager(), this.world, crumblingRenderData);

        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);

        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, world);
        }

        EntityProxy.queueWeatherBuild(this.weatherRendering, this.worldBorderRendering, this.world,
            camera, this.ticks, tickDelta);

        // clouds
        if (cloudRenderMode != CloudRenderMode.OFF) {
            float k = this.world.getDimensionEffects().getCloudsHeight();
            if (!Float.isNaN(k)) {
                float ticks = (float) this.ticks + f;
                int color = this.world.getCloudsColor(f);
                float cloudHeight = k + 0.33F;
                this.cloudRenderer.renderClouds(color, cloudRenderMode, cloudHeight, null, null,
                    camera.getPos(), ticks);
            }
        }

        // Chunks
        ChunkProxy.rebuild(camera, this.builtChunks);
        ChunkProxy.queueSpecialGeometry(this.builtChunks, camera);

        this.renderedEntities.clear();

        ci.cancel();
    }
    // endregion

    // region <setWorld>
    @Redirect(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;setStorage"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelSetWorldChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }
    // endregion

    //region <setupTerrain>
    @Inject(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.AFTER), cancellable = true)
    public void cancelCullAndUpdateWithChunkRenderingDataPreparer(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci, @Local Profiler profiler) {
//        PlayerProxy.setCameraPos(camera.getPos());
        profiler.pop();
        ci.cancel();
    }
    //endregion

    // region <addBuiltChunk>
    @Redirect(method = "addBuiltChunk(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;schedulePropagationFrom"
            + "(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"))
    public void cancelPropagateWithChunkRenderingDataPreparer(ChunkRenderingDataPreparer instance,
        ChunkBuilder.BuiltChunk builtChunk) {

    }
    // endregion

    // region <onChunkUnload>
    @Redirect(method = "onChunkUnload(J)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;schedulePropagationFrom"
            + "(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"))
    public void cancelPropagateUnloadWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance, ChunkBuilder.BuiltChunk builtChunk) {

    }
    // endregion

    // region <scheduleNeighborUpdates>
    @Redirect(method = "scheduleNeighborUpdates(Lnet/minecraft/util/math/ChunkPos;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;addNeighbors(Lnet/minecraft/util/math/ChunkPos;)"
            + "V"))
    public void cancelNeighborUpdatesWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance, ChunkPos chunkPos) {

    }
    // endregion

    // region <isRenderingReady>
    @Inject(method = "isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ChunkBuilder.BuiltChunk builtChunk = chunks.getRenderedChunk(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.data.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.data.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }
    // endregion

    // region <>
    @Inject(method = "getCompletedChunkCount()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkNum - 54); // 54 + 10 = 64
    }
    // endregion
}

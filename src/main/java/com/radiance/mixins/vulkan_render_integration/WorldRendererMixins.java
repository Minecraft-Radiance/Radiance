package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.mod_support.ModSupport;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;

import com.radiance.client.vista.Vista;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.render.CloudRenderer;
import com.radiance.client.render.WeatherRenderer;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.BlockEntitySectionCache;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBuiltChunkStorageExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldRenderer.class, priority = 1100)
public abstract class WorldRendererMixins {

    @Inject(method = "addBuiltChunk(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V",
        at = @At("HEAD"))
    private void updateBlockEntitySections(ChunkBuilder.BuiltChunk chunk, CallbackInfo ci) {
        BlockEntitySectionCache.changed(chunk);
    }

    @Unique
    private final WorldRenderContextImpl radiance$worldRenderContext = new WorldRenderContextImpl();


    @Inject(method = "setWorld", at = @At("RETURN"))
    private void updateVistaWorld(ClientWorld world, CallbackInfo ci) {
        Vista.setWorld(world);
        com.radiance.client.texture.RainExposure.setWorld(world);
        if (ModSupport.FLYWHEEL) FlywheelSupport.setWorld(world);
    }

    @Unique
    private static final Identifier RADIANCE_SUN_TEXTURE = new Identifier("minecraft",
        "textures/environment/sun.png");
    @Unique
    private static final Identifier RADIANCE_MOON_PHASES_TEXTURE = new Identifier("minecraft",
        "textures/environment/moon_phases.png");

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
    @Final
    private ObjectArrayList<?> chunkInfos;

    @Shadow
    private Frustum frustum;

    @Shadow
    private int cameraChunkX;

    @Shadow
    private int cameraChunkY;

    @Shadow
    private int cameraChunkZ;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    private int ticks;

    @Unique
    private int radiance$previousCameraChunkX;

    @Unique
    private int radiance$previousCameraChunkY;

    @Unique
    private int radiance$previousCameraChunkZ;

    @Unique
    private final CloudRenderer cloudRenderer = new CloudRenderer();
    @Unique
    private final WeatherRenderer weatherRenderer = new WeatherRenderer();

    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    public abstract boolean isRenderingReady(BlockPos pos);

    @Shadow
    protected abstract void renderWeather(LightmapTextureManager manager, float tickDelta,
        double cameraX, double cameraY, double cameraZ);

    @Redirect(method = "renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"))
    private void captureWeatherTexture(int textureSlot, Identifier texture) {
        WeatherRenderer.setActiveTexture(textureSlot, texture);
        RenderSystem.setShaderTexture(textureSlot, texture);
    }

    @Unique
    private boolean isSkyDark(float tickDelta) {
        return this.client.player != null && this.client.player.getCameraPosVec(tickDelta).y
            - this.world.getLevelProperties().getSkyDarknessHeight(this.world) < 0.0;
    }

    @Unique
    private boolean hasBlindnessOrDarkness(Camera camera) {
        return camera.getFocusedEntity() instanceof LivingEntity livingEntity
            && (livingEntity.hasStatusEffect(StatusEffects.BLINDNESS)
            || livingEntity.hasStatusEffect(StatusEffects.DARKNESS));
    }

    @Unique
    private boolean shouldRenderEntity(Entity entity, Frustum frustum, double x, double y,
        double z) {
        return entity.squaredDistanceTo(x, y, z) < 48.0 * 48.0
            || this.entityRenderDispatcher.shouldRender(entity, frustum, x, y, z);
    }

    @Unique
    private void collectEntitiesToRender(Camera camera, Frustum frustum,
        List<Entity> output, double x, double y, double z) {
        Entity player = this.client.player;
        for (Entity entity : this.world.getEntities()) {
            if (!this.shouldRenderEntity(entity, frustum, x, y, z)
                && (player == null || !entity.hasPassengerDeep(player))) {
                continue;
            }

            BlockPos blockPos = entity.getBlockPos();
            if (!this.world.isOutOfHeightLimit(blockPos.getY()) && !this.isRenderingReady(
                blockPos)) {
                continue;
            }

            if (entity instanceof ClientPlayerEntity && camera.getFocusedEntity() != entity) {
                continue;
            }

            output.add(entity);
        }
    }

    @Redirect(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;loadEntityOutlinePostProcessor()V"))
    public void cancelReloadLoadEntityOutlinePostProcessor(WorldRenderer instance) {

    }

    @Inject(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At("HEAD"))
    public void invalidateCloudCellsOnResourceReload(CallbackInfo ci) {
        this.cloudRenderer.invalidate();
    }

    @Inject(method = "reload()V", at = @At("HEAD"))
    public void invalidateCloudCellsOnReload(CallbackInfo ci) {
        this.cloudRenderer.invalidate();
    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/BuiltChunkStorage;updateCameraPosition(DD)V"))
    public void cancelReloadCameraPosition(BuiltChunkStorage instance, double x, double z) {

    }

    @Inject(method = "reload()V", at = @At("TAIL"))
    public void updateReloadCameraPositionNew(CallbackInfo ci) {
        Entity cameraEntity = this.client.getCameraEntity();
        if (this.chunks != null && cameraEntity != null) {
            ((IBuiltChunkStorageExt) this.chunks).radiance$updateCameraPosition(
                ChunkSectionPos.from(cameraEntity.getPos()));
        }
    }

    @Inject(method =
        "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;"
            + "Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;"
            + "Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(MatrixStack matrices, float tickDelta, long limitTime,
        boolean renderBlockOutline,
        Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
        Matrix4f projectionMatrix, CallbackInfo ci) {
        MatrixStack eventMatrices = new MatrixStack();
        eventMatrices.multiplyPositionMatrix(((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        this.radiance$worldRenderContext.prepare((WorldRenderer) (Object) this, eventMatrices,
            tickDelta, limitTime, renderBlockOutline, camera, gameRenderer, lightmapTextureManager,
            projectionMatrix, this.client.getBufferBuilders().getEntityVertexConsumers(),
            this.world.getProfiler(), false, this.world);
        this.radiance$worldRenderContext.setFrustum(null);
        EntityProxy.captureWorldRender(WorldRenderEvents.START,
            () -> WorldRenderEvents.START.invoker().onStart(this.radiance$worldRenderContext));
        PlayerProxy.setCameraPos(camera.getPos());
        Vista.render(camera,
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix(), projectionMatrix, tickDelta);

        RenderSystem.setShaderGameTime(this.world.getTime(), tickDelta);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Frustum frustum = this.frustum;
        Vec3d cameraPos = camera.getPos();
        double x = cameraPos.getX();
        double y = cameraPos.getY();
        double z = cameraPos.getZ();

        this.setupTerrain(camera, frustum, false,
            this.client.player != null && this.client.player.isSpectator());

        List<Entity> renderedEntities = new ArrayList<>();
        this.collectEntitiesToRender(camera, frustum, renderedEntities, x, y, z);

        this.radiance$worldRenderContext.setFrustum(frustum);
        EntityProxy.captureWorldRender(WorldRenderEvents.AFTER_SETUP,
            () -> WorldRenderEvents.AFTER_SETUP.invoker().afterSetup(this.radiance$worldRenderContext));
        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(matrices.peek().getPositionMatrix());

        float viewDistance = gameRenderer.getViewDistance();
        boolean thickFog = this.world.getDimensionEffects()
            .useThickFog(MathHelper.floor(x), MathHelper.floor(y))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        BackgroundRenderer.render(camera, tickDelta, this.world,
            this.client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(tickDelta));
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN,
            Math.max(viewDistance, 32.0F), thickFog, tickDelta);
        BackgroundRenderer.setFogBlack();

        TextureManager textureManager = this.client.getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(EndPortalBlockEntityRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            EndPortalBlockEntityRenderer.PORTAL_TEXTURE).getGlId();
        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) gameRenderer.getLightmapTextureManager();
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, RenderSystem.getShaderFogStart(),
            RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor(),
            RenderSystem.getShaderFogShape().getId(), this.world, endSkyTextureID,
            endPortalTextureID, lightMapManagerExt.radiance$getTextureId());

        float skyAngle = this.world.getSkyAngle(tickDelta);
        Vec3d baseColor = this.world.getSkyColor(camera.getPos(), tickDelta);

        DimensionEffects dimensionEffects = this.world.getDimensionEffects();
        float[] horizonColor = dimensionEffects.getFogColorOverride(skyAngle, tickDelta);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.peek().getPositionMatrix();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.pop();

        BufferProxy.updateSkyUniform((float) baseColor.x, (float) baseColor.y, (float) baseColor.z,
            horizonColor == null ? 0.0F : horizonColor[0],
            horizonColor == null ? 0.0F : horizonColor[1],
            horizonColor == null ? 0.0F : horizonColor[2],
            horizonColor == null ? 0.0F : horizonColor[3],
            sunDirection, dimensionEffects.getSkyType().ordinal(), horizonColor != null,
            this.isSkyDark(tickDelta), this.hasBlindnessOrDarkness(camera),
            camera.getSubmersionType().ordinal(), this.world.getMoonPhase(),
            this.world.getRainGradient(tickDelta),
            textureManager.getTexture(RADIANCE_SUN_TEXTURE).getGlId(),
            textureManager.getTexture(RADIANCE_MOON_PHASES_TEXTURE).getGlId());

        BufferProxy.updateMapping();

        if (ModSupport.FLYWHEEL) {
            FlywheelSupport.render((WorldRenderer) (Object) this,
                matrices, projectionMatrix, camera, tickDelta, this.ticks, this.blockBreakingProgressions);
        }

        EntityProxy.captureWorldRender(WorldRenderEvents.BEFORE_ENTITIES,
            () -> WorldRenderEvents.BEFORE_ENTITIES.invoker().beforeEntities(this.radiance$worldRenderContext));
        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickDelta, this.canDrawEntityOutlines());

        EntityProxy.captureWorldRender(WorldRenderEvents.AFTER_ENTITIES,
            () -> WorldRenderEvents.AFTER_ENTITIES.invoker().afterEntities(this.radiance$worldRenderContext));
        Pair<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            this.chunks, this.noCullingBlockEntities, this.blockBreakingProgressions,
            this.blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, this.blockBreakingProgressions,
            this.client.getBlockRenderManager(), this.world, crumblingRenderData.getLeft(),
            crumblingRenderData.getRight());

        EntityProxy.captureWorldRender(WorldRenderEvents.BEFORE_DEBUG_RENDER,
            () -> WorldRenderEvents.BEFORE_DEBUG_RENDER.invoker().beforeDebugRender(this.radiance$worldRenderContext));
        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);
        EntityProxy.captureWorldRender(WorldRenderEvents.AFTER_TRANSLUCENT,
            () -> WorldRenderEvents.AFTER_TRANSLUCENT.invoker().afterTranslucent(this.radiance$worldRenderContext));


        if (renderBlockOutline) {
            EntityProxy.captureWorldRender(WorldRenderEvents.BEFORE_BLOCK_OUTLINE, () -> {
                this.radiance$worldRenderContext.renderBlockOutline = WorldRenderEvents.BEFORE_BLOCK_OUTLINE
                    .invoker().beforeBlockOutline(this.radiance$worldRenderContext, this.client.crosshairTarget);
                if (this.radiance$worldRenderContext.renderBlockOutline
                    && this.client.crosshairTarget instanceof BlockHitResult hit
                    && hit.getType() != HitResult.Type.MISS) {
                    this.radiance$worldRenderContext.prepareBlockOutline(camera.getFocusedEntity(),
                        camera.getPos().x, camera.getPos().y, camera.getPos().z, hit.getBlockPos(),
                        this.world.getBlockState(hit.getBlockPos()));
                    this.radiance$worldRenderContext.renderBlockOutline = WorldRenderEvents.BLOCK_OUTLINE
                        .invoker().onBlockOutline(this.radiance$worldRenderContext, this.radiance$worldRenderContext);
                }
            });
            if (this.radiance$worldRenderContext.renderBlockOutline) {
                EntityProxy.queueTargetBlockOutlineRebuild(camera, this.world);
            }
        }

        this.cloudRenderer.renderClouds(this.world, camera, this.ticks, tickDelta);
        this.weatherRenderer.begin();
        try {
            this.renderWeather(lightmapTextureManager, tickDelta, x, y, z);
        } finally {
            this.weatherRenderer.end();
        }
        ChunkProxy.setStorage(this.chunks);
        ChunkProxy.rebuild(camera, ChunkProxy.extractVisibleChunks(this.chunkInfos), null);

        BackgroundRenderer.clearFog();
        EntityProxy.captureWorldRender(WorldRenderEvents.LAST,
            () -> WorldRenderEvents.LAST.invoker().onLast(this.radiance$worldRenderContext));
        EntityProxy.captureWorldRender(WorldRenderEvents.END,
            () -> WorldRenderEvents.END.invoker().onEnd(this.radiance$worldRenderContext));

        ci.cancel();
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At("HEAD"))
    public void capturePreviousCameraChunkPosition(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        this.radiance$previousCameraChunkX = this.cameraChunkX;
        this.radiance$previousCameraChunkY = this.cameraChunkY;
        this.radiance$previousCameraChunkZ = this.cameraChunkZ;
    }

    @Redirect(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/BuiltChunkStorage;updateCameraPosition(DD)V"))
    public void cancelLegacyCameraPositionUpdate(BuiltChunkStorage instance, double x, double z) {

    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.AFTER))
    public void cancelCullAndUpdateWithChunkRenderingDataPreparer(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        Vec3d cameraPos = camera.getPos();
        int cameraSectionX = ChunkSectionPos.getSectionCoord(cameraPos.getX());
        int cameraSectionY = ChunkSectionPos.getSectionCoord(cameraPos.getY());
        int cameraSectionZ = ChunkSectionPos.getSectionCoord(cameraPos.getZ());
        if (this.radiance$previousCameraChunkX != cameraSectionX
            || this.radiance$previousCameraChunkY != cameraSectionY
            || this.radiance$previousCameraChunkZ != cameraSectionZ) {
            ((IBuiltChunkStorageExt) this.chunks).radiance$updateCameraPosition(
                ChunkSectionPos.from(cameraPos));
        }

        this.cameraChunkX = cameraSectionX;
        this.cameraChunkY = cameraSectionY;
        this.cameraChunkZ = cameraSectionZ;
    }

    @Inject(method = "isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ChunkBuilder.BuiltChunk builtChunk = this.chunks.getRenderedChunk(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.data.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.data.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }

    @Inject(method = "getCompletedChunkCount()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkNum - 54);
    }

    @Inject(method = "renderStars()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderStars(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderLightSky()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderLightSky(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderDarkSky()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderDarkSky(CallbackInfo ci) {
        ci.cancel();
    }
}

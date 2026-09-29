package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.mod_support.ModSupport;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;

import com.radiance.client.vista.Vista;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.render.CloudRenderer;
import com.radiance.client.render.WeatherRenderer;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBuiltChunkStorageExt;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
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

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixins {

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void updateVistaWorld(ClientLevel world, CallbackInfo ci) {
        Vista.setWorld(world);
        com.radiance.client.texture.RainExposure.setWorld(world);
        EntityProxy.setSourceWorld(world);
        if (ModSupport.FLYWHEEL) FlywheelSupport.setWorld(world);
    }

    @Unique
    private static final ResourceLocation RADIANCE_SUN_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/sun.png");
    @Unique
    private static final ResourceLocation RADIANCE_MOON_PHASES_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/moon_phases.png");

    @Shadow
    private ClientLevel level;

    @Final
    @Shadow
    private Minecraft minecraft;

    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private ViewArea viewArea;

    @Shadow
    private Frustum cullingFrustum;

    @Shadow
    private int lastCameraSectionX;

    @Shadow
    private int lastCameraSectionY;

    @Shadow
    private int lastCameraSectionZ;

    @Final
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Shadow
    @Final
    private Set<BlockEntity> globalBlockEntities;

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
    protected abstract void setupRender(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    public abstract boolean shouldShowEntityOutlines();

    @Shadow
    public abstract boolean isSectionCompiled(BlockPos pos);

    @Shadow
    protected abstract void renderSnowAndRain(LightTexture manager, float tickDelta,
        double cameraX, double cameraY, double cameraZ);

    @Redirect(method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void captureWeatherTexture(int textureSlot, ResourceLocation texture) {
        WeatherRenderer.setActiveTexture(textureSlot, texture);
        RenderSystem.setShaderTexture(textureSlot, texture);
    }

    @Unique
    private boolean isSkyDark(float tickDelta) {
        return this.minecraft.player != null && this.minecraft.player.getEyePosition(tickDelta).y
            - this.level.getLevelData().getHorizonHeight(this.level) < 0.0;
    }

    @Unique
    private boolean hasBlindnessOrDarkness(Camera camera) {
        return camera.getEntity() instanceof LivingEntity livingEntity
            && (livingEntity.hasEffect(MobEffects.BLINDNESS)
            || livingEntity.hasEffect(MobEffects.DARKNESS));
    }

    @Unique
    private boolean shouldRenderEntity(Entity entity, Frustum frustum, double x, double y,
        double z) {
        return entity.distanceToSqr(x, y, z) < 48.0 * 48.0
            || this.entityRenderDispatcher.shouldRender(entity, frustum, x, y, z);
    }

    @Unique
    private void collectEntitiesToRender(Camera camera, Frustum frustum,
        List<Entity> output, double x, double y, double z) {
        Entity player = this.minecraft.player;
        for (Entity entity : this.level.entitiesForRendering()) {
            if (!this.shouldRenderEntity(entity, frustum, x, y, z)
                && (player == null || !entity.hasIndirectPassenger(player))) {
                continue;
            }

            BlockPos blockPos = entity.blockPosition();
            if (!this.level.isOutsideBuildHeight(blockPos.getY()) && !this.isSectionCompiled(
                blockPos)) {
                continue;
            }

            if (entity instanceof LocalPlayer && camera.getEntity() != entity) {
                continue;
            }

            output.add(entity);
        }
    }

    @Redirect(method = "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;initOutline()V"))
    public void cancelReloadLoadEntityOutlinePostProcessor(LevelRenderer instance) {

    }

    @Inject(method = "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"))
    public void invalidateCloudCellsOnResourceReload(CallbackInfo ci) {
        this.cloudRenderer.invalidate();
    }

    @Inject(method = "allChanged()V", at = @At("HEAD"))
    public void invalidateCloudCellsOnReload(CallbackInfo ci) {
        this.cloudRenderer.invalidate();
        EntityProxy.invalidateSources();
    }

    @Redirect(method = "allChanged()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"))
    public void cancelReloadCameraPosition(ViewArea instance, double x, double z) {

    }

    @Inject(method = "allChanged()V", at = @At("TAIL"))
    public void updateReloadCameraPositionNew(CallbackInfo ci) {
        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (this.viewArea != null && cameraEntity != null) {
            ((IBuiltChunkStorageExt) this.viewArea).radiance$updateCameraPosition(
                SectionPos.of(cameraEntity.position()));
        }
    }

    @Inject(method =
        "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(DeltaTracker tickCounter, boolean renderBlockOutline,
        Camera camera, GameRenderer gameRenderer, LightTexture lightmapTextureManager,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PlayerProxy.setCameraPos(camera.getPosition());
        Vista.render(camera,
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix(), projectionMatrix, tickCounter.getGameTimeDeltaPartialTick(false));

        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
        RenderSystem.setShaderGameTime(this.level.getGameTime(), tickDelta);
        this.blockEntityRenderDispatcher.prepare(this.level, camera, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, camera, this.minecraft.crosshairPickEntity);

        this.level.pollLightUpdates();
        this.level.getChunkSource().getLightEngine().runLightUpdates();

        Frustum frustum = this.cullingFrustum;
        Vec3 cameraPos = camera.getPosition();
        double x = cameraPos.x();
        double y = cameraPos.y();
        double z = cameraPos.z();

        this.setupRender(camera, frustum, false,
            this.minecraft.player != null && this.minecraft.player.isSpectator());

        List<Entity> renderedEntities = new ArrayList<>();
        this.collectEntitiesToRender(camera, frustum, renderedEntities, x, y, z);

        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);

        float viewDistance = gameRenderer.getRenderDistance();
        boolean thickFog = this.level.effects()
            .isFoggyAt(Mth.floor(x), Mth.floor(y))
            || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupColor(camera, tickDelta, this.level,
            this.minecraft.options.getEffectiveRenderDistance(), gameRenderer.getDarkenWorldAmount(tickDelta));
        FogRenderer.levelFogColor();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN,
            Math.max(viewDistance, 32.0F), thickFog, tickDelta);

        TextureManager textureManager = this.minecraft.getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.overlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getId();
        int endSkyTextureID = textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION)
            .getId();
        int endPortalTextureID = textureManager.getTexture(
            TheEndPortalRenderer.END_PORTAL_LOCATION).getId();
        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) gameRenderer.lightTexture();
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, RenderSystem.getShaderFogStart(),
            RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor(),
            RenderSystem.getShaderFogShape().getIndex(), this.level, endSkyTextureID,
            endPortalTextureID, lightMapManagerExt.radiance$getTextureId());

        float skyAngle = this.level.getTimeOfDay(tickDelta);
        Vec3 baseColor = this.level.getSkyColor(camera.getPosition(), tickDelta);

        DimensionSpecialEffects dimensionEffects = this.level.effects();
        float[] horizonColor = dimensionEffects.getSunriseColor(skyAngle, tickDelta);

        PoseStack matrixStack = new PoseStack();
        matrixStack.pushPose();
        matrixStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        matrixStack.mulPose(Axis.XP.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.last().pose();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.popPose();

        BufferProxy.updateSkyUniform((float) baseColor.x, (float) baseColor.y, (float) baseColor.z,
            horizonColor == null ? 0.0F : horizonColor[0],
            horizonColor == null ? 0.0F : horizonColor[1],
            horizonColor == null ? 0.0F : horizonColor[2],
            horizonColor == null ? 0.0F : horizonColor[3],
            sunDirection, dimensionEffects.skyType().ordinal(), horizonColor != null,
            this.isSkyDark(tickDelta), this.hasBlindnessOrDarkness(camera),
            camera.getFluidInCamera().ordinal(), this.level.getMoonPhase(),
            this.level.getRainLevel(tickDelta),
            textureManager.getTexture(RADIANCE_SUN_TEXTURE).getId(),
            textureManager.getTexture(RADIANCE_MOON_PHASES_TEXTURE).getId());

        BufferProxy.updateMapping();

        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_SKY, effectedRotationMatrix,
            projectionMatrix, camera, frustum);
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            if (layer == RenderType.translucent() || layer == RenderType.tripwire()) continue;
            RenderLevelStageEvent.Stage stage = RenderLevelStageEvent.Stage.fromRenderType(layer);
            if (stage != null) dispatchWorldStage(stage, effectedRotationMatrix, projectionMatrix,
                camera, frustum);
        }

        EntityProxy.prepareSources(tickDelta);
        if (ModSupport.FLYWHEEL) {
            FlywheelSupport.render((LevelRenderer) (Object) this,
                viewMatrix, projectionMatrix, camera, tickDelta, this.ticks, this.destructionProgress);
        }

        EntityProxy.queueSources(tickDelta, this.blockEntityRenderDispatcher);

        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickCounter, this.shouldShowEntityOutlines());

        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_ENTITIES, effectedRotationMatrix,
            projectionMatrix, camera, frustum);

        Tuple<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            this.viewArea, this.globalBlockEntities, this.destructionProgress,
            this.blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, this.destructionProgress,
            this.minecraft.getBlockRenderer(), this.level, crumblingRenderData.getA(),
            crumblingRenderData.getB());

        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, effectedRotationMatrix,
            projectionMatrix, camera, frustum);
        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, effectedRotationMatrix,
            projectionMatrix, camera, frustum);
        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS, effectedRotationMatrix,
            projectionMatrix, camera, frustum);

        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);
        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_PARTICLES, effectedRotationMatrix,
            projectionMatrix, camera, frustum);


        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, this.level);
        }

        this.cloudRenderer.renderClouds(this.level, camera, this.ticks, tickDelta);
        this.weatherRenderer.begin();
        try {
            this.renderSnowAndRain(lightmapTextureManager, tickDelta, x, y, z);
        } finally {
            this.weatherRenderer.end();
        }
        dispatchWorldStage(RenderLevelStageEvent.Stage.AFTER_WEATHER, effectedRotationMatrix,
            projectionMatrix, camera, frustum);
        ChunkProxy.setStorage(this.viewArea);
        ChunkProxy.rebuild(camera, this.visibleSections, null);
        FogRenderer.setupNoFog();

        ci.cancel();
    }

    @Inject(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At("HEAD"))
    public void capturePreviousCameraChunkPosition(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        this.radiance$previousCameraChunkX = this.lastCameraSectionX;
        this.radiance$previousCameraChunkY = this.lastCameraSectionY;
        this.radiance$previousCameraChunkZ = this.lastCameraSectionZ;
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"))
    public void cancelLegacyCameraPositionUpdate(ViewArea instance, double x, double z) {

    }

    @Inject(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;setCamera(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER))
    public void updateChunkStorageBeforeCulling(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
        Vec3 cameraPos = camera.getPosition();
        int cameraSectionX = SectionPos.posToSectionCoord(cameraPos.x());
        int cameraSectionY = SectionPos.posToSectionCoord(cameraPos.y());
        int cameraSectionZ = SectionPos.posToSectionCoord(cameraPos.z());
        if (this.radiance$previousCameraChunkX != cameraSectionX
            || this.radiance$previousCameraChunkY != cameraSectionY
            || this.radiance$previousCameraChunkZ != cameraSectionZ) {
            ((IBuiltChunkStorageExt) this.viewArea).radiance$updateCameraPosition(
                SectionPos.of(cameraPos));
        }

        this.lastCameraSectionX = cameraSectionX;
        this.lastCameraSectionY = cameraSectionY;
        this.lastCameraSectionZ = cameraSectionZ;
    }

    @Inject(method = "isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        SectionRenderDispatcher.RenderSection builtChunk = this.viewArea.getRenderSectionAt(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.compiled.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.compiled.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }

    @Inject(method = "countRenderedSections()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkCount - 54);
    }

    @Inject(method = "createStars()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderStars(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "createLightSky()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderLightSky(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "createDarkSky()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelRenderDarkSky(CallbackInfo ci) {
        ci.cancel();
    }

    @Unique
    private void dispatchWorldStage(RenderLevelStageEvent.Stage stage, Matrix4f view,
        Matrix4f projection, Camera camera, Frustum frustum) {
        ClientHooks.dispatchRenderStage(stage, (LevelRenderer) (Object) this,
            new PoseStack(), view, projection, this.ticks, camera, frustum);
    }
}

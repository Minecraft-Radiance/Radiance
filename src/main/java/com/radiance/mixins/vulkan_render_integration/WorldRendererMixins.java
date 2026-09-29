package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.UnsafeManager;
import com.radiance.client.vista.Vista;
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
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
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

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixins {

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void updateVistaWorld(ClientLevel world, CallbackInfo ci) {
        Vista.setWorld(world);
        com.radiance.client.texture.RainExposure.setWorld(world);
    }


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

    @Final
    @Shadow
    private List<Entity> visibleEntities;

    @Shadow
    private int visibleEntityCount;

    @Shadow
    private double prevCamRotX;

    @Shadow
    private double prevCamRotY;

    @Final
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Final
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Shadow
    @Final
    private Set<BlockEntity> globalBlockEntities;

    @Shadow
    @Final
    private WeatherEffectRenderer weatherEffectRenderer;

    @Shadow
    @Final
    private WorldBorderRenderer worldBorderRenderer;

    @Shadow
    private int ticks;
    @Shadow
    @Final
    private CloudRenderer cloudRenderer;



    @Redirect(method = "Lnet/minecraft/client/renderer/LevelRenderer;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/client/renderer/RenderBuffers;)V", at = @At(value = "NEW", target = "net/minecraft/client/renderer/SkyRenderer"))
    private SkyRenderer cancelNewSkyRendering() {
        return UnsafeManager.INSTANCE.allocateInstance(SkyRenderer.class);
    }



    @Redirect(method = "Lnet/minecraft/client/renderer/LevelRenderer;close()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SkyRenderer;close()V"))
    public void cancelSkyRenderingClose(SkyRenderer instance) {

    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LevelRenderer;onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;initOutline()V"))
    public void cancelReloadWithResourceManager(LevelRenderer instance) {

    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LevelRenderer;collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    public boolean enablePlayerRendererInFirstPlayer(Camera instance) {
        return true;
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LevelRenderer;collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    public <E extends Entity> boolean loosenEntityFiltering(EntityRenderDispatcher instance,
        E entity, Frustum cullingFrustum, double x, double y, double z) {
        Vec3 vec3d = entity.position().subtract(new Vec3(x, y, z));
        double distance = vec3d.length();
        if (distance < 24) {
            return true;
        }
        return this.entityRenderDispatcher.shouldRender(entity, cullingFrustum, x, y, z);
    }


    @Shadow
    protected abstract void setupRender(Camera camera, Frustum cullingFrustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean collectVisibleEntities(Camera camera, Frustum cullingFrustum,
        List<Entity> output);

    @Shadow
    protected abstract boolean shouldShowEntityOutlines();

    @Shadow
    protected abstract void applyFrustum(Frustum cullingFrustum);

    @Shadow
    protected abstract boolean shouldRenderDarkDisc(float tickDelta);

    @Shadow
    protected abstract boolean doesMobEffectBlockSky(Camera camera);

    @Inject(method =
        "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;"
            + "ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(GraphicsResourceAllocator allocator, DeltaTracker tickCounter,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        PlayerProxy.setCameraPos(camera.getPosition());
        Vista.render(camera, ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix(),
            projectionMatrix, tickCounter.getGameTimeDeltaPartialTick(false));

        float f = tickCounter.getGameTimeDeltaPartialTick(false);
        RenderSystem.setShaderGameTime(this.level.getGameTime(), f);
        this.blockEntityRenderDispatcher.prepare(this.level, camera, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, camera, this.minecraft.crosshairPickEntity);

        this.level.pollLightUpdates();
        this.level.getChunkSource().getLightEngine().runLightUpdates();

        Frustum cullingFrustum = this.cullingFrustum;

        Vec3 vec3d = camera.getPosition();
        double x = vec3d.x();
        double y = vec3d.y();
        double z = vec3d.z();

        this.setupRender(camera, cullingFrustum, false, false);

        boolean renderEntityOutline = false;
        if (EntityProxy.isEntityNativeTransferEnabled()) {
            renderEntityOutline = this.collectVisibleEntities(camera, cullingFrustum,
                this.visibleEntities);
        }

        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);


        float h = gameRenderer.getRenderDistance();
        boolean bl2 = this.minecraft.level.effects()
            .isFoggyAt(Mth.floor(x), Mth.floor(y))
            || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        Vector4f vector4f = FogRenderer.computeFogColor(camera, f, this.minecraft.level,
            this.minecraft.options.getEffectiveRenderDistance(), gameRenderer.getDarkenWorldAmount(f));
        FogParameters fog = FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN,
            vector4f, h, bl2, f);

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.overlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getId();
        int endSkyTextureID = textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION)
            .getId();
        int endPortalTextureID = textureManager.getTexture(
            TheEndPortalRenderer.END_PORTAL_LOCATION).getId();
        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) (gameRenderer.lightTexture());
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, fog, level, endSkyTextureID, endPortalTextureID,
            lightMapManagerExt.radiance$getTextureId());


        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
        float skyAngle = this.level.getTimeOfDay(tickDelta);
        int baseColor = this.level.getSkyColor(camera.getPosition(), tickDelta);

        DimensionSpecialEffects dimensionEffects = this.level.effects();
        int horizonColor = dimensionEffects.getSunriseOrSunsetColor(skyAngle);

        PoseStack matrixStack = new PoseStack();
        matrixStack.pushPose();
        matrixStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        matrixStack.mulPose(Axis.XP.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.last().pose();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.popPose();

        boolean doesMobEffectBlockSky = this.doesMobEffectBlockSky(camera);

        int submersionType = camera.getFluidInCamera().ordinal();

        int moonPhase = this.level.getMoonPhase();

        float rainGradient = this.level.getRainLevel(tickDelta);

        int sunTextureID = textureManager.getTexture(SkyRenderer.SUN_LOCATION).getId();

        int moonTextureID = textureManager.getTexture(SkyRenderer.MOON_LOCATION).getId();

        BufferProxy.updateSkyUniform(ARGB.redFloat(baseColor),
            ARGB.greenFloat(baseColor), ARGB.blueFloat(baseColor),
            ARGB.redFloat(horizonColor), ARGB.greenFloat(horizonColor),
            ARGB.blueFloat(horizonColor), ARGB.alphaFloat(horizonColor), sunDirection,
            dimensionEffects.skyType().ordinal(), dimensionEffects.isSunriseOrSunset(skyAngle),
            this.shouldRenderDarkDisc(tickDelta), doesMobEffectBlockSky, submersionType, moonPhase,
            rainGradient, sunTextureID, moonTextureID);

        BufferProxy.updateMapping();


        EntityProxy.queueEntitiesBuild(camera, visibleEntities, this.entityRenderDispatcher,
            tickCounter, shouldShowEntityOutlines());

        Tuple<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            viewArea, this.globalBlockEntities, destructionProgress,
            blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, destructionProgress,
            this.minecraft.getBlockRenderer(), this.level, crumblingRenderData.getA(),
            crumblingRenderData.getB());

        EntityProxy.queueParticleRebuild(camera, tickDelta, cullingFrustum);

        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, level);
        }

        EntityProxy.queueWeatherBuild(this.weatherEffectRenderer, this.worldBorderRenderer, this.level,
            camera, this.ticks, tickDelta);


        CloudStatus cloudRenderMode = this.minecraft.options.getCloudsType();
        if (cloudRenderMode != CloudStatus.OFF) {
            float k = this.level.effects().getCloudHeight();
            if (!Float.isNaN(k)) {
                float ticks = (float) this.ticks + f;
                int color = this.level.getCloudColor(f);
                float cloudHeight = k + 0.33F;
                this.cloudRenderer.render(color, cloudRenderMode, cloudHeight, null, null,
                    camera.getPosition(), ticks);
            }
        }


        ChunkProxy.setStorage(viewArea);
        ChunkProxy.rebuild(camera, this.visibleSections, this.nearbyVisibleSections);

        this.visibleEntities.clear();

        ci.cancel();
    }






    @Inject(method = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        SectionRenderDispatcher.RenderSection builtChunk = viewArea.getRenderSectionAt(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.compiled.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.compiled.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }



    @Inject(method = "Lnet/minecraft/client/renderer/LevelRenderer;countRenderedSections()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkCount - 54);
    }

}

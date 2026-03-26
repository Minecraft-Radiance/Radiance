package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
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
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    @Final
    private static net.minecraft.util.Identifier SUN;

    @Shadow
    @Final
    private static net.minecraft.util.Identifier MOON_PHASES;

    @Shadow
    private ClientWorld world;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private Frustum frustum;

    @Shadow
    private Framebuffer entityOutlinesFramebuffer;

    @Shadow
    private PostEffectProcessor entityOutlinePostProcessor;

    @Shadow
    private PostEffectProcessor transparencyPostProcessor;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean hasBlindnessOrDarkness(Camera camera);

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    public abstract boolean isRenderingReady(BlockPos pos);

    @Redirect(method = "<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;Lnet/minecraft/client/render/BufferBuilderStorage;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderStars()V"))
    private void cancelRenderStars(WorldRenderer instance) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;Lnet/minecraft/client/render/BufferBuilderStorage;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderLightSky()V"))
    private void cancelRenderLightSky(WorldRenderer instance) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;Lnet/minecraft/client/render/BufferBuilderStorage;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderDarkSky()V"))
    private void cancelRenderDarkSky(WorldRenderer instance) {
    }

    @Inject(method = "loadEntityOutlinePostProcessor()V", at = @At("HEAD"),
        cancellable = true)
    private void disableEntityOutlinePostProcessor(CallbackInfo ci) {
        this.entityOutlinesFramebuffer = null;
        this.entityOutlinePostProcessor = null;
        ci.cancel();
    }

    @Inject(method = "reloadTransparencyPostProcessor()V", at = @At("HEAD"),
        cancellable = true)
    private void disableTransparencyPostProcessor(CallbackInfo ci) {
        this.transparencyPostProcessor = null;
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
        at = @At("HEAD"), cancellable = true)
    private void redirectRender(MatrixStack matrices, float tickDelta, long limitTime,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        PlayerProxy.setCameraPos(camera.getPos());

        RenderSystem.setShaderGameTime(this.world.getTime(), tickDelta);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Vec3d cameraPos = camera.getPos();
        double cameraX = cameraPos.getX();
        double cameraY = cameraPos.getY();
        double cameraZ = cameraPos.getZ();

        boolean spectator = this.client.player != null && this.client.player.isSpectator();
        this.setupTerrain(camera, this.frustum, false, spectator);

        BackgroundRenderer.render(camera, tickDelta, this.world,
            this.client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(tickDelta));
        BackgroundRenderer.setFogBlack();

        float viewDistance = gameRenderer.getViewDistance();
        boolean thickFog = this.world.getDimensionEffects()
            .useThickFog(MathHelper.floor(cameraX), MathHelper.floor(cameraY))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, viewDistance,
            thickFog, tickDelta);

        TextureManager textureManager = this.client.getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) (Object) overlayTexture).radiance$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(EndPortalBlockEntityRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            EndPortalBlockEntityRenderer.PORTAL_TEXTURE).getGlId();

        Matrix4f viewMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
        BufferProxy.updateWorldUniform(camera, viewMatrix, new Matrix4f(viewMatrix),
            projectionMatrix, overlayTextureID, RenderSystem.getShaderFogStart(),
            RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor(),
            RenderSystem.getShaderFogShape(), this.world, endSkyTextureID, endPortalTextureID);

        float skyAngle = this.world.getSkyAngle(tickDelta);
        Vec3d skyColor = this.world.getSkyColor(cameraPos, tickDelta);
        float baseColorR = (float) skyColor.x;
        float baseColorG = (float) skyColor.y;
        float baseColorB = (float) skyColor.z;

        DimensionEffects dimensionEffects = this.world.getDimensionEffects();
        float[] fogColorOverride = dimensionEffects.getFogColorOverride(skyAngle, tickDelta);
        float horizontalColorR = fogColorOverride == null ? 0.0F : fogColorOverride[0];
        float horizontalColorG = fogColorOverride == null ? 0.0F : fogColorOverride[1];
        float horizontalColorB = fogColorOverride == null ? 0.0F : fogColorOverride[2];
        float horizontalColorA = fogColorOverride == null ? 0.0F : 1.0F;

        MatrixStack skyMatrices = new MatrixStack();
        skyMatrices.push();
        skyMatrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        skyMatrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0F));
        Matrix4f skyRotationMatrix = skyMatrices.peek().getPositionMatrix();
        Vector3f sunDirection = skyRotationMatrix.transformPosition(0.0F, 1.0F, 0.0F,
            new Vector3f()).normalize();
        skyMatrices.pop();

        int skyType = dimensionEffects.getSkyType().ordinal();
        boolean sunRisingOrSetting = fogColorOverride != null;
        boolean skyDark = dimensionEffects.isDarkened();
        boolean hasBlindnessOrDarkness = this.hasBlindnessOrDarkness(camera);
        int submersionType = camera.getSubmersionType().ordinal();
        int moonPhase = this.world.getMoonPhase();
        float rainGradient = this.world.getRainGradient(tickDelta);
        int sunTextureID = textureManager.getTexture(SUN).getGlId();
        int moonTextureID = textureManager.getTexture(MOON_PHASES).getGlId();

        BufferProxy.updateSkyUniform(baseColorR, baseColorG, baseColorB, horizontalColorR,
            horizontalColorG, horizontalColorB, horizontalColorA, sunDirection, skyType,
            sunRisingOrSetting, skyDark, hasBlindnessOrDarkness, submersionType, moonPhase,
            rainGradient, sunTextureID, moonTextureID);

        BufferProxy.updateMapping();

        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) lightmapTextureManager;
        BufferProxy.updateLightMapUniform(lightMapManagerExt.radiance$getAmbientLightFactor(),
            lightMapManagerExt.radiance$getSkyFactor(),
            lightMapManagerExt.radiance$getBlockFactor(),
            lightMapManagerExt.radiance$isUseBrightLightmap(),
            lightMapManagerExt.radiance$getSkyLightColor(),
            lightMapManagerExt.radiance$getNightVisionFactor(),
            lightMapManagerExt.radiance$getDarknessScale(),
            lightMapManagerExt.radiance$getDarkenWorldFactor(),
            lightMapManagerExt.radiance$getBrightnessFactor());

        List<Entity> renderedEntities = new ArrayList<>();
        Entity focusedEntity = camera.getFocusedEntity();
        for (Entity entity : this.world.getEntities()) {
            BlockPos blockPos = entity.getBlockPos();
            boolean shouldRender = this.entityRenderDispatcher.shouldRender(entity, this.frustum,
                cameraX, cameraY, cameraZ);
            if (this.client.player != null && entity.hasPassengerDeep(this.client.player)) {
                shouldRender = true;
            }
            if (entity == focusedEntity) {
                shouldRender = true;
            }
            if (!shouldRender) {
                continue;
            }
            if (!this.world.isOutOfHeightLimit(blockPos.getY()) && !this.isRenderingReady(blockPos)) {
                continue;
            }
            if (entity instanceof ClientPlayerEntity && focusedEntity != entity) {
                continue;
            }
            if (entity == focusedEntity && !camera.isThirdPerson()
                && (!(focusedEntity instanceof LivingEntity livingEntity)
                || !livingEntity.isSleeping())) {
                renderedEntities.add(entity);
                continue;
            }
            renderedEntities.add(entity);
        }

        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickDelta);
        if (this.chunks != null) {
            EntityProxy.queueBlockEntitiesBuild(this.chunks, this.noCullingBlockEntities,
                this.blockBreakingProgressions, this.blockEntityRenderDispatcher, tickDelta);
        }

        ChunkProxy.rebuild(camera);

        ci.cancel();
    }
}

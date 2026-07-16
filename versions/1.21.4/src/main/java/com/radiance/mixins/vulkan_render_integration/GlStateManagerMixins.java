package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixins {

    @Inject(method = "_activeTexture(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectActiveTexture(int texture, CallbackInfo ci) {
        ci.cancel();
    }

    // region <PipelineStateProxy.ViewportState>
    @Inject(method = "_disableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableScissorTest(CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setScissorEnabled(false);
        ci.cancel();
    }

    @Inject(method = "_enableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableScissorTest(CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setScissorEnabled(true);
        ci.cancel();
    }

    @Inject(method = "_scissorBox(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
        ci.cancel();
    }

    @Inject(method = "_viewport(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectViewport(int x, int y, int width, int height, CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setViewport(x, y, width, height);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ColorBlendState>
    @Inject(method = "_disableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableBlend(CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.setBlendEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableBlend(CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.setBlendEnable(true);
        ci.cancel();
    }

    @Inject(method = "_blendFunc(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendFuncCombined(srcFactor, dstFactor);
        ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate(IIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFuncSeparate(int srcFactorRGB,
        int dstFactorRGB,
        int srcFactorAlpha,
        int dstFactorAlpha,
        CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
            dstFactorRGB, dstFactorAlpha);
        ci.cancel();
    }

    @Inject(method = "_blendEquation(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(int mode, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendOpCombined(mode);
        ci.cancel();
    }

    @Inject(method = "_colorMask(ZZZZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(boolean red, boolean green, boolean blue,
        boolean alpha, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetColorWriteMask(red, green, blue, alpha);
        ci.cancel();
    }

    @Inject(method = "_enableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableColorLogicOp(CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(true);
        ci.cancel();
    }

    @Inject(method = "_disableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableColorLogicOp(CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(false);
        ci.cancel();
    }

    @Inject(method = "_logicOp(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectLogicOp(int op, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetColorLogicOp(op);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.DepthStencilState>
    @Inject(method = "_disableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableDepthTest(CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.setDepthTestEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableDepthTest(CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.setDepthTestEnable(true);
        ci.cancel();
    }

    @Inject(method = "_depthFunc(I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthFunc(int func, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetDepthCompareOp(func);
        ci.cancel();
    }

    @Inject(method = "_depthMask(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthMask(boolean mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.setDepthWriteEnable(mask);
        ci.cancel();
    }

    @Inject(method = "_stencilFunc(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilFunc(int func, int ref, int mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetStencilFunc(func, ref, mask);
        ci.cancel();
    }

    @Inject(method = "_stencilMask(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.vkSetStencilWriteMask(mask);
        ci.cancel();
    }

    @Inject(method = "_stencilOp(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int sfail, int dpfail, int dppass, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetStencilOp(sfail, dpfail, dppass);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.RasterizationState>
    @Inject(method = "_enableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableCull(CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetCullMode(GL11.GL_BACK);
        ci.cancel();
    }

    @Inject(method = "_disableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableCull(CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.vkSetCullMode(
            VulkanConstants.VkCullMode.VK_CULL_MODE_NONE.getValue());
        ci.cancel();
    }

    @Inject(method = "_polygonMode(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonMode(int face, int mode, CallbackInfo ci) {
        /*
          @Warning no vulkan equivalent implementation
         */
        PipelineStateProxy.RasterizationState.glSetPolygonMode(mode);
        ci.cancel();
    }

    @Inject(method = "_enablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnablePolygonOffset(CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, true);
        ci.cancel();
    }

    @Inject(method = "_disablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisablePolygonOffset(CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, false);
        ci.cancel();
    }

    @Inject(method = "_polygonOffset(FF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonOffset(float factor, float units, CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetPolygonOffset(factor, units);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ClearState>
    @Inject(method = "_clearColor(FFFF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearColor(float red, float green, float blue, float alpha,
        CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearColor(red, green, blue, alpha);
        ci.cancel();
    }

    @Inject(method = "_clearDepth(D)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearDepth(double depth, CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearDepth(depth);
        ci.cancel();
    }

    @Inject(method = "_clearStencil(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearStencil(int stencil, CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearStencil(stencil);
        ci.cancel();
    }
    // endregion

    // region <DrawCommandProxy.Overlay>
    @Inject(method = "_clear(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClear(int mask, CallbackInfo ci) {
        DrawCommandProxy.Overlay.glClear(mask);
        ci.cancel();
    }
    // endregion

    @Redirect(method = "_getString(I)Ljava/lang/String;", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;", remap = false))
    private static String redirectGetString(int name) {
        return "Vulkan 1.4";
    }
}

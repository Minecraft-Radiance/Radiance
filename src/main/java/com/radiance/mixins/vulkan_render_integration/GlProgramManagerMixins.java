package com.radiance.mixins.vulkan_render_integration;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.GlProgramManager;
import net.minecraft.client.gl.ShaderProgramSetupView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlProgramManager.class)
public class GlProgramManagerMixins {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);

    @Inject(method = "createProgram", at = @At("HEAD"), cancellable = true)
    private static void createProgramWithoutOpenGL(CallbackInfoReturnable<Integer> cir)
        throws IOException {
        cir.setReturnValue(NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement());
    }

    @Inject(method = "linkProgram", at = @At("HEAD"), cancellable = true)
    private static void linkProgramWithoutOpenGL(ShaderProgramSetupView program, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "deleteProgram", at = @At("HEAD"), cancellable = true)
    private static void deleteProgramWithoutOpenGL(ShaderProgramSetupView program, CallbackInfo ci) {
        program.getFragmentShader()
            .release();
        program.getVertexShader()
            .release();
        ci.cancel();
    }
}

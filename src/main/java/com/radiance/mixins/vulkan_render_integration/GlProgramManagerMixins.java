package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProgramManager.class)
public class GlProgramManagerMixins {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);

    @Inject(method = "createProgram()I", at = @At("HEAD"), cancellable = true)
    private static void createProgramWithoutOpenGL(CallbackInfoReturnable<Integer> cir)
        throws IOException {
        cir.setReturnValue(NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement());
    }

    @Inject(method = "linkShader(Lcom/mojang/blaze3d/shaders/Shader;)V", at = @At("HEAD"), cancellable = true)
    private static void linkProgramWithoutOpenGL(Shader program, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "releaseProgram(Lcom/mojang/blaze3d/shaders/Shader;)V", at = @At("HEAD"), cancellable = true)
    private static void deleteProgramWithoutOpenGL(Shader program, CallbackInfo ci) {
        program.getFragmentProgram()
            .close();
        program.getVertexProgram()
            .close();
        ci.cancel();
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.CompiledShader;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompiledShader.class)
public abstract class CompiledShaderMixins implements ICompiledShaderExt {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_SHADER_ID = new AtomicInteger(1);
    @Unique
    private static final Constructor<CompiledShader> CONSTRUCTOR = createConstructor();

    @Shadow
    private int shaderId;

    @Unique
    private String radiance$resolvedSource;

    @Inject(method = "Lcom/mojang/blaze3d/shaders/CompiledShader;compile(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/shaders/CompiledShader$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/CompiledShader;", at = @At("HEAD"), cancellable = true)
    private static void compileWithoutOpenGL(ResourceLocation id, CompiledShader.Type type, String source,
        CallbackInfoReturnable<CompiledShader> cir) throws ShaderManager.CompilationException {
        try {
            CompiledShader shader = CONSTRUCTOR.newInstance(NEXT_VIRTUAL_SHADER_ID.getAndIncrement(),
                id);
            ICompiledShaderExt ext = (ICompiledShaderExt) (Object) shader;
            ext.radiance$setResolvedSource(source);
            cir.setReturnValue(shader);
        } catch (ReflectiveOperationException e) {
            throw new ShaderManager.CompilationException(
                "Failed to create virtual compiled shader: " + id);
        }
    }

    @Inject(method = "Lcom/mojang/blaze3d/shaders/CompiledShader;close()V", at = @At("HEAD"), cancellable = true)
    private void closeWithoutOpenGL(CallbackInfo ci) {
        if (this.shaderId == -1) {
            throw new IllegalStateException("Already closed");
        }
        this.shaderId = -1;
        ci.cancel();
    }

    @Override
    public String radiance$getResolvedSource() {
        return this.radiance$resolvedSource;
    }

    @Override
    public void radiance$setResolvedSource(String resolvedSource) {
        this.radiance$resolvedSource = resolvedSource;
    }

    @Unique
    private static Constructor<CompiledShader> createConstructor() {
        try {
            Constructor<CompiledShader> constructor = CompiledShader.class.getDeclaredConstructor(
                int.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access CompiledShader constructor", e);
        }
    }
}

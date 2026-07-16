package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.util.Identifier;
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
    private int handle;

    @Unique
    private String radiance$resolvedSource;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private static void compileWithoutOpenGL(Identifier id, CompiledShader.Type type, String source,
        CallbackInfoReturnable<CompiledShader> cir) throws ShaderLoader.LoadException {
        try {
            CompiledShader shader = CONSTRUCTOR.newInstance(NEXT_VIRTUAL_SHADER_ID.getAndIncrement(),
                id);
            ICompiledShaderExt ext = (ICompiledShaderExt) (Object) shader;
            ext.radiance$setResolvedSource(source);
            cir.setReturnValue(shader);
        } catch (ReflectiveOperationException e) {
            throw new ShaderLoader.LoadException(
                "Failed to create virtual compiled shader: " + id);
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void closeWithoutOpenGL(CallbackInfo ci) {
        if (this.handle == -1) {
            throw new IllegalStateException("Already closed");
        }
        this.handle = -1;
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
                int.class, Identifier.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access CompiledShader constructor", e);
        }
    }
}

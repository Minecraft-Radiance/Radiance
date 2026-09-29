package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.CompiledShader;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.ShaderProgramConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompiledShaderProgram.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);
    @Unique
    private static final Constructor<CompiledShaderProgram> CONSTRUCTOR = createConstructor();

    @Shadow
    @Final
    private List<ShaderProgramConfig.Sampler> samplers;

    @Shadow
    @Final
    private Object2IntMap<String> samplerTextures;

    @Shadow
    @Final
    private IntList samplerLocations;

    @Shadow
    @Final
    private List<Uniform> uniforms;

    @Shadow
    @Final
    private Map<String, Uniform> uniformsByName;

    @Shadow
    @Final
    private Map<String, ShaderProgramConfig.Uniform> uniformConfigs;

    @Shadow
    public Uniform MODEL_VIEW_MATRIX;

    @Shadow
    public Uniform PROJECTION_MATRIX;

    @Shadow
    public Uniform TEXTURE_MATRIX;

    @Shadow
    public Uniform SCREEN_SIZE;

    @Shadow
    public Uniform COLOR_MODULATOR;

    @Shadow
    public Uniform LIGHT0_DIRECTION;

    @Shadow
    public Uniform LIGHT1_DIRECTION;

    @Shadow
    public Uniform GLINT_ALPHA;

    @Shadow
    public Uniform FOG_START;

    @Shadow
    public Uniform FOG_END;

    @Shadow
    public Uniform FOG_COLOR;

    @Shadow
    public Uniform FOG_SHAPE;

    @Shadow
    public Uniform LINE_WIDTH;

    @Shadow
    public Uniform GAME_TIME;

    @Shadow
    public Uniform MODEL_OFFSET;

    @Shadow
    private Uniform parseUniformNode(ShaderProgramConfig.Uniform uniform) {
        throw new AssertionError();
    }

    @Unique
    private String radiance$shaderName;
    @Unique
    private VertexFormat radiance$vertexFormat;
    @Unique
    private String radiance$vertexSource;
    @Unique
    private String radiance$fragmentSource;
    @Unique
    private List<String> radiance$samplerNames = List.of();

    @Inject(method = "Lnet/minecraft/client/renderer/CompiledShaderProgram;link(Lcom/mojang/blaze3d/shaders/CompiledShader;Lcom/mojang/blaze3d/shaders/CompiledShader;Lcom/mojang/blaze3d/vertex/VertexFormat;)Lnet/minecraft/client/renderer/CompiledShaderProgram;", at = @At("HEAD"), cancellable = true)
    private static void createWithoutOpenGL(CompiledShader vertexShader,
        CompiledShader fragmentShader, VertexFormat format,
        CallbackInfoReturnable<CompiledShaderProgram> cir) throws ShaderManager.CompilationException {
        try {
            CompiledShaderProgram shaderProgram = CONSTRUCTOR.newInstance(
                NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement());
            IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
            ext.radiance$setVertexFormat(format);
            ext.radiance$setVertexSource(
                ((ICompiledShaderExt) (Object) vertexShader).radiance$getResolvedSource());
            ext.radiance$setFragmentSource(
                ((ICompiledShaderExt) (Object) fragmentShader).radiance$getResolvedSource());
            cir.setReturnValue(shaderProgram);
        } catch (ReflectiveOperationException e) {
            throw new ShaderManager.CompilationException("Could not create virtual shader program");
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/CompiledShaderProgram;setupUniforms(Ljava/util/List;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void setWithoutOpenGL(List<ShaderProgramConfig.Uniform> uniforms,
        List<ShaderProgramConfig.Sampler> samplers, CallbackInfo ci) {
        this.uniforms.clear();
        this.uniformsByName.clear();
        this.uniformConfigs.clear();
        this.samplers.clear();
        this.samplerLocations.clear();
        this.samplerTextures.clear();

        for (ShaderProgramConfig.Uniform uniform : uniforms) {
            Uniform glUniform = this.parseUniformNode(uniform);
            glUniform.setLocation(this.uniforms.size());
            this.uniforms.add(glUniform);
            this.uniformsByName.put(uniform.name(), glUniform);
            this.uniformConfigs.put(uniform.name(), uniform);
        }

        ArrayList<String> samplerNames = new ArrayList<>(samplers.size());
        for (int i = 0; i < samplers.size(); i++) {
            ShaderProgramConfig.Sampler sampler = samplers.get(i);
            this.samplers.add(sampler);
            this.samplerLocations.add(i);
            samplerNames.add(sampler.name());
        }
        this.radiance$samplerNames = List.copyOf(samplerNames);

        this.MODEL_VIEW_MATRIX = this.uniformsByName.get("ModelViewMat");
        this.PROJECTION_MATRIX = this.uniformsByName.get("ProjMat");
        this.TEXTURE_MATRIX = this.uniformsByName.get("TextureMat");
        this.SCREEN_SIZE = this.uniformsByName.get("ScreenSize");
        this.COLOR_MODULATOR = this.uniformsByName.get("ColorModulator");
        this.LIGHT0_DIRECTION = this.uniformsByName.get("Light0_Direction");
        this.LIGHT1_DIRECTION = this.uniformsByName.get("Light1_Direction");
        this.GLINT_ALPHA = this.uniformsByName.get("GlintAlpha");
        this.FOG_START = this.uniformsByName.get("FogStart");
        this.FOG_END = this.uniformsByName.get("FogEnd");
        this.FOG_COLOR = this.uniformsByName.get("FogColor");
        this.FOG_SHAPE = this.uniformsByName.get("FogShape");
        this.LINE_WIDTH = this.uniformsByName.get("LineWidth");
        this.GAME_TIME = this.uniformsByName.get("GameTime");
        this.MODEL_OFFSET = this.uniformsByName.get("ModelOffset");

        ci.cancel();
    }

    @Inject(method = "Lnet/minecraft/client/renderer/CompiledShaderProgram;apply()V", at = @At("HEAD"), cancellable = true)
    private void bindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "Lnet/minecraft/client/renderer/CompiledShaderProgram;clear()V", at = @At("HEAD"), cancellable = true)
    private void unbindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "Lnet/minecraft/client/renderer/CompiledShaderProgram;close()V", at = @At("HEAD"), cancellable = true)
    private void closeWithoutOpenGL(CallbackInfo ci) {
        this.uniforms.forEach(Uniform::close);
        ci.cancel();
    }

    @Override
    public String radiance$getShaderName() {
        return this.radiance$shaderName;
    }

    @Override
    public void radiance$setShaderName(String shaderName) {
        this.radiance$shaderName = shaderName;
    }

    @Override
    public VertexFormat radiance$getVertexFormat() {
        return this.radiance$vertexFormat;
    }

    @Override
    public void radiance$setVertexFormat(VertexFormat vertexFormat) {
        this.radiance$vertexFormat = vertexFormat;
    }

    @Override
    public String radiance$getVertexSource() {
        return this.radiance$vertexSource;
    }

    @Override
    public void radiance$setVertexSource(String vertexSource) {
        this.radiance$vertexSource = vertexSource;
    }

    @Override
    public String radiance$getFragmentSource() {
        return this.radiance$fragmentSource;
    }

    @Override
    public void radiance$setFragmentSource(String fragmentSource) {
        this.radiance$fragmentSource = fragmentSource;
    }

    @Override
    public List<String> radiance$getSamplerNamesValue() {
        return this.radiance$samplerNames;
    }

    @Override
    public List<Uniform> radiance$getUniformsValue() {
        return this.uniforms;
    }

    @Override
    public Object2IntMap<String> radiance$getSamplerTexturesValue() {
        return this.samplerTextures;
    }

    @Unique
    private static Constructor<CompiledShaderProgram> createConstructor() {
        try {
            Constructor<CompiledShaderProgram> constructor = CompiledShaderProgram.class.getDeclaredConstructor(
                int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access ShaderProgram constructor", e);
        }
    }
}

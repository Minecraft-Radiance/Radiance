package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramDefinition;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderProgram.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);
    @Unique
    private static final Constructor<ShaderProgram> CONSTRUCTOR = createConstructor();

    @Shadow
    @Final
    private List<ShaderProgramDefinition.Sampler> samplers;

    @Shadow
    @Final
    private Object2IntMap<String> samplerTextures;

    @Shadow
    @Final
    private IntList samplerLocations;

    @Shadow
    @Final
    private List<GlUniform> uniforms;

    @Shadow
    @Final
    private Map<String, GlUniform> uniformsByName;

    @Shadow
    @Final
    private Map<String, ShaderProgramDefinition.Uniform> uniformDefinitionsByName;

    @Shadow
    public GlUniform modelViewMat;

    @Shadow
    public GlUniform projectionMat;

    @Shadow
    public GlUniform textureMat;

    @Shadow
    public GlUniform screenSize;

    @Shadow
    public GlUniform colorModulator;

    @Shadow
    public GlUniform light0Direction;

    @Shadow
    public GlUniform light1Direction;

    @Shadow
    public GlUniform glintAlpha;

    @Shadow
    public GlUniform fogStart;

    @Shadow
    public GlUniform fogEnd;

    @Shadow
    public GlUniform fogColor;

    @Shadow
    public GlUniform fogShape;

    @Shadow
    public GlUniform lineWidth;

    @Shadow
    public GlUniform gameTime;

    @Shadow
    public GlUniform modelOffset;

    @Shadow
    private GlUniform createGlUniform(ShaderProgramDefinition.Uniform uniform) {
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

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void createWithoutOpenGL(CompiledShader vertexShader,
        CompiledShader fragmentShader, VertexFormat format,
        CallbackInfoReturnable<ShaderProgram> cir) throws ShaderLoader.LoadException {
        try {
            ShaderProgram shaderProgram = CONSTRUCTOR.newInstance(
                NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement());
            IShaderProgramExt ext = (IShaderProgramExt) (Object) shaderProgram;
            ext.radiance$setVertexFormat(format);
            ext.radiance$setVertexSource(
                ((ICompiledShaderExt) (Object) vertexShader).radiance$getResolvedSource());
            ext.radiance$setFragmentSource(
                ((ICompiledShaderExt) (Object) fragmentShader).radiance$getResolvedSource());
            cir.setReturnValue(shaderProgram);
        } catch (ReflectiveOperationException e) {
            throw new ShaderLoader.LoadException("Could not create virtual shader program");
        }
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void setWithoutOpenGL(List<ShaderProgramDefinition.Uniform> uniforms,
        List<ShaderProgramDefinition.Sampler> samplers, CallbackInfo ci) {
        this.uniforms.clear();
        this.uniformsByName.clear();
        this.uniformDefinitionsByName.clear();
        this.samplers.clear();
        this.samplerLocations.clear();
        this.samplerTextures.clear();

        for (ShaderProgramDefinition.Uniform uniform : uniforms) {
            GlUniform glUniform = this.createGlUniform(uniform);
            glUniform.setLocation(this.uniforms.size());
            this.uniforms.add(glUniform);
            this.uniformsByName.put(uniform.name(), glUniform);
            this.uniformDefinitionsByName.put(uniform.name(), uniform);
        }

        ArrayList<String> samplerNames = new ArrayList<>(samplers.size());
        for (int i = 0; i < samplers.size(); i++) {
            ShaderProgramDefinition.Sampler sampler = samplers.get(i);
            this.samplers.add(sampler);
            this.samplerLocations.add(i);
            samplerNames.add(sampler.name());
        }
        this.radiance$samplerNames = List.copyOf(samplerNames);

        this.modelViewMat = this.uniformsByName.get("ModelViewMat");
        this.projectionMat = this.uniformsByName.get("ProjMat");
        this.textureMat = this.uniformsByName.get("TextureMat");
        this.screenSize = this.uniformsByName.get("ScreenSize");
        this.colorModulator = this.uniformsByName.get("ColorModulator");
        this.light0Direction = this.uniformsByName.get("Light0_Direction");
        this.light1Direction = this.uniformsByName.get("Light1_Direction");
        this.glintAlpha = this.uniformsByName.get("GlintAlpha");
        this.fogStart = this.uniformsByName.get("FogStart");
        this.fogEnd = this.uniformsByName.get("FogEnd");
        this.fogColor = this.uniformsByName.get("FogColor");
        this.fogShape = this.uniformsByName.get("FogShape");
        this.lineWidth = this.uniformsByName.get("LineWidth");
        this.gameTime = this.uniformsByName.get("GameTime");
        this.modelOffset = this.uniformsByName.get("ModelOffset");

        ci.cancel();
    }

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true)
    private void bindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "unbind", at = @At("HEAD"), cancellable = true)
    private void unbindWithoutOpenGL(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void closeWithoutOpenGL(CallbackInfo ci) {
        this.uniforms.forEach(GlUniform::close);
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
    public List<GlUniform> radiance$getUniformsValue() {
        return this.uniforms;
    }

    @Override
    public Object2IntMap<String> radiance$getSamplerTexturesValue() {
        return this.samplerTextures;
    }

    @Unique
    private static Constructor<ShaderProgram> createConstructor() {
        try {
            Constructor<ShaderProgram> constructor = ShaderProgram.class.getDeclaredConstructor(
                int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access ShaderProgram constructor", e);
        }
    }
}

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
import com.mojang.blaze3d.shaders.CompiledShader;
import com.mojang.blaze3d.opengl.Uniform;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderProgramConfig;
import com.mojang.blaze3d.vertex.VertexFormat;
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
    private Map<String, ShaderProgramConfig.Uniform> uniformDefinitionsByName;

    @Shadow
    public Uniform modelViewMat;

    @Shadow
    public Uniform projectionMat;

    @Shadow
    public Uniform textureMat;

    @Shadow
    public Uniform screenSize;

    @Shadow
    public Uniform colorModulator;

    @Shadow
    public Uniform light0Direction;

    @Shadow
    public Uniform light1Direction;

    @Shadow
    public Uniform glintAlpha;

    @Shadow
    public Uniform fogStart;

    @Shadow
    public Uniform fogEnd;

    @Shadow
    public Uniform fogColor;

    @Shadow
    public Uniform fogShape;

    @Shadow
    public Uniform lineWidth;

    @Shadow
    public Uniform gameTime;

    @Shadow
    public Uniform modelOffset;

    @Shadow
    private Uniform createGlUniform(ShaderProgramConfig.Uniform uniform) {
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
        CallbackInfoReturnable<CompiledShaderProgram> cir) throws ShaderManager.LoadException {
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
            throw new ShaderManager.LoadException("Could not create virtual shader program");
        }
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void setWithoutOpenGL(List<ShaderProgramConfig.Uniform> uniforms,
        List<ShaderProgramConfig.Sampler> samplers, CallbackInfo ci) {
        this.uniforms.clear();
        this.uniformsByName.clear();
        this.uniformDefinitionsByName.clear();
        this.samplers.clear();
        this.samplerLocations.clear();
        this.samplerTextures.clear();

        for (ShaderProgramConfig.Uniform uniform : uniforms) {
            Uniform glUniform = this.createGlUniform(uniform);
            glUniform.setLocation(this.uniforms.size());
            this.uniforms.add(glUniform);
            this.uniformsByName.put(uniform.name(), glUniform);
            this.uniformDefinitionsByName.put(uniform.name(), uniform);
        }

        ArrayList<String> samplerNames = new ArrayList<>(samplers.size());
        for (int i = 0; i < samplers.size(); i++) {
            ShaderProgramConfig.Sampler sampler = samplers.get(i);
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
            throw new RuntimeException("Failed to access CompiledShaderProgram constructor", e);
        }
    }
}

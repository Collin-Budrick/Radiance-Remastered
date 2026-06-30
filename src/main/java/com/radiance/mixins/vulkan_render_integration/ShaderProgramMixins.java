package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RendererAvailability;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlProgram.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Shadow
    public abstract Map<String, Uniform> getUniforms();

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

    @Unique
    private final Object2IntMap<String> radiance$samplerTextures = new Object2IntOpenHashMap<>();

    @Unique
    private boolean radiance$virtualProgram;

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void closeVirtualProgramWithoutOpenGL(CallbackInfo ci) {
        if (!RendererAvailability.shouldOwnRendererLifecycle() || !this.radiance$virtualProgram) {
            return;
        }
        this.getUniforms().values().forEach(Uniform::close);
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
    public void radiance$setSamplerNamesValue(List<String> samplerNames) {
        this.radiance$samplerNames = List.copyOf(samplerNames);
    }

    @Override
    public List<Uniform> radiance$getUniformsValue() {
        return List.copyOf(this.getUniforms().values());
    }

    @Override
    public Object2IntMap<String> radiance$getSamplerTexturesValue() {
        return this.radiance$samplerTextures;
    }

    @Override
    public boolean radiance$isVirtualProgram() {
        return this.radiance$virtualProgram;
    }

    @Override
    public void radiance$setVirtualProgram(boolean virtualProgram) {
        this.radiance$virtualProgram = virtualProgram;
    }
}

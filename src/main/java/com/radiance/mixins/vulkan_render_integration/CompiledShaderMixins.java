package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlShaderModule;
import com.radiance.client.RendererAvailability;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlShaderModule.class)
public abstract class CompiledShaderMixins implements ICompiledShaderExt {

    @Shadow
    private int shaderId;

    @Unique
    private String radiance$resolvedSource;

    @Unique
    private boolean radiance$virtualShader;

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void closeVirtualShaderWithoutOpenGL(CallbackInfo ci) {
        if (!RendererAvailability.shouldOwnRendererLifecycle() || !this.radiance$virtualShader) {
            return;
        }
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

    @Override
    public boolean radiance$isVirtualShader() {
        return this.radiance$virtualShader;
    }

    @Override
    public void radiance$setVirtualShader(boolean virtualShader) {
        this.radiance$virtualShader = virtualShader;
    }
}

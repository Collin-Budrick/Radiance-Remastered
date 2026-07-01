package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.radiance.client.proxy.vulkan.BufferProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
    "com.mojang.blaze3d.opengl.GlBuffer$Direct",
    "com.mojang.blaze3d.vulkan.VulkanGpuBuffer$Direct"
})
public class GpuBufferCloseMixins {

    @Inject(method = "close()V", at = @At("HEAD"))
    private void radiance$invalidateNativeBufferMirror(CallbackInfo ci) {
        BufferProxy.invalidateNativeBufferMirror((GpuBuffer) (Object) this, "GpuBuffer.close");
    }
}

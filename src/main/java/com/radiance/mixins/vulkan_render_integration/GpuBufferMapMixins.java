package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.proxy.vulkan.BufferProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
    "com.mojang.blaze3d.opengl.GlBuffer$Direct",
    "com.mojang.blaze3d.vulkan.VulkanGpuBuffer$Direct"
})
public class GpuBufferMapMixins {

    @Inject(method = "map(JJZZ)Lcom/mojang/blaze3d/buffers/GpuBufferSlice$MappedView;",
        at = @At("RETURN"))
    private void radiance$trackWriteMappedView(long offset, long length, boolean read,
        boolean write, CallbackInfoReturnable<GpuBufferSlice.MappedView> cir) {
        BufferProxy.trackMappedView(cir.getReturnValue(), write);
    }
}

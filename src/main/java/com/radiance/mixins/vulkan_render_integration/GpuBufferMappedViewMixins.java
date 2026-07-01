package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.proxy.vulkan.BufferProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GpuBufferSlice.MappedView.class)
public class GpuBufferMappedViewMixins {

    @Inject(method = "close()V", at = @At("HEAD"))
    private void radiance$mirrorMappedViewBeforeClose(CallbackInfo ci) {
        BufferProxy.mirrorMappedView((GpuBufferSlice.MappedView) (Object) this);
    }
}

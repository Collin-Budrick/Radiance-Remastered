package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.radiance.client.proxy.vulkan.BufferProxy;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandEncoder.class)
public class CommandEncoderBufferMixins {

    @Inject(method = "writeToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V",
        at = @At("TAIL"))
    private void radiance$mirrorWriteToBuffer(GpuBufferSlice target, ByteBuffer source,
        CallbackInfo ci) {
        BufferProxy.mirrorWriteToBuffer(target, source);
    }

    @Inject(method = "copyToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
        at = @At("TAIL"))
    private void radiance$mirrorCopyToBuffer(GpuBufferSlice source, GpuBufferSlice target,
        CallbackInfo ci) {
        BufferProxy.mirrorCopyToBuffer(source, target);
    }
}

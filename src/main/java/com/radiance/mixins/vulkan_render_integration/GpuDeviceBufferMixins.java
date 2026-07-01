package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.radiance.client.proxy.vulkan.BufferProxy;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GpuDevice.class)
public class GpuDeviceBufferMixins {

    private static final ThreadLocal<byte[]> RADIANCE_INITIAL_BUFFER_BYTES = new ThreadLocal<>();

    @Inject(method = "createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
        at = @At("RETURN"))
    private void radiance$mirrorSizedBuffer(Supplier<String> label, int usage, long size,
        CallbackInfoReturnable<GpuBuffer> cir) {
        BufferProxy.mirrorCreatedGpuBuffer(cir.getReturnValue(), usage, size, null,
            "GpuDevice.createBuffer(size)");
    }

    @Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
        at = @At("HEAD"))
    private void radiance$captureInitialBufferBytes(Supplier<String> label, int usage,
        ByteBuffer source, CallbackInfoReturnable<GpuBuffer> cir) {
        if (source == null || !source.hasRemaining()) {
            RADIANCE_INITIAL_BUFFER_BYTES.remove();
            return;
        }
        ByteBuffer view = source.slice();
        byte[] bytes = new byte[view.remaining()];
        view.get(bytes);
        RADIANCE_INITIAL_BUFFER_BYTES.set(bytes);
    }

    @Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
        at = @At("RETURN"))
    private void radiance$mirrorInitialBuffer(Supplier<String> label, int usage,
        ByteBuffer source, CallbackInfoReturnable<GpuBuffer> cir) {
        try {
            GpuBuffer buffer = cir.getReturnValue();
            byte[] bytes = RADIANCE_INITIAL_BUFFER_BYTES.get();
            ByteBuffer replaySource = bytes == null ? null : ByteBuffer.wrap(bytes);
            long size = buffer == null
                ? bytes == null ? source == null ? 0L : source.remaining() : bytes.length
                : buffer.size();
            BufferProxy.mirrorCreatedGpuBuffer(buffer, usage, size, replaySource,
                "GpuDevice.createBuffer(ByteBuffer)");
        } finally {
            RADIANCE_INITIAL_BUFFER_BYTES.remove();
        }
    }
}

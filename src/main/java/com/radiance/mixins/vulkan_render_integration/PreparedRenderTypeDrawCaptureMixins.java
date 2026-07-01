package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.radiance.client.render.submit.replay.RenderPassDrawPacket;
import com.radiance.client.render.submit.replay.RenderPassPacketCapture;
import com.radiance.client.render.submit.replay.RenderPassReplayBridge;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PreparedRenderType.class)
public class PreparedRenderTypeDrawCaptureMixins {

    @Inject(method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
        at = @At("HEAD"),
        cancellable = true)
    private void radiance$captureDrawFromBuffer(GpuBuffer vertexBuffer, GpuBuffer indexBuffer,
        IndexType indexType, int baseVertex, int firstIndex, int indexCount, CallbackInfo ci) {
        RenderPassDrawPacket packet = RenderPassPacketCapture.captureDraw(
            (PreparedRenderType) (Object) this, vertexBuffer, indexBuffer, indexType,
            baseVertex, firstIndex, indexCount);
        if (RenderPassReplayBridge.tryReplay(packet)) {
            ci.cancel();
        }
    }
}

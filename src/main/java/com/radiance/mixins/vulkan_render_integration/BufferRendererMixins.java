package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StagedVertexBuffer.class)
public class BufferRendererMixins {

    @Inject(method = "upload()V", at = @At(value = "TAIL"))
    private void radiance$afterStagedUpload(CallbackInfo ci) {
        // Retired stub: keep excluded unless a real staged-buffer capture is restored.
        // Upstream BufferRenderer.drawWithGlobalProgram(BuiltBuffer)V is gone in 26.2.
        // StagedVertexBuffer.upload()V is the surviving upload boundary.
    }
}

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
        // 26.2 removed BufferUploader.drawWithGlobalProgram. This hook marks the new
        // staged vertex upload point for Radiance's buffer bridge.
    }
}

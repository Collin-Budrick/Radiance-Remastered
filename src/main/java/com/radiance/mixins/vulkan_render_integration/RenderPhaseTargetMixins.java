package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.ScissorState;
import com.radiance.client.renderpass.RenderPassPipelineStateBridge;
import java.util.List;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PreparedRenderType.class)
public abstract class RenderPhaseTargetMixins {

    @Inject(method = "<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/renderer/rendertype/OutputTarget;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/systems/ScissorState;Ljava/util/List;)V",
        at = @At("RETURN"))
    private void radiance$capturePreparedOnly(RenderPipeline pipeline, OutputTarget outputTarget,
        GpuBufferSlice dynamicTransforms, ScissorState scissorState,
        List<PreparedRenderType.Texture> textures, CallbackInfo ci) {
        if (RenderPassPipelineStateBridge.shouldCapture()) {
            RenderPassPipelineStateBridge.rememberPreparedOnly((PreparedRenderType) (Object) this);
        }
    }
}

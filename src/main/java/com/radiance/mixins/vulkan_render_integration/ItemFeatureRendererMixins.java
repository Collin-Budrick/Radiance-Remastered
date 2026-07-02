package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.submit.replay.ItemSubmitMetadataBridge;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixins {

    @Inject(method =
        "getFoilBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;" +
            "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)" +
        "Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At("RETURN"))
    private void radiance$captureFoilVertexConsumer(RenderType itemRenderType,
        PoseStack.Pose foilDecalPose, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemSubmitMetadataBridge.observeFoilVertexConsumer(itemRenderType, null,
            cir.getReturnValue(), foilDecalPose != null);
    }
}

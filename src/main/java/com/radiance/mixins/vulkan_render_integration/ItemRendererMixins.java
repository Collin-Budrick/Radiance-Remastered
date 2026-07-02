package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.render.submit.replay.ItemSubmitMetadataBridge;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemRendererMixins {

    @Redirect(method =
        "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;" +
                    "Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void radiance$submitItemBridge(SubmitNodeCollector collector, PoseStack poseStack,
        ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
        int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        if (ItemSubmitMetadataBridge.tryReplay(poseStack, displayContext, lightCoords,
            overlayCoords, outlineColor, tintLayers, quads, foilType)) {
            return;
        }

        collector.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
            tintLayers, quads, foilType);
    }
}

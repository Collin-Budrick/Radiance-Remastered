package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemRendererMixins {

    @Unique
    private static boolean radiance$itemBridgeLogged;

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
        if (RendererAvailability.isRendererLifecycleActive()) {
            radiance$logMissingItemReplayOnce(displayContext, lightCoords, overlayCoords,
                outlineColor, tintLayers, quads, foilType);
        }

        collector.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
            tintLayers, quads, foilType);
    }

    @Unique
    private static void radiance$logMissingItemReplayOnce(ItemDisplayContext displayContext,
        int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
        List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        if (radiance$itemBridgeLogged) {
            return;
        }
        radiance$itemBridgeLogged = true;
        RadianceClient.LOGGER.warn(
            "Radiance item bridge: observed 26.2 item submit metadata, but native item replay is "
                + "not wired yet; falling back to vanilla submit. displayContext={}, quads={}, "
                + "tints={}, foilType={}, light={}, overlay={}, outlineColor={}. PBR glint "
                + "wrapping needs a VertexConsumer, which this submit-node path does not expose.",
            displayContext, quads == null ? 0 : quads.size(),
            tintLayers == null ? 0 : tintLayers.length, foilType, lightCoords, overlayCoords,
            outlineColor);
    }
}

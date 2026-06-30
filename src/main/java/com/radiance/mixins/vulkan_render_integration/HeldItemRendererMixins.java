package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    public abstract void submitHandsWithItems(float tickDelta,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light);

    @Override
    public void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light) {
        this.submitHandsWithItems(tickDelta, matrices, submitNodeCollector, player, light);
    }
}

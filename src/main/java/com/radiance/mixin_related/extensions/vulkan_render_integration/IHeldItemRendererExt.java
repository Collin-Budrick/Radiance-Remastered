package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;

public interface IHeldItemRendererExt {

    void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light);
}

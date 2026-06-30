package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    protected abstract void submitArmWithItem(AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light);

    @Override
    public void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light) {
        float f = player.getAttackAnim(tickDelta);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        float g = player.getViewXRot(tickDelta);
        float h = player.getViewXRot(tickDelta);
        float i = player.getViewYRot(tickDelta);
        matrices.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(tickDelta) - h) * 0.1F));
        matrices.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(tickDelta) - i) * 0.1F));
        {
            float j = hand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oMainHandHeight,
                this.mainHandHeight);
            this.submitArmWithItem(player, tickDelta, g, InteractionHand.MAIN_HAND, j, this.mainHandItem, k,
                matrices, submitNodeCollector, light);
        }

        {
            float j = hand == InteractionHand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oOffHandHeight,
                this.offHandHeight);
            this.submitArmWithItem(player, tickDelta, g, InteractionHand.OFF_HAND, j, this.offHandItem, k,
                matrices, submitNodeCollector, light);
        }
    }
}

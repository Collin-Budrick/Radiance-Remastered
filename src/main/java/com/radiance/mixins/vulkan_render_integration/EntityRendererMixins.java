package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins {

    @Redirect(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            ordinal = 0))
    private void radiance$redirectScoreNameTag(SubmitNodeCollector submitNodeCollector,
        PoseStack matrices,
        Vec3 attachment,
        int yOffset,
        Component text,
        boolean seeThrough,
        int light,
        CameraRenderState cameraRenderState) {
        radiance$nameTagCollector(submitNodeCollector)
            .submitNameTag(matrices, attachment, yOffset, text, seeThrough, light, cameraRenderState);
    }

    @Redirect(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            ordinal = 1))
    private void radiance$redirectNameTag(SubmitNodeCollector submitNodeCollector,
        PoseStack matrices,
        Vec3 attachment,
        int yOffset,
        Component text,
        boolean seeThrough,
        int light,
        CameraRenderState cameraRenderState) {
        radiance$nameTagCollector(submitNodeCollector)
            .submitNameTag(matrices, attachment, yOffset, text, seeThrough, light, cameraRenderState);
    }

    @Unique
    private static SubmitNodeCollector radiance$nameTagCollector(
        SubmitNodeCollector submitNodeCollector) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return submitNodeCollector;
        }

        Object postTextSubmitNodeCollector = EntityProxy.postTextVertexConsumerProvider;
        if (postTextSubmitNodeCollector instanceof SubmitNodeCollector collector) {
            return collector;
        }
        return submitNodeCollector;
    }
}

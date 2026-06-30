package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RendererAvailability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRenderDispatcherMixins {

    @Inject(method = "extractShadow(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void radiance$cancelExtractShadow(EntityRenderState state,
        Minecraft minecraft,
        Level level,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        state.shadowPieces.clear();
        state.shadowRadius = 0.0F;
        ci.cancel();
    }
}

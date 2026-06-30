package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixins {

    @Inject(method =
        "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
        at = @At(value = "HEAD"))
    private void radiance$onCloudRender(int color, CloudStatus cloudStatus, float bottomY,
        int range, Vec3 cameraPosition, long gameTime, float partialTicks, CallbackInfo ci) {
        // Retired stub: vanilla 26.2 cloud RenderPipelines still own this path.
        // 26.2 renders encoded cloud faces through RenderPipelines; do not cancel vanilla here.
    }
}

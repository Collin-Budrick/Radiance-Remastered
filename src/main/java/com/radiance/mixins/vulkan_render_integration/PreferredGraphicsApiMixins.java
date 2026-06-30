package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.radiance.client.RendererAvailability;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
public class PreferredGraphicsApiMixins {

    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void radiance$preferVulkanBackend(CallbackInfoReturnable<GpuBackend[]> cir) {
        if (RendererAvailability.shouldOwnRendererLifecycle()) {
            cir.setReturnValue(new GpuBackend[]{new VulkanBackend(), new GlBackend()});
        }
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixins {

    @Unique
    private static boolean radiance$loggedBlurSkip = false;

    @Inject(method = "extractBlurredBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
        at = @At("HEAD"), cancellable = true)
    private void radiance$skipVanillaBlurFramebuffer(GuiGraphicsExtractor context,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        if (!radiance$loggedBlurSkip) {
            radiance$loggedBlurSkip = true;
            RadianceClient.LOGGER.info(
                "Radiance screen bridge: skipped vanilla 26.2 blur framebuffer path while native renderer owns the frame");
        }
        ci.cancel();
    }
}

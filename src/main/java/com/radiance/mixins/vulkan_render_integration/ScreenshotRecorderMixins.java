package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotRecorderMixins {

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(RenderTarget framebuffer,
        Consumer<NativeImage> callback,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        callback.accept(RendererProxy.takeScreenshotWithoutUI());
        ci.cancel();
    }

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(RenderTarget framebuffer,
        int downscale,
        Consumer<NativeImage> callback,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        callback.accept(RendererProxy.takeScreenshotWithoutUI());
        ci.cancel();
    }
}

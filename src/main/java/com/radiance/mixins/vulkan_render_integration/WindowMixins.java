package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.WindowProxy;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixins {

    @Inject(method = "createGlfwWindow(IILjava/lang/String;JLcom/mojang/blaze3d/systems/GpuBackend;)J",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 0,
            shift = At.Shift.BEFORE),
        remap = false)
    private static void radiance$useVulkanWindow(int width, int height, String title, long monitor,
        GpuBackend backend, CallbackInfoReturnable<Long> cir) {
        if (RendererAvailability.shouldOwnRendererLifecycle()) {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        }
    }

    @Inject(method = "onFramebufferResize(JII)V", at = @At("TAIL"))
    private void radiance$framebufferResized(long window, int width, int height, CallbackInfo ci) {
        if (RendererAvailability.isRendererLifecycleActive()) {
            WindowProxy.onFramebufferSizeChanged();
        }
    }
}

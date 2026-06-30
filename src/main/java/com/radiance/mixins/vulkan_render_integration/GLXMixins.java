package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlDebug;
import com.mojang.blaze3d.opengl.GlDevice;
import com.radiance.client.RendererAvailability;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GlDevice.class)
public class GLXMixins {

    @Redirect(method =
        "<init>(JLcom/mojang/blaze3d/shaders/ShaderSource;"
            + "Lcom/mojang/blaze3d/shaders/GpuDebugOptions;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlDebug;enableDebugCallback"
                + "(IZLjava/util/Set;)Lcom/mojang/blaze3d/opengl/GlDebug;"))
    private static GlDebug radiance$suppressOpenGlDebugCallback(int logLevel,
        boolean synchronousLogs, Set<String> enabledExtensions) {
        if (RendererAvailability.shouldOwnRendererLifecycle()) {
            return null;
        }

        return GlDebug.enableDebugCallback(logLevel, synchronousLogs, enabledExtensions);
    }
}

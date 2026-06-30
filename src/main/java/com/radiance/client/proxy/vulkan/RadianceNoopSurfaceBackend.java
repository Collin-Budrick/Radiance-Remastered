package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.EnumSet;
import java.util.Set;

public final class RadianceNoopSurfaceBackend implements GpuSurfaceBackend {

    private static final Set<GpuSurface.PresentMode> SUPPORTED_PRESENT_MODES =
        EnumSet.allOf(GpuSurface.PresentMode.class);

    @Override
    public void configure(GpuSurface.Configuration configuration) {
        // Radiance owns the real swapchain; this surface only satisfies Minecraft's 26.2 lifecycle.
    }

    @Override
    public boolean isSuboptimal() {
        return false;
    }

    @Override
    public void acquireNextTexture() {
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend encoder, GpuTextureView textureView) {
    }

    @Override
    public void present() {
    }

    @Override
    public void close() {
    }

    @Override
    public Set<GpuSurface.PresentMode> supportedPresentModes() {
        return SUPPORTED_PRESENT_MODES;
    }
}

package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTextureView;

public interface IOverlayTextureExt {

    GpuTextureView radiance$getTextureView();
}

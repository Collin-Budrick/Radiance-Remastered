package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.platform.NativeImage;

public interface ISpriteContentsImagesExt {

    NativeImage[] radiance$getMipImages();
}

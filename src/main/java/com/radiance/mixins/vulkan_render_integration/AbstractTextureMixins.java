package com.radiance.mixins.vulkan_render_integration;

// Retired in 26.2: AbstractTexture no longer owns GL texture ids or GL bind/filter/clamp
// behavior. GpuTexture allocation is tracked by GpuDeviceTextureMixins, uploads are mirrored
// by CommandEncoderTextureMixins, and atlas writes are cached by TextureAtlasUploadMixins.
// Keep this source excluded while the old upstream GL-id bridge remains retired.
public final class AbstractTextureMixins {

    private AbstractTextureMixins() {
    }
}

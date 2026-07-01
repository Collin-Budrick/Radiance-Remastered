package com.radiance.mixins.vulkan_render_integration;

// Retired in 26.2: TextureUtil no longer owns texture ids or image allocation.
// Allocation is tracked at GpuDevice.createTexture(...), and uploads are mirrored at
// CommandEncoder.writeToTexture(...). Keep this source excluded; the old hooks targeted
// missing generateTextureId()/prepareImage(...) methods.
public final class TextureUtilMixins {

    private TextureUtilMixins() {
    }
}

package com.radiance.mixins.vanilla_resource_tracker;

// Retired in 26.2: TextureUtil no longer owns texture ids or image allocation.
// Texture tracking now lives on GpuDevice.createTexture(...), CommandEncoder.writeToTexture(...),
// and sprite/atlas upload bridges. Keep this source excluded instead of installing a no-op mixin.
public final class TextureUtilMixins {

    private TextureUtilMixins() {
    }
}

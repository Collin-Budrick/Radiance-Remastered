package com.radiance.mixins.vanilla_resource_tracker;

// Retired in 26.2: do not add this class to radiance.mixins.json.
// The descriptor-correct replacement is vulkan_render_integration.TextureAtlasUploadMixins on
// TextureAtlas.upload(SpriteLoader.Preparations), with native work deferred by TextureUploadReplay.
// Keep this source build-excluded so the old vanilla tracker entry cannot install a no-op mixin.
public final class SpriteAtlasTextureMixins {

    private SpriteAtlasTextureMixins() {
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ISpriteContentsImagesExt;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsImagesMixins implements ISpriteContentsImagesExt {

    @Shadow
    private NativeImage[] byMipLevel;

    @Override
    public NativeImage[] radiance$getMipImages() {
        return byMipLevel;
    }
}

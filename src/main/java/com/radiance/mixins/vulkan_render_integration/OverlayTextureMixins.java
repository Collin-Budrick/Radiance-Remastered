package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins implements IOverlayTextureExt {

    @Shadow
    public abstract GpuTextureView getTextureView();

    @Override
    public GpuTextureView radiance$getTextureView() {
        return this.getTextureView();
    }
}

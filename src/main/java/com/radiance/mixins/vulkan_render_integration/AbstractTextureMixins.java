package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IAbstractTextureExt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractTexture.class)
public class AbstractTextureMixins implements IAbstractTextureExt {

    @Shadow
    protected GpuTexture texture;

    @Override
    public int radiance$getGlIDUnsafe() {
        if (this.texture == null || this.texture.isClosed()) {
            throw new IllegalStateException("texture is not initialized");
        }

        int id = TextureTracker.getOrRegisterGpuTexture(this.texture);
        if (id == 0) {
            throw new IllegalStateException("texture is not mirrored");
        }
        return id;
    }
}

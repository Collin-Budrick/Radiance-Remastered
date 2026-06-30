package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.texture.TextureTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.SpecialGlyphs$1")
public abstract class BuiltinEmptyGlyphMixins {

    @Inject(method = "upload(IILcom/mojang/blaze3d/textures/GpuTexture;)V", at = @At("HEAD"))
    private void radiance$trackSpecialGlyphUpload(int x, int y, GpuTexture texture,
        CallbackInfo ci) {
        TextureTracker.getOrRegisterGuiTexture(texture);
    }
}

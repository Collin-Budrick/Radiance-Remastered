package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.texture.TextureAtlasUploadBridge;
import java.util.List;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasUploadMixins {

    @Shadow
    @Final
    private Identifier location;

    @Shadow
    private List<TextureAtlasSprite> sprites;

    @Inject(method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",
        at = @At("RETURN"))
    private void radiance$captureAtlasUpload(SpriteLoader.Preparations preparations,
        CallbackInfo ci) {
        GpuTexture texture = ((AbstractTexture) (Object) this).getTexture();
        TextureAtlasUploadBridge.captureAtlas(location, texture, sprites);
    }
}

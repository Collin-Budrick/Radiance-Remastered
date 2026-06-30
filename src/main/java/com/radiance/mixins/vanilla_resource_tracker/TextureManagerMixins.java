package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixins {

    @Inject(method = "register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
        at = @At("HEAD"))
    private void profileTextureRegister(Identifier id, AbstractTexture texture, CallbackInfo ci) {
        try {
            TextureTracker.registerTextureIdentifier(id, texture.getTexture());
        } catch (IllegalStateException ignored) {
            // 26.2 registers some texture placeholders before their GpuTexture exists.
        }
    }
}

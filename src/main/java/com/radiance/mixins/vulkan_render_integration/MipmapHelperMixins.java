package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MipmapGenerator.class)
public class MipmapHelperMixins {

    @Inject(method = "generateMipLevels(Lnet/minecraft/resources/Identifier;[Lcom/mojang/blaze3d/platform/NativeImage;ILnet/minecraft/client/renderer/texture/MipmapStrategy;FLcom/mojang/blaze3d/platform/Transparency;)[Lcom/mojang/blaze3d/platform/NativeImage;",
        at = @At("RETURN"))
    private static void addIdentifier(Identifier identifier, NativeImage[] originals, int mipmap,
        MipmapStrategy strategy, float alphaCutoffBias, Transparency transparency,
        CallbackInfoReturnable<NativeImage[]> cir) {
        if (originals.length == 0) {
            return;
        }

        Identifier sourceIdentifier =
            ((INativeImageExt) (Object) originals[0]).radiance$getIdentifier();
        for (NativeImage nativeImage : cir.getReturnValue()) {
            ((INativeImageExt) (Object) nativeImage).radiance$setIdentifier(sourceIdentifier);
        }
    }
}

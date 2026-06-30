package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandEncoder.class)
public class CommandEncoderTextureMixins {

    @Inject(method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIII)V",
        at = @At("HEAD"))
    private void mirrorNativeImageUpload(GpuTexture texture, NativeImage image, int mipLevel,
        int dstX, int dstY, int unused, CallbackInfo ci) {
        if (texture == null || image == null) {
            return;
        }

        com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt imageExt =
            (com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt) (Object) image;
        Identifier identifier = imageExt.radiance$getIdentifier();
        TextureTracker.rememberTextureIdentifier(identifier, texture);

        boolean fullUploadAlreadyMirrored = imageExt.radiance$consumeCommandEncoderMirrorSkip();
        if (!TextureTracker.shouldMirrorTextures() || fullUploadAlreadyMirrored) {
            return;
        }

        TextureTracker.registerTextureIdentifier(identifier, texture);

        Integer trackedTargetId = TextureTracker.GPU_TEXTURE2GLID.get(texture);
        int targetId = imageExt.radiance$getTargetID();
        if (targetId <= 0 || trackedTargetId == null || trackedTargetId != targetId) {
            targetId = TextureTracker.shouldAllowSmallTexture(identifier, texture)
                ? TextureTracker.getOrRegisterGuiTexture(texture)
                : TextureTracker.getOrRegisterGpuTexture(texture);
        }
        if (targetId == 0) {
            return;
        }
        imageExt.radiance$setTargetID(targetId);

        AuxiliaryTextures.loadAndUpload(image, texture, mipLevel, dstX, dstY);

        long pointer = ((INativeImageExt) (Object) image).radiance$getPointer();
        int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();
        TextureProxy.queueUpload(pointer, srcSizeInBytes, image.getWidth(), targetId, 0, 0, dstX,
            dstY, image.getWidth(), image.getHeight(), mipLevel);
    }
}

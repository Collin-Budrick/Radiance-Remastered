package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.TextureUploadReplay;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixins {

    @Shadow
    public abstract GpuTexture getTexture();

    @Unique
    protected void radiance$mirrorFullImageUpload(NativeImage image, int mipLevel, int dstX,
        int dstY, boolean allowSmallTexture) {
        GpuTexture texture = getTexture();
        if (texture == null || image == null) {
            return;
        }

        com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt imageExt =
            (com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt) (Object) image;
        Identifier identifier = imageExt.radiance$getIdentifier();
        TextureTracker.rememberTextureIdentifier(identifier, texture);

        if (!RendererAvailability.isRendererLifecycleActive()
            || !TextureTracker.shouldMirrorTextures()) {
            TextureUploadReplay.capture(texture, identifier, image, mipLevel, dstX, dstY);
            return;
        }

        TextureTracker.registerTextureIdentifier(identifier, texture);
        int targetId = allowSmallTexture || TextureTracker.shouldAllowSmallTexture(identifier,
            texture)
            ? TextureTracker.getOrRegisterGuiTexture(texture)
            : TextureTracker.getOrRegisterGpuTexture(texture);
        if (targetId == 0) {
            return;
        }

        imageExt.radiance$setTargetID(targetId);
        AuxiliaryTextures.loadAndUpload(image, texture, mipLevel, dstX, dstY);

        long pointer =
            ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) image).radiance$getPointer();
        int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();
        TextureProxy.queueUpload(pointer, srcSizeInBytes, image.getWidth(), targetId, 0, 0, dstX,
            dstY, image.getWidth(), image.getHeight(), mipLevel);
        imageExt.radiance$setCommandEncoderMirrorSkip(true);
    }
}

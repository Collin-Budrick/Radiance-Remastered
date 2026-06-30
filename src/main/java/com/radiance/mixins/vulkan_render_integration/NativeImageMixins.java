package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class NativeImageMixins implements
    com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt {

    @Final
    @Shadow
    private int width;

    @Final
    @Shadow
    private NativeImage.Format format;

    @Final
    @Shadow
    private int height;

    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void closeImage(CallbackInfo ci) {
        com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt self =
            (com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt) this;
        NativeImage specularImage = self.radiance$getSpecularNativeImage();
        NativeImage normalImage = self.radiance$getNormalNativeImage();
        NativeImage flagImage = self.radiance$getFlagNativeImage();
        if (specularImage != null) {
            specularImage.close();
        }
        if (normalImage != null) {
            normalImage.close();
        }
        if (flagImage != null) {
            flagImage.close();
        }
    }

    @Override
    public NativeImage radiance$alignTo(NativeImage source) {
        int targetWidth = source.getWidth();
        int targetHeight = source.getHeight();
        NativeImage.Format targetFormat = source.format();

        if (width == targetWidth && height == targetHeight && format == targetFormat) {
            return (NativeImage) (Object) this;
        }

        NativeImage dest = new NativeImage(targetFormat, targetWidth, targetHeight, false);

        int srcChannels = this.format.components();
        int destChannels = targetFormat.components();
        int commonChannels = Math.min(srcChannels, destChannels);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int sampleX = (x < this.width) ? x : (x % this.width);
                int sampleY = (y < this.height) ? y : (y % this.height);

                long srcPixelPtr =
                    this.getPointer() + (sampleX + (long) sampleY * this.width) * srcChannels;
                long destPixelPtr =
                    dest.getPointer() + (long) (x + (long) y * targetWidth) * destChannels;

                for (int c = 0; c < commonChannels; c++) {
                    byte val = MemoryUtil.memGetByte(srcPixelPtr + c);
                    MemoryUtil.memPutByte(destPixelPtr + c, val);
                }

                if (destChannels > srcChannels) {
                    for (int c = srcChannels; c < destChannels; c++) {
                        MemoryUtil.memPutByte(destPixelPtr + c, (byte) 0);
                    }
                }
            }
        }
        return dest;
    }

    @Override
    public long radiance$getPointer() {
        return getPointer();
    }

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract long getPointer();

    @Shadow
    public abstract int getPixel(int x, int y);

    @Shadow
    public abstract void setPixel(int x, int y, int color);

    @Shadow
    public abstract void close();

    @Override
    public void radiance$loadFromTextureImageWithoutUI(int level, boolean removeAlpha) {
        RenderSystem.assertOnRenderThread();
        RendererProxy.takeScreenshot(false, this.width, this.height, this.format.components(),
            this.getPointer());
        if (removeAlpha && this.format.hasAlpha()) {
            for (int i = 0; i < this.getHeight(); i++) {
                for (int j = 0; j < this.getWidth(); j++) {
                    this.setPixel(j, i, this.getPixel(j, i) | 255 << this.format.alphaOffset());
                }
            }
        }
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.texture.TextureTracker;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GpuDevice.class)
public class GpuDeviceTextureMixins {

    @Inject(method = "createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
        at = @At("RETURN"))
    private void registerSupplierTexture(Supplier<String> label, int usage, GpuFormat format,
        int width, int height, int depthOrLayers, int mipLevels,
        CallbackInfoReturnable<GpuTexture> cir) {
        TextureTracker.registerGpuTexture(cir.getReturnValue());
    }

    @Inject(method = "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
        at = @At("RETURN"))
    private void registerNamedTexture(String label, int usage, GpuFormat format, int width,
        int height, int depthOrLayers, int mipLevels, CallbackInfoReturnable<GpuTexture> cir) {
        TextureTracker.registerGpuTexture(cir.getReturnValue());
    }
}

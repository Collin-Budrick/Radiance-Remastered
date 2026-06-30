package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.radiance.client.RendererAvailability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.GpuWarnlistManager$Preparations")
public class VideoWarningManagerWarningPatternLoaderMixins {

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/DeviceInfo;backendName()Ljava/lang/String;"))
    public String setRendererName(DeviceInfo deviceInfo) {
        return RendererAvailability.shouldOwnRendererLifecycle()
            ? "NeoVoxelRT - Vulkan"
            : deviceInfo.backendName();
    }

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/DeviceInfo;driverInfo()Ljava/lang/String;"))
    public String setRendererVersion(DeviceInfo deviceInfo) {
        return RendererAvailability.shouldOwnRendererLifecycle()
            ? "1.3"
            : deviceInfo.driverInfo();
    }

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/DeviceInfo;vendorName()Ljava/lang/String;"))
    public String setRendererVendor(DeviceInfo deviceInfo) {
        return RendererAvailability.shouldOwnRendererLifecycle()
            ? "Cross Platform"
            : deviceInfo.vendorName();
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public abstract class LightmapTextureManagerMixins implements ILightMapManagerExt {

    @Unique
    private float ambientLightFactor = 0.0F;
    @Unique
    private float skyFactor = 0.0F;
    @Unique
    private float blockFactor = 0.0F;
    @Unique
    private boolean useBrightLightmap = false;
    @Unique
    private Vector3f skyLightColor = new Vector3f(0.0F, 0.0F, 0.0F);
    @Unique
    private float nightVisionFactor = 0.0F;
    @Unique
    private float darknessScale = 0.0F;
    @Unique
    private float darkenWorldFactor = 0.0F;
    @Unique
    private float brightnessFactor = 0.0F;
    @Unique
    private static boolean radiance$loggedGpuTextureViewBridge = false;

    @Shadow
    public abstract GpuTextureView getTextureView();

    @Inject(method = "render(Lnet/minecraft/client/renderer/state/LightmapRenderState;)V", at = @At("HEAD"))
    private void radiance$captureLightmapState(LightmapRenderState state, CallbackInfo ci) {
        this.blockFactor = state.blockFactor;
        this.skyFactor = state.skyFactor;
        this.skyLightColor = radiance$copy(state.skyLightColor);
        this.ambientLightFactor = radiance$average(state.ambientColor);
        this.brightnessFactor = state.brightness;
        this.darknessScale = state.darknessEffectScale;
        this.nightVisionFactor = state.nightVisionEffectIntensity;
        this.darkenWorldFactor = state.bossOverlayWorldDarkening;
        this.useBrightLightmap = false;
    }

    @Unique
    private static Vector3f radiance$copy(Vector3fc value) {
        return value == null ? new Vector3f(0.0F, 0.0F, 0.0F) : new Vector3f(value);
    }

    @Unique
    private static float radiance$average(Vector3fc value) {
        return value == null ? 0.0F : (value.x() + value.y() + value.z()) / 3.0F;
    }

    @Override
    public int radiance$getTextureId() {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return 0;
        }

        GpuTextureView view = this.getTextureView();
        if (view == null || view.texture() == null || view.isClosed()) {
            return 0;
        }

        int id = TextureTracker.registerLightmapTexture(view.texture());
        if (id != 0 && !radiance$loggedGpuTextureViewBridge) {
            radiance$loggedGpuTextureViewBridge = true;
            RadianceClient.LOGGER.info(
                "Radiance lightmap bridge: using 26.2 GpuTextureView texture label={}, size={}x{}, id={}",
                view.texture().getLabel(), view.getWidth(0), view.getHeight(0), id);
        }
        return id;
    }

    @Override
    public GpuTextureView radiance$getTextureView() {
        return this.getTextureView();
    }

    @Override
    public float radiance$getAmbientLightFactor() {
        return ambientLightFactor;
    }

    @Override
    public float radiance$getSkyFactor() {
        return skyFactor;
    }

    @Override
    public float radiance$getBlockFactor() {
        return blockFactor;
    }

    @Override
    public boolean radiance$isUseBrightLightmap() {
        return useBrightLightmap;
    }

    @Override
    public Vector3f radiance$getSkyLightColor() {
        return skyLightColor;
    }

    @Override
    public float radiance$getNightVisionFactor() {
        return nightVisionFactor;
    }

    @Override
    public float radiance$getDarknessScale() {
        return darknessScale;
    }

    @Override
    public float radiance$getDarkenWorldFactor() {
        return darkenWorldFactor;
    }

    @Override
    public float radiance$getBrightnessFactor() {
        return brightnessFactor;
    }
}

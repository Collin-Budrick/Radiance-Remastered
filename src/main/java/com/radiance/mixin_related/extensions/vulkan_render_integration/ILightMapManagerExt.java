package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector3f;

public interface ILightMapManagerExt {

    int radiance$getTextureId();

    GpuTextureView radiance$getTextureView();

    float radiance$getAmbientLightFactor();

    float radiance$getSkyFactor();

    float radiance$getBlockFactor();

    boolean radiance$isUseBrightLightmap();

    Vector3f radiance$getSkyLightColor();

    float radiance$getNightVisionFactor();

    float radiance$getDarknessScale();

    float radiance$getDarkenWorldFactor();

    float radiance$getBrightnessFactor();
}

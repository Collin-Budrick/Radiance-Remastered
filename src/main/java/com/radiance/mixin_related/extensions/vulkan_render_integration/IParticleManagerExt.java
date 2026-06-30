package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.util.List;
import java.util.Map;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;

public interface IParticleManagerExt {

    List<ParticleRenderType> radiance$getTextureSheets();

    Map<ParticleRenderType, ParticleGroup<?>> radiance$getParticles();
}

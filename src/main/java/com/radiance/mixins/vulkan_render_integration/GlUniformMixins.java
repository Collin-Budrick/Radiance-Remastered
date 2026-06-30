package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.Uniform;
import org.spongepowered.asm.mixin.Mixin;

// Retired in 26.2: Uniform is only an AutoCloseable interface now.
@Mixin(Uniform.class)
public abstract class GlUniformMixins {
}

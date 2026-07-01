package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessors {

    @Accessor("useLightmap")
    boolean radiance$getUseLightmap();

    @Accessor("useOverlay")
    boolean radiance$getUseOverlay();

    @Accessor("affectsCrumbling")
    boolean radiance$getAffectsCrumbling();

    @Accessor("sortOnUpload")
    boolean radiance$getSortOnUpload();
}

package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;

public interface IChunkBuilderExt {

    SectionCompiler radiance$getSectionBuilder();

    SectionBufferBuilderPack radiance$getBuffers();
}

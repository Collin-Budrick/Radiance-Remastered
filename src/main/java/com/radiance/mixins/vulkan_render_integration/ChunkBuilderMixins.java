package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionRenderDispatcher.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Shadow
    private volatile SectionCompiler sectionCompiler;

    @Final
    @Shadow
    private SectionBufferBuilderPack fixedBuffers;

    @Override
    public SectionCompiler radiance$getSectionBuilder() {
        return sectionCompiler;
    }

    @Override
    public SectionBufferBuilderPack radiance$getBuffers() {
        return fixedBuffers;
    }
}

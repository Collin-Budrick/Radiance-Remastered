package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public interface IViewAreaExt {

    RotatingSectionStorage<SectionRenderDispatcher.RenderSection> radiance$getSections();
}

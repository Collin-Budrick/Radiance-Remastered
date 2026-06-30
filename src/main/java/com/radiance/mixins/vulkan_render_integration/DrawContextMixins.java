package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiGraphicsExtractor.class)
public class DrawContextMixins implements IDrawContextExt {

    @Override
    public void radiance$drawOrientedQuad(RenderType layer, float x1, float y1, float x2,
        float y2, float thickness, int color) {
        int minX = (int) Math.floor(Math.min(x1, x2) - thickness);
        int minY = (int) Math.floor(Math.min(y1, y2) - thickness);
        int maxX = (int) Math.ceil(Math.max(x1, x2) + thickness);
        int maxY = (int) Math.ceil(Math.max(y1, y2) + thickness);
        ((GuiGraphicsExtractor) (Object) this).fill(minX, minY, maxX, maxY, color);
    }
}

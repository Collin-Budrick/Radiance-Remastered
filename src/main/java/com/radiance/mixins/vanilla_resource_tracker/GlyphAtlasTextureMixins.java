package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FontTexture.class)
public abstract class GlyphAtlasTextureMixins extends AbstractTextureMixins implements
    IGlyphAtlasTextureExt {

    @Final
    @Shadow
    private GlyphRenderTypes textRenderLayers;
    @Final
    @Shadow
    private boolean hasColor;
    @Final
    @Shadow
    private FontTexture.Slot rootSlot;

    @Override
    public BakedGlyph radiance$bake(IRenderableGlyphExt glyph) {
        if (glyph.hasColor() != this.hasColor) {
            return null;
        }
        FontTexture.Slot slot = this.rootSlot.findSlotFor(glyph);
        if (slot != null) {
            this.bindTexture();
            glyph.upload(this.getGlId(), slot.x, slot.y);
            float f = 256.0f;
            float g = 256.0f;
            float h = 0.01f;
            return new BakedGlyph(this.textRenderLayers,
                ((float) slot.x + 0.01f) / 256.0f,
                ((float) slot.x - 0.01f + (float) glyph.getWidth()) / 256.0f,
                ((float) slot.y + 0.01f) / 256.0f,
                ((float) slot.y - 0.01f + (float) glyph.getHeight()) / 256.0f,
                glyph.getXMin(),
                glyph.getXMax(),
                glyph.getYMin(),
                glyph.getYMax());
        }
        return null;
    }
}

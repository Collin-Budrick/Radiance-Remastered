package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import com.mojang.blaze3d.vertex.VertexFormat;

public class PBRVertexFormats {

    public static final VertexFormat
        PBR_TRIANGLE =
        VertexFormat.builder(0)
            .addAttribute(PBR_POS.name(), PBR_POS.offset(), PBR_POS.format().blockSize(),
                PBR_POS.format(), 1)
            .addAttribute(PBR_USE_NORM.name(), PBR_USE_NORM.offset(),
                PBR_USE_NORM.format().blockSize(), PBR_USE_NORM.format(), 1)

            .addAttribute(PBR_NORM.name(), PBR_NORM.offset(), PBR_NORM.format().blockSize(),
                PBR_NORM.format(), 1)
            .addAttribute(PBR_USE_COLOR_LAYER.name(), PBR_USE_COLOR_LAYER.offset(),
                PBR_USE_COLOR_LAYER.format().blockSize(), PBR_USE_COLOR_LAYER.format(), 1)

            .addAttribute(PBR_COLOR_LAYER.name(), PBR_COLOR_LAYER.offset(),
                PBR_COLOR_LAYER.format().blockSize(), PBR_COLOR_LAYER.format(), 1)

            .addAttribute(PBR_USE_TEXTURE.name(), PBR_USE_TEXTURE.offset(),
                PBR_USE_TEXTURE.format().blockSize(), PBR_USE_TEXTURE.format(), 1)
            .addAttribute(PBR_USE_OVERLAY.name(), PBR_USE_OVERLAY.offset(),
                PBR_USE_OVERLAY.format().blockSize(), PBR_USE_OVERLAY.format(), 1)
            .addAttribute(PBR_TEXTURE_UV.name(), PBR_TEXTURE_UV.offset(),
                PBR_TEXTURE_UV.format().blockSize(), PBR_TEXTURE_UV.format(), 1)

            .addAttribute(PBR_OVERLAY_UV.name(), PBR_OVERLAY_UV.offset(),
                PBR_OVERLAY_UV.format().blockSize(), PBR_OVERLAY_UV.format(), 1)
            .addAttribute(PBR_USE_GLINT.name(), PBR_USE_GLINT.offset(),
                PBR_USE_GLINT.format().blockSize(), PBR_USE_GLINT.format(), 1)
            .addAttribute(PBR_TEXTURE_ID.name(), PBR_TEXTURE_ID.offset(),
                PBR_TEXTURE_ID.format().blockSize(), PBR_TEXTURE_ID.format(), 1)

            .addAttribute(PBR_GLINT_UV.name(), PBR_GLINT_UV.offset(),
                PBR_GLINT_UV.format().blockSize(), PBR_GLINT_UV.format(), 1)
            .addAttribute(PBR_GLINT_TEXTURE.name(), PBR_GLINT_TEXTURE.offset(),
                PBR_GLINT_TEXTURE.format().blockSize(), PBR_GLINT_TEXTURE.format(), 1)
            .addAttribute(PBR_USE_LIGHT.name(), PBR_USE_LIGHT.offset(),
                PBR_USE_LIGHT.format().blockSize(), PBR_USE_LIGHT.format(), 1)

            .addAttribute(PBR_LIGHT_UV.name(), PBR_LIGHT_UV.offset(),
                PBR_LIGHT_UV.format().blockSize(), PBR_LIGHT_UV.format(), 1)
            .addAttribute(PBR_COORDINATE.name(), PBR_COORDINATE.offset(), 24,
                PBR_COORDINATE.format(), 1)
            .build();
}

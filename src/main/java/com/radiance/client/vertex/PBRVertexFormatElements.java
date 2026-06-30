package com.radiance.client.vertex;

import com.mojang.blaze3d.GpuFormat;

public class PBRVertexFormatElements {

    public record PBRAttribute(String name, int bit, int offset, GpuFormat format) {
    }

    public static final PBRAttribute PBR_POS =
        new PBRAttribute("Pos", 6, 0, GpuFormat.RGB32_FLOAT);
    public static final PBRAttribute PBR_USE_NORM =
        new PBRAttribute("UseNorm", 7, 12, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_NORM =
        new PBRAttribute("Norm", 8, 16, GpuFormat.RGB32_FLOAT);
    public static final PBRAttribute PBR_USE_COLOR_LAYER =
        new PBRAttribute("UseColorLayer", 9, 28, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_COLOR_LAYER =
        new PBRAttribute("ColorLayer", 10, 32, GpuFormat.RGBA32_FLOAT);
    public static final PBRAttribute PBR_USE_TEXTURE =
        new PBRAttribute("UseTexture", 11, 48, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_USE_OVERLAY =
        new PBRAttribute("UseOverlay", 12, 52, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_TEXTURE_UV =
        new PBRAttribute("TextureUV", 13, 56, GpuFormat.RG32_FLOAT);
    public static final PBRAttribute PBR_OVERLAY_UV =
        new PBRAttribute("OverlayUV", 14, 64, GpuFormat.RG32_SINT);
    public static final PBRAttribute PBR_USE_GLINT =
        new PBRAttribute("UseGlint", 15, 72, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_TEXTURE_ID =
        new PBRAttribute("TextureID", 16, 76, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_GLINT_UV =
        new PBRAttribute("GlintUV", 17, 80, GpuFormat.RG32_FLOAT);
    public static final PBRAttribute PBR_GLINT_TEXTURE =
        new PBRAttribute("GlintTexture", 18, 88, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_USE_LIGHT =
        new PBRAttribute("UseLight", 19, 92, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_LIGHT_UV =
        new PBRAttribute("LightUV", 20, 96, GpuFormat.RG32_SINT);
    public static final PBRAttribute PBR_COORDINATE =
        new PBRAttribute("Coordinate", 21, 104, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_ALBEDO_EMISSION =
        new PBRAttribute("AlbedoEmission", 23, 108, GpuFormat.R32_UINT);
    public static final PBRAttribute PBR_POST_BASE =
        new PBRAttribute("PostBase", 22, 112, GpuFormat.RGB32_FLOAT);
}

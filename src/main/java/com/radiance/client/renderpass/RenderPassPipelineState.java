package com.radiance.client.renderpass;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;

public record RenderPassPipelineState(
    RenderPassSetupState setup,
    PipelineState pipeline,
    TargetState outputTarget,
    DynamicTransformState dynamicTransforms,
    ScissorStateSnapshot scissor,
    List<PreparedTextureState> textures,
    boolean canConsolidateConsecutiveGeometry
) {

    public RenderPassPipelineState {
        textures = List.copyOf(textures);
    }

    public record RenderPassSetupState(
        RenderPipeline pipeline,
        OutputTarget outputTarget,
        List<SetupTextureState> textures,
        boolean useLightmap,
        boolean useOverlay,
        boolean affectsCrumbling,
        boolean sortOnUpload,
        String outlineProperty,
        String textureTransform,
        float[] textureTransformMatrix,
        String layeringTransform,
        float[] layeringTransformMatrix
    ) {

        public RenderPassSetupState {
            textures = List.copyOf(textures);
        }
    }

    public record SetupTextureState(
        String name,
        Identifier location,
        String samplerSupplier
    ) {
    }

    public record PipelineState(
        Identifier location,
        Identifier vertexShader,
        Identifier fragmentShader,
        int sortKey,
        String primitiveTopology,
        String polygonMode,
        boolean cull,
        boolean wantsDepthTexture,
        DepthState depth,
        List<ColorTargetStateSnapshot> colorTargets
    ) {

        public PipelineState {
            colorTargets = List.copyOf(colorTargets);
        }
    }

    public record ColorTargetStateSnapshot(
        int index,
        String format,
        int writeMask,
        boolean writeRed,
        boolean writeGreen,
        boolean writeBlue,
        boolean writeAlpha,
        BlendState blend
    ) {
    }

    public record BlendState(
        boolean enabled,
        BlendEquationState color,
        BlendEquationState alpha
    ) {
    }

    public record BlendEquationState(
        String sourceFactor,
        String destFactor,
        String op
    ) {
    }

    public record DepthState(
        String depthTest,
        boolean writeDepth,
        float depthBiasScaleFactor,
        float depthBiasConstant
    ) {
    }

    public record TargetState(
        String outputTarget,
        String renderTargetClass,
        int renderTargetIdentity,
        int width,
        int height,
        boolean useDepth,
        TextureObjectState colorTexture,
        TextureObjectState depthTexture
    ) {

        public static TargetState missing(OutputTarget outputTarget) {
            return new TargetState(String.valueOf(outputTarget), null, 0, 0, 0, false, null, null);
        }
    }

    public record TextureObjectState(
        int textureHandle,
        String label,
        int objectIdentity,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels,
        String format,
        int usage,
        boolean closed
    ) {
    }

    public record DynamicTransformState(
        int bufferIdentity,
        long offset,
        long length
    ) {
    }

    public record ScissorStateSnapshot(
        boolean enabled,
        int x,
        int y,
        int width,
        int height
    ) {
    }

    public record PreparedTextureState(
        String name,
        Identifier identifier,
        TextureObjectState texture,
        int baseMipLevel,
        int viewMipLevels,
        SamplerState sampler
    ) {
    }

    public record SamplerState(
        String addressModeU,
        String addressModeV,
        String minFilter,
        String magFilter,
        int maxAnisotropy,
        String maxLod
    ) {
    }
}

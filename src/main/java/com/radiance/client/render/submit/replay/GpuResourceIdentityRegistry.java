package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Formal entry point for 26.2 GPU resource identity mirroring.
 *
 * <p>The current implementation delegates to {@link RenderPassNativeResourceMirror}; future
 * replay work should extend that mirror instead of adding parallel weak identity maps.
 */
public final class GpuResourceIdentityRegistry {

    private GpuResourceIdentityRegistry() {
    }

    public static MirroredGpuBuffer captureBuffer(GpuBuffer buffer) {
        return RenderPassNativeResourceMirror.captureMirroredBuffer(buffer);
    }

    public static RenderPipelineMirror capturePipeline(RenderPipeline pipeline) {
        return RenderPassNativeResourceMirror.captureRenderPipelineMirror(pipeline);
    }

    public static RenderPipelineMirror rememberPipeline(RenderPipeline pipeline,
        int nativePipelineId, long nativeSerial, String source) {
        return RenderPassNativeResourceMirror.rememberRenderPipelineMirror(pipeline,
            nativePipelineId, nativeSerial, source);
    }

    public static UniformStateSnapshot captureUniform(Object owner, String name) {
        return RenderPassNativeResourceMirror.captureUniformStateSnapshot(owner, name);
    }

    public static UniformStateSnapshot rememberUniform(Object owner, String name,
        long uniformPtr, int uniformSize, String source) {
        return RenderPassNativeResourceMirror.rememberUniformStateSnapshot(owner, name,
            uniformPtr, uniformSize, source);
    }
}

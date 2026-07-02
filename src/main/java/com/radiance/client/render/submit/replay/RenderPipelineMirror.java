package com.radiance.client.render.submit.replay;

/**
 * Formal 26.2 mirror name for a captured {@code RenderPipeline} identity.
 */
public record RenderPipelineMirror(long mirrorSerial, int identityHash, int nativePipelineId,
                                   boolean nativePipelineIdAvailable, int shaderId,
                                   boolean shaderIdAvailable, long nativeSerial,
                                   boolean nativeSerialAvailable, String source) {

    public static RenderPipelineMirror missing() {
        return from(RenderPassDrawPacket.NativePipelineReference.missing());
    }

    public static RenderPipelineMirror from(
        RenderPassDrawPacket.NativePipelineReference reference) {
        if (reference == null) {
            reference = RenderPassDrawPacket.NativePipelineReference.missing();
        }
        return new RenderPipelineMirror(reference.mirrorSerial(), reference.identityHash(),
            reference.nativePipelineId(), reference.nativePipelineIdAvailable(),
            reference.shaderId(), reference.shaderIdAvailable(), reference.nativeSerial(),
            reference.nativeSerialAvailable(), reference.source());
    }

    public boolean hasNativePipelineId() {
        return nativePipelineIdAvailable && nativePipelineId >= 0;
    }

    public boolean hasShaderId() {
        return shaderIdAvailable && shaderId >= 0;
    }

    public boolean isMissing() {
        return mirrorSerial == 0L;
    }
}

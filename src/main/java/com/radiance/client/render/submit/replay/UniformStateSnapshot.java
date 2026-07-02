package com.radiance.client.render.submit.replay;

/**
 * Formal 26.2 snapshot name for mirrored uniform payload and binding state.
 */
public record UniformStateSnapshot(long mirrorSerial, int identityHash, String name,
                                   long uniformPtr, int uniformSize,
                                   boolean uniformPtrAvailable,
                                   boolean uniformSizeAvailable, int nativeBinding,
                                   boolean nativeBindingAvailable, long nativeSerial,
                                   boolean nativeSerialAvailable, String source) {

    public static UniformStateSnapshot missing(String name) {
        return from(RenderPassDrawPacket.NativeUniformReference.missing(name));
    }

    public static UniformStateSnapshot from(
        RenderPassDrawPacket.NativeUniformReference reference) {
        if (reference == null) {
            reference = RenderPassDrawPacket.NativeUniformReference.missing(null);
        }
        return new UniformStateSnapshot(reference.mirrorSerial(), reference.identityHash(),
            reference.name(), reference.uniformPtr(), reference.uniformSize(),
            reference.uniformPtrAvailable(), reference.uniformSizeAvailable(),
            reference.nativeBinding(), reference.nativeBindingAvailable(),
            reference.nativeSerial(), reference.nativeSerialAvailable(), reference.source());
    }

    public boolean hasUniformPayload() {
        return uniformSizeAvailable && uniformSize >= 0
            && (uniformSize == 0 || (uniformPtrAvailable && uniformPtr != 0L));
    }

    public boolean hasNativeBinding() {
        return nativeBindingAvailable && nativeBinding >= 0;
    }

    public boolean isMissing() {
        return mirrorSerial == 0L;
    }
}

package com.radiance.client.render.submit.replay;

import com.radiance.client.proxy.vulkan.BufferProxy;

/**
 * Formal 26.2 mirror name for a Java {@code GpuBuffer} tracked by Radiance.
 */
public record MirroredGpuBuffer(long mirrorSerial, int identityHash, int nativeId,
                                boolean nativeIdAvailable, long nativeSerial,
                                boolean nativeSerialAvailable, long contentSerial,
                                long size, int usage, boolean contentAvailable,
                                boolean cpuBackingAvailable, boolean fallback,
                                String fallbackReason, String source) {

    public static MirroredGpuBuffer missing() {
        return from(BufferProxy.NativeBufferMirror.missing());
    }

    public static MirroredGpuBuffer from(BufferProxy.NativeBufferMirror mirror) {
        if (mirror == null) {
            mirror = BufferProxy.NativeBufferMirror.missing();
        }
        return new MirroredGpuBuffer(mirror.mirrorSerial(), mirror.identityHash(),
            mirror.nativeId(), mirror.nativeIdAvailable(), mirror.nativeSerial(),
            mirror.nativeSerialAvailable(), mirror.contentSerial(), mirror.size(),
            mirror.usage(), mirror.contentAvailable(), mirror.cpuBackingAvailable(),
            mirror.fallback(), mirror.fallbackReason(), mirror.source());
    }

    public boolean hasNativeId() {
        return nativeIdAvailable && nativeId >= 0;
    }

    public boolean isMissing() {
        return mirrorSerial == 0L;
    }
}

package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.radiance.client.proxy.vulkan.BufferProxy;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class RenderPassNativeResourceMirror {

    private static final WeakIdentityStore<BufferMirror> BUFFERS = new WeakIdentityStore<>();
    private static final WeakIdentityStore<PipelineMirror> PIPELINES = new WeakIdentityStore<>();
    private static final WeakIdentityStore<UniformMirror> UNIFORMS = new WeakIdentityStore<>();

    private RenderPassNativeResourceMirror() {
    }

    public static RenderPassDrawPacket.NativeBufferReference captureBuffer(GpuBuffer buffer) {
        if (buffer == null) {
            return RenderPassDrawPacket.NativeBufferReference.missing();
        }

        return bufferReference(BufferProxy.resolveNativeBufferMirror(buffer));
    }

    public static RenderPassDrawPacket.NativeBufferReference rememberBuffer(GpuBuffer buffer,
        int nativeId) {
        return rememberBuffer(buffer, nativeId, 0L, "registered-buffer");
    }

    public static RenderPassDrawPacket.NativeBufferReference rememberBuffer(GpuBuffer buffer,
        int nativeId, long nativeSerial, String source) {
        if (buffer == null) {
            return RenderPassDrawPacket.NativeBufferReference.missing();
        }

        return bufferReference(BufferProxy.registerNativeBufferMirror(buffer, nativeId,
            nativeSerial, source));
    }

    private static RenderPassDrawPacket.NativeBufferReference bufferReference(
        BufferProxy.NativeBufferMirror mirror) {
        if (mirror == null || mirror.isMissing()) {
            return RenderPassDrawPacket.NativeBufferReference.missing();
        }
        return new RenderPassDrawPacket.NativeBufferReference(mirror.mirrorSerial(),
            mirror.identityHash(), mirror.nativeId(), mirror.hasNativeId(),
            mirror.nativeSerial(), mirror.nativeSerialAvailable(), mirror.source());
    }

    public static RenderPassDrawPacket.NativePipelineReference capturePipeline(
        RenderPipeline pipeline) {
        if (pipeline == null) {
            return RenderPassDrawPacket.NativePipelineReference.missing();
        }

        PipelineMirror mirror = PIPELINES.getOrCreate(pipeline, PipelineMirror::new);
        return new RenderPassDrawPacket.NativePipelineReference(mirror.serial(),
            System.identityHashCode(pipeline), mirror.nativePipelineId(),
            mirror.hasNativePipelineId(), mirror.shaderId(), mirror.hasShaderId(),
            mirror.nativeSerial(), mirror.hasNativeSerial(), mirror.source());
    }

    public static RenderPassDrawPacket.NativePipelineReference rememberPipeline(
        RenderPipeline pipeline, int nativePipelineId, long nativeSerial, String source) {
        if (pipeline == null) {
            return RenderPassDrawPacket.NativePipelineReference.missing();
        }

        PipelineMirror updated = PIPELINES.update(pipeline, PipelineMirror::new,
            mirror -> mirror.withPipeline(nativePipelineId, nativeSerial, source));
        return new RenderPassDrawPacket.NativePipelineReference(updated.serial(),
            System.identityHashCode(pipeline), updated.nativePipelineId(),
            updated.hasNativePipelineId(), updated.shaderId(), updated.hasShaderId(),
            updated.nativeSerial(), updated.hasNativeSerial(), updated.source());
    }

    public static RenderPassDrawPacket.NativePipelineReference rememberPipelineShader(
        RenderPipeline pipeline, int shaderId, String source) {
        if (pipeline == null) {
            return RenderPassDrawPacket.NativePipelineReference.missing();
        }

        PipelineMirror updated = PIPELINES.update(pipeline, PipelineMirror::new,
            mirror -> mirror.withShader(shaderId, source));
        return new RenderPassDrawPacket.NativePipelineReference(updated.serial(),
            System.identityHashCode(pipeline), updated.nativePipelineId(),
            updated.hasNativePipelineId(), updated.shaderId(), updated.hasShaderId(),
            updated.nativeSerial(), updated.hasNativeSerial(), updated.source());
    }

    public static RenderPassDrawPacket.NativePipelineReference rememberPipelineNative(
        RenderPipeline pipeline, int nativePipelineId, int shaderId, long nativeSerial,
        String source) {
        if (pipeline == null) {
            return RenderPassDrawPacket.NativePipelineReference.missing();
        }

        PipelineMirror updated = PIPELINES.update(pipeline, PipelineMirror::new,
            mirror -> mirror.withNative(nativePipelineId, shaderId, nativeSerial, source));
        return new RenderPassDrawPacket.NativePipelineReference(updated.serial(),
            System.identityHashCode(pipeline), updated.nativePipelineId(),
            updated.hasNativePipelineId(), updated.shaderId(), updated.hasShaderId(),
            updated.nativeSerial(), updated.hasNativeSerial(), updated.source());
    }

    public static RenderPassDrawPacket.NativeUniformReference captureUniform(Object owner,
        String name) {
        if (owner == null) {
            return RenderPassDrawPacket.NativeUniformReference.missing(name);
        }

        UniformMirror mirror = UNIFORMS.getOrCreate(owner, serial -> new UniformMirror(serial,
            name, 0L, -1, false, false, -1, false, 0L, false, "java-mirror"));
        return new RenderPassDrawPacket.NativeUniformReference(mirror.serial(),
            System.identityHashCode(owner), mirror.nameOr(name), mirror.uniformPtr(),
            mirror.uniformSize(), mirror.hasUniformPtr(), mirror.hasUniformSize(),
            mirror.nativeBinding(), mirror.hasNativeBinding(), mirror.nativeSerial(),
            mirror.hasNativeSerial(), mirror.source());
    }

    public static RenderPassDrawPacket.NativeUniformReference rememberUniform(Object owner,
        String name, long uniformPtr, int uniformSize, String source) {
        if (owner == null) {
            return RenderPassDrawPacket.NativeUniformReference.missing(name);
        }

        UniformMirror updated = UNIFORMS.update(owner,
            serial -> new UniformMirror(serial, name, 0L, -1, false, false, -1, false, 0L,
                false, "java-mirror"),
            mirror -> mirror.withPointer(name, uniformPtr, uniformSize, source));
        return new RenderPassDrawPacket.NativeUniformReference(updated.serial(),
            System.identityHashCode(owner), updated.nameOr(name), updated.uniformPtr(),
            updated.uniformSize(), updated.hasUniformPtr(), updated.hasUniformSize(),
            updated.nativeBinding(), updated.hasNativeBinding(), updated.nativeSerial(),
            updated.hasNativeSerial(), updated.source());
    }

    public static RenderPassDrawPacket.NativeUniformReference rememberUniformBinding(Object owner,
        String name, int nativeBinding, long nativeSerial, String source) {
        if (owner == null) {
            return RenderPassDrawPacket.NativeUniformReference.missing(name);
        }

        UniformMirror updated = UNIFORMS.update(owner,
            serial -> new UniformMirror(serial, name, 0L, -1, false, false, -1, false, 0L,
                false, "java-mirror"),
            mirror -> mirror.withBinding(name, nativeBinding, nativeSerial, source));
        return new RenderPassDrawPacket.NativeUniformReference(updated.serial(),
            System.identityHashCode(owner), updated.nameOr(name), updated.uniformPtr(),
            updated.uniformSize(), updated.hasUniformPtr(), updated.hasUniformSize(),
            updated.nativeBinding(), updated.hasNativeBinding(), updated.nativeSerial(),
            updated.hasNativeSerial(), updated.source());
    }

    private record BufferMirror(long serial, int nativeId, boolean hasNativeId,
                                long nativeSerial, boolean hasNativeSerial, String source) {

        private BufferMirror(long serial) {
            this(serial, -1, false, 0L, false, "java-mirror");
        }

        private BufferMirror withNative(int nativeId, long nativeSerial, String source) {
            return new BufferMirror(serial, nativeId, nativeId >= 0, nativeSerial,
                nativeSerial > 0L, cleanSource(source));
        }
    }

    private record PipelineMirror(long serial, int nativePipelineId,
                                  boolean hasNativePipelineId, int shaderId,
                                  boolean hasShaderId, long nativeSerial,
                                  boolean hasNativeSerial, String source) {

        private PipelineMirror(long serial) {
            this(serial, -1, false, -1, false, 0L, false, "java-mirror");
        }

        private PipelineMirror withPipeline(int nativePipelineId, long nativeSerial,
            String source) {
            return new PipelineMirror(serial, nativePipelineId, nativePipelineId >= 0, shaderId,
                hasShaderId, nativeSerial, nativeSerial > 0L, cleanSource(source));
        }

        private PipelineMirror withShader(int shaderId, String source) {
            return new PipelineMirror(serial, nativePipelineId, hasNativePipelineId, shaderId,
                shaderId >= 0, nativeSerial, hasNativeSerial, cleanSource(source));
        }

        private PipelineMirror withNative(int nativePipelineId, int shaderId,
            long nativeSerial, String source) {
            return new PipelineMirror(serial, nativePipelineId, nativePipelineId >= 0, shaderId,
                shaderId >= 0, nativeSerial, nativeSerial > 0L, cleanSource(source));
        }
    }

    private record UniformMirror(long serial, String name, long uniformPtr,
                                 int uniformSize, boolean hasUniformPtr,
                                 boolean hasUniformSize, int nativeBinding,
                                 boolean hasNativeBinding, long nativeSerial,
                                 boolean hasNativeSerial, String source) {

        private String nameOr(String fallback) {
            return name == null ? fallback : name;
        }

        private UniformMirror withPointer(String name, long uniformPtr, int uniformSize,
            String source) {
            return new UniformMirror(serial, nameOr(name), uniformPtr, uniformSize,
                uniformPtr != 0L || uniformSize == 0, uniformSize >= 0, nativeBinding,
                hasNativeBinding, nativeSerial, hasNativeSerial, cleanSource(source));
        }

        private UniformMirror withBinding(String name, int nativeBinding, long nativeSerial,
            String source) {
            return new UniformMirror(serial, nameOr(name), uniformPtr, uniformSize,
                hasUniformPtr, hasUniformSize, nativeBinding, nativeBinding >= 0, nativeSerial,
                nativeSerial > 0L, cleanSource(source));
        }
    }

    private static String cleanSource(String source) {
        return source == null || source.isBlank() ? "registered" : source;
    }

    private interface MirrorFactory<T> {

        T create(long serial);
    }

    private interface MirrorUpdater<T> {

        T update(T mirror);
    }

    private static final class WeakIdentityStore<T> {

        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        private final Map<WeakIdentityKey, Entry<T>> values = new HashMap<>();
        private final AtomicLong nextSerial = new AtomicLong(1L);

        private synchronized T getOrCreate(Object owner, MirrorFactory<T> factory) {
            return entry(owner, factory).mirror;
        }

        private synchronized T update(Object owner, MirrorFactory<T> factory,
            MirrorUpdater<T> updater) {
            Entry<T> entry = entry(owner, factory);
            entry.mirror = updater.update(entry.mirror);
            return entry.mirror;
        }

        private Entry<T> entry(Object owner, MirrorFactory<T> factory) {
            expungeStaleEntries();
            WeakIdentityKey lookup = WeakIdentityKey.lookup(owner);
            Entry<T> existing = values.get(lookup);
            if (existing != null) {
                return existing;
            }

            long serial = nextSerial.getAndIncrement();
            Entry<T> created = new Entry<>(factory.create(serial));
            values.put(WeakIdentityKey.weak(owner, queue), created);
            return created;
        }

        private void expungeStaleEntries() {
            WeakIdentityKey key;
            while ((key = (WeakIdentityKey) queue.poll()) != null) {
                values.remove(key);
            }
        }
    }

    private static final class Entry<T> {

        private T mirror;

        private Entry(T mirror) {
            this.mirror = mirror;
        }
    }

    private static final class WeakIdentityKey extends WeakReference<Object> {

        private final int hash;

        private WeakIdentityKey(Object owner, ReferenceQueue<Object> queue) {
            super(owner, queue);
            this.hash = System.identityHashCode(owner);
        }

        private static WeakIdentityKey lookup(Object owner) {
            return new WeakIdentityKey(owner, null);
        }

        private static WeakIdentityKey weak(Object owner, ReferenceQueue<Object> queue) {
            return new WeakIdentityKey(owner, queue);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WeakIdentityKey other)) {
                return false;
            }
            Object owner = get();
            return owner != null && owner == other.get();
        }
    }
}

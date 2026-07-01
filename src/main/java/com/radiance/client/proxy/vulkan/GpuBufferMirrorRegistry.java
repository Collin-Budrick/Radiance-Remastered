package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class GpuBufferMirrorRegistry {

    private static final int MAX_CPU_BACKING_BYTES = 64 * 1024 * 1024;

    private final ReferenceQueue<GpuBuffer> queue = new ReferenceQueue<>();
    private final Map<WeakIdentityKey, Entry> entries = new HashMap<>();
    private final AtomicLong nextMirrorSerial = new AtomicLong(1L);
    private final AtomicLong nextNativeSerial = new AtomicLong(1L);
    private final AtomicLong nextContentSerial = new AtomicLong(1L);

    synchronized BufferProxy.NativeBufferMirror observe(GpuBuffer buffer, long size, int usage,
        String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }

        Entry entry = entry(buffer);
        entry.size = size;
        entry.usage = usage;
        entry.source = cleanSource(source);
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror registerNative(GpuBuffer buffer, int nativeId,
        long nativeSerial, String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }

        Entry entry = entry(buffer);
        entry.nativeId = nativeId;
        entry.nativeAllocated = nativeId >= 0;
        entry.nativeSerial = nativeSerial > 0L ? nativeSerial : nextNativeSerial.getAndIncrement();
        entry.source = cleanSource(source);
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror lookup(GpuBuffer buffer) {
        Entry entry = existing(buffer);
        return entry == null ? BufferProxy.NativeBufferMirror.missing() : snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror recordCpuWrite(GpuBuffer buffer, long offset,
        byte[] bytes, String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }
        if (bytes == null || bytes.length == 0) {
            return lookup(buffer);
        }

        Entry entry = entry(buffer);
        entry.size = buffer.size();
        entry.usage = buffer.usage();
        entry.source = cleanSource(source);

        long end = offset + bytes.length;
        if (offset < 0L || end < offset || end > entry.size) {
            return markFallback(entry, "CPU write range is outside the GpuBuffer bounds",
                source);
        }

        boolean rangeRecorded = recordKnownBytes(entry, offset, bytes);
        boolean completeAfterWrite = rangeRecorded && isFullyKnown(entry);

        entry.contentSerial = nextContentSerial.getAndIncrement();
        entry.contentAvailable = completeAfterWrite;
        if (rangeRecorded) {
            entry.fallback = false;
            entry.fallbackReason = null;
        }
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror recordCpuWrite(GpuBuffer buffer, long offset,
        java.nio.ByteBuffer source, String sourceName) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }
        if (source == null || !source.hasRemaining()) {
            return lookup(buffer);
        }

        Entry entry = entry(buffer);
        entry.size = buffer.size();
        entry.usage = buffer.usage();
        entry.source = cleanSource(sourceName);

        int size = source.remaining();
        long end = offset + size;
        if (offset < 0L || end < offset || end > entry.size) {
            return markFallback(entry, "CPU write range is outside the GpuBuffer bounds",
                sourceName);
        }

        boolean rangeRecorded = recordKnownBytes(entry, offset, source.slice(), size);
        boolean completeAfterWrite = rangeRecorded && isFullyKnown(entry);

        entry.contentSerial = nextContentSerial.getAndIncrement();
        entry.contentAvailable = completeAfterWrite;
        if (rangeRecorded) {
            entry.fallback = false;
            entry.fallbackReason = null;
        }
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror markNativeContent(GpuBuffer buffer,
        String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }

        Entry entry = entry(buffer);
        entry.source = cleanSource(source);
        if (entry.contentAvailable) {
            entry.fallback = false;
            entry.fallbackReason = null;
            markKnownNativeBytes(entry, 0L, entry.size);
        }
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror markNativeRangeUploaded(GpuBuffer buffer,
        long offset, int size, String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }
        if (size <= 0) {
            return lookup(buffer);
        }

        Entry entry = entry(buffer);
        entry.source = cleanSource(source);
        if (offset < 0L || offset + size < offset || offset + size > entry.size) {
            return markFallback(entry, "native upload range is outside the GpuBuffer bounds",
                source);
        }
        if (markKnownNativeBytes(entry, offset, size)) {
            entry.fallback = false;
            entry.fallbackReason = null;
        }
        return snapshot(entry);
    }

    synchronized BufferProxy.NativeBufferMirror markFallback(GpuBuffer buffer, String reason,
        String source) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }
        return markFallback(entry(buffer), reason, source);
    }

    synchronized BufferProxy.NativeBufferMirror invalidate(GpuBuffer buffer, String reason) {
        if (buffer == null) {
            return BufferProxy.NativeBufferMirror.missing();
        }

        Entry entry = entry(buffer);
        entry.nativeId = -1;
        entry.nativeAllocated = false;
        entry.nativeSerial = 0L;
        entry.contentAvailable = false;
        entry.contentSerial = nextContentSerial.getAndIncrement();
        entry.backing = null;
        entry.knownBytes = null;
        entry.nativeKnownBytes = null;
        return markFallback(entry, reason == null ? "buffer mirror invalidated" : reason,
            "invalidate");
    }

    synchronized byte[] copyRange(GpuBuffer buffer, long offset, int size) {
        Entry entry = existing(buffer);
        if (entry == null || entry.backing == null || entry.knownBytes == null || offset < 0L
            || offset > Integer.MAX_VALUE || size < 0) {
            return null;
        }

        long end = offset + size;
        if (end < offset || end > entry.backing.length) {
            return null;
        }
        int start = (int) offset;
        int finish = (int) end;
        if (entry.knownBytes.nextClearBit(start) < finish) {
            return null;
        }
        return Arrays.copyOfRange(entry.backing, start, finish);
    }

    synchronized byte[] copyFullContent(GpuBuffer buffer) {
        Entry entry = existing(buffer);
        if (entry == null || !entry.contentAvailable || entry.backing == null
            || entry.size < 0L || entry.size > entry.backing.length) {
            return null;
        }
        return Arrays.copyOf(entry.backing, (int) entry.size);
    }

    private BufferProxy.NativeBufferMirror markFallback(Entry entry, String reason,
        String source) {
        entry.fallback = true;
        entry.fallbackReason = cleanReason(reason);
        entry.source = cleanSource(source);
        return snapshot(entry);
    }

    private boolean recordKnownBytes(Entry entry, long offset, byte[] bytes) {
        if (!ensureCpuBacking(entry) || bytes == null || bytes.length == 0) {
            return false;
        }
        int start = (int) offset;
        System.arraycopy(bytes, 0, entry.backing, start, bytes.length);
        entry.knownBytes.set(start, start + bytes.length);
        return true;
    }

    private boolean recordKnownBytes(Entry entry, long offset, java.nio.ByteBuffer source,
        int size) {
        if (!ensureCpuBacking(entry) || source == null || size <= 0
            || source.remaining() < size) {
            return false;
        }
        int start = (int) offset;
        java.nio.ByteBuffer view = source.slice();
        view.limit(size);
        view.get(entry.backing, start, size);
        entry.knownBytes.set(start, start + size);
        return true;
    }

    private boolean ensureCpuBacking(Entry entry) {
        if (entry.size < 0L || entry.size > MAX_CPU_BACKING_BYTES) {
            return false;
        }
        int backingSize = (int) entry.size;
        if (entry.backing == null || entry.backing.length != backingSize) {
            entry.backing = new byte[backingSize];
            entry.knownBytes = new BitSet(backingSize);
        } else if (entry.knownBytes == null) {
            entry.knownBytes = new BitSet(backingSize);
        }
        return true;
    }

    private boolean isFullyKnown(Entry entry) {
        if (entry.backing == null || entry.knownBytes == null || entry.size < 0L
            || entry.size > entry.backing.length) {
            return false;
        }
        int size = (int) entry.size;
        return entry.knownBytes.nextClearBit(0) >= size;
    }

    private boolean markKnownNativeBytes(Entry entry, long offset, long size) {
        if (entry.size < 0L || entry.size > MAX_CPU_BACKING_BYTES || offset < 0L
            || size <= 0L || offset + size < offset || offset + size > entry.size) {
            return false;
        }
        if (entry.nativeKnownBytes == null) {
            entry.nativeKnownBytes = new BitSet((int) entry.size);
        }
        int start = (int) offset;
        entry.nativeKnownBytes.set(start, start + (int) size);
        return true;
    }

    private boolean hasNativeContent(Entry entry) {
        return entry.nativeKnownBytes != null && entry.nativeKnownBytes.nextSetBit(0) >= 0;
    }

    private Entry existing(GpuBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        expungeStaleEntries();
        return entries.get(WeakIdentityKey.lookup(buffer));
    }

    private Entry entry(GpuBuffer buffer) {
        expungeStaleEntries();
        WeakIdentityKey lookup = WeakIdentityKey.lookup(buffer);
        Entry existing = entries.get(lookup);
        if (existing != null) {
            return existing;
        }

        Entry created = new Entry(nextMirrorSerial.getAndIncrement(),
            System.identityHashCode(buffer), buffer.size(), buffer.usage());
        entries.put(WeakIdentityKey.weak(buffer, queue), created);
        return created;
    }

    private void expungeStaleEntries() {
        WeakIdentityKey key;
        while ((key = (WeakIdentityKey) queue.poll()) != null) {
            entries.remove(key);
        }
    }

    private BufferProxy.NativeBufferMirror snapshot(Entry entry) {
        boolean nativeSerialAvailable = entry.nativeAllocated && entry.nativeSerial > 0L;
        boolean nativeIdAvailable = entry.nativeAllocated && hasNativeContent(entry)
            && !entry.fallback;
        return new BufferProxy.NativeBufferMirror(entry.mirrorSerial, entry.identityHash,
            entry.nativeId, nativeIdAvailable, entry.nativeSerial, nativeSerialAvailable,
            entry.contentSerial, entry.size, entry.usage, entry.contentAvailable,
            entry.backing != null, entry.fallback, entry.fallbackReason,
            snapshotSource(entry));
    }

    private String snapshotSource(Entry entry) {
        if (entry.fallback) {
            return "fallback:" + cleanReason(entry.fallbackReason) + "@" + entry.source;
        }
        if (!entry.contentAvailable && hasNativeContent(entry)) {
            return "partial-native-content@" + entry.source;
        }
        if (!entry.contentAvailable) {
            return "pending-content@" + entry.source;
        }
        return entry.source;
    }

    private static String cleanSource(String source) {
        return source == null || source.isBlank() ? "registered" : source;
    }

    private static String cleanReason(String reason) {
        return reason == null || reason.isBlank() ? "unsupported buffer mirror" : reason;
    }

    private static final class Entry {

        private final long mirrorSerial;
        private final int identityHash;
        private int nativeId = -1;
        private boolean nativeAllocated;
        private long nativeSerial;
        private long contentSerial;
        private long size;
        private int usage;
        private boolean contentAvailable;
        private byte[] backing;
        private BitSet knownBytes;
        private BitSet nativeKnownBytes;
        private boolean fallback;
        private String fallbackReason;
        private String source = "java-mirror";

        private Entry(long mirrorSerial, int identityHash, long size, int usage) {
            this.mirrorSerial = mirrorSerial;
            this.identityHash = identityHash;
            this.size = size;
            this.usage = usage;
        }
    }

    private static final class WeakIdentityKey extends WeakReference<GpuBuffer> {

        private final int hash;

        private WeakIdentityKey(GpuBuffer buffer, ReferenceQueue<GpuBuffer> queue) {
            super(buffer, queue);
            this.hash = System.identityHashCode(buffer);
        }

        private static WeakIdentityKey lookup(GpuBuffer buffer) {
            return new WeakIdentityKey(buffer, null);
        }

        private static WeakIdentityKey weak(GpuBuffer buffer, ReferenceQueue<GpuBuffer> queue) {
            return new WeakIdentityKey(buffer, queue);
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
            GpuBuffer buffer = get();
            return buffer != null && buffer == other.get();
        }
    }
}

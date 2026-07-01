package com.radiance.client.proxy.world;

import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

public final class CloudProxy {

    private static final int ENCODED_FACE_STRIDE_BYTES = 3;
    private static final int NATIVE_ACCEPTED = 1;
    private static final int NATIVE_DECLINED_NO_RENDERER = 0;
    private static final int NATIVE_DECLINED_INVALID_PACKET = -1;
    private static final int NATIVE_DECLINED_UNSUPPORTED_PACKET = -2;
    private static final int NATIVE_DECLINED_NATIVE_ERROR = -3;
    private static final int NATIVE_STORED_NO_DRAW = -4;

    private static volatile boolean nativeSubmitUnavailable;
    private static volatile boolean loggedNativeSubmitUnavailable;

    private CloudProxy() {
    }

    private static native int submitEncodedFacesNative(int colorArgb, int cloudStatus,
        boolean fancy, int relativeCameraPosition, int textureWidth, int textureHeight, int cellX,
        int cellZ, int radiusCells, int quadCount, float cloudBaseRelativeY,
        float cloudTopRelativeY, float cellOffsetX, float cellOffsetZ, double cameraX,
        double cameraY, double cameraZ, float bottomY, long gameTime, float partialTicks,
        long encodedFaces, int encodedFaceByteCount, int shaderId, long uniformPtr,
        int uniformSize);

    public static SubmitResult submitEncodedFaces(EncodedCloudPacket packet) {
        if (!RendererAvailability.isRendererLifecycleActive() || nativeSubmitUnavailable
            || packet == null || !packet.hasFaces()) {
            return SubmitResult.javaRejected();
        }

        if (!packet.hasValidShape()) {
            logNativeSubmitUnavailable("encoded cloud packet failed Java-side validation", null,
                packet);
            return SubmitResult.javaRejected();
        }

        ByteBuffer encodedFaces = packet.encodedFaces().duplicate().order(ByteOrder.nativeOrder());
        if (!encodedFaces.isDirect()) {
            nativeSubmitUnavailable = true;
            logNativeSubmitUnavailable("encoded cloud face buffer is not direct", null, packet);
            return SubmitResult.nativeUnavailable();
        }

        encodedFaces.position(0);
        encodedFaces.limit(packet.encodedFaceByteCount());
        CloudReplayShaderBridge.NativeDrawState drawState =
            CloudReplayShaderBridge.prepareNativeDrawState(packet);
        try {
            int nativeStatus = submitEncodedFacesNative(packet.colorArgb(), packet.cloudStatus(),
                packet.fancy(), packet.relativeCameraPosition(), packet.textureWidth(),
                packet.textureHeight(), packet.cellX(), packet.cellZ(), packet.radiusCells(),
                packet.quadCount(), packet.cloudBaseRelativeY(), packet.cloudTopRelativeY(),
                packet.cellOffsetX(), packet.cellOffsetZ(), packet.cameraX(), packet.cameraY(),
                packet.cameraZ(), packet.bottomY(), packet.gameTime(), packet.partialTicks(),
                MemoryUtil.memAddress(encodedFaces), packet.encodedFaceByteCount(),
                drawState == null ? -1 : drawState.shaderId(),
                drawState == null ? 0L : drawState.uniformAddress(),
                drawState == null ? 0 : drawState.uniformSize());
            SubmitResult result = SubmitResult.fromNative(nativeStatus);
            if (!result.shouldCancelVanilla()) {
                logNativeSubmitUnavailable(nativeStatusReason(result.status(), nativeStatus), null,
                    packet);
            }
            return result;
        } catch (UnsatisfiedLinkError | RuntimeException exception) {
            nativeSubmitUnavailable = true;
            logNativeSubmitUnavailable("native encoded-cloud submit failed", exception, packet);
            return SubmitResult.nativeUnavailable();
        }
    }

    private static String nativeStatusReason(SubmitStatus status, int nativeStatus) {
        return switch (status) {
            case DECLINED_NO_RENDERER ->
                "native encoded-cloud renderer is not lifecycle-ready";
            case DECLINED_INVALID_PACKET ->
                "native encoded-cloud packet failed validation";
            case DECLINED_UNSUPPORTED_PACKET ->
                "native encoded-cloud packet uses unsupported encoded face data";
            case DECLINED_NATIVE_ERROR ->
                "native encoded-cloud packet storage failed";
            case STORED_NO_DRAW ->
                "native encoded-cloud packet was decoded and stored but no 26.2 cloud draw is scheduled yet";
            case JAVA_REJECTED ->
                "encoded cloud packet was rejected before native submission";
            case NATIVE_UNAVAILABLE ->
                "native encoded-cloud renderer is unavailable";
            case UNKNOWN_NATIVE_STATUS ->
                "native encoded-cloud renderer declined the packet with status "
                    + nativeStatus;
            default -> "native encoded-cloud renderer declined the packet with status "
                + nativeStatus;
        };
    }

    private static void logNativeSubmitUnavailable(String reason, Throwable throwable,
        EncodedCloudPacket packet) {
        if (loggedNativeSubmitUnavailable) {
            return;
        }
        loggedNativeSubmitUnavailable = true;
        if (throwable == null) {
            RadianceClient.LOGGER.warn(
                "Radiance cloud bridge: {}; leaving vanilla clouds active (status={} fancy={} faces={} bytes={} cell=({}, {}) radius={})",
                reason, packet.cloudStatus(), packet.fancy(), packet.quadCount(),
                packet.encodedFaceByteCount(), packet.cellX(), packet.cellZ(),
                packet.radiusCells());
        } else {
            RadianceClient.LOGGER.warn(
                "Radiance cloud bridge: {}; leaving vanilla clouds active (status={} fancy={} faces={} bytes={} cell=({}, {}) radius={})",
                reason, packet.cloudStatus(), packet.fancy(), packet.quadCount(),
                packet.encodedFaceByteCount(), packet.cellX(), packet.cellZ(),
                packet.radiusCells(), throwable);
        }
    }

    public record EncodedCloudPacket(int colorArgb, int cloudStatus, boolean fancy,
                                     int relativeCameraPosition, int textureWidth,
                                     int textureHeight, int cellX, int cellZ, int radiusCells,
                                     int quadCount, float cloudBaseRelativeY,
                                     float cloudTopRelativeY, float cellOffsetX,
                                     float cellOffsetZ, double cameraX, double cameraY,
                                     double cameraZ, float bottomY, long gameTime,
                                     float partialTicks, ByteBuffer encodedFaces,
                                     int encodedFaceByteCount) {

        public boolean hasFaces() {
            return encodedFaces != null && encodedFaceByteCount > 0 && quadCount > 0;
        }

        public boolean hasValidShape() {
            return hasFaces() && encodedFaceByteCount % ENCODED_FACE_STRIDE_BYTES == 0
                && encodedFaceByteCount / ENCODED_FACE_STRIDE_BYTES == quadCount
                && encodedFaceByteCount <= encodedFaces.capacity();
        }
    }

    public record SubmitResult(SubmitStatus status, int nativeStatus) {

        private static SubmitResult fromNative(int nativeStatus) {
            return new SubmitResult(SubmitStatus.fromNative(nativeStatus), nativeStatus);
        }

        private static SubmitResult javaRejected() {
            return new SubmitResult(SubmitStatus.JAVA_REJECTED, 0);
        }

        private static SubmitResult nativeUnavailable() {
            return new SubmitResult(SubmitStatus.NATIVE_UNAVAILABLE, 0);
        }

        public boolean shouldCancelVanilla() {
            return status == SubmitStatus.ACCEPTED;
        }
    }

    public enum SubmitStatus {
        ACCEPTED,
        DECLINED_NO_RENDERER,
        DECLINED_INVALID_PACKET,
        DECLINED_UNSUPPORTED_PACKET,
        DECLINED_NATIVE_ERROR,
        STORED_NO_DRAW,
        JAVA_REJECTED,
        NATIVE_UNAVAILABLE,
        UNKNOWN_NATIVE_STATUS;

        private static SubmitStatus fromNative(int nativeStatus) {
            return switch (nativeStatus) {
                case NATIVE_ACCEPTED -> ACCEPTED;
                case NATIVE_DECLINED_NO_RENDERER -> DECLINED_NO_RENDERER;
                case NATIVE_DECLINED_INVALID_PACKET -> DECLINED_INVALID_PACKET;
                case NATIVE_DECLINED_UNSUPPORTED_PACKET -> DECLINED_UNSUPPORTED_PACKET;
                case NATIVE_DECLINED_NATIVE_ERROR -> DECLINED_NATIVE_ERROR;
                case NATIVE_STORED_NO_DRAW -> STORED_NO_DRAW;
                default -> UNKNOWN_NATIVE_STATUS;
            };
        }
    }
}

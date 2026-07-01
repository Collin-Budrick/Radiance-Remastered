package com.radiance.client.render.submit.replay;

import com.radiance.client.RadianceClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderPassReplayBridge {

    private static final Handler FALLBACK_HANDLER = RenderPassNativeReplayHandler::replay;
    private static final int MAX_LOGGED_PACKET_FALLBACKS = 16;
    private static final Set<String> LOGGED_PACKET_FALLBACKS = ConcurrentHashMap.newKeySet();
    private static volatile Handler handler = FALLBACK_HANDLER;

    private RenderPassReplayBridge() {
    }

    public static void setHandler(Handler handler) {
        RenderPassReplayBridge.handler = handler == null ? FALLBACK_HANDLER : handler;
    }

    public static void clearHandler(Handler expectedHandler) {
        if (handler == expectedHandler) {
            handler = FALLBACK_HANDLER;
        }
    }

    public static boolean hasHandler() {
        return handler != null;
    }

    public static List<String> getLastMissingNativeFields() {
        return RenderPassNativeReplayHandler.getLastMissingFields();
    }

    public static boolean tryReplay(RenderPassDrawPacket packet) {
        if (packet == null) {
            return false;
        }
        if (!packet.canAttemptNativeReplay()) {
            RenderPassPacketCapture.rememberReplayFallback(packet, packet.fallbackReason(),
                packet.fallbackDetails());
            logPacketFallback(packet);
            return false;
        }

        Handler activeHandler = handler;
        if (activeHandler == null) {
            RenderPassPacketCapture.rememberReplayFallback(packet,
                RenderPassDrawPacket.FallbackReason.NO_NATIVE_REPLAY_HANDLER,
                List.of(RenderPassDrawPacket.FallbackDetail.of(
                    RenderPassDrawPacket.FallbackReason.NO_NATIVE_REPLAY_HANDLER,
                    "handler", "no native replay handler is registered", true)));
            return false;
        }

        try {
            ReplayResult result = activeHandler.replay(packet);
            if (result != null && result.handled()) {
                return true;
            }

            RenderPassPacketCapture.rememberReplayFallback(packet,
                result == null || result.fallbackReason() == null
                    ? RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED
                    : result.fallbackReason(),
                result == null ? List.of() : result.fallbackDetails());
            return false;
        } catch (Throwable ex) {
            RenderPassPacketCapture.rememberReplayFallback(packet,
                RenderPassDrawPacket.FallbackReason.BRIDGE_FAILED,
                List.of(RenderPassDrawPacket.FallbackDetail.of(
                    RenderPassDrawPacket.FallbackReason.BRIDGE_FAILED,
                    "bridge", ex.getClass().getName(), true)));
            return false;
        }
    }

    private static void logPacketFallback(RenderPassDrawPacket packet) {
        String key = logKey(packet);
        if (LOGGED_PACKET_FALLBACKS.size() < MAX_LOGGED_PACKET_FALLBACKS
            && LOGGED_PACKET_FALLBACKS.add(key)) {
            RadianceClient.LOGGER.info(
                "Radiance render-pass capture remains vanilla: captured PreparedRenderType draw packet requires fallback reason={}; pipeline={}; renderType={}; details={}",
                packet.fallbackReason(),
                packet.pipeline() == null ? null : packet.pipeline().location(),
                packet.renderType() == null ? null : packet.renderType().name(),
                packet.fallbackDetails());
        }
    }

    private static String logKey(RenderPassDrawPacket packet) {
        String pipeline = packet == null || packet.pipeline() == null
            ? "<missing>"
            : String.valueOf(packet.pipeline().location());
        String renderType = packet == null || packet.renderType() == null
            ? "<missing>"
            : packet.renderType().name();
        return pipeline + "|" + renderType + "|"
            + (packet == null ? null : packet.fallbackReason());
    }

    @FunctionalInterface
    public interface Handler {

        ReplayResult replay(RenderPassDrawPacket packet);
    }

    public record ReplayResult(boolean handled,
                               RenderPassDrawPacket.FallbackReason fallbackReason,
                               List<RenderPassDrawPacket.FallbackDetail> fallbackDetails) {

        public ReplayResult(boolean handled,
            RenderPassDrawPacket.FallbackReason fallbackReason) {
            this(handled, fallbackReason, List.of());
        }

        public ReplayResult {
            fallbackReason = fallbackReason == null
                ? RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED
                : fallbackReason;
            fallbackDetails = fallbackDetails == null ? List.of() : List.copyOf(fallbackDetails);
        }

        public static ReplayResult handledResult() {
            return new ReplayResult(true, RenderPassDrawPacket.FallbackReason.NONE);
        }

        public static ReplayResult rejected(RenderPassDrawPacket.FallbackReason fallbackReason) {
            return new ReplayResult(false, fallbackReason == null
                ? RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED
                : fallbackReason);
        }

        public static ReplayResult rejected(RenderPassDrawPacket.FallbackReason fallbackReason,
            List<RenderPassDrawPacket.FallbackDetail> fallbackDetails) {
            return new ReplayResult(false, fallbackReason == null
                ? RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED
                : fallbackReason, fallbackDetails);
        }
    }
}

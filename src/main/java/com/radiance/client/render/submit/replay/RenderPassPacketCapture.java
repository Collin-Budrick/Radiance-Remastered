package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.renderpass.RenderPassPipelineState;
import com.radiance.client.renderpass.RenderPassPipelineStateBridge;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

public final class RenderPassPacketCapture {

    private static final Map<PreparedRenderType, PreparedMetadata> PREPARED_METADATA =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<PreparedRenderType, RenderPassPipelineState> PREPARED_PIPELINE_STATE =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicReference<RenderPassDrawPacket> LAST_PACKET =
        new AtomicReference<>();
    private static final AtomicBoolean LOGGED_CAPTURE_FAILURE = new AtomicBoolean();

    private RenderPassPacketCapture() {
    }

    public static void rememberPreparedRenderType(PreparedRenderType prepared,
        PreparedMetadata metadata) {
        if (!RendererAvailability.isRendererLifecycleActive() || prepared == null
            || metadata == null) {
            return;
        }

        PREPARED_METADATA.put(prepared, metadata);
    }

    public static void rememberPipelineState(PreparedRenderType prepared,
        RenderPassPipelineState pipelineState) {
        if (!RendererAvailability.isRendererLifecycleActive() || prepared == null
            || pipelineState == null) {
            return;
        }

        PREPARED_PIPELINE_STATE.put(prepared, pipelineState);
    }

    public static Optional<RenderPassPipelineState> getPipelineState(PreparedRenderType prepared) {
        return Optional.ofNullable(prepared == null ? null : PREPARED_PIPELINE_STATE.get(prepared));
    }

    public static RenderPassDrawPacket captureDraw(PreparedRenderType prepared,
        GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int baseVertex,
        int firstIndex, int indexCount) {
        if (!RendererAvailability.isRendererLifecycleActive() || prepared == null) {
            return null;
        }

        try {
            if (!PREPARED_PIPELINE_STATE.containsKey(prepared)) {
                RenderPassPipelineStateBridge.rememberPreparedOnly(prepared);
            }
            RenderPassPipelineState pipelineState = PREPARED_PIPELINE_STATE.get(prepared);
            RenderPassDrawPacket packet = RenderPassDrawPacket.capture(prepared,
                PREPARED_METADATA.get(prepared), pipelineState, vertexBuffer, indexBuffer,
                indexType, baseVertex, firstIndex, indexCount);
            LAST_PACKET.set(packet);
            return packet;
        } catch (RuntimeException ex) {
            if (LOGGED_CAPTURE_FAILURE.compareAndSet(false, true)) {
                RadianceClient.LOGGER.warn(
                    "Radiance render-pass capture fell back to vanilla after a 26.2 packet metadata mismatch.",
                    ex);
            }
            return null;
        }
    }

    public static void rememberReplayFallback(RenderPassDrawPacket packet,
        RenderPassDrawPacket.FallbackReason fallbackReason) {
        if (packet != null) {
            LAST_PACKET.set(packet.withFallbackReason(fallbackReason));
        }
    }

    public static void rememberReplayFallback(RenderPassDrawPacket packet,
        RenderPassDrawPacket.FallbackReason fallbackReason,
        List<RenderPassDrawPacket.FallbackDetail> fallbackDetails) {
        if (packet != null) {
            LAST_PACKET.set(packet.withFallbackReason(fallbackReason, fallbackDetails));
        }
    }

    public static Optional<RenderPassDrawPacket> getLastPacket() {
        return Optional.ofNullable(LAST_PACKET.get());
    }

    public record PreparedMetadata(RenderPassDrawPacket.RenderTypeIdentity renderType,
                                   RenderPassDrawPacket.RenderFlags flags) {
    }
}

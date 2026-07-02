package com.radiance.client.render.submit.replay;

import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RenderPassNativeReplayHandler {

    private static final int MAX_LOGGED_NATIVE_REPLAY_BLOCKERS = 16;
    private static final Set<String> LOGGED_NATIVE_REPLAY_BLOCKERS =
        ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_NATIVE_REPLAY_ACCEPTED =
        ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_LINE_REPLAY_ACCEPTED =
        ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_ITEM_GLINT_REPLAY_ACCEPTED =
        ConcurrentHashMap.newKeySet();
    private static volatile List<String> lastMissingFields = List.of();

    private RenderPassNativeReplayHandler() {
    }

    static RenderPassReplayBridge.ReplayResult replay(RenderPassDrawPacket packet) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return RenderPassReplayBridge.ReplayResult.rejected(
                RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED,
                List.of(detail(RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED,
                    "rendererLifecycle", "native renderer lifecycle is not active", true)));
        }

        List<RenderPassDrawPacket.FallbackDetail> blockers = nativeReplayBlockers(packet);
        if (!blockers.isEmpty()) {
            lastMissingFields = fields(blockers);
            logNativeReplayBlockers(packet, blockers);
            return RenderPassReplayBridge.ReplayResult.rejected(reasonFor(blockers), blockers);
        }

        List<RenderPassDrawPacket.FallbackDetail> shaderBlockers =
            RenderPassReplayShaders.replayShaderBlockers(packet);
        if (!shaderBlockers.isEmpty()) {
            lastMissingFields = fields(shaderBlockers);
            logNativeReplayBlockers(packet, shaderBlockers);
            return RenderPassReplayBridge.ReplayResult.rejected(
                reasonFor(shaderBlockers), shaderBlockers);
        }

        int status = DrawCommandProxy.RenderPass.tryReplayDrawPacket(
            packet.nativeDrawCall().toProxyPacket());
        if (status == DrawCommandProxy.RenderPass.STATUS_REPLAYED) {
            lastMissingFields = List.of();
            logNativeReplayAccepted(packet);
            return RenderPassReplayBridge.ReplayResult.handledResult();
        }

        RenderPassDrawPacket.FallbackReason fallbackReason = reasonForStatus(status);
        List<RenderPassDrawPacket.FallbackDetail> details = List.of(detail(fallbackReason,
            "nativeReplayStatus", "DrawCommandProxy.RenderPass returned status " + status,
            true));
        lastMissingFields = fields(details);
        logNativeReplayBlockers(packet, details);
        return RenderPassReplayBridge.ReplayResult.rejected(fallbackReason, details);
    }

    static List<String> getLastMissingFields() {
        return lastMissingFields;
    }

    private static void logNativeReplayAccepted(RenderPassDrawPacket packet) {
        if (packet == null || packet.nativeDrawCall() == null) {
            return;
        }
        RenderPassDrawPacket.NativeDrawCall drawCall = packet.nativeDrawCall();
        String key = pipelineLocation(packet) + "|" + renderTypeName(packet) + "|"
            + drawCall.target() + "|" + drawCall.vertexFormatType() + "|"
            + drawCall.drawMode();
        if (!LOGGED_NATIVE_REPLAY_ACCEPTED.add(key)) {
            return;
        }
        RadianceClient.LOGGER.info(
            "Radiance render-pass replay accepted by native overlay path: pipeline={}, renderType={}, shaderId={}, vertexFormat={}, drawMode={}, indexCount={}, uniformSize={}",
            pipelineLocation(packet), renderTypeName(packet), drawCall.shaderId(),
            drawCall.vertexFormatType(), drawCall.drawMode(), drawCall.indexCount(),
            drawCall.uniformSize());
        logLineReplayAccepted(packet, drawCall);
        logItemGlintReplayAccepted(packet, drawCall);
    }

    private static void logItemGlintReplayAccepted(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall drawCall) {
        if (!isItemGlintReplayAcceptance(packet, drawCall)) {
            return;
        }
        String key = pipelineLocation(packet) + "|" + renderTypeName(packet);
        if (!LOGGED_ITEM_GLINT_REPLAY_ACCEPTED.add(key)) {
            return;
        }
        RadianceClient.LOGGER.info(
            "RADIANCE_ITEM_GLINT_RENDERPASS_NATIVE_ACCEPTED_26_2 pipeline={} renderType={} target={} flags={} vertexFormat={} drawMode={} indexCount={} uniformSize={}",
            pipelineLocation(packet), renderTypeName(packet), drawCall.target(),
            drawCall.flags(), drawCall.vertexFormatType(), drawCall.drawMode(),
            drawCall.indexCount(), drawCall.uniformSize());
    }

    private static void logLineReplayAccepted(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall drawCall) {
        if (!isLineReplayAcceptance(packet, drawCall)) {
            return;
        }
        String key = pipelineLocation(packet) + "|" + renderTypeName(packet);
        if (!LOGGED_LINE_REPLAY_ACCEPTED.add(key)) {
            return;
        }
        RadianceClient.LOGGER.info(
            "Radiance line replay proof: accepted native replay pipeline={} renderType={} target={} flags={} vertexFormat={} drawMode={} indexCount={} uniformSize={}",
            pipelineLocation(packet), renderTypeName(packet), drawCall.target(),
            drawCall.flags(), drawCall.vertexFormatType(), drawCall.drawMode(),
            drawCall.indexCount(), drawCall.uniformSize());
    }

    private static boolean isLineReplayAcceptance(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall drawCall) {
        return "minecraft:pipeline/lines".equals(String.valueOf(pipelineLocation(packet)))
            && drawCall.target() == DrawCommandProxy.RenderPass.TARGET_ITEM_ENTITY
            && drawCall.flags() == DrawCommandProxy.RenderPass.lineFlags()
            && drawCall.vertexFormatType()
                == DrawCommandProxy.RenderPass.VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
            && drawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_LINES;
    }

    private static boolean isItemGlintReplayAcceptance(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall drawCall) {
        String renderType = renderTypeName(packet);
        return drawCall != null
            && drawCall.target() == DrawCommandProxy.RenderPass.TARGET_NON_OPAQUE_ENTITY
            && drawCall.flags() == DrawCommandProxy.RenderPass.FLAG_INDEXED
            && drawCall.vertexFormatType() == DrawCommandProxy.RenderPass.VERTEX_FORMAT_ENTITY
            && "minecraft:pipeline/glint".equals(pipelineLocation(packet))
            && ("glint".equals(renderType)
                || "glint_translucent".equals(renderType)
                || "entity_glint".equals(renderType)
                || "armor_entity_glint".equals(renderType));
    }

    private static List<RenderPassDrawPacket.FallbackDetail> nativeReplayBlockers(
        RenderPassDrawPacket packet) {
        ArrayList<RenderPassDrawPacket.FallbackDetail> blockers = new ArrayList<>();
        if (packet == null) {
            blockers.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_PACKET, "packet",
                "render-pass replay was asked to handle a null packet", true));
            return blockers;
        }

        RenderPassDrawPacket.NativeDrawCall nativeDrawCall = packet.nativeDrawCall();
        if (nativeDrawCall == null) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_DRAW_PACKET_FIELDS,
                "nativeDrawCall", "packet has no native draw-call projection", true));
            return blockers;
        }

        ReplayPassClassification replayPass = classifyReplayPass(packet, nativeDrawCall);
        if (!replayPass.requirementsAvailable()) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_DRAW_PACKET_FIELDS,
                "pipeline.nonOpaqueState",
                "non-opaque render-pass replay candidate cannot be classified safely; missing "
                    + replayPass.detail(),
                true));
        } else if (replayPass.nonOpaque()
            && !isBoundedNonOpaqueEntityReplayTarget(nativeDrawCall)) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_TARGET,
                "pass." + replayPass.pass().logName(),
                "non-opaque render-pass replay classified as " + replayPass.pass().logName()
                    + " from packet-local state (" + replayPass.detail()
                    + "), but no descriptor-correct TARGET_NON_OPAQUE_ENTITY packet was built",
                false));
        } else if (!replayPass.nativeSupported()) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_RENDER_FLAGS,
                "flags",
                "native supported subset is solid opaque indexed draws, plus descriptor-correct item-entity line draws; "
                    + replayPass.detail(),
                false));
        }
        if (!nativeDrawCall.targetSupported() || nativeDrawCall.target() < 0) {
            blockers.add(detail(
                packet.outputTarget() == null || packet.outputTarget().failure() != null
                    ? RenderPassDrawPacket.FallbackReason.MISSING_OUTPUT_TARGET
                    : RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_TARGET,
                "target",
                "native replay target could not be resolved for the captured output target",
                true));
        }
        if (!isSupportedDrawMode(nativeDrawCall)) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_MODE,
                "drawMode",
                isLineReplayShape(nativeDrawCall)
                    ? "native line replay requires drawMode=LINES and POSITION_COLOR_NORMAL_LINE_WIDTH vertex format"
                    : "native supported drawMode is LINES, TRIANGLES, or QUADS", false));
        }
        if (nativeDrawCall.firstIndex() < 0 || nativeDrawCall.firstInstance() < 0
            || nativeDrawCall.instanceCount() < 1) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_OFFSETS,
                "index",
                "native replay requires firstIndex >= 0, firstInstance >= 0, and instanceCount >= 1",
                false));
        }
        if (nativeDrawCall.scissorEnabled()
            && (nativeDrawCall.scissorWidth() <= 0 || nativeDrawCall.scissorHeight() <= 0)) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_OFFSETS,
                "scissor",
                "native replay requires enabled scissor width and height to be positive",
                false));
        }
        if (nativeDrawCall.vertexFormatType() < 0) {
            blockers.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_VERTEX_FORMAT,
                "vertexFormatType", "native vertex format mapping is unavailable", true));
        }
        if (!nativeDrawCall.hasVertexBufferId()) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_VERTEX_BUFFER_ID,
                "vertexBuffer.nativeReference.nativeId",
                "vertex buffer mirror has no native id; serial="
                    + serial(packet.vertexBuffer()) + ", source="
                    + source(packet.vertexBuffer()), true));
        }
        if (!nativeDrawCall.hasIndexBufferId()) {
            blockers.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_INDEX_BUFFER_ID,
                "indexBuffer.nativeReference.nativeId",
                "index buffer mirror has no native id; serial="
                    + serial(packet.indexBuffer()) + ", source="
                    + source(packet.indexBuffer()), true));
        }
        return blockers;
    }

    private static boolean isSolidOpaque(RenderPassDrawPacket packet) {
        RenderPassDrawPacket.RenderFlags flags = packet.flags();
        return flags != null
            && flags.metadataAvailable()
            && !flags.blending()
            && !flags.sortOnUpload()
            && !flags.outline();
    }

    private static boolean isBoundedLineReplayTarget(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall nativeDrawCall) {
        return packet != null
            && nativeDrawCall != null
            && nativeDrawCall.target() == DrawCommandProxy.RenderPass.TARGET_ITEM_ENTITY
            && nativeDrawCall.flags() == DrawCommandProxy.RenderPass.lineFlags()
            && nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_LINES
            && nativeDrawCall.vertexFormatType()
                == DrawCommandProxy.RenderPass.VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;
    }

    private static boolean isBoundedNonOpaqueEntityReplayTarget(
        RenderPassDrawPacket.NativeDrawCall nativeDrawCall) {
        return nativeDrawCall != null
            && nativeDrawCall.target() == DrawCommandProxy.RenderPass.TARGET_NON_OPAQUE_ENTITY
            && nativeDrawCall.flags() == DrawCommandProxy.RenderPass.FLAG_INDEXED
            && nativeDrawCall.vertexFormatType() == DrawCommandProxy.RenderPass.VERTEX_FORMAT_ENTITY
            && (nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_TRIANGLES
                || nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_QUADS);
    }

    private static ReplayPassClassification classifyReplayPass(RenderPassDrawPacket packet,
        RenderPassDrawPacket.NativeDrawCall nativeDrawCall) {
        if (isSolidOpaque(packet)) {
            return ReplayPassClassification.nativeSupported(ReplayPass.SOLID_OPAQUE,
                "solid opaque render flags");
        }
        if (isBoundedLineReplayTarget(packet, nativeDrawCall)) {
            return ReplayPassClassification.nativeSupported(ReplayPass.BOUNDED_LINES,
                "item-entity LINES target with POSITION_COLOR_NORMAL_LINE_WIDTH");
        }
        if (isBoundedNonOpaqueEntityReplayTarget(nativeDrawCall)) {
            return ReplayPassClassification.nativeSupported(nonOpaquePass(packet),
                "known non-opaque ENTITY target with packet-local blend/depth/cull state");
        }

        RenderPassDrawPacket.RenderFlags flags = packet == null ? null : packet.flags();
        if (flags == null || !flags.metadataAvailable()) {
            return ReplayPassClassification.unsupported("render flags metadata unavailable");
        }
        if (!flags.blending() && !flags.sortOnUpload() && !flags.outline()) {
            return ReplayPassClassification.unsupported(
                "packet is not solid opaque, bounded lines, or a captured non-opaque render pass");
        }

        RenderPassDrawPacket.PipelineBinding pipeline = packet.pipeline();
        List<String> missing = missingNonOpaqueRequirements(pipeline);
        ReplayPass pass = nonOpaquePass(packet);
        if (!missing.isEmpty()) {
            return ReplayPassClassification.missing(pass, String.join(", ", missing));
        }

        return ReplayPassClassification.nonOpaque(pass,
            "renderType=" + renderTypeName(packet)
                + ", pipeline=" + pipelineLocation(packet)
                + ", vertexShader=" + pipeline.vertexShader()
                + ", fragmentShader=" + pipeline.fragmentShader()
                + ", cull=" + pipeline.cull()
                + ", depth=" + describeDepth(pipeline.depth())
                + ", blend=" + describeBlend(pipeline.colorTargets()));
    }

    private static List<String> missingNonOpaqueRequirements(
        RenderPassDrawPacket.PipelineBinding pipeline) {
        ArrayList<String> missing = new ArrayList<>();
        if (pipeline == null) {
            missing.add("pipeline");
            return missing;
        }
        if (pipeline.location() == null) {
            missing.add("pipeline.location");
        }
        if (pipeline.vertexShader() == null) {
            missing.add("pipeline.vertexShader");
        }
        if (pipeline.fragmentShader() == null) {
            missing.add("pipeline.fragmentShader");
        }
        if (pipeline.depth() == null) {
            missing.add("pipeline.depth");
        }
        if (pipeline.colorTargets() == null || pipeline.colorTargets().isEmpty()) {
            missing.add("pipeline.colorTargets");
        } else if (pipeline.colorTargets().stream()
            .anyMatch(target -> target == null || target.blend() == null)) {
            missing.add("pipeline.colorTargets.blend");
        }
        return missing;
    }

    private static ReplayPass nonOpaquePass(RenderPassDrawPacket packet) {
        String renderType = renderTypeName(packet);
        String pipeline = String.valueOf(pipelineLocation(packet));
        if ("eyes".equals(renderType) || pipeline.contains("eyes")) {
            return ReplayPass.EYES;
        }
        if ((renderType != null && renderType.startsWith("entity_translucent"))
            || pipeline.contains("entity_translucent")) {
            return ReplayPass.ENTITY_TRANSLUCENT;
        }
        return ReplayPass.RELATED_NON_OPAQUE;
    }

    private static String describeDepth(
        com.radiance.client.renderpass.RenderPassPipelineState.DepthState depth) {
        if (depth == null) {
            return "<missing>";
        }
        return "{test=" + depth.depthTest() + ", write=" + depth.writeDepth()
            + ", biasScale=" + depth.depthBiasScaleFactor()
            + ", biasConstant=" + depth.depthBiasConstant() + "}";
    }

    private static String describeBlend(
        List<com.radiance.client.renderpass.RenderPassPipelineState.ColorTargetStateSnapshot> targets) {
        if (targets == null || targets.isEmpty()) {
            return "<missing>";
        }
        return targets.stream()
            .map(target -> "target" + target.index() + "{enabled="
                + (target.blend() != null && target.blend().enabled())
                + ", color=" + describeBlendEquation(target.blend() == null
                    ? null : target.blend().color())
                + ", alpha=" + describeBlendEquation(target.blend() == null
                    ? null : target.blend().alpha())
                + ", writeMask=" + target.writeMask() + "}")
            .toList()
            .toString();
    }

    private static String describeBlendEquation(
        com.radiance.client.renderpass.RenderPassPipelineState.BlendEquationState equation) {
        if (equation == null) {
            return "<none>";
        }
        return equation.sourceFactor() + "/" + equation.destFactor() + "/" + equation.op();
    }

    private static boolean isSupportedDrawMode(RenderPassDrawPacket.NativeDrawCall nativeDrawCall) {
        if (isLineReplayShape(nativeDrawCall)) {
                return nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_LINES
                && nativeDrawCall.vertexFormatType()
                    == DrawCommandProxy.RenderPass.VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;
        }
        return nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_TRIANGLES
            || nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_QUADS;
    }

    private static boolean isLineReplayShape(RenderPassDrawPacket.NativeDrawCall nativeDrawCall) {
        return nativeDrawCall.drawMode() == DrawCommandProxy.RenderPass.DRAW_MODE_LINES
            || nativeDrawCall.vertexFormatType()
                == DrawCommandProxy.RenderPass.VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;
    }

    private static RenderPassDrawPacket.FallbackReason reasonFor(
        List<RenderPassDrawPacket.FallbackDetail> blockers) {
        return hasOnlyUnsupportedSubsetBlockers(blockers)
            ? RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET
            : RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_DRAW_PACKET_FIELDS;
    }

    private static boolean hasOnlyUnsupportedSubsetBlockers(
        List<RenderPassDrawPacket.FallbackDetail> blockers) {
        return blockers.stream()
            .noneMatch(blocker -> switch (blocker.reason()) {
                case MISSING_PACKET,
                    MISSING_OUTPUT_TARGET,
                    MISSING_NATIVE_TARGET,
                    MISSING_TEXTURE_OR_SAMPLER,
                    MISSING_VERTEX_FORMAT,
                    MISSING_NATIVE_VERTEX_BUFFER_ID,
                    MISSING_NATIVE_INDEX_BUFFER_ID,
                    MISSING_NATIVE_SHADER_ID,
                    MISSING_NATIVE_UNIFORM_POINTER,
                    MISSING_NATIVE_UNIFORM_SIZE,
                    MISSING_NATIVE_DRAW_PACKET_FIELDS -> true;
                default -> false;
            });
    }

    private static RenderPassDrawPacket.FallbackReason reasonForStatus(int status) {
        if (status == DrawCommandProxy.RenderPass.STATUS_DROPPED_NO_NATIVE) {
            return RenderPassDrawPacket.FallbackReason.NATIVE_REPLAY_DROPPED_NO_NATIVE;
        }
        if (status == DrawCommandProxy.RenderPass.STATUS_DROPPED_UNSUPPORTED) {
            return RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET;
        }
        if (status == DrawCommandProxy.RenderPass.STATUS_DROPPED_INVALID) {
            return RenderPassDrawPacket.FallbackReason.NATIVE_REPLAY_DROPPED_INVALID;
        }
        if (status == DrawCommandProxy.RenderPass.STATUS_DROPPED_NATIVE_ERROR) {
            return RenderPassDrawPacket.FallbackReason.NATIVE_REPLAY_DROPPED_NATIVE_ERROR;
        }
        return RenderPassDrawPacket.FallbackReason.BRIDGE_REJECTED;
    }

    private static long serial(RenderPassDrawPacket.BufferBinding binding) {
        return binding == null || binding.nativeReference() == null
            ? 0L
            : binding.nativeReference().mirrorSerial();
    }

    private static String source(RenderPassDrawPacket.BufferBinding binding) {
        return binding == null || binding.nativeReference() == null
            ? "missing"
            : binding.nativeReference().source();
    }

    private static RenderPassDrawPacket.FallbackDetail detail(
        RenderPassDrawPacket.FallbackReason reason, String field, String message,
        boolean retryable) {
        return RenderPassDrawPacket.FallbackDetail.of(reason, field, message, retryable);
    }

    private enum ReplayPass {
        SOLID_OPAQUE("solid_opaque"),
        BOUNDED_LINES("bounded_lines"),
        EYES("eyes"),
        ENTITY_TRANSLUCENT("entity_translucent"),
        RELATED_NON_OPAQUE("related_non_opaque");

        private final String logName;

        ReplayPass(String logName) {
            this.logName = logName;
        }

        String logName() {
            return logName;
        }
    }

    private record ReplayPassClassification(ReplayPass pass, boolean requirementsAvailable,
                                            boolean nativeSupported, boolean nonOpaque,
                                            String detail) {

        private static ReplayPassClassification nativeSupported(ReplayPass pass, String detail) {
            return new ReplayPassClassification(pass, true, true, false, detail);
        }

        private static ReplayPassClassification unsupported(String detail) {
            return new ReplayPassClassification(ReplayPass.RELATED_NON_OPAQUE, true, false,
                false, detail);
        }

        private static ReplayPassClassification missing(ReplayPass pass, String detail) {
            return new ReplayPassClassification(pass, false, false, true, detail);
        }

        private static ReplayPassClassification nonOpaque(ReplayPass pass, String detail) {
            return new ReplayPassClassification(pass, true, false, true, detail);
        }
    }

    private static List<String> fields(List<RenderPassDrawPacket.FallbackDetail> details) {
        return details.stream()
            .map(detail -> detail.field() + ": " + detail.message())
            .toList();
    }

    private static void logNativeReplayBlockers(RenderPassDrawPacket packet,
        List<RenderPassDrawPacket.FallbackDetail> blockers) {
        String key = logKey(packet, blockers);
        if (LOGGED_NATIVE_REPLAY_BLOCKERS.size() < MAX_LOGGED_NATIVE_REPLAY_BLOCKERS
            && LOGGED_NATIVE_REPLAY_BLOCKERS.add(key)) {
            RadianceClient.LOGGER.info(
                "Radiance render-pass replay remains vanilla: DrawCommandProxy.RenderPass.DrawPacket cannot be built or replayed from the current PreparedRenderType capture. Missing/unsupported fields: {}. pipeline={}, renderType={}",
                blockers, pipelineLocation(packet), renderTypeName(packet));
        }
    }

    private static Object pipelineLocation(RenderPassDrawPacket packet) {
        return packet == null || packet.pipeline() == null ? null : packet.pipeline().location();
    }

    private static String renderTypeName(RenderPassDrawPacket packet) {
        return packet == null || packet.renderType() == null ? null : packet.renderType().name();
    }

    private static String logKey(RenderPassDrawPacket packet,
        List<RenderPassDrawPacket.FallbackDetail> blockers) {
        String pipeline = packet == null || packet.pipeline() == null
            ? "<missing>"
            : String.valueOf(packet.pipeline().location());
        String renderType = packet == null || packet.renderType() == null
            ? "<missing>"
            : packet.renderType().name();
        String fields = blockers == null ? "<none>" : blockers.stream()
            .map(blocker -> blocker.reason() + ":" + blocker.field())
            .sorted()
            .toList()
            .toString();
        return pipeline + "|" + renderType + "|" + fields;
    }
}

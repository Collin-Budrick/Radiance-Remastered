package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.renderpass.RenderPassPipelineState;
import com.radiance.client.state.RenderSystemStateBridge;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public record RenderPassDrawPacket(RenderTypeIdentity renderType,
                                   PipelineBinding pipeline,
                                   OutputTargetBinding outputTarget,
                                   NativeDrawCall nativeDrawCall,
                                   BufferBinding vertexBuffer,
                                   BufferBinding indexBuffer,
                                   IndexDrawInfo index,
                                   BufferSliceBinding dynamicTransforms,
                                   ScissorBinding scissor,
                                   List<TextureBinding> textures,
                                   List<UniformBinding> uniforms,
                                   RenderFlags flags,
                                    FallbackReason fallbackReason,
                                    List<FallbackDetail> fallbackDetails) {

    private static final Identifier LINES_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/lines");

    public RenderPassDrawPacket {
        textures = textures == null ? List.of() : List.copyOf(textures);
        uniforms = uniforms == null ? List.of() : List.copyOf(uniforms);
        fallbackReason = fallbackReason == null ? FallbackReason.NONE : fallbackReason;
        fallbackDetails = fallbackDetails == null ? List.of() : List.copyOf(fallbackDetails);
    }

    public static RenderPassDrawPacket capture(PreparedRenderType prepared,
        RenderPassPacketCapture.PreparedMetadata metadata,
        RenderPassPipelineState pipelineState, GpuBuffer vertexBuffer,
        GpuBuffer indexBuffer, IndexType indexType, int baseVertex, int firstIndex,
        int indexCount) {
        PipelineBinding pipeline = PipelineBinding.capture(prepared.pipeline(),
            pipelineState == null ? null : pipelineState.pipeline());
        RenderTypeIdentity renderType = metadata == null
            ? RenderTypeIdentity.unknown()
            : metadata.renderType();
        RenderFlags flags = metadata == null ? RenderFlags.unknown() : metadata.flags();
        BufferBinding vertexBinding = BufferBinding.capture(vertexBuffer);
        BufferBinding indexBinding = BufferBinding.capture(indexBuffer);
        BufferSliceBinding transforms = BufferSliceBinding.capture(prepared.dynamicTransforms());
        List<TextureBinding> textures = captureTextures(prepared.textures(), pipelineState);
        List<UniformBinding> uniforms = captureUniforms(prepared.pipeline(), pipeline, textures,
            transforms);
        IndexDrawInfo index = new IndexDrawInfo(indexType, indexType == null ? 0 : indexType.bytes,
            indexCount, 1, firstIndex, baseVertex, 0);
        ScissorBinding scissor = ScissorBinding.capture(prepared.scissorState());
        OutputTargetBinding outputTarget = OutputTargetBinding.capture(prepared.outputTarget(),
            pipelineState == null ? null : pipelineState.outputTarget());
        NativeDrawCall nativeDrawCall = NativeDrawCall.capture(renderType, outputTarget, pipeline,
            vertexBinding, indexBinding, index, uniforms, flags, scissor);
        FallbackReport fallbackReport = classifyFallback(metadata, pipeline, vertexBinding,
            indexBinding, transforms, textures, indexType, indexCount);

        return new RenderPassDrawPacket(renderType, pipeline, outputTarget, nativeDrawCall,
            vertexBinding, indexBinding, index, transforms, scissor, textures, uniforms, flags,
            fallbackReport.reason(), fallbackReport.details());
    }

    public RenderPassDrawPacket withFallbackReason(FallbackReason fallbackReason) {
        return withFallbackReason(fallbackReason,
            fallbackReason == null || fallbackReason == FallbackReason.NONE ? List.of()
                : List.of(FallbackDetail.of(fallbackReason, "packet", fallbackReason.name(),
                    true)));
    }

    public RenderPassDrawPacket withFallbackReason(FallbackReason fallbackReason,
        List<FallbackDetail> fallbackDetails) {
        return new RenderPassDrawPacket(renderType, pipeline, outputTarget, nativeDrawCall,
            vertexBuffer, indexBuffer, index, dynamicTransforms, scissor, textures, uniforms,
            flags, fallbackReason, fallbackDetails);
    }

    public boolean canAttemptNativeReplay() {
        return fallbackReason == FallbackReason.NONE;
    }

    private static List<TextureBinding> captureTextures(
        List<PreparedRenderType.Texture> textures, RenderPassPipelineState pipelineState) {
        if (textures == null || textures.isEmpty()) {
            return List.of();
        }

        List<RenderPassPipelineState.PreparedTextureState> textureStates =
            pipelineState == null ? List.of() : pipelineState.textures();
        ArrayList<TextureBinding> bindings = new ArrayList<>(textures.size());
        for (int i = 0; i < textures.size(); i++) {
            TextureBinding binding = TextureBinding.capture(textures.get(i),
                i < textureStates.size() ? textureStates.get(i) : null);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    private static List<UniformBinding> captureUniforms(RenderPipeline renderPipeline,
        PipelineBinding pipeline, List<TextureBinding> textures,
        BufferSliceBinding dynamicTransforms) {
        ArrayList<UniformBinding> bindings = new ArrayList<>();
        List<UniformBinding> defaultUniforms = UniformBinding.captureDefaultUniforms();
        UniformBinding simpleUniform = RenderPassReplayShaders.simpleUniform(renderPipeline,
            pipeline, textures, dynamicTransforms, defaultUniforms);
        if (simpleUniform != null) {
            bindings.add(simpleUniform);
        }
        bindings.addAll(defaultUniforms);
        return bindings;
    }

    private static FallbackReport classifyFallback(
        RenderPassPacketCapture.PreparedMetadata metadata, PipelineBinding pipeline,
        BufferBinding vertexBuffer, BufferBinding indexBuffer,
        BufferSliceBinding dynamicTransforms, List<TextureBinding> textures,
        IndexType indexType, int indexCount) {
        ArrayList<FallbackDetail> details = new ArrayList<>();
        if (metadata == null) {
            addFallback(details, FallbackReason.MISSING_RENDER_TYPE_METADATA, "renderType",
                "PreparedRenderType draw was captured without RenderType.prepare metadata",
                false);
        }
        if (pipeline == null || pipeline.pipeline() == null
            || pipeline.primitiveTopology() == null) {
            addFallback(details, FallbackReason.MISSING_PIPELINE, "pipeline",
                "PreparedRenderType draw does not expose a complete RenderPipeline", false);
        }
        if (vertexBuffer == null || vertexBuffer.buffer() == null) {
            addFallback(details, FallbackReason.MISSING_VERTEX_BUFFER, "vertexBuffer",
                "drawFromBuffer did not provide a vertex GpuBuffer", false);
        }
        if (indexBuffer == null || indexBuffer.buffer() == null) {
            addFallback(details, FallbackReason.MISSING_INDEX_BUFFER, "indexBuffer",
                "drawFromBuffer did not provide an index GpuBuffer", false);
        }
        if (indexType == null) {
            addFallback(details, FallbackReason.MISSING_INDEX_TYPE, "index.indexType",
                "drawFromBuffer did not provide an index type", false);
        }
        if (indexCount <= 0) {
            addFallback(details, FallbackReason.EMPTY_INDEX_COUNT, "index.indexCount",
                "drawFromBuffer requested no indices", false);
        }
        if (vertexBuffer != null && vertexBuffer.closed()) {
            addFallback(details, FallbackReason.CLOSED_VERTEX_BUFFER, "vertexBuffer.closed",
                "vertex GpuBuffer is already closed", true);
        }
        if (indexBuffer != null && indexBuffer.closed()) {
            addFallback(details, FallbackReason.CLOSED_INDEX_BUFFER, "indexBuffer.closed",
                "index GpuBuffer is already closed", true);
        }
        if (dynamicTransforms == null || dynamicTransforms.slice() == null
            || dynamicTransforms.buffer() == null
            || dynamicTransforms.buffer().buffer() == null) {
            addFallback(details, FallbackReason.MISSING_DYNAMIC_TRANSFORMS,
                "dynamicTransforms",
                "PreparedRenderType draw has no dynamic transform buffer slice", true);
        }
        for (TextureBinding texture : textures) {
            if (texture.textureView() == null || texture.sampler() == null) {
                addFallback(details, FallbackReason.MISSING_TEXTURE_OR_SAMPLER,
                    "texture." + texture.name(),
                    "PreparedRenderType texture is missing a view or sampler", true);
            }
        }
        return FallbackReport.of(details);
    }

    private static void addFallback(List<FallbackDetail> details, FallbackReason reason,
        String field, String message, boolean retryable) {
        details.add(FallbackDetail.of(reason, field, message, retryable));
    }

    private record FallbackReport(FallbackReason reason, List<FallbackDetail> details) {

        private static FallbackReport of(List<FallbackDetail> details) {
            if (details.isEmpty()) {
                return new FallbackReport(FallbackReason.NONE, List.of());
            }
            return new FallbackReport(details.getFirst().reason(), List.copyOf(details));
        }
    }

    public record RenderTypeIdentity(RenderType renderType, String name, String description,
                                     boolean metadataAvailable) {

        public static RenderTypeIdentity capture(RenderType renderType, String name) {
            return new RenderTypeIdentity(renderType, name, String.valueOf(renderType), true);
        }

        static RenderTypeIdentity unknown() {
            return new RenderTypeIdentity(null, "<unknown>", "<missing RenderType.prepare metadata>",
                false);
        }
    }

    public record PipelineBinding(RenderPipeline pipeline, Identifier location,
                                  Identifier vertexShader, Identifier fragmentShader,
                                  String description, PrimitiveTopology primitiveTopology,
                                  String polygonMode, boolean cull,
                                  boolean wantsDepthTexture, int sortKey,
                                  List<VertexFormat> vertexFormats,
                                  NativePipelineReference nativeReference) {

        static PipelineBinding capture(RenderPipeline pipeline,
            RenderPassPipelineState.PipelineState state) {
            if (pipeline == null) {
                return new PipelineBinding(null, state == null ? null : state.location(),
                    state == null ? null : state.vertexShader(),
                    state == null ? null : state.fragmentShader(), "<missing pipeline>",
                    null, state == null ? null : state.polygonMode(),
                    state != null && state.cull(), state != null && state.wantsDepthTexture(),
                    state == null ? 0 : state.sortKey(), List.of(),
                    NativePipelineReference.missing());
            }
            VertexFormat[] vertexFormatBindings = pipeline.getVertexFormatBindings();
            List<VertexFormat> vertexFormats = vertexFormatBindings == null ? List.of()
                : Arrays.stream(vertexFormatBindings)
                    .filter(Objects::nonNull)
                    .toList();
            RenderPassReplayShaders.preparePipelineShader(pipeline, vertexFormats);
            return new PipelineBinding(pipeline, pipeline.getLocation(),
                pipeline.getVertexShader(), pipeline.getFragmentShader(),
                String.valueOf(pipeline), pipeline.getPrimitiveTopology(),
                state == null ? null : state.polygonMode(),
                state != null && state.cull(),
                state != null && state.wantsDepthTexture(), pipeline.getSortKey(),
                vertexFormats, RenderPassNativeResourceMirror.capturePipeline(pipeline));
        }
    }

    public record OutputTargetBinding(OutputTarget outputTarget, String name,
                                      RenderTarget renderTarget, int renderTargetIdentity,
                                      int width, int height, boolean useDepth,
                                      GpuTextureView colorTextureView,
                                      GpuTextureView depthTextureView,
                                      TextureObjectMetadata colorTexture,
                                      TextureObjectMetadata depthTexture, String failure) {

        static OutputTargetBinding capture(OutputTarget outputTarget,
            RenderPassPipelineState.TargetState state) {
            if (outputTarget == null) {
                return new OutputTargetBinding(null, "<missing output target>", null, 0, 0, 0,
                    false, null, null, TextureObjectMetadata.fromState(
                        state == null ? null : state.colorTexture()),
                    TextureObjectMetadata.fromState(state == null ? null : state.depthTexture()),
                    "missing outputTarget");
            }
            try {
                RenderTarget renderTarget = outputTarget.getRenderTarget();
                GpuTextureView colorTextureView = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : renderTarget.getColorTextureView();
                GpuTextureView depthTextureView = !renderTarget.useDepth ? null
                    : RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride
                        : renderTarget.getDepthTextureView();
                return new OutputTargetBinding(outputTarget, String.valueOf(outputTarget),
                    renderTarget, System.identityHashCode(renderTarget), renderTarget.width,
                    renderTarget.height, renderTarget.useDepth, colorTextureView,
                    depthTextureView,
                    TextureObjectMetadata.fromState(state == null ? null : state.colorTexture()),
                    TextureObjectMetadata.fromState(state == null ? null : state.depthTexture()),
                    null);
            } catch (RuntimeException ex) {
                return new OutputTargetBinding(outputTarget, String.valueOf(outputTarget), null,
                    state == null ? 0 : state.renderTargetIdentity(),
                    state == null ? 0 : state.width(), state == null ? 0 : state.height(),
                    state != null && state.useDepth(), null, null,
                    TextureObjectMetadata.fromState(state == null ? null : state.colorTexture()),
                    TextureObjectMetadata.fromState(state == null ? null : state.depthTexture()),
                    ex.getClass().getName());
            }
        }
    }

    public record TextureObjectMetadata(int nativeTextureId,
                                        boolean nativeTextureIdAvailable,
                                        String label, int objectIdentity,
                                        int width, int height, int depthOrLayers,
                                        int mipLevels, String format, int usage,
                                        boolean closed) {

        static TextureObjectMetadata fromState(RenderPassPipelineState.TextureObjectState state) {
            if (state == null) {
                return null;
            }
            return new TextureObjectMetadata(state.textureHandle(), state.textureHandle() > 0,
                state.label(), state.objectIdentity(), state.width(), state.height(),
                state.depthOrLayers(), state.mipLevels(), state.format(), state.usage(),
                state.closed());
        }
    }

    public record BufferBinding(GpuBuffer buffer, long size, int usage, boolean closed,
                                NativeBufferReference nativeReference) {

        static BufferBinding capture(GpuBuffer buffer) {
            return buffer == null ? new BufferBinding(null, 0L, 0, false,
                NativeBufferReference.missing())
                : new BufferBinding(buffer, buffer.size(), buffer.usage(), buffer.isClosed(),
                    RenderPassNativeResourceMirror.captureBuffer(buffer));
        }

        boolean hasNativeId() {
            return nativeReference != null && nativeReference.hasNativeId();
        }
    }

    public record BufferSliceBinding(GpuBufferSlice slice, BufferBinding buffer, long offset,
                                     long length) {

        static BufferSliceBinding capture(GpuBufferSlice slice) {
            return slice == null ? new BufferSliceBinding(null, null, 0L, 0L)
                : new BufferSliceBinding(slice, BufferBinding.capture(slice.buffer()),
                    slice.offset(), slice.length());
        }
    }

    public record IndexDrawInfo(IndexType indexType, int indexElementBytes, int indexCount,
                                int instanceCount, int firstIndex, int baseVertex,
                                int firstInstance) {
    }

    public record ScissorBinding(boolean enabled, int x, int y, int width, int height) {

        static ScissorBinding capture(ScissorState scissorState) {
            return scissorState == null ? new ScissorBinding(false, 0, 0, 0, 0)
                : new ScissorBinding(scissorState.enabled(), scissorState.x(), scissorState.y(),
                    scissorState.width(), scissorState.height());
        }
    }

    public record TextureBinding(String name, Identifier identifier,
                                 GpuTextureView textureView, GpuTexture texture,
                                 String textureLabel, int textureIdentity,
                                 int nativeTextureId, boolean nativeTextureIdAvailable,
                                 int baseMipLevel, int mipLevels, int width, int height,
                                 String format, boolean textureClosed,
                                 boolean textureViewClosed, GpuSampler sampler,
                                 SamplerBinding samplerState) {

        static TextureBinding capture(PreparedRenderType.Texture texture,
            RenderPassPipelineState.PreparedTextureState state) {
            if (texture == null) {
                return new TextureBinding("<missing texture>", null, null, null, null, 0, 0,
                    false, 0, 0, 0, 0, null, false, false, null, null);
            }
            GpuTextureView textureView = texture.textureView();
            boolean textureViewClosed = textureView != null && textureView.isClosed();
            GpuTexture gpuTexture = textureView == null || textureViewClosed
                ? null
                : textureView.texture();
            int baseMipLevel = textureView == null || textureViewClosed
                ? 0
                : textureView.baseMipLevel();
            TextureObjectMetadata metadata = TextureObjectMetadata.fromState(
                state == null ? null : state.texture());
            int nativeTextureId = metadata == null ? 0 : metadata.nativeTextureId();
            return new TextureBinding(texture.name(), state == null ? null : state.identifier(),
                textureView, gpuTexture, gpuTexture == null ? null : gpuTexture.getLabel(),
                metadata == null ? System.identityHashCode(gpuTexture) : metadata.objectIdentity(),
                nativeTextureId, nativeTextureId > 0, baseMipLevel,
                textureView == null || textureViewClosed ? 0 : textureView.mipLevels(),
                textureView == null || textureViewClosed ? 0 : textureView.getWidth(baseMipLevel),
                textureView == null || textureViewClosed ? 0 : textureView.getHeight(baseMipLevel),
                gpuTexture == null ? null : String.valueOf(gpuTexture.getFormat()),
                gpuTexture != null && gpuTexture.isClosed(),
                textureViewClosed, texture.sampler(),
                SamplerBinding.capture(texture.sampler()));
        }
    }

    public record SamplerBinding(AddressMode addressModeU, AddressMode addressModeV,
                                 FilterMode minFilter, FilterMode magFilter,
                                 int maxAnisotropy, OptionalDouble maxLod) {

        static SamplerBinding capture(GpuSampler sampler) {
            return sampler == null ? null
                : new SamplerBinding(sampler.getAddressModeU(), sampler.getAddressModeV(),
                    sampler.getMinFilter(), sampler.getMagFilter(), sampler.getMaxAnisotropy(),
                    sampler.getMaxLod());
        }
    }

    public record UniformBinding(String name, UniformKind kind, BufferSliceBinding slice,
                                 BufferBinding buffer, NativeUniformReference nativeReference,
                                 String projectionType, boolean metadataAvailable) {

        static List<UniformBinding> captureDefaultUniforms() {
            RenderSystemStateBridge.DefaultUniformBindings defaults =
                RenderSystemStateBridge.defaultUniformBindings();
            if (defaults == null) {
                return List.of();
            }
            ArrayList<UniformBinding> bindings = new ArrayList<>(4);
            bindings.add(slice(RenderSystemStateBridge.PROJECTION_UNIFORM, defaults.projection(),
                defaults.projectionType() == null ? null : defaults.projectionType().name()));
            bindings.add(slice(RenderSystemStateBridge.FOG_UNIFORM, defaults.fog(), null));
            bindings.add(buffer(RenderSystemStateBridge.GLOBALS_UNIFORM, defaults.globals()));
            bindings.add(slice(RenderSystemStateBridge.LIGHTING_UNIFORM, defaults.lighting(),
                null));
            return bindings.stream()
                .filter(Objects::nonNull)
                .toList();
        }

        private static UniformBinding slice(String name, GpuBufferSlice slice,
            String projectionType) {
            if (slice == null) {
                return null;
            }
            return new UniformBinding(name, UniformKind.SLICED_BUFFER,
                BufferSliceBinding.capture(slice), null,
                RenderPassNativeResourceMirror.captureUniform(slice, name), projectionType,
                true);
        }

        private static UniformBinding buffer(String name, GpuBuffer buffer) {
            if (buffer == null) {
                return null;
            }
            return new UniformBinding(name, UniformKind.BUFFER, null,
                BufferBinding.capture(buffer),
                RenderPassNativeResourceMirror.captureUniform(buffer, name), null, true);
        }

        boolean hasUniformPayload() {
            return nativeReference != null && nativeReference.hasUniformPayload();
        }
    }

    public enum UniformKind {
        SLICED_BUFFER,
        BUFFER
    }

    public record NativeDrawCall(int target, boolean targetSupported, int flags,
                                  boolean scissorEnabled, int scissorX, int scissorY,
                                  int scissorWidth, int scissorHeight,
                                  int vertexBufferId, long vertexBufferSerial,
                                  int indexBufferId, long indexBufferSerial, int shaderId,
                                  long pipelineSerial, int vertexFormatType, int drawMode,
                                 int indexType, int indexCount, int firstIndex,
                                 int vertexOffset, int firstInstance, int instanceCount,
                                 long uniformPtr, int uniformSize, ByteBuffer vertexPayload,
                                 ByteBuffer indexPayload) {

        static NativeDrawCall capture(RenderTypeIdentity renderType,
            OutputTargetBinding outputTarget, PipelineBinding pipeline, BufferBinding vertexBuffer,
            BufferBinding indexBuffer, IndexDrawInfo index, List<UniformBinding> uniforms,
            RenderFlags flags, ScissorBinding scissor) {
            boolean lineReplay = isBoundedLineReplay(renderType, outputTarget, pipeline);
            boolean solidOpaqueTarget = isSolidOpaque(flags)
                && outputTarget != null
                && outputTarget.failure() == null;
            boolean supportedTarget = solidOpaqueTarget || lineReplay;
            NativeBufferReference vertexReference = vertexBuffer == null
                || vertexBuffer.nativeReference() == null
                ? NativeBufferReference.missing()
                : vertexBuffer.nativeReference();
            NativeBufferReference indexReference = indexBuffer == null
                || indexBuffer.nativeReference() == null
                ? NativeBufferReference.missing()
                : indexBuffer.nativeReference();
            NativePipelineReference pipelineReference = pipeline == null
                || pipeline.nativeReference() == null
                ? NativePipelineReference.missing()
                : pipeline.nativeReference();
            NativeUniformReference uniformReference = firstNativeUniform(uniforms);
            ByteBuffer vertexPayload = replayPayload(vertexBuffer == null ? null
                : vertexBuffer.buffer(), requiredVertexPayloadSize(vertexBuffer, pipeline, index));
            ByteBuffer indexPayload = replayPayload(indexBuffer == null ? null
                : indexBuffer.buffer(), requiredIndexPayloadSize(indexBuffer, index));

            return new NativeDrawCall(
                lineReplay ? DrawCommandProxy.RenderPass.TARGET_ITEM_ENTITY
                    : solidOpaqueTarget ? DrawCommandProxy.RenderPass.TARGET_SOLID_OPAQUE : -1,
                supportedTarget,
                lineReplay ? DrawCommandProxy.RenderPass.lineFlags()
                    : DrawCommandProxy.RenderPass.flags(solidOpaqueTarget, solidOpaqueTarget,
                        true),
                scissor != null && scissor.enabled(),
                scissor == null ? 0 : scissor.x(),
                scissor == null ? 0 : scissor.y(),
                scissor == null ? 0 : scissor.width(),
                scissor == null ? 0 : scissor.height(),
                vertexReference.hasNativeId() ? vertexReference.nativeId() : -1,
                vertexReference.mirrorSerial(),
                indexReference.hasNativeId() ? indexReference.nativeId() : -1,
                indexReference.mirrorSerial(),
                pipelineReference.hasShaderId() ? pipelineReference.shaderId() : -1,
                pipelineReference.mirrorSerial(),
                vertexFormatType(pipeline),
                DrawCommandProxy.RenderPass.drawModeValue(
                    pipeline == null ? null : pipeline.primitiveTopology()),
                DrawCommandProxy.RenderPass.indexTypeValue(index == null ? null
                    : index.indexType()),
                index == null ? 0 : index.indexCount(),
                index == null ? 0 : index.firstIndex(),
                index == null ? 0 : index.baseVertex(),
                index == null ? 0 : index.firstInstance(),
                index == null ? 0 : index.instanceCount(),
                uniformReference.hasUniformPayload() ? uniformReference.uniformPtr() : 0L,
                uniformReference.hasUniformPayload() ? uniformReference.uniformSize() : -1,
                vertexPayload, indexPayload);
        }

        DrawCommandProxy.RenderPass.DrawPacket toProxyPacket() {
            return new DrawCommandProxy.RenderPass.DrawPacket(target, flags, scissorEnabled,
                scissorX, scissorY, scissorWidth, scissorHeight, vertexBufferId, indexBufferId,
                shaderId, vertexFormatType, drawMode, indexType, indexCount, firstIndex,
                vertexOffset, firstInstance, instanceCount, uniformPtr, uniformSize,
                vertexPayload, indexPayload);
        }

        boolean hasVertexBufferId() {
            return vertexBufferId >= 0 || hasPayload(vertexPayload);
        }

        boolean hasIndexBufferId() {
            return indexBufferId >= 0 || hasPayload(indexPayload);
        }

        boolean hasShaderId() {
            return shaderId >= 0;
        }

        boolean hasUniformPayload() {
            return uniformSize >= 0 && (uniformSize == 0 || uniformPtr != 0L);
        }

        private static boolean isSolidOpaque(RenderFlags flags) {
            return flags != null
                && flags.metadataAvailable()
                && !flags.blending()
                && !flags.sortOnUpload()
                && !flags.outline();
        }

        private static boolean isBoundedLineReplay(RenderTypeIdentity renderType,
            OutputTargetBinding outputTarget, PipelineBinding pipeline) {
            return renderType != null
                && renderType.metadataAvailable()
                && "lines".equals(renderType.name())
                && pipeline != null
                && LINES_PIPELINE.equals(pipeline.location())
                && pipeline.primitiveTopology() == PrimitiveTopology.LINES
                && outputTarget != null
                && outputTarget.failure() == null
                && outputTarget.outputTarget() == OutputTarget.ITEM_ENTITY_TARGET;
        }

        private static int vertexFormatType(PipelineBinding pipeline) {
            if (pipeline == null || pipeline.vertexFormats().isEmpty()
                || pipeline.vertexFormats().getFirst() == null) {
                return -1;
            }
            return DrawCommandProxy.RenderPass.vertexFormatType(pipeline.vertexFormats().getFirst());
        }

        private static NativeUniformReference firstNativeUniform(List<UniformBinding> uniforms) {
            NativeUniformReference replayUniform = namedNativeUniform(uniforms,
                "radianceLineReplay", "radianceEntityReplay", "radianceSimpleTexture",
                "radianceSimpleColor");
            if (replayUniform != null) {
                return replayUniform;
            }
            if (uniforms != null) {
                for (UniformBinding uniform : uniforms) {
                    if (uniform != null && uniform.hasUniformPayload()) {
                        return uniform.nativeReference();
                    }
                }
            }
            return NativeUniformReference.missing("<uniform>");
        }

        private static NativeUniformReference namedNativeUniform(List<UniformBinding> uniforms,
            String... names) {
            if (uniforms == null || names == null) {
                return null;
            }
            for (String name : names) {
                for (UniformBinding uniform : uniforms) {
                    if (uniform != null && name.equals(uniform.name())
                        && uniform.hasUniformPayload()) {
                        return uniform.nativeReference();
                    }
                }
            }
            return null;
        }

        private static int requiredVertexPayloadSize(BufferBinding binding,
            PipelineBinding pipeline, IndexDrawInfo index) {
            if (binding == null || binding.buffer() == null || pipeline == null
                || pipeline.vertexFormats().isEmpty() || pipeline.vertexFormats().getFirst() == null
                || index == null || index.indexCount() <= 0) {
                return -1;
            }
            int vertexSize = pipeline.vertexFormats().getFirst().getVertexSize();
            if (vertexSize <= 0) {
                return -1;
            }
            long vertexCount = requiredVertexCount(pipeline.primitiveTopology(), index.indexCount());
            long firstVertex = Math.max(0, index.baseVertex());
            long end = (firstVertex + vertexCount) * (long) vertexSize;
            return boundedPayloadSize(binding, end);
        }

        private static long requiredVertexCount(PrimitiveTopology topology, int indexCount) {
            if (topology == PrimitiveTopology.QUADS) {
                return ((long) indexCount + 5L) / 6L * 4L;
            }
            return indexCount;
        }

        private static int requiredIndexPayloadSize(BufferBinding binding, IndexDrawInfo index) {
            if (binding == null || binding.buffer() == null || index == null
                || index.indexElementBytes() <= 0 || index.indexCount() <= 0) {
                return -1;
            }
            long end = ((long) index.firstIndex() + (long) index.indexCount())
                * (long) index.indexElementBytes();
            return boundedPayloadSize(binding, end);
        }

        private static int boundedPayloadSize(BufferBinding binding, long requiredEnd) {
            if (requiredEnd <= 0L || requiredEnd > Integer.MAX_VALUE
                || binding.size() <= 0L) {
                return -1;
            }
            long capped = Math.min(requiredEnd, binding.size());
            return capped > Integer.MAX_VALUE ? -1 : (int) capped;
        }

        private static ByteBuffer replayPayload(GpuBuffer buffer, int requiredSize) {
            byte[] bytes = BufferProxy.copyMirroredBufferContent(buffer);
            if (bytes == null || bytes.length == 0) {
                if (requiredSize <= 0) {
                    return null;
                }
                bytes = BufferProxy.copyMirroredBufferRange(buffer, 0L, requiredSize);
                if (bytes == null || bytes.length == 0) {
                    return null;
                }
            }
            ByteBuffer payload = ByteBuffer.allocateDirect(bytes.length);
            payload.put(bytes);
            payload.flip();
            return payload;
        }

        private static boolean hasPayload(ByteBuffer payload) {
            return payload != null && payload.isDirect() && payload.capacity() > 0;
        }
    }

    public record NativeBufferReference(long mirrorSerial, int identityHash, int nativeId,
                                        boolean nativeIdAvailable, long nativeSerial,
                                        boolean nativeSerialAvailable, String source) {

        static NativeBufferReference missing() {
            return new NativeBufferReference(0L, 0, -1, false, 0L, false, "missing");
        }

        boolean hasNativeId() {
            return nativeIdAvailable && nativeId >= 0;
        }
    }

    public record NativePipelineReference(long mirrorSerial, int identityHash,
                                          int nativePipelineId,
                                          boolean nativePipelineIdAvailable,
                                          int shaderId, boolean shaderIdAvailable,
                                          long nativeSerial,
                                          boolean nativeSerialAvailable, String source) {

        static NativePipelineReference missing() {
            return new NativePipelineReference(0L, 0, -1, false, -1, false, 0L, false,
                "missing");
        }

        boolean hasShaderId() {
            return shaderIdAvailable && shaderId >= 0;
        }
    }

    public record NativeUniformReference(long mirrorSerial, int identityHash, String name,
                                         long uniformPtr, int uniformSize,
                                         boolean uniformPtrAvailable,
                                         boolean uniformSizeAvailable,
                                         int nativeBinding,
                                         boolean nativeBindingAvailable,
                                         long nativeSerial,
                                         boolean nativeSerialAvailable, String source) {

        static NativeUniformReference missing(String name) {
            return new NativeUniformReference(0L, 0, name, 0L, -1, false, false, -1, false,
                0L, false, "missing");
        }

        boolean hasUniformPayload() {
            return uniformSizeAvailable && uniformSize >= 0
                && (uniformSize == 0 || (uniformPtrAvailable && uniformPtr != 0L));
        }
    }

    public record RenderFlags(boolean useLightmap, boolean useOverlay,
                              boolean affectsCrumbling, boolean sortOnUpload,
                              boolean outline, boolean blending, boolean metadataAvailable) {

        public static RenderFlags capture(boolean useLightmap, boolean useOverlay,
            boolean affectsCrumbling, boolean sortOnUpload, boolean outline, boolean blending) {
            return new RenderFlags(useLightmap, useOverlay, affectsCrumbling, sortOnUpload,
                outline, blending, true);
        }

        static RenderFlags unknown() {
            return new RenderFlags(false, false, false, false, false, false, false);
        }
    }

    public record FallbackDetail(FallbackReason reason, String field, String message,
                                 boolean retryable) {

        public static FallbackDetail of(FallbackReason reason, String field,
            String message, boolean retryable) {
            return new FallbackDetail(reason == null ? FallbackReason.BRIDGE_REJECTED : reason,
                field == null ? "<unknown>" : field, message == null ? "" : message,
                retryable);
        }
    }

    public enum FallbackReason {
        NONE,
        MISSING_PACKET,
        MISSING_RENDER_TYPE_METADATA,
        MISSING_PIPELINE,
        MISSING_OUTPUT_TARGET,
        MISSING_VERTEX_BUFFER,
        MISSING_INDEX_BUFFER,
        MISSING_INDEX_TYPE,
        EMPTY_INDEX_COUNT,
        CLOSED_VERTEX_BUFFER,
        CLOSED_INDEX_BUFFER,
        MISSING_DYNAMIC_TRANSFORMS,
        MISSING_TEXTURE_OR_SAMPLER,
        MISSING_NATIVE_TARGET,
        UNSUPPORTED_NATIVE_TARGET,
        UNSUPPORTED_NATIVE_RENDER_FLAGS,
        UNSUPPORTED_NATIVE_DRAW_MODE,
        UNSUPPORTED_NATIVE_OFFSETS,
        MISSING_VERTEX_FORMAT,
        MISSING_NATIVE_VERTEX_BUFFER_ID,
        MISSING_NATIVE_INDEX_BUFFER_ID,
        MISSING_NATIVE_SHADER_ID,
        MISSING_NATIVE_UNIFORM_POINTER,
        MISSING_NATIVE_UNIFORM_SIZE,
        MISSING_NATIVE_DRAW_PACKET_FIELDS,
        UNSUPPORTED_NATIVE_DRAW_PACKET,
        NATIVE_REPLAY_DROPPED_NO_NATIVE,
        NATIVE_REPLAY_DROPPED_INVALID,
        NATIVE_REPLAY_DROPPED_NATIVE_ERROR,
        NO_NATIVE_REPLAY_HANDLER,
        BRIDGE_REJECTED,
        BRIDGE_FAILED
    }
}

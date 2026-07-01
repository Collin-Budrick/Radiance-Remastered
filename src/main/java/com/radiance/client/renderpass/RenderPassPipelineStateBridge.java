package com.radiance.client.renderpass;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendEquation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.RendererAvailability;
import com.radiance.client.render.submit.replay.RenderPassPacketCapture;
import com.radiance.client.renderpass.RenderPassPipelineState.BlendEquationState;
import com.radiance.client.renderpass.RenderPassPipelineState.BlendState;
import com.radiance.client.renderpass.RenderPassPipelineState.ColorTargetStateSnapshot;
import com.radiance.client.renderpass.RenderPassPipelineState.DepthState;
import com.radiance.client.renderpass.RenderPassPipelineState.DynamicTransformState;
import com.radiance.client.renderpass.RenderPassPipelineState.PipelineState;
import com.radiance.client.renderpass.RenderPassPipelineState.PreparedTextureState;
import com.radiance.client.renderpass.RenderPassPipelineState.RenderPassSetupState;
import com.radiance.client.renderpass.RenderPassPipelineState.SamplerState;
import com.radiance.client.renderpass.RenderPassPipelineState.ScissorStateSnapshot;
import com.radiance.client.renderpass.RenderPassPipelineState.TargetState;
import com.radiance.client.renderpass.RenderPassPipelineState.TextureObjectState;
import com.radiance.client.texture.TextureTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class RenderPassPipelineStateBridge {

    private static final Map<PreparedRenderType, RenderPassPipelineState> PREPARED_STATES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<RenderPassPipelineState> CURRENT_STATE = new ThreadLocal<>();

    private RenderPassPipelineStateBridge() {
    }

    public static boolean shouldCapture() {
        return RendererAvailability.isRendererLifecycleActive();
    }

    public static RenderPassPipelineState remember(RenderType renderType,
        RenderPassSetupState setup, PreparedRenderType prepared) {
        if (!shouldCapture() || prepared == null) {
            return null;
        }

        RenderPassPipelineState state = snapshot(renderType, setup, prepared);
        PREPARED_STATES.put(prepared, state);
        RenderPassPacketCapture.rememberPipelineState(prepared, state);
        return state;
    }

    public static RenderPassPipelineState rememberPreparedOnly(PreparedRenderType prepared) {
        if (!shouldCapture() || prepared == null) {
            return null;
        }

        RenderPassPipelineState state = snapshot(null, null, prepared);
        PREPARED_STATES.put(prepared, state);
        RenderPassPacketCapture.rememberPipelineState(prepared, state);
        return state;
    }

    public static Optional<RenderPassPipelineState> stateFor(PreparedRenderType prepared) {
        if (prepared == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(PREPARED_STATES.get(prepared));
    }

    public static Optional<RenderPassPipelineState> currentState() {
        return Optional.ofNullable(CURRENT_STATE.get());
    }

    public static Optional<RenderPassPipelineState> currentPacketState() {
        return currentState();
    }

    public static Optional<RenderPassPipelineState> packetStateFor(PreparedRenderType prepared) {
        return stateFor(prepared);
    }

    public static void enterPreparedDraw(PreparedRenderType prepared) {
        if (!shouldCapture() || prepared == null) {
            CURRENT_STATE.remove();
            return;
        }

        RenderPassPipelineState state = PREPARED_STATES.get(prepared);
        if (state == null) {
            state = rememberPreparedOnly(prepared);
        }

        if (state == null) {
            CURRENT_STATE.remove();
        } else {
            CURRENT_STATE.set(state);
        }
    }

    public static void exitPreparedDraw() {
        CURRENT_STATE.remove();
    }

    private static RenderPassPipelineState snapshot(RenderType renderType,
        RenderPassSetupState setup, PreparedRenderType prepared) {
        return new RenderPassPipelineState(setup, pipelineState(prepared.pipeline()),
            targetState(prepared.outputTarget()),
            dynamicTransforms(prepared.dynamicTransforms()),
            scissorState(prepared.scissorState()),
            preparedTextures(prepared.textures()),
            renderType != null && renderType.canConsolidateConsecutiveGeometry());
    }

    private static PipelineState pipelineState(RenderPipeline pipeline) {
        if (pipeline == null) {
            return new PipelineState(null, null, null, 0, null, null, false, false,
                null, List.of());
        }

        ColorTargetState[] colorTargets = pipeline.getColorTargetStates();
        List<ColorTargetStateSnapshot> colorTargetStates = new ArrayList<>(colorTargets.length);
        for (int i = 0; i < colorTargets.length; i++) {
            ColorTargetState colorTarget = colorTargets[i];
            colorTargetStates.add(new ColorTargetStateSnapshot(i, name(colorTarget.format()),
                colorTarget.writeMask(), colorTarget.writeRed(), colorTarget.writeGreen(),
                colorTarget.writeBlue(), colorTarget.writeAlpha(),
                blendState(colorTarget.blendFunction().orElse(null))));
        }

        return new PipelineState(pipeline.getLocation(), pipeline.getVertexShader(),
            pipeline.getFragmentShader(), pipeline.getSortKey(),
            name(pipeline.getPrimitiveTopology()), name(pipeline.getPolygonMode()),
            pipeline.isCull(), pipeline.wantsDepthTexture(),
            depthState(pipeline.getDepthStencilState()), colorTargetStates);
    }

    private static DepthState depthState(DepthStencilState depth) {
        if (depth == null) {
            return null;
        }

        return new DepthState(name(depth.depthTest()), depth.writeDepth(),
            depth.depthBiasScaleFactor(), depth.depthBiasConstant());
    }

    private static BlendState blendState(BlendFunction blend) {
        if (blend == null) {
            return new BlendState(false, null, null);
        }

        return new BlendState(true, blendEquation(blend.color()), blendEquation(blend.alpha()));
    }

    private static BlendEquationState blendEquation(BlendEquation equation) {
        if (equation == null) {
            return null;
        }

        return new BlendEquationState(name(equation.sourceFactor()), name(equation.destFactor()),
            name(equation.op()));
    }

    private static TargetState targetState(OutputTarget outputTarget) {
        RenderTarget renderTarget;
        try {
            renderTarget = outputTarget == null ? null : outputTarget.getRenderTarget();
        } catch (RuntimeException ignored) {
            renderTarget = null;
        }

        if (renderTarget == null) {
            return TargetState.missing(outputTarget);
        }

        return new TargetState(String.valueOf(outputTarget), renderTarget.getClass().getName(),
            System.identityHashCode(renderTarget), renderTarget.width, renderTarget.height,
            renderTarget.useDepth, textureState(renderTarget.getColorTexture(), 0, false),
            textureState(renderTarget.getDepthTexture(), 0, false));
    }

    private static DynamicTransformState dynamicTransforms(GpuBufferSlice transforms) {
        if (transforms == null) {
            return null;
        }

        return new DynamicTransformState(System.identityHashCode(transforms.buffer()),
            transforms.offset(), transforms.length());
    }

    private static ScissorStateSnapshot scissorState(ScissorState scissor) {
        if (scissor == null) {
            return null;
        }

        return new ScissorStateSnapshot(scissor.enabled(), scissor.x(), scissor.y(),
            scissor.width(), scissor.height());
    }

    private static List<PreparedTextureState> preparedTextures(
        List<PreparedRenderType.Texture> textures) {
        if (textures == null || textures.isEmpty()) {
            return List.of();
        }

        List<PreparedTextureState> states = new ArrayList<>(textures.size());
        for (PreparedRenderType.Texture texture : textures) {
            states.add(preparedTexture(texture));
        }
        return states;
    }

    private static PreparedTextureState preparedTexture(PreparedRenderType.Texture texture) {
        GpuTextureView view = texture.textureView();
        GpuTexture gpuTexture = view == null || view.isClosed() ? null : view.texture();
        Identifier identifier = gpuTexture == null ? null : TextureTracker.gpuTextureToIdentifier
            .get(gpuTexture);
        int handle = textureHandle(texture.name(), identifier, gpuTexture);

        return new PreparedTextureState(texture.name(), identifier,
            textureState(gpuTexture, handle, view != null && view.isClosed()),
            view == null ? 0 : view.baseMipLevel(),
            view == null ? 0 : view.mipLevels(),
            samplerState(texture.sampler()));
    }

    private static TextureObjectState textureState(GpuTexture texture, int textureHandle,
        boolean viewClosed) {
        if (texture == null) {
            return null;
        }

        boolean closed = viewClosed || texture.isClosed();
        if (closed) {
            return new TextureObjectState(textureHandle, texture.getLabel(),
                System.identityHashCode(texture), 0, 0, 0, 0, name(texture.getFormat()),
                texture.usage(), true);
        }

        return new TextureObjectState(textureHandle, texture.getLabel(),
            System.identityHashCode(texture), texture.getWidth(0), texture.getHeight(0),
            texture.getDepthOrLayers(), texture.getMipLevels(), name(texture.getFormat()),
            texture.usage(), false);
    }

    private static int textureHandle(String samplerName, Identifier identifier,
        GpuTexture texture) {
        if (!shouldCapture() || texture == null || texture.isClosed()) {
            return 0;
        }

        try {
            if ("Sampler1".equals(samplerName) || "Sampler2".equals(samplerName)) {
                return TextureTracker.registerLightmapTexture(texture);
            }

            if (TextureTracker.shouldAllowSmallTexture(identifier, texture)) {
                int existing = TextureTracker.textureHandle(texture);
                return existing != 0 ? existing : TextureTracker.registerGuiTexture(texture);
            }

            return TextureTracker.getOrRegisterTextureHandle(texture);
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    private static SamplerState samplerState(GpuSampler sampler) {
        if (sampler == null) {
            return null;
        }

        try {
            return new SamplerState(name(sampler.getAddressModeU()), name(sampler.getAddressModeV()),
                name(sampler.getMinFilter()), name(sampler.getMagFilter()),
                sampler.getMaxAnisotropy(), sampler.getMaxLod().toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String name(Object value) {
        return value == null ? null : value.toString();
    }
}

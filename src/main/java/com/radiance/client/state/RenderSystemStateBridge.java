package com.radiance.client.state;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import org.joml.Matrix4f;

public final class RenderSystemStateBridge {

    public static final String PROJECTION_UNIFORM = "Projection";
    public static final String FOG_UNIFORM = "Fog";
    public static final String GLOBALS_UNIFORM = "Globals";
    public static final String LIGHTING_UNIFORM = "Lighting";

    private static volatile GpuBufferSlice projectionMatrixBuffer;
    private static volatile ProjectionType projectionType;
    private static volatile GpuBufferSlice savedProjectionMatrixBuffer;
    private static volatile ProjectionType savedProjectionType;
    private static volatile Matrix4f modelViewMatrix = new Matrix4f();
    private static volatile GpuBufferSlice shaderFog;
    private static volatile GpuBufferSlice shaderLights;
    private static volatile GpuBuffer globalSettingsUniform;
    private static volatile ScissorSnapshot renderTypeScissor =
        new ScissorSnapshot(false, 0, 0, 0, 0);
    private static volatile DefaultUniformBindings defaultUniformBindings =
        new DefaultUniformBindings(null, null, null, null, null, null);
    private static volatile boolean nativeScissorBridgeDisabled;
    private static volatile boolean loggedNativeScissorFailure;
    private static volatile boolean loggedDefaultUniformBridge;

    private RenderSystemStateBridge() {
    }

    public static void captureCurrentState(GpuBufferSlice projectionMatrix,
        ProjectionType type, Matrix4f modelView, GpuBufferSlice fog,
        GpuBufferSlice lights, GpuBuffer globals) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        projectionMatrixBuffer = projectionMatrix;
        projectionType = type;
        setModelViewMatrix(modelView);
        shaderFog = fog;
        shaderLights = lights;
        globalSettingsUniform = globals;
    }

    public static void setProjectionMatrix(GpuBufferSlice projectionMatrix,
        ProjectionType type, Matrix4f modelView) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        projectionMatrixBuffer = projectionMatrix;
        projectionType = type;
        setModelViewMatrix(modelView);
    }

    public static void backupProjectionMatrix(GpuBufferSlice projectionMatrix,
        ProjectionType type) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        savedProjectionMatrixBuffer = projectionMatrix;
        savedProjectionType = type;
    }

    public static void restoreProjectionMatrix(GpuBufferSlice projectionMatrix,
        ProjectionType type, Matrix4f modelView) {
        setProjectionMatrix(projectionMatrix, type, modelView);
    }

    public static void setModelViewMatrix(Matrix4f matrix) {
        if (!RendererAvailability.isRendererLifecycleActive() || matrix == null) {
            return;
        }

        modelViewMatrix = new Matrix4f(matrix);
    }

    public static void setShaderFog(GpuBufferSlice fog) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        shaderFog = fog;
    }

    public static void setShaderLights(GpuBufferSlice lights) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        shaderLights = lights;
    }

    public static void setGlobalSettingsUniform(GpuBuffer globals) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        globalSettingsUniform = globals;
    }

    public static void setRenderTypeScissor(boolean enabled, int x, int y,
        int width, int height) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        renderTypeScissor = new ScissorSnapshot(enabled, x, y, width, height);
        syncNativeScissor(enabled, x, y, width, height);
    }

    public static void setDefaultUniformBindings(GpuBufferSlice projection,
        GpuBufferSlice fog, GpuBuffer globals, GpuBufferSlice lights,
        ProjectionType type, Matrix4f modelView) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        projectionMatrixBuffer = projection;
        projectionType = type;
        shaderFog = fog;
        shaderLights = lights;
        globalSettingsUniform = globals;
        setModelViewMatrix(modelView);
        defaultUniformBindings = new DefaultUniformBindings(projection, fog, globals, lights,
            type, modelView == null ? null : new Matrix4f(modelView));
        if (!loggedDefaultUniformBridge) {
            loggedDefaultUniformBridge = true;
            RadianceClient.LOGGER.info(
                "Radiance RenderSystem bridge: captured 26.2 default uniform bindings Projection={}, Fog={}, Globals={}, Lighting={}",
                projection != null, fog != null, globals != null, lights != null);
        }
    }

    public static GpuBufferSlice projectionMatrixBuffer() {
        return projectionMatrixBuffer;
    }

    public static ProjectionType projectionType() {
        return projectionType;
    }

    public static GpuBufferSlice savedProjectionMatrixBuffer() {
        return savedProjectionMatrixBuffer;
    }

    public static ProjectionType savedProjectionType() {
        return savedProjectionType;
    }

    public static Matrix4f modelViewMatrixCopy() {
        return new Matrix4f(modelViewMatrix);
    }

    public static GpuBufferSlice shaderFog() {
        return shaderFog;
    }

    public static GpuBufferSlice shaderLights() {
        return shaderLights;
    }

    public static GpuBuffer globalSettingsUniform() {
        return globalSettingsUniform;
    }

    public static ScissorSnapshot renderTypeScissor() {
        return renderTypeScissor;
    }

    public static DefaultUniformBindings defaultUniformBindings() {
        return defaultUniformBindings;
    }

    private static void syncNativeScissor(boolean enabled, int x, int y, int width,
        int height) {
        if (nativeScissorBridgeDisabled) {
            return;
        }

        try {
            PipelineStateProxy.ViewportState.setScissorEnabled(enabled);
            if (enabled) {
                PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
            }
        } catch (LinkageError | RuntimeException e) {
            nativeScissorBridgeDisabled = true;
            if (!loggedNativeScissorFailure) {
                loggedNativeScissorFailure = true;
                RadianceClient.LOGGER.warn(
                    "Radiance RenderSystem scissor bridge disabled after native state update failed",
                    e);
            }
        }
    }

    public record ScissorSnapshot(boolean enabled, int x, int y, int width, int height) {
    }

    public record DefaultUniformBindings(GpuBufferSlice projection, GpuBufferSlice fog,
                                         GpuBuffer globals, GpuBufferSlice lighting,
                                         ProjectionType projectionType,
                                         Matrix4f modelViewMatrix) {
    }
}

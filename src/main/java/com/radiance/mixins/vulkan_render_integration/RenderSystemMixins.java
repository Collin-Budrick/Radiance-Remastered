package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.RendererAvailability;
import com.radiance.client.state.RenderSystemStateBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixins {

    @Inject(method = "setupDefaultState()V", at = @At("TAIL"))
    private static void radiance$afterSetupDefaultState(CallbackInfo ci) {
        radiance$captureCurrentState();
    }

    @Inject(method = "setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
        at = @At("TAIL"))
    private static void radiance$afterSetShaderFog(GpuBufferSlice shaderFog, CallbackInfo ci) {
        RenderSystemStateBridge.setShaderFog(shaderFog);
    }

    @Inject(method = "setShaderLights(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
        at = @At("TAIL"))
    private static void radiance$afterSetShaderLights(GpuBufferSlice shaderLights,
        CallbackInfo ci) {
        RenderSystemStateBridge.setShaderLights(shaderLights);
    }

    @Inject(method = "enableScissorForRenderTypeDraws(IIII)V", at = @At("TAIL"))
    private static void radiance$afterEnableScissorForRenderTypeDraws(int x, int y,
        int width, int height, CallbackInfo ci) {
        RenderSystemStateBridge.setRenderTypeScissor(true, x, y, width, height);
    }

    @Inject(method = "disableScissorForRenderTypeDraws()V", at = @At("TAIL"))
    private static void radiance$afterDisableScissorForRenderTypeDraws(CallbackInfo ci) {
        RenderSystemStateBridge.setRenderTypeScissor(false, 0, 0, 0, 0);
    }

    @Inject(method = "setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V",
        at = @At("TAIL"))
    private static void radiance$afterSetProjectionMatrix(GpuBufferSlice projectionMatrix,
        ProjectionType projectionType, CallbackInfo ci) {
        RenderSystemStateBridge.setProjectionMatrix(projectionMatrix, projectionType,
            RenderSystem.getModelViewMatrixCopy());
    }

    @Inject(method = "backupProjectionMatrix()V", at = @At("TAIL"))
    private static void radiance$afterBackupProjectionMatrix(CallbackInfo ci) {
        RenderSystemStateBridge.backupProjectionMatrix(RenderSystem.getProjectionMatrixBuffer(),
            RenderSystem.getProjectionType());
    }

    @Inject(method = "restoreProjectionMatrix()V", at = @At("TAIL"))
    private static void radiance$afterRestoreProjectionMatrix(CallbackInfo ci) {
        RenderSystemStateBridge.restoreProjectionMatrix(RenderSystem.getProjectionMatrixBuffer(),
            RenderSystem.getProjectionType(), RenderSystem.getModelViewMatrixCopy());
    }

    @Inject(method = "getModelViewMatrixCopy()Lorg/joml/Matrix4f;", at = @At("RETURN"))
    private static void radiance$afterGetModelViewMatrixCopy(
        CallbackInfoReturnable<org.joml.Matrix4f> cir) {
        RenderSystemStateBridge.setModelViewMatrix(cir.getReturnValue());
    }

    @Inject(method = "setGlobalSettingsUniform(Lcom/mojang/blaze3d/buffers/GpuBuffer;)V",
        at = @At("TAIL"))
    private static void radiance$afterSetGlobalSettingsUniform(GpuBuffer buffer,
        CallbackInfo ci) {
        RenderSystemStateBridge.setGlobalSettingsUniform(buffer);
    }

    @Inject(method = "bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V",
        at = @At("TAIL"))
    private static void radiance$afterBindDefaultUniforms(RenderPass renderPass,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        RenderSystemStateBridge.setDefaultUniformBindings(RenderSystem.getProjectionMatrixBuffer(),
            RenderSystem.getShaderFog(),
            RenderSystem.getGlobalSettingsUniform(),
            RenderSystem.getShaderLights(),
            RenderSystem.getProjectionType(),
            RenderSystem.getModelViewMatrixCopy());
    }

    private static void radiance$captureCurrentState() {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        RenderSystemStateBridge.captureCurrentState(RenderSystem.getProjectionMatrixBuffer(),
            RenderSystem.getProjectionType(),
            RenderSystem.getModelViewMatrixCopy(),
            RenderSystem.getShaderFog(),
            RenderSystem.getShaderLights(),
            RenderSystem.getGlobalSettingsUniform());
    }
}

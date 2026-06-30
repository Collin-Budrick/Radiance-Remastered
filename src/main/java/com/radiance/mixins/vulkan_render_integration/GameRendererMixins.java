package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private Matrix4f radiance$rotationMatrix = new Matrix4f();

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void radiance$updateNativeFrameGate(DeltaTracker tickCounter, boolean tick,
        CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }
        RendererProxy.shouldRenderWorld(tick && this.minecraft.isGameLoadFinished()
            && this.minecraft.level != null);
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void radiance$finishNativeLevelFrame(DeltaTracker tickCounter, CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }
        EntityProxy.build();
    }

    @Inject(method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
        at = @At("HEAD"), cancellable = true)
    private void radiance$skipVanillaHandRender(CameraRenderState cameraRenderState,
        float tickDelta, org.joml.Matrix4fc viewMatrix, CallbackInfo ci) {
        if (RendererAvailability.isRendererLifecycleActive()
            && this.minecraft.isGameLoadFinished()
            && this.minecraft.level != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void radiance$captureLevelViewMatrix(DeltaTracker tickCounter, CallbackInfo ci,
        @Local CameraRenderState cameraRenderState) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }
        this.radiance$rotationMatrix = new Matrix4f(cameraRenderState.viewRotationMatrix);
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return this.radiance$rotationMatrix;
    }
}

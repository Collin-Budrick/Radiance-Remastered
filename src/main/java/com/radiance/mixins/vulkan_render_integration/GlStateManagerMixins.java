package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.radiance.client.RendererAvailability;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixins {

    @Inject(method = "_activeTexture(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectActiveTexture(int texture, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        ci.cancel();
    }

    // region <PipelineStateProxy.ViewportState>
    @Inject(method = "_disableScissorTest()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectDisableScissorTest(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ViewportState.setScissorEnabled(false);
        ci.cancel();
    }

    @Inject(method = "_enableScissorTest()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectEnableScissorTest(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ViewportState.setScissorEnabled(true);
        ci.cancel();
    }

    @Inject(method = "_scissorBox(IIII)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
        ci.cancel();
    }

    @Inject(method = "_viewport(IIII)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectViewport(int x, int y, int width, int height, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ViewportState.setViewport(x, y, width, height);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ColorBlendState>
    @Inject(method = "_disableBlend(I)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectDisableBlend(int bufferIndex, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.setBlendEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableBlend(I)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectEnableBlend(int bufferIndex, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.setBlendEnable(true);
        ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectBlendFuncSeparate(int srcFactorRGB, int dstFactorRGB,
        int srcFactorAlpha, int dstFactorAlpha, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
            dstFactorRGB, dstFactorAlpha);
        ci.cancel();
    }

    @Inject(method = "_blendEquationSeparate(II)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectBlendEquationSeparate(int modeRGB, int modeAlpha,
        CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.glSetBlendOpSeparate(modeRGB, modeAlpha);
        ci.cancel();
    }

    @Inject(method = "_colorMask(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectColorMask(int colorMask, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.vkSetColorWriteMask(colorMask);
        ci.cancel();
    }

    @Inject(method = "_colorMask(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectIndexedColorMask(int bufferIndex, int colorMask, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.vkSetColorWriteMask(colorMask);
        ci.cancel();
    }

    @Inject(method = "_enableColorLogicOp()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectEnableColorLogicOp(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(true);
        ci.cancel();
    }

    @Inject(method = "_disableColorLogicOp()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectDisableColorLogicOp(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(false);
        ci.cancel();
    }

    @Inject(method = "_logicOp(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectLogicOp(int op, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ColorBlendState.glSetColorLogicOp(op);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.DepthStencilState>
    @Inject(method = "_disableDepthTest()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectDisableDepthTest(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.DepthStencilState.setDepthTestEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableDepthTest()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectEnableDepthTest(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.DepthStencilState.setDepthTestEnable(true);
        ci.cancel();
    }

    @Inject(method = "_depthFunc(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectDepthFunc(int func, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.DepthStencilState.glSetDepthCompareOp(func);
        ci.cancel();
    }

    @Inject(method = "_depthMask(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectDepthMask(boolean mask, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.DepthStencilState.setDepthWriteEnable(mask);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.RasterizationState>
    @Inject(method = "_enableCull()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectEnableCull(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.glSetCullMode(GL11.GL_BACK);
        ci.cancel();
    }

    @Inject(method = "_disableCull()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectDisableCull(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.vkSetCullMode(
            VulkanConstants.VkCullMode.VK_CULL_MODE_NONE.getValue());
        ci.cancel();
    }

    @Inject(method = "_polygonMode(II)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectPolygonMode(int face, int mode, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.glSetPolygonMode(mode);
        ci.cancel();
    }

    @Inject(method = "_enablePolygonOffset()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectEnablePolygonOffset(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, true);
        ci.cancel();
    }

    @Inject(method = "_disablePolygonOffset()V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectDisablePolygonOffset(CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, false);
        ci.cancel();
    }

    @Inject(method = "_polygonOffset(FF)V", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectPolygonOffset(float factor, float units, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.RasterizationState.glSetPolygonOffset(factor, units);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ClearState>
    @Inject(method = "_clearBuffer(ILorg/joml/Vector4fc;)V", at = @At("HEAD"),
        cancellable = true, remap = false)
    private static void redirectClearColor(int buffer, Vector4fc color, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ClearState.setClearColor(color.x(), color.y(), color.z(), color.w());
        ci.cancel();
    }

    @Inject(method = "_clearBuffer(D)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectClearDepth(double depth, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        PipelineStateProxy.ClearState.setClearDepth(depth);
        ci.cancel();
    }
    // endregion

    // region <DrawCommandProxy.Overlay>
    @Inject(method = "_clear(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void redirectClear(int mask, CallbackInfo ci) {
        if (!radiance$shouldVirtualizeGlState()) {
            return;
        }

        DrawCommandProxy.Overlay.glClear(mask);
        ci.cancel();
    }
    // endregion

    @Redirect(method = "_getString(I)Ljava/lang/String;",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL33C;glGetString(I)Ljava/lang/String;",
            remap = false),
        remap = false)
    private static String redirectGetString(int name) {
        if (!radiance$shouldVirtualizeGlState()) {
            return GL33C.glGetString(name);
        }

        return "Vulkan 1.4";
    }

    @Unique
    private static boolean radiance$shouldVirtualizeGlState() {
        return RendererAvailability.isRendererLifecycleActive();
    }
}

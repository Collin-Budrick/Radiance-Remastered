package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.renderpass.RenderPassPipelineStateBridge;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IRenderSetupStateExt;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public abstract class RenderPhaseMixins {

    @Shadow
    @Final
    private RenderSetup state;

    @Inject(method = "prepare()Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;",
        at = @At("RETURN"))
    private void radiance$capturePreparedRenderType(
        CallbackInfoReturnable<PreparedRenderType> cir) {
        if (!RenderPassPipelineStateBridge.shouldCapture()) {
            return;
        }

        PreparedRenderType prepared = cir.getReturnValue();
        if (prepared == null
            || !((Object) this.state instanceof IRenderSetupStateExt setupState)) {
            return;
        }

        RenderPassPipelineStateBridge.remember((RenderType) (Object) this,
            setupState.radiance$captureRenderPassSetupState(), prepared);
    }
}

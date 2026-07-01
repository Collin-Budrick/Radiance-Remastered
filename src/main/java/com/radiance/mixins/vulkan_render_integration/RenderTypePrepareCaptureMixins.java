package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RendererAvailability;
import com.radiance.client.render.submit.replay.RenderPassDrawPacket;
import com.radiance.client.render.submit.replay.RenderPassPacketCapture;
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
public class RenderTypePrepareCaptureMixins {

    @Shadow
    @Final
    private RenderSetup state;

    @Shadow
    @Final
    protected String name;

    @Inject(method = "prepare()Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;",
        at = @At("RETURN"))
    private void radiance$rememberPreparedRenderType(
        CallbackInfoReturnable<PreparedRenderType> cir) {
        if (!RendererAvailability.isRendererLifecycleActive() || cir.getReturnValue() == null) {
            return;
        }

        RenderSetupAccessors setup = (RenderSetupAccessors) (Object) this.state;
        RenderType renderType = (RenderType) (Object) this;
        RenderPassPacketCapture.rememberPreparedRenderType(cir.getReturnValue(),
            new RenderPassPacketCapture.PreparedMetadata(
                RenderPassDrawPacket.RenderTypeIdentity.capture(renderType, this.name),
                RenderPassDrawPacket.RenderFlags.capture(setup.radiance$getUseLightmap(),
                    setup.radiance$getUseOverlay(), setup.radiance$getAffectsCrumbling(),
                    setup.radiance$getSortOnUpload(), renderType.isOutline(),
                    renderType.hasBlending())));
    }
}

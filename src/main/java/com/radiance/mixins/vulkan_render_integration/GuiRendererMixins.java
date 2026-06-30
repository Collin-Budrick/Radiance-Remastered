package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.gui.GuiRenderStateBridge;
import com.radiance.client.RendererAvailability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixins {

    @Shadow
    @Final
    private GuiRenderState renderState;

    @Inject(method = "render()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;draw()V"))
    private void submitRadianceGuiOverlay(CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }

        GuiRenderStateBridge.render(this.renderState,
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),
            minecraft.getWindow().getWidth(),
            minecraft.getWindow().getHeight());
    }
}

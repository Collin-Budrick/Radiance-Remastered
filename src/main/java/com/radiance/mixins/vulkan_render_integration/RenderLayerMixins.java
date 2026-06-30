package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RendererAvailability;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTypes.class)
public class RenderLayerMixins {

    @Shadow
    @Final
    @Mutable
    private static RenderType LIGHTNING;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void replaceLightning(CallbackInfo ci) {
        if (!RendererAvailability.shouldOwnRendererLifecycle()) {
            return;
        }

        RenderSetup setup = RenderSetup.builder(RenderPipelines.LIGHTNING)
            .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/block/lightning.png"))
            .setOutputTarget(OutputTarget.WEATHER_TARGET)
            .sortOnUpload()
            .createRenderSetup();
        LIGHTNING = radiance$createRenderType("lightning", setup);
    }

    @Unique
    private static RenderType radiance$createRenderType(String name, RenderSetup setup) {
        try {
            Method method = RenderType.class.getDeclaredMethod("create", String.class,
                RenderSetup.class);
            method.setAccessible(true);
            return (RenderType) method.invoke(null, name, setup);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create render type: " + name, e);
        }
    }
}

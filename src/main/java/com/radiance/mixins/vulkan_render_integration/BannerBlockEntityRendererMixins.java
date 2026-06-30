package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.RendererAvailability;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public class BannerBlockEntityRendererMixins {

    @Unique
    private static final float radiance$layerDepthStep = 0.002F;

    @Redirect(method = "submitBanner(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/model/object/banner/BannerModel;Lnet/minecraft/client/model/object/banner/BannerFlagModel;FLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 1))
    private static void radiance$cancelSolidCanvasSubmit(SubmitNodeCollector submitNodeCollector,
        Model<?> model,
        Object state,
        PoseStack matrices,
        int light,
        int overlay,
        int color,
        SpriteId sprite,
        SpriteGetter spriteGetter,
        int outlineColor,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        if (!radiance$shouldAdjustBannerLayers()) {
            radiance$submitModel(submitNodeCollector, model, state, matrices, light, overlay, color,
                sprite, spriteGetter, outlineColor, crumblingOverlay);
        }
    }

    @Inject(method = "submitPatterns(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;submitPatternLayer(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/world/item/DyeColor;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 0))
    private static void radiance$expandBaseLayerPre(SpriteGetter spriteGetter,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        int overlay,
        Model<?> model,
        Object state,
        boolean isBanner,
        DyeColor color,
        BannerPatternLayers patterns,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci) {
        if (!radiance$shouldAdjustBannerLayers()) {
            return;
        }

        radiance$pushCanvasLayer(matrices, 1);
    }

    @Inject(method = "submitPatterns(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;submitPatternLayer(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/world/item/DyeColor;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 0,
            shift = Shift.AFTER))
    private static void radiance$expandBaseLayerPost(SpriteGetter spriteGetter,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        int overlay,
        Model<?> model,
        Object state,
        boolean isBanner,
        DyeColor color,
        BannerPatternLayers patterns,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci) {
        if (!radiance$shouldAdjustBannerLayers()) {
            return;
        }

        radiance$popCanvasLayer(matrices);
    }

    @Inject(method = "submitPatterns(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;submitPatternLayer(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/world/item/DyeColor;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 1))
    private static void radiance$expandPatternLayerPre(SpriteGetter spriteGetter,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        int overlay,
        Model<?> model,
        Object state,
        boolean isBanner,
        DyeColor color,
        BannerPatternLayers patterns,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci,
        @Local(ordinal = 2) int patternIndex) {
        if (!radiance$shouldAdjustBannerLayers()) {
            return;
        }

        radiance$pushCanvasLayer(matrices, patternIndex + 2);
    }

    @Inject(method = "submitPatterns(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;submitPatternLayer(Lnet/minecraft/client/resources/model/sprite/SpriteGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;IILnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/world/item/DyeColor;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 1,
            shift = Shift.AFTER))
    private static void radiance$expandPatternLayerPost(SpriteGetter spriteGetter,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        int overlay,
        Model<?> model,
        Object state,
        boolean isBanner,
        DyeColor color,
        BannerPatternLayers patterns,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci) {
        if (!radiance$shouldAdjustBannerLayers()) {
            return;
        }

        radiance$popCanvasLayer(matrices);
    }

    @Unique
    private static boolean radiance$shouldAdjustBannerLayers() {
        return RendererAvailability.isRendererLifecycleActive();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Unique
    private static void radiance$submitModel(SubmitNodeCollector submitNodeCollector,
        Model<?> model,
        Object state,
        PoseStack matrices,
        int light,
        int overlay,
        int color,
        SpriteId sprite,
        SpriteGetter spriteGetter,
        int outlineColor,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        submitNodeCollector.submitModel((Model) model, state, matrices, light, overlay, color,
            sprite, spriteGetter, outlineColor, crumblingOverlay);
    }

    @Unique
    private static void radiance$pushCanvasLayer(PoseStack matrices, int depthIndex) {
        matrices.pushPose();
        matrices.translate(0.0F, 0.0F, -radiance$layerDepthStep * depthIndex);
    }

    @Unique
    private static void radiance$popCanvasLayer(PoseStack matrices) {
        matrices.popPose();
    }
}

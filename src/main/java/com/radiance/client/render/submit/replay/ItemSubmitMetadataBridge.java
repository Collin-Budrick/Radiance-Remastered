package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;

public final class ItemSubmitMetadataBridge {

    private static final AtomicReference<ItemSubmitMetadata> LAST_METADATA =
        new AtomicReference<>();
    private static final AtomicBoolean LOGGED_MISSING_CONSUMER = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FOIL_CONSUMER = new AtomicBoolean();
    private static final String GLINT_BLOCKER_MARKER =
        "RADIANCE_ITEM_GLINT_BLOCKED_26_2_SUBMIT_NODE_STATE";
    private static final String GLINT_SURFACE_MARKER =
        "RADIANCE_ITEM_GLINT_SURFACE_26_2_FEATURE_RENDERER";

    private ItemSubmitMetadataBridge() {
    }

    public static boolean tryReplay(PoseStack poseStack, ItemDisplayContext displayContext,
        int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
        List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            LAST_METADATA.set(null);
            return false;
        }

        ItemSubmitMetadata metadata = ItemSubmitMetadata.capture(poseStack, displayContext,
            lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
        LAST_METADATA.set(metadata);

        ItemReplayEligibility eligibility = ItemReplayEligibility.from(metadata);
        if (!eligibility.nativeReplayReady()) {
            logMissingReplayStateOnce(metadata, eligibility);
            return false;
        }

        return false;
    }

    public static ItemSubmitMetadata getLastMetadata() {
        return LAST_METADATA.get();
    }

    public static void observeFoilVertexConsumer(RenderType itemRenderType,
        RenderType glintRenderType, VertexConsumer consumer, boolean foilDecalPose) {
        if (!RendererAvailability.isRendererLifecycleActive()
            || !LOGGED_FOIL_CONSUMER.compareAndSet(false, true)) {
            return;
        }

        RadianceClient.LOGGER.info(
            "{} Radiance item glint hook observed 26.2 ItemFeatureRenderer.getFoilBuffer: itemRenderType={}, glintRenderType={}, consumer={}, foilDecalPose={}. This is the first descriptor-correct surface where item foil replay has the source item RenderType and returned live VertexConsumer; native/PBR wiring must wrap or replace this consumer before putBakedQuad writes foil vertices.",
            GLINT_SURFACE_MARKER, itemRenderType, glintRenderType,
            consumer == null ? "<null>" : consumer.getClass().getName(), foilDecalPose);
    }

    private static void logMissingReplayStateOnce(ItemSubmitMetadata metadata,
        ItemReplayEligibility eligibility) {
        if (!LOGGED_MISSING_CONSUMER.compareAndSet(false, true)) {
            return;
        }

        RadianceClient.LOGGER.warn(
            "{} Radiance item submit-node replay kept vanilla: captured geometry={}, transform={}, lightOverlay={}, foilState={} for displayContext={}, quads={}, tints={}, foilType={}, light={}, overlay={}, outlineColor={}. 26.2 LayerRenderState.submit(...) only forwards pose/display/light/overlay/outline/tints/quads/foilType to SubmitNodeCollector.submitItem; it does not expose texture/render-type state or a VertexConsumer for PBR glint wrapping, so foil metadata alone is not enough for native glint replay; missing={}.",
            GLINT_BLOCKER_MARKER,
            eligibility.geometryAvailable(), eligibility.transformAvailable(),
            eligibility.lightOverlayAvailable(), eligibility.foilStateAvailable(),
            metadata.displayContext(), metadata.quadCount(), metadata.tintLayerCount(),
            metadata.foilType(), metadata.lightCoords(), metadata.overlayCoords(),
            metadata.outlineColor(), eligibility.missingFields());
    }

    public record ItemSubmitMetadata(ItemDisplayContext displayContext, int lightCoords,
                                     int overlayCoords, int outlineColor, int tintLayerCount,
                                     int quadCount, ItemStackRenderState.FoilType foilType,
                                     boolean transformAvailable) {

        static ItemSubmitMetadata capture(PoseStack poseStack, ItemDisplayContext displayContext,
            int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
            List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
            return new ItemSubmitMetadata(displayContext, lightCoords, overlayCoords,
                outlineColor, tintLayers == null ? 0 : tintLayers.length,
                quads == null ? 0 : quads.size(), foilType, poseStack != null);
        }
    }

    private record ItemReplayEligibility(boolean geometryAvailable,
                                         boolean textureAvailable,
                                         boolean transformAvailable,
                                         boolean lightOverlayAvailable,
                                         boolean foilStateAvailable,
                                         boolean vertexConsumerAvailable) {

        static ItemReplayEligibility from(ItemSubmitMetadata metadata) {
            // The 26.2 submit-node hook exposes the foil enum, but not the RenderType,
            // texture binding, or live VertexConsumer that PBR glint emission needs.
            return new ItemReplayEligibility(
                metadata != null && metadata.quadCount() > 0,
                false,
                metadata != null && metadata.transformAvailable(),
                metadata != null,
                metadata != null && metadata.foilType() != null,
                false);
        }

        boolean nativeReplayReady() {
            return geometryAvailable && textureAvailable && transformAvailable
                && lightOverlayAvailable && foilStateAvailable && vertexConsumerAvailable;
        }

        String missingFields() {
            StringBuilder missing = new StringBuilder();
            appendMissing(missing, geometryAvailable, "geometry");
            appendMissing(missing, textureAvailable, "texture/renderType");
            appendMissing(missing, transformAvailable, "transform");
            appendMissing(missing, lightOverlayAvailable, "light/overlay");
            appendMissing(missing, foilStateAvailable, "foil/glintState");
            appendMissing(missing, vertexConsumerAvailable, "VertexConsumer");
            return missing.isEmpty() ? "<none>" : missing.toString();
        }

        private static void appendMissing(StringBuilder missing, boolean available,
            String field) {
            if (available) {
                return;
            }
            if (!missing.isEmpty()) {
                missing.append(',');
            }
            missing.append(field);
        }
    }
}

package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.radiance.client.renderpass.RenderPassPipelineState.RenderPassSetupState;
import com.radiance.client.renderpass.RenderPassPipelineState.SetupTextureState;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IRenderSetupStateExt;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderSetup.class)
public abstract class RenderPhaseLightmapMixins implements IRenderSetupStateExt {

    @Shadow
    @Final
    private RenderPipeline pipeline;

    @Shadow
    @Final
    private Map<String, Object> textures;

    @Shadow
    @Final
    private TextureTransform textureTransform;

    @Shadow
    @Final
    private OutputTarget outputTarget;

    @Shadow
    @Final
    private OutlineProperty outlineProperty;

    @Shadow
    @Final
    private boolean useLightmap;

    @Shadow
    @Final
    private boolean useOverlay;

    @Shadow
    @Final
    private boolean affectsCrumbling;

    @Shadow
    @Final
    private boolean sortOnUpload;

    @Shadow
    @Final
    private LayeringTransform layeringTransform;

    @Override
    public RenderPassSetupState radiance$captureRenderPassSetupState() {
        return new RenderPassSetupState(this.pipeline, this.outputTarget,
            radiance$captureSetupTextures(), this.useLightmap, this.useOverlay,
            this.affectsCrumbling, this.sortOnUpload, String.valueOf(this.outlineProperty),
            String.valueOf(this.textureTransform), radiance$textureTransformMatrix(),
            String.valueOf(this.layeringTransform), radiance$layeringTransformMatrix());
    }

    @Unique
    private List<SetupTextureState> radiance$captureSetupTextures() {
        if (this.textures == null || this.textures.isEmpty()) {
            return List.of();
        }

        List<SetupTextureState> states = new ArrayList<>(this.textures.size());
        for (Map.Entry<String, Object> entry : this.textures.entrySet()) {
            Object binding = entry.getValue();
            states.add(new SetupTextureState(entry.getKey(),
                radiance$recordAccessor(binding, "location", Identifier.class),
                radiance$samplerSupplierClass(binding)));
        }
        return states;
    }

    @Unique
    private float[] radiance$textureTransformMatrix() {
        try {
            Matrix4f matrix = this.textureTransform == null
                ? new Matrix4f()
                : this.textureTransform.createMatrix();
            return radiance$copyMatrix(matrix);
        } catch (RuntimeException ignored) {
            return radiance$copyMatrix(new Matrix4f());
        }
    }

    @Unique
    private float[] radiance$layeringTransformMatrix() {
        Matrix4f matrix = new Matrix4f();
        try {
            Consumer<Matrix4f> modifier = this.layeringTransform == null
                ? null
                : this.layeringTransform.getModifier();
            if (modifier != null) {
                modifier.accept(matrix);
            }
        } catch (RuntimeException ignored) {
            matrix.identity();
        }
        return radiance$copyMatrix(matrix);
    }

    @Unique
    private static float[] radiance$copyMatrix(Matrix4f matrix) {
        float[] values = new float[16];
        matrix.get(values);
        return values;
    }

    @Unique
    private static String radiance$samplerSupplierClass(Object binding) {
        Supplier<?> supplier = radiance$recordAccessor(binding, "sampler", Supplier.class);
        return supplier == null ? null : supplier.getClass().getName();
    }

    @Unique
    private static <T> T radiance$recordAccessor(Object target, String methodName, Class<T> type) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }
}

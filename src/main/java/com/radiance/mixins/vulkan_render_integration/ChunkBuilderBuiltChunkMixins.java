package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.RendererAvailability;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    @Shadow
    @Final
    SectionRenderDispatcher this$0;

    @Unique
    public SectionRenderDispatcher radiance$getChunkBuilder() {
        return this$0;
    }

    @Inject(method = "reset()V", at = @At(value = "TAIL"))
    private void addToRebuildGridReset(CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "compileAsync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V",
        at = @At(value = "HEAD"))
    private void addToRebuildGridCompileAsync(RenderSectionRegion renderSectionRegion,
        CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.enqueueRebuild(self, renderSectionRegion);
    }

    @Inject(method = "compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V",
        at = @At(value = "HEAD"))
    private void addToRebuildGridCompileSync(RenderSectionRegion renderSectionRegion,
        CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        ChunkProxy.enqueueRebuild(self, renderSectionRegion);
    }

    @Inject(method = "setSectionNode(J)V", at = @At(value = "TAIL"))
    private void syncNativeChunkSlot(long sectionNode, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self =
            (SectionRenderDispatcher.RenderSection) (Object) this;
        BlockPos origin = self.getRenderOrigin();
        if (RendererAvailability.isRendererLifecycleActive()) {
            ChunkProxy.relocateSingle(self.index, origin.getX(), origin.getY(), origin.getZ());
        }
    }
}

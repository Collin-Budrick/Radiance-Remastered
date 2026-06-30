package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IViewAreaExt;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public class BuiltChunkStorageMixins implements IViewAreaExt {

    @Shadow
    @Final
    private RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void initChunkProxy(SectionRenderDispatcher sectionRenderDispatcher, int minY,
        int maxY, int minSectionY, int sectionCount, int viewDistance,
        SectionOcclusionGraph sectionOcclusionGraph, CallbackInfo ci) {
        ViewArea viewArea = (ViewArea) (Object) this;
        int horizontalSectionCount = viewArea.getViewDistance() * 2 + 1;
        ChunkProxy.setStorage(viewArea);
        ChunkProxy.init(viewArea.size(), horizontalSectionCount, viewArea.sectionCount(),
            horizontalSectionCount, viewArea.minSectionY());
    }

    @Inject(method = "releaseAllBuffers()V", at = @At(value = "HEAD"))
    private void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @Inject(method = "repositionCamera(Lnet/minecraft/core/SectionPos;)Z",
        at = @At(value = "HEAD"))
    private void updateChunkStorageSectionPos(SectionPos sectionPos,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkProxy.updateSectionPos(sectionPos);
    }

    @Override
    public RotatingSectionStorage<SectionRenderDispatcher.RenderSection> radiance$getSections() {
        return this.sections;
    }
}

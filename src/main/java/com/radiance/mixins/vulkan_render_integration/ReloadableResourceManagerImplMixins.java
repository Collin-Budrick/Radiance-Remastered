package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.shader.ShaderRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public class ReloadableResourceManagerImplMixins {

    @Inject(method = "registerReloadListener(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V", at = @At(value = "HEAD"))
    public void addInfo(PreparableReloadListener reloader, CallbackInfo ci) {
//        if (reloader == null) {
//            System.out.println("Reloader: null");
//        } else {
//            System.out.println("Reloader: " + reloader.getClass()
//                .getName());
//        }
    }

    @Inject(method = "createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;", at = @At("HEAD"))
    private void clearShaderCache(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<PackResources> packs,
        CallbackInfoReturnable<ReloadInstance> cir) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        RadianceClient.LOGGER.info("Radiance resource reload started: clearing shader cache");
        ShaderRegistry.clear();
        Pipeline.reloadAllModuleEntries();
    }

    @Inject(method = "createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;", at = @At("RETURN"))
    private void rebuildPipelineAfterResourceReload(Executor prepareExecutor, Executor applyExecutor,
        CompletableFuture<Unit> initialStage, List<PackResources> packs,
        CallbackInfoReturnable<ReloadInstance> cir) {
        if (!RendererAvailability.isRendererLifecycleActive() || cir.getReturnValue() == null) {
            return;
        }

        cir.getReturnValue().done().thenRunAsync(() -> {
            RadianceClient.LOGGER.info("Radiance resource reload completed: rebuilding pipeline");
            Pipeline.loadPipeline();
        }, applyExecutor);
    }
}

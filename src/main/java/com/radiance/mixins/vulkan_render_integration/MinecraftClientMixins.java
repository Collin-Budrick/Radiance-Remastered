package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuSurface;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RadianceNoopSurfaceBackend;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    @Mutable
    private GpuSurface windowSurface;

    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Shadow
    private boolean windowSurfaceNeedsReconfiguring;

    @Shadow
    private boolean surfaceIsInvalid;

    @Unique
    private boolean radiance$rendererInitializationAttempted;

    @Unique
    private boolean radiance$surfaceHandoffComplete;

    @Unique
    private boolean radiance$loggedDeferredMenuPresentation;

    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At("TAIL"))
    private void initializeRadianceRenderer(GameConfig args, CallbackInfo ci) {
        if (!RendererAvailability.canInitializeRendererLifecycle()) {
            RadianceClient.LOGGER.warn(
                "Radiance renderer lifecycle skipped: nativeLoaded={}, shaderResourcesStaged={}",
                RendererAvailability.isNativeRendererLoaded(),
                RendererAvailability.areShaderResourcesStaged());
            return;
        }

        RadianceClient.LOGGER.info("Radiance registering auxiliary texture resource reloader");
        this.resourceManager.registerReloadListener(new AuxiliaryTextureReloader());
        if (RendererAvailability.isRendererRequired()) {
            RadianceClient.LOGGER.info(
                "Radiance native renderer initialization deferred until a loaded world frame");
        }
    }

    @Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
    private void beginRadianceFrame(boolean tick, CallbackInfo ci) {
        radiance$initializeRequiredRendererForWorld();

        if (!RendererAvailability.isRendererLifecycleActive() || !radiance$shouldOwnFrame()) {
            return;
        }

        synchronized (TextureProxy.class) {
            RendererProxy.acquireContext();
            RendererProxy.fuseWorld();
        }
    }

    @Inject(method = "renderFrame(Z)V", at = @At("TAIL"))
    private void presentRadianceFrame(boolean tick, CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive() || !radiance$shouldOwnFrame()) {
            return;
        }

        ChunkProxy.waitImportantChunkRebuild();
        synchronized (TextureProxy.class) {
            RendererProxy.submitCommandAndPresent();
        }
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void persistRadianceOptions(CallbackInfo ci) {
        Options.overwriteConfig();
    }

    @Inject(method = "stop()V", at = @At("TAIL"))
    private void closeRadianceRenderer(CallbackInfo ci) {
        if (!RendererAvailability.isRendererInitialized()) {
            return;
        }

        RendererProxy.close();
        RendererAvailability.markRendererStopped();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring, boolean save,
        CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }

    @Unique
    private void radiance$initializeRequiredRendererForWorld() {
        if (!RendererAvailability.isRendererRequired()
            || RendererAvailability.isRendererInitialized()
            || this.radiance$rendererInitializationAttempted) {
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null || !minecraft.isGameLoadFinished()) {
            if (!this.radiance$loggedDeferredMenuPresentation) {
                RadianceClient.LOGGER.info(
                    "Radiance required mode: keeping Minecraft's 26.2 surface active for menu/loading presentation");
                this.radiance$loggedDeferredMenuPresentation = true;
            }
            return;
        }

        this.radiance$rendererInitializationAttempted = true;
        radiance$handoffMinecraftSurfaceToRadiance();
        radiance$initializeNativeRenderer();
    }

    @Unique
    private void radiance$handoffMinecraftSurfaceToRadiance() {
        if (this.radiance$surfaceHandoffComplete) {
            return;
        }

        RadianceClient.LOGGER.info(
            "Radiance renderer required: closing Minecraft GpuSurface and installing no-op surface for native swapchain ownership");
        var currentConfiguration = this.windowSurface.currentConfiguration();
        this.windowSurface.close();
        this.windowSurface = new GpuSurface(new RadianceNoopSurfaceBackend());
        this.surfaceIsInvalid = false;
        currentConfiguration.ifPresent(configuration -> {
            try {
                this.windowSurface.configure(configuration);
            } catch (Exception exception) {
                RadianceClient.LOGGER.warn("Radiance failed to copy Minecraft surface configuration",
                    exception);
                this.surfaceIsInvalid = true;
            }
        });
        this.windowSurfaceNeedsReconfiguring = true;
        this.radiance$surfaceHandoffComplete = true;
    }

    @Unique
    private void radiance$initializeNativeRenderer() {
        Throwable[] failure = new Throwable[1];
        Thread initThread = new Thread(null, () -> {
            try {
                RadianceClient.LOGGER.info("Radiance native renderer initialization started");
                RendererProxy.initRenderer(this.window);
                Pipeline.collectNativeModules();
                RendererAvailability.markRendererInitialized();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            }
        }, "Radiance renderer init", 512L * 1024L * 1024L);

        initThread.start();
        try {
            initThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while initializing Radiance renderer", exception);
        }

        if (failure[0] != null) {
            throw new RuntimeException("Failed to initialize Radiance renderer", failure[0]);
        }

        RadianceClient.LOGGER.info("Radiance loading pipeline after native module collection");
        Pipeline.loadPipeline();
    }

    private boolean radiance$shouldOwnFrame() {
        return RendererAvailability.isRendererRequired() && this.radiance$surfaceHandoffComplete;
    }

}

package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class AuxiliaryTextureReloader implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepareExecutor,
        PreparationBarrier preparationBarrier, Executor applyExecutor) {
        ResourceManager manager = sharedState.resourceManager();
        return AuxiliaryTextures.prepareDecodedImagesAsync(manager, prepareExecutor)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(AuxiliaryTextures::applyPreparedImages, applyExecutor);
    }
}

package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.Options.genericValueLabel;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public class VideoOptionsScreenMixins extends GameOptionsScreenMixins {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY = new PotentialValuesBasedCallbacksNoValue<>(
        ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL
    );

    @Inject(method = "addOptions()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectAddOptions(CallbackInfo ci) {
        Window
            window =
            Minecraft.getInstance()
                .getWindow();
        Monitor monitor = window.findBestMonitor();
        int j;
        if (monitor == null) {
            j = -1;
        } else {
            Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
            j =
                optional.map(monitor::indexOfMode)
                    .orElse(-1);
        }

        OptionInstance<Integer>
            fullScreenResolutionOption =
            new OptionInstance<>("options.fullscreen.resolution", OptionInstance.noTooltip(),
                (optionText, value) -> {
                    if (monitor == null) {
                        return Component.translatable("options.fullscreen.unavailable");
                    } else if (value == -1) {
                        return genericValueLabel(optionText,
                            Component.translatable("options.fullscreen.current"));
                    } else {
                        VideoMode videoMode = monitor.mode(value);
                        return genericValueLabel(optionText,
                            Component.translatable("options.fullscreen.entry",
                                videoMode.getWidth(),
                                videoMode.getHeight(),
                                videoMode.getRefreshRate(),
                                videoMode.getRedBits() + videoMode.getGreenBits() +
                                    videoMode.getBlueBits()));
                    }
                }, new OptionInstance.IntRange(-1,
                monitor != null ? monitor.modeCount() - 1 : -1), j, value -> {
                if (monitor != null) {
                    window.setPreferredFullscreenVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.mode(value)));
                }
            });

        OptionInstance<Integer>
            chunkBuildingBatchSize =
            new OptionInstance<>(com.radiance.client.option.Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1, 32),
                com.radiance.client.option.Options.chunkBuildingBatchSize,
                value -> {
                    com.radiance.client.option.Options.setChunkBuildingBatchSize(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingTotalBatches =
            new OptionInstance<>(com.radiance.client.option.Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1, 32),
                com.radiance.client.option.Options.chunkBuildingTotalBatches,
                value -> {
                    com.radiance.client.option.Options.setChunkBuildingTotalBatches(value, true);
                });

        OptionInstance<Integer>
            chunkBuildingThreads =
            new OptionInstance<>(com.radiance.client.option.Options.CHUNK_BUILDING_THREADS_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText,
                    Component.literal(Integer.toString(value))),
                new OptionInstance.IntRange(1,
                    com.radiance.client.option.Options.getMaxChunkBuildingThreads()),
                com.radiance.client.option.Options.chunkBuildingThreads,
                value -> com.radiance.client.option.Options.setChunkBuildingThreads(value, true));

        OptionInstance<Boolean> collectChunkEmission = OptionInstance.createBoolean(
            com.radiance.client.option.Options.COLLECT_CHUNK_EMISSION_KEY,
            com.radiance.client.option.Options.collectChunkEmission,
            value -> com.radiance.client.option.Options.setCollectChunkEmission(value, true));

        OptionInstance<Boolean> pipelineSettings = new OptionInstance<>(
            com.radiance.client.option.Options.PIPELINE_SETUP_KEY,
            OptionInstance.noTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                Minecraft.getInstance()
                    .gui
                    .setScreen(new RenderPipelineScreen((VideoSettingsScreen) (Object) this));
            });

        this.list.addHeader(
            Component.translatable(com.radiance.client.option.Options.CATEGORY_GAMEPLAY));
        OptionInstance<?>[] optionsGameplay = new OptionInstance[]{ //
            options.graphicsPreset(), //
            options.renderDistance(), //
            options.simulationDistance(), //
            options.guiScale(), //
            options.attackIndicator(), //
            options.gamma(), //
            options.cloudStatus(), //
            options.particles(), //
            options.screenEffectScale(), //
            options.entityDistanceScaling(), //
            options.fovEffectScale(), //
            options.showAutosaveIndicator(), //
            options.glintSpeed(), //
            options.glintStrength(), //
            options.menuBackgroundBlurriness(), //
            options.bobView(), //
        };
        this.list.addBig(options.biomeBlendRadius());
        this.list.addBig(options.mipmapLevels());
        this.list.addSmall(optionsGameplay);

        this.list.addHeader(
            Component.translatable(com.radiance.client.option.Options.CATEGORY_WINDOW));
        OptionInstance[] optionsWindow = new OptionInstance[]{ //
            options.framerateLimit(), //
            options.inactivityFpsLimit(), //
            options.enableVsync(), //
            options.fullscreen(), //
        };
        this.list.addSmall(optionsWindow);
        this.list.addBig(fullScreenResolutionOption);

        this.list.addHeader(
            Component.translatable(com.radiance.client.option.Options.CATEGORY_TERRAIN));
        this.list.addBig(chunkBuildingBatchSize);
        this.list.addBig(chunkBuildingTotalBatches);
        this.list.addBig(chunkBuildingThreads);
        this.list.addBig(collectChunkEmission);

        this.list.addHeader(
            Component.translatable(com.radiance.client.option.Options.CATEGORY_PIPELINE));
        this.list.addBig(pipelineSettings);

        ci.cancel();
    }
}

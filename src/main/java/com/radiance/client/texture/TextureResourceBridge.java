package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class TextureResourceBridge {

    public static final Identifier END_SKY_TEXTURE =
        Identifier.withDefaultNamespace("textures/environment/end_sky.png");
    public static final Identifier END_PORTAL_TEXTURE =
        Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png");
    public static final Identifier SUN_TEXTURE =
        Identifier.withDefaultNamespace("textures/environment/celestial/sun.png");

    private static final Identifier[] MOON_PHASE_TEXTURES = {
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/full_moon.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_gibbous.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/third_quarter.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_crescent.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/new_moon.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_crescent.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/first_quarter.png"),
        Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_gibbous.png")
    };

    private static final Map<Identifier, Integer> RESOURCE_TEXTURE_IDS =
        new ConcurrentHashMap<>();
    private static int moonAtlasTextureId;

    private TextureResourceBridge() {
    }

    public static int textureId(Identifier identifier) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return 0;
        }

        Integer trackedId = TextureTracker.identifierToTextureHandle.get(identifier);
        if (trackedId != null && trackedId != 0) {
            return trackedId;
        }

        Integer existing = RESOURCE_TEXTURE_IDS.get(identifier);
        if (existing != null && existing != 0) {
            return existing;
        }

        int loaded = loadTexture(identifier);
        if (loaded != 0) {
            RESOURCE_TEXTURE_IDS.put(identifier, loaded);
        }
        return loaded;
    }

    public static int optionalTextureId(Identifier identifier) {
        int textureId = textureId(identifier);
        return textureId == 0 ? -1 : textureId;
    }

    public static synchronized int moonAtlasTextureId() {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return 0;
        }
        if (moonAtlasTextureId != 0) {
            return moonAtlasTextureId;
        }

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        try (NativeImage first = readImage(manager, MOON_PHASE_TEXTURES[0])) {
            int tileWidth = first.getWidth();
            int tileHeight = first.getHeight();
            try (NativeImage atlas = new NativeImage(first.format(), tileWidth * 4,
                tileHeight * 2, false)) {
                copyTile(first, atlas, 0, 0, tileWidth, tileHeight);
                for (int i = 1; i < MOON_PHASE_TEXTURES.length; i++) {
                    try (NativeImage phase = readImage(manager, MOON_PHASE_TEXTURES[i])) {
                        copyTile(phase, atlas, i % 4, i / 4, tileWidth, tileHeight);
                    }
                }
                moonAtlasTextureId = uploadImage(Identifier.withDefaultNamespace(
                    "radiance/generated/moon_phases"), atlas);
                return moonAtlasTextureId;
            }
        } catch (IOException | IllegalStateException e) {
            RadianceClient.LOGGER.warn("Radiance texture bridge: failed to load moon atlas", e);
            return 0;
        }
    }

    private static int loadTexture(Identifier identifier) {
        try (NativeImage image = readImage(Minecraft.getInstance().getResourceManager(),
            identifier)) {
            return uploadImage(identifier, image);
        } catch (IOException | IllegalStateException e) {
            RadianceClient.LOGGER.warn("Radiance texture bridge: failed to load {}", identifier,
                e);
            return 0;
        }
    }

    private static NativeImage readImage(ResourceManager manager, Identifier identifier)
        throws IOException {
        try (InputStream stream = manager.open(identifier)) {
            return NativeImage.read(stream);
        }
    }

    private static void copyTile(NativeImage source, NativeImage target, int tileX, int tileY,
        int tileWidth, int tileHeight) {
        int baseX = tileX * tileWidth;
        int baseY = tileY * tileHeight;
        int width = Math.min(tileWidth, source.getWidth());
        int height = Math.min(tileHeight, source.getHeight());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                target.setPixel(baseX + x, baseY + y, source.getPixel(x, y));
            }
        }
    }

    private static int uploadImage(Identifier identifier, NativeImage image) {
        int id = TextureProxy.generateTextureId();
        TextureProxy.prepareImage(image.format(), id, 1, image.getWidth(), image.getHeight());
        int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();
        TextureProxy.queueUpload(image.getPointer(), srcSizeInBytes, image.getWidth(), id, 0, 0,
            0, 0, image.getWidth(), image.getHeight(), 0);
        TextureTracker.rememberTextureHandle(identifier, id);
        return id;
    }
}

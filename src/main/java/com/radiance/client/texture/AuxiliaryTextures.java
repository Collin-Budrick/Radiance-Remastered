package com.radiance.client.texture;

import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.RadianceClient;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

public enum AuxiliaryTextures {
    SPECULAR("specular", "_s", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String specularFileName = String.join("",
            new String[]{fileNameComponents[0], "_s.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = specularFileName;
        String specularPath = String.join("/", pathComponents);
        Identifier specularIdentifier = Identifier.fromNamespaceAndPath(namespace, specularPath);
        return List.of(specularIdentifier);
    }, INativeImageExt::radiance$getSpecularNativeImage,
        INativeImageExt::radiance$setSpecularNativeImage, source -> 0,
        TextureTracker.textureHandleToSpecularHandle),
    NORMAL("normal", "_n", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String normalFileName = String.join("",
            new String[]{fileNameComponents[0], "_n.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = normalFileName;
        String normalPath = String.join("/", pathComponents);
        Identifier normalIdentifier = Identifier.fromNamespaceAndPath(namespace, normalPath);
        return List.of(normalIdentifier);
    }, INativeImageExt::radiance$getNormalNativeImage,
        INativeImageExt::radiance$setNormalNativeImage,
        AuxiliaryTextures::defaultNormalPixel,
        TextureTracker.textureHandleToNormalHandle),
    FLAG(
        "flag", "_f", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String flagFileName = String.join("",
            new String[]{fileNameComponents[0], "_f.", fileNameComponents[1]});

        pathComponents[pathComponents.length - 1] = flagFileName;
        String flagPath = String.join("/", pathComponents)
            .replace("textures/", "textures/flag/");
        Identifier flagIdentifier = Identifier.fromNamespaceAndPath(namespace, flagPath);
        return List.of(flagIdentifier);
    }, INativeImageExt::radiance$getFlagNativeImage,
        INativeImageExt::radiance$setFlagNativeImage, source -> 0,
        TextureTracker.textureHandleToFlagHandle);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private static final Object DECODED_IMAGE_CACHE_LOCK = new Object();
    private static final Map<CacheKey, CacheEntry> DECODED_IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean LOGGED_AUXILIARY_UPLOAD = new AtomicBoolean(false);
    private final String suffix;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final DefaultValueProvider defaultValueProvider;
    private final String name;
    private final Map<Integer, Integer> textureHandleMapping;

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        DefaultValueProvider defaultValueProvider, Map<Integer, Integer> textureHandleMapping) {
        this.suffix = suffix;
        this.identifierCandidateProvider = identifierCandidateProvider;
        this.getter = getter;
        this.setter = setter;
        this.defaultValueProvider = defaultValueProvider;
        this.name = name;
        this.textureHandleMapping = textureHandleMapping;
    }

    public static boolean isAuxiliaryTexture(Identifier identifier) {
        if (identifier == null) {
            return false;
        }

        String path = identifier.getPath();
        int dotIndex = path.lastIndexOf('.');
        String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

        return ALL_TEXTURES.stream().anyMatch(texture -> texture.matchesSuffix(baseName));
    }

    public static boolean shouldSkipAtlasSprite(ResourceManager resourceManager,
        Identifier spriteId) {
        String spritePath = spriteId.getPath();
        for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
            if (!auxiliaryTexture.matchesSuffix(spritePath)) {
                continue;
            }

            Identifier baseSpriteId = auxiliaryTexture.toBaseSpriteId(spriteId);
            if (resourceManager.getResource(
                    SpriteSource.TEXTURE_ID_CONVERTER.idToFile(baseSpriteId))
                .isPresent()) {
                return true;
            }
        }

        return false;
    }

    public static void clearDecodedImageCache() {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            for (CacheEntry entry : DECODED_IMAGE_CACHE.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            DECODED_IMAGE_CACHE.clear();
        }
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        loadAndUpload(source, sourceExt, sourceExt.radiance$getTargetID(), level, offsetX, offsetY,
            unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight);
    }

    public static void loadAndUpload(NativeImage source, GpuTexture targetTexture, int level,
        int offsetX, int offsetY) {
        int targetId = TextureTracker.getOrRegisterTextureHandle(targetTexture);
        INativeImageExt sourceExt = (INativeImageExt) (Object) source;
        loadAndUpload(source, sourceExt, targetId, level, offsetX, offsetY, 0, 0,
            source.getWidth(), source.getHeight());
    }

    private static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int targetId,
        int level, int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows,
        int regionWidth, int regionHeight) {
        if (targetId == 0) {
            return;
        }

        Identifier identifier = sourceExt.radiance$getIdentifier();

        if (identifier != null) {
            if (isAuxiliaryTexture(identifier)) {
                return;
            }

            for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
                NativeImage auxiliaryTemplateImage = null;
                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.textureHandleToTexture.get(
                    targetId);
                if (texture == null) {
                    return;
                }
                if (!auxiliaryTexture.textureHandleMapping.containsKey(targetId)) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();
//                    System.out.println(
//                        "generate " + auxiliaryTexture.name + " texture for " + targetId + ": "
//                            + auxiliaryTargetId);

                    TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer(),
                        texture.width(), texture.height(), texture.format());
                    TextureTracker.textureHandleToTexture.put(auxiliaryTargetId, texture);
                    auxiliaryTexture.textureHandleMapping.put(targetId, auxiliaryTargetId);
                } else {
                    auxiliaryTargetId = auxiliaryTexture.textureHandleMapping.get(targetId);

                    TextureTracker.Texture auxiliaryTrackerTexture =
                        TextureTracker.textureHandleToTexture.get(auxiliaryTargetId);
                    if (auxiliaryTrackerTexture == null
                        || texture.width() != auxiliaryTrackerTexture.width()
                        || texture.height() != auxiliaryTrackerTexture.height()
                        || texture.format() != auxiliaryTrackerTexture.format()) {
                        TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer(),
                            texture.width(), texture.height(), texture.format());
                        TextureTracker.textureHandleToTexture.put(auxiliaryTargetId, texture);
                    }
                }

                Identifier textureIdentifier = toTextureFileIdentifier(identifier);
                if (auxiliaryTemplateImage == null && isTrackedTexturePath(
                    textureIdentifier.getPath())) {
                    NativeImage preparedLevelCopy = auxiliaryTexture.copyPreparedImage(
                        textureIdentifier, level);
                    if (preparedLevelCopy != null) {
                        auxiliaryTemplateImage = preparedLevelCopy;
                    } else {
                        int defaultValue = auxiliaryTexture.defaultValueProvider.get(source);
                        auxiliaryTemplateImage = source.mappedCopy(i -> defaultValue);
                    }
                }

                if (auxiliaryTemplateImage == null) {
                    continue;
                }

                NativeImage auxiliaryImage = null;
                try {
                    auxiliaryImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage).radiance$alignTo(
                        source);
                    if (auxiliaryTemplateImage != auxiliaryImage) {
                        auxiliaryTemplateImage.close();
                    }

                    ((INativeImageExt) (Object) auxiliaryImage).radiance$setTargetID(
                        auxiliaryTargetId);

                    if (auxiliaryImage.getWidth() != source.getWidth()
                        || auxiliaryImage.getHeight() != source.getHeight()
                        || auxiliaryImage.format() != source.format()) {
                        throw new RuntimeException(
                            auxiliaryTexture.name + " image size / format mismatch");
                    }

                    if (level == 0 && auxiliaryTexture == SPECULAR) {
                        long tileKey = EmissionRecorder.buildTileKey(offsetX, offsetY,
                            regionWidth, regionHeight);
                        if (TextureProxy.hasEmissionTile(targetId, tileKey)) {
                            uploadToTexture(auxiliaryTargetId, auxiliaryImage, level, offsetX,
                                offsetY, unpackSkipPixels, unpackSkipRows, regionWidth,
                                regionHeight);
                            continue;
                        }

                        TextureProxy.uploadEmissionTile(EmissionRecorder.buildTileUpdate(targetId,
                            source, auxiliaryImage, offsetX, offsetY, unpackSkipPixels,
                            unpackSkipRows, regionWidth, regionHeight));
                    }

                    uploadToTexture(auxiliaryTargetId, auxiliaryImage, level, offsetX, offsetY,
                        unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight);
                    if (LOGGED_AUXILIARY_UPLOAD.compareAndSet(false, true)) {
                        RadianceClient.LOGGER.info(
                            "Radiance auxiliary texture bridge: initialized {} texture for {} as Radiance handle {} mapped from {}",
                            auxiliaryTexture.name, textureIdentifier, auxiliaryTargetId, targetId);
                    }
                } finally {
                    if (auxiliaryImage != null) {
                        auxiliaryImage.close();
                    }
                }
            }
        }
    }

    private static void uploadToTexture(int targetId, NativeImage image, int level, int offsetX,
        int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth, int regionHeight) {
        long pointer =
            ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) image).radiance$getPointer();
        int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();
        TextureProxy.queueUpload(pointer, srcSizeInBytes, image.getWidth(), targetId,
            unpackSkipPixels, unpackSkipRows, offsetX, offsetY, regionWidth, regionHeight, level);
    }

    private static int defaultNormalPixel(NativeImage source) {
        NativeImage.Format format = source.format();
        int value = 0;
        if (format.hasRed()) {
            value |= 128 << format.redOffset();
        }
        if (format.hasGreen()) {
            value |= 128 << format.greenOffset();
        }
        if (format.hasBlue()) {
            value |= 255 << format.blueOffset();
        }
        if (format.hasAlpha()) {
            value |= 255 << format.alphaOffset();
        }
        if (format.hasLuminance()) {
            value |= 128 << format.luminanceOffset();
        }
        return value;
    }

    private static Identifier toTextureFileIdentifier(Identifier identifier) {
        String path = identifier.getPath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return identifier;
        }
        if (path.startsWith("block/") || path.startsWith("item/")
            || path.startsWith("entity/")) {
            return SpriteSource.TEXTURE_ID_CONVERTER.idToFile(identifier);
        }
        return identifier;
    }

    private boolean matchesSuffix(String path) {
        return path.endsWith(suffix);
    }

    private Identifier toBaseSpriteId(Identifier spriteId) {
        String spritePath = spriteId.getPath();
        return spriteId.withPath(spritePath.substring(0, spritePath.length() - suffix.length()));
    }

    private CacheKey toBaseCacheKey(Identifier auxiliaryIdentifier) {
        String path = auxiliaryIdentifier.getPath();
        if (this == FLAG) {
            path = path.replaceFirst("^textures/flag/", "textures/");
        }

        int dotIndex = path.lastIndexOf('.');
        String baseName = path.substring(0, dotIndex);
        if (!baseName.endsWith(suffix)) {
            throw new IllegalArgumentException("Unexpected auxiliary path: " + auxiliaryIdentifier);
        }
        baseName = baseName.substring(0, baseName.length() - suffix.length());
        return new CacheKey(this, Identifier.fromNamespaceAndPath(auxiliaryIdentifier.getNamespace(),
            baseName + path.substring(dotIndex)));
    }

    private CacheEntry getPreparedEntry(Identifier identifier) {
        return DECODED_IMAGE_CACHE.getOrDefault(new CacheKey(this, identifier), CacheEntry.MISSING);
    }

    private NativeImage copyPreparedImage(Identifier identifier, int level) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            NativeImage preparedLevel = this.getPreparedEntry(identifier).getImage(level);
            if (preparedLevel == null) {
                return null;
            }

            NativeImage copied = new NativeImage(preparedLevel.format(),
                preparedLevel.getWidth(), preparedLevel.getHeight(), false);
            copied.copyFrom(preparedLevel);
            return copied;
        }
    }

    private static AuxiliaryTextures classifyAuxiliaryResource(Identifier id) {
        String path = id.getPath();
        if (!path.endsWith(".png")) {
            return null;
        }
        if (isTrackedFlagPath(path) && path.endsWith(FLAG.suffix + ".png")) {
            return FLAG;
        }
        if (isTrackedTexturePath(path) && path.endsWith(SPECULAR.suffix + ".png")) {
            return SPECULAR;
        }
        if (isTrackedTexturePath(path) && path.endsWith(NORMAL.suffix + ".png")) {
            return NORMAL;
        }
        return null;
    }

    public static CompletableFuture<PreparedImages> prepareDecodedImagesAsync(
        ResourceManager resourceManager, Executor prepareExecutor) {
        List<CompletableFuture<DecodedEntry>> futures = new ArrayList<>();
        Map<Identifier, Resource> resources = resourceManager.listResources("textures",
            id -> classifyAuxiliaryResource(id) != null);

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            AuxiliaryTextures auxiliaryTexture = classifyAuxiliaryResource(entry.getKey());
            if (auxiliaryTexture == null) {
                continue;
            }
            CacheKey cacheKey = auxiliaryTexture.toBaseCacheKey(entry.getKey());
            Resource resource = entry.getValue();
            futures.add(CompletableFuture.supplyAsync(
                () -> decodePreparedEntry(cacheKey, resource), prepareExecutor));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(CompletableFuture[]::new));
        return allFutures.thenApply(unused -> {
            PreparedImages prepared = new PreparedImages();
            for (CompletableFuture<DecodedEntry> future : futures) {
                prepared.add(future.join());
            }
            return prepared;
        });
    }

    private static DecodedEntry decodePreparedEntry(CacheKey cacheKey, Resource resource) {
        try (InputStream inputStream = resource.open()) {
            NativeImage image = NativeImage.read(inputStream);
            NativeImage[] levels = MipmapUtil.buildMipmapChain(image);
            return new DecodedEntry(cacheKey, new CacheEntry(levels));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void applyPreparedImages(PreparedImages prepared) {
        synchronized (DECODED_IMAGE_CACHE_LOCK) {
            clearDecodedImageCache();
            DECODED_IMAGE_CACHE.putAll(prepared.entries);
        }
    }

    private static boolean isTrackedTexturePath(String path) {
        return path.startsWith("textures/block/")
            || path.startsWith("textures/item/")
            || path.startsWith("textures/entity/");
    }

    private static boolean isTrackedFlagPath(String path) {
        return path.startsWith("textures/flag/block/")
            || path.startsWith("textures/flag/item/")
            || path.startsWith("textures/flag/entity/");
    }

    private record CacheKey(AuxiliaryTextures texture, Identifier identifier) {}

    private static final class CacheEntry {

        private static final CacheEntry MISSING = new CacheEntry(null);

        private final NativeImage[] levels;

        private CacheEntry(NativeImage[] levels) {
            this.levels = levels;
        }

        private NativeImage getImage(int level) {
            if (levels == null || levels.length == 0) {
                return null;
            }
            return levels[Math.min(level, levels.length - 1)];
        }
    }

    private record DecodedEntry(CacheKey cacheKey, CacheEntry cacheEntry) {}

    public static final class PreparedImages {

        private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

        private void add(DecodedEntry entry) {
            this.entries.put(entry.cacheKey(), entry.cacheEntry());
        }

        private void close() {
            for (CacheEntry entry : this.entries.values()) {
                if (entry.levels == null) {
                    continue;
                }
                for (NativeImage level : entry.levels) {
                    if (level != null) {
                        level.close();
                    }
                }
            }
            this.entries.clear();
        }
    }

    public interface IdentifierCandidateProvider {

        List<Identifier> get(Identifier identifier, NativeImage source);
    }

    public interface Getter {

        NativeImage get(INativeImageExt nativeImageExt);
    }

    public interface Setter {

        void set(INativeImageExt nativeImageExt, NativeImage nativeImage);
    }

    public interface DefaultValueProvider {

        int get(NativeImage source);
    }
}

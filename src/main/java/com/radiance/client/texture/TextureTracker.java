package com.radiance.client.texture;

import com.radiance.client.RendererAvailability;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class TextureTracker {

    /**
     * Radiance texture handles are native renderer ids produced by TextureProxy. They are not
     * Mojang/OpenGL texture names; the GpuTexture object remains the authoritative 26.2 identity.
     */
    public static final Map<Identifier, Integer> identifierToTextureHandle =
        new ConcurrentHashMap<>();
    public static final Map<Integer, Texture> textureHandleToTexture = new ConcurrentHashMap<>();
    public static final Map<Integer, Integer> textureHandleToSpecularHandle =
        new ConcurrentHashMap<>();
    public static final Map<Integer, Integer> textureHandleToNormalHandle =
        new ConcurrentHashMap<>();
    public static final Map<Integer, Integer> textureHandleToFlagHandle =
        new ConcurrentHashMap<>();
    public static final Map<GpuTexture, Integer> gpuTextureToTextureHandle =
        new ConcurrentHashMap<>();
    public static final Map<GpuTexture, Identifier> gpuTextureToIdentifier =
        new ConcurrentHashMap<>();
    public static final Map<GpuTexture, Set<Identifier>> gpuTextureToIdentifiers =
        new ConcurrentHashMap<>();

    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<Identifier, Integer> textureID2GLID = identifierToTextureHandle;
    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<Integer, Texture> GLID2Texture = textureHandleToTexture;
    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<Integer, Integer> GLID2SpecularGLID =
        textureHandleToSpecularHandle;
    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<Integer, Integer> GLID2NormalGLID = textureHandleToNormalHandle;
    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<Integer, Integer> GLID2FlagGLID = textureHandleToFlagHandle;
    /**
     * @deprecated Compatibility alias for old GL-id call sites. Values are Radiance texture
     * handles, not OpenGL ids.
     */
    @Deprecated
    public static final Map<GpuTexture, Integer> GPU_TEXTURE2GLID =
        gpuTextureToTextureHandle;
    /**
     * @deprecated Compatibility alias for old GL-id call sites.
     */
    @Deprecated
    public static final Map<GpuTexture, Identifier> GPU_TEXTURE2IDENTIFIER =
        gpuTextureToIdentifier;
    /**
     * @deprecated Compatibility alias for old GL-id call sites.
     */
    @Deprecated
    public static final Map<GpuTexture, Set<Identifier>> GPU_TEXTURE2IDENTIFIERS =
        gpuTextureToIdentifiers;

    public static int registerTextureHandle(GpuTexture gpuTexture) {
        return registerTextureHandle(gpuTexture, false);
    }

    public static int registerLightmapTexture(GpuTexture gpuTexture) {
        return registerTextureHandle(gpuTexture, true);
    }

    public static int registerGuiTexture(GpuTexture gpuTexture) {
        return registerTextureHandle(gpuTexture, true);
    }

    /**
     * @deprecated Use registerTextureHandle; the returned value is a Radiance texture handle.
     */
    @Deprecated
    public static int registerGpuTexture(GpuTexture gpuTexture) {
        return registerTextureHandle(gpuTexture);
    }

    private static int registerTextureHandle(GpuTexture gpuTexture, boolean allowSmallTexture) {
        if (gpuTexture == null) {
            return 0;
        }

        int existing = textureHandle(gpuTexture);
        if (existing != 0) {
            return existing;
        }
        if (!shouldMirrorTextures() || gpuTexture.isClosed()) {
            return 0;
        }

        Texture trackerTexture;
        try {
            trackerTexture = new Texture(gpuTexture);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0;
        }
        if (!allowSmallTexture && trackerTexture.width() < 64 && trackerTexture.height() < 64) {
            return 0;
        }

        int id = TextureProxy.generateTextureId();
        textureHandleToTexture.put(id, trackerTexture);
        TextureProxy.prepareImage(id, trackerTexture.maxLayer(), trackerTexture.width(),
            trackerTexture.height(), trackerTexture.format());
        gpuTextureToTextureHandle.put(gpuTexture, id);
        Identifier identifier = gpuTextureToIdentifier.get(gpuTexture);
        if (identifier != null) {
            identifierToTextureHandle.put(identifier, id);
        }
        Set<Identifier> identifiers = gpuTextureToIdentifiers.get(gpuTexture);
        if (identifiers != null) {
            for (Identifier trackedIdentifier : identifiers) {
                identifierToTextureHandle.put(trackedIdentifier, id);
            }
        }
        return id;
    }

    public static int getOrRegisterTextureHandle(GpuTexture gpuTexture) {
        int id = textureHandle(gpuTexture);
        return id != 0 ? id : registerTextureHandle(gpuTexture);
    }

    public static int getOrRegisterGuiTextureHandle(GpuTexture gpuTexture) {
        int id = textureHandle(gpuTexture);
        return id != 0 ? id : registerGuiTexture(gpuTexture);
    }

    /**
     * @deprecated Use getOrRegisterTextureHandle; the returned value is a Radiance texture handle.
     */
    @Deprecated
    public static int getOrRegisterGpuTexture(GpuTexture gpuTexture) {
        return getOrRegisterTextureHandle(gpuTexture);
    }

    /**
     * @deprecated Use getOrRegisterGuiTextureHandle; the returned value is a Radiance texture
     * handle.
     */
    @Deprecated
    public static int getOrRegisterGuiTexture(GpuTexture gpuTexture) {
        return getOrRegisterGuiTextureHandle(gpuTexture);
    }

    public static void registerTextureIdentifier(Identifier identifier, GpuTexture gpuTexture) {
        rememberTextureIdentifier(identifier, gpuTexture);
        if (identifier != null && isTrackable(gpuTexture) && shouldMirrorTextures()) {
            int id = shouldAllowSmallTexture(identifier, gpuTexture)
                ? getOrRegisterGuiTextureHandle(gpuTexture)
                : getOrRegisterTextureHandle(gpuTexture);
            if (id != 0) {
                identifierToTextureHandle.put(identifier, id);
            }
        }
    }

    public static void rememberTextureIdentifier(Identifier identifier, GpuTexture gpuTexture) {
        if (identifier == null || gpuTexture == null) {
            return;
        }
        if (gpuTexture.isClosed()) {
            textureHandle(gpuTexture);
            return;
        }

        gpuTextureToIdentifier.put(gpuTexture, identifier);
        gpuTextureToIdentifiers.computeIfAbsent(gpuTexture,
            ignored -> ConcurrentHashMap.newKeySet()).add(identifier);
        int existing = textureHandle(gpuTexture);
        if (existing != 0) {
            identifierToTextureHandle.put(identifier, existing);
        }
    }

    public static boolean shouldMirrorTextures() {
        return RendererAvailability.isRendererLifecycleActive()
            && Minecraft.getInstance() != null;
    }

    public static boolean shouldAllowSmallTexture(Identifier identifier, GpuTexture gpuTexture) {
        if (identifier != null) {
            String path = identifier.getPath();
            if (path.contains("/gui/")
                || path.contains("textures/gui")
                || path.contains("textures/font")
                || path.contains("font/")
                || path.contains("hud/")) {
                return true;
            }
        }

        if (isTrackable(gpuTexture) && gpuTexture.getLabel() != null) {
            String label = gpuTexture.getLabel().toLowerCase(java.util.Locale.ROOT);
            return label.contains("gui") || label.contains("font") || label.contains("hud");
        }
        return false;
    }

    public static int textureHandle(GpuTexture gpuTexture) {
        if (gpuTexture == null) {
            return 0;
        }

        Integer existing = gpuTextureToTextureHandle.get(gpuTexture);
        if (existing == null || existing == 0) {
            return 0;
        }

        if (gpuTexture.isClosed()) {
            forgetTextureIdentity(gpuTexture, existing);
            return 0;
        }
        return existing;
    }

    public static void rememberTextureHandle(Identifier identifier, int textureHandle) {
        if (identifier != null && textureHandle != 0) {
            identifierToTextureHandle.put(identifier, textureHandle);
        }
    }

    private static boolean isTrackable(GpuTexture gpuTexture) {
        return gpuTexture != null && !gpuTexture.isClosed();
    }

    private static void forgetTextureIdentity(GpuTexture gpuTexture, int textureHandle) {
        gpuTextureToTextureHandle.remove(gpuTexture, textureHandle);
        Identifier identifier = gpuTextureToIdentifier.remove(gpuTexture);
        if (identifier != null) {
            identifierToTextureHandle.remove(identifier, textureHandle);
        }

        Set<Identifier> identifiers = gpuTextureToIdentifiers.remove(gpuTexture);
        if (identifiers != null) {
            for (Identifier trackedIdentifier : identifiers) {
                identifierToTextureHandle.remove(trackedIdentifier, textureHandle);
            }
        }
    }

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, NativeImage.Format format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer);
        }

        public Texture(GpuTexture texture) {
            this(texture.getWidth(0), texture.getHeight(0), texture.getFormat().componentCount(),
                getFormat(texture.getFormat()), Math.max(1, texture.getMipLevels()));
        }

        private static int getChannel(NativeImage.Format internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case LUMINANCE_ALPHA -> 2;
                case LUMINANCE -> 1;
                default -> throw new IllegalArgumentException(
                    "Unknown internal format: " + internalFormat);
            };
        }

        private static VulkanConstants.VkFormat getFormat(
            NativeImage.Format internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case LUMINANCE_ALPHA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case LUMINANCE -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }

        private static VulkanConstants.VkFormat getFormat(GpuFormat format) {
            return switch (format) {
                case R8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
                case RG8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RGBA8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Unsupported GPU texture format: "
                    + format);
            };
        }
    }
}

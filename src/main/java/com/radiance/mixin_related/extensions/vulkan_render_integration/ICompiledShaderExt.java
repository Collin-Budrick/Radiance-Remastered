package com.radiance.mixin_related.extensions.vulkan_render_integration;

public interface ICompiledShaderExt {

    String radiance$getResolvedSource();

    void radiance$setResolvedSource(String resolvedSource);

    boolean radiance$isVirtualShader();

    void radiance$setVirtualShader(boolean virtualShader);
}

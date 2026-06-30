package com.radiance.mixin_related.extensions.vulkan_render_integration;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;

public interface IShaderProgramExt {

    String radiance$getShaderName();

    void radiance$setShaderName(String shaderName);

    VertexFormat radiance$getVertexFormat();

    void radiance$setVertexFormat(VertexFormat vertexFormat);

    String radiance$getVertexSource();

    void radiance$setVertexSource(String vertexSource);

    String radiance$getFragmentSource();

    void radiance$setFragmentSource(String fragmentSource);

    List<String> radiance$getSamplerNamesValue();

    void radiance$setSamplerNamesValue(List<String> samplerNames);

    List<Uniform> radiance$getUniformsValue();

    Object2IntMap<String> radiance$getSamplerTexturesValue();

    boolean radiance$isVirtualProgram();

    void radiance$setVirtualProgram(boolean virtualProgram);
}

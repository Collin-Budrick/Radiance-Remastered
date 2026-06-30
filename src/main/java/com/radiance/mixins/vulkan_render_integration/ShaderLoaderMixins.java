package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RendererAvailability;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public class ShaderLoaderMixins {

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_SHADER_ID = new AtomicInteger(1);

    @Unique
    private static final AtomicInteger NEXT_VIRTUAL_PROGRAM_ID = new AtomicInteger(1);

    @Unique
    private static final Constructor<GlProgram> PROGRAM_CONSTRUCTOR = radiance$programConstructor();

    @Inject(
        method = "compileShader(Lcom/mojang/blaze3d/opengl/GlDevice$ShaderCompilationKey;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/opengl/GlShaderModule;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void compileShaderWithoutOpenGL(@Coerce Object key, ShaderSource shaderSource,
        CallbackInfoReturnable<GlShaderModule> cir) {
        if (!RendererAvailability.shouldOwnRendererLifecycle()) {
            return;
        }

        Identifier id = radiance$keyValue(key, "id", Identifier.class);
        ShaderType type = radiance$keyValue(key, "type", ShaderType.class);
        ShaderDefines defines = radiance$keyValue(key, "defines", ShaderDefines.class);
        String source = radiance$shaderSource(shaderSource, id, type, defines);
        if (source == null) {
            cir.setReturnValue(GlShaderModule.INVALID_SHADER);
            return;
        }

        GlShaderModule shader = new GlShaderModule(NEXT_VIRTUAL_SHADER_ID.getAndIncrement(), id,
            type);
        if ((Object) shader instanceof ICompiledShaderExt ext) {
            ext.radiance$setResolvedSource(source);
            ext.radiance$setVirtualShader(true);
        }
        cir.setReturnValue(shader);
    }

    @Inject(
        method = "compileProgram(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/opengl/GlProgram;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void compileProgramWithoutOpenGL(RenderPipeline pipeline, ShaderSource shaderSource,
        CallbackInfoReturnable<GlProgram> cir) {
        if (!RendererAvailability.shouldOwnRendererLifecycle()) {
            return;
        }

        String vertexSource = radiance$shaderSource(shaderSource, pipeline.getVertexShader(),
            ShaderType.VERTEX, pipeline.getShaderDefines());
        String fragmentSource = radiance$shaderSource(shaderSource, pipeline.getFragmentShader(),
            ShaderType.FRAGMENT, pipeline.getShaderDefines());
        if (vertexSource == null || fragmentSource == null) {
            cir.setReturnValue(GlProgram.INVALID_PROGRAM);
            return;
        }

        GlProgram program = radiance$newProgram(pipeline.getLocation().toString());
        if ((Object) program instanceof IShaderProgramExt ext) {
            ext.radiance$setShaderName(pipeline.getLocation().toString());
            ext.radiance$setVertexFormat(radiance$primaryVertexFormat(pipeline));
            ext.radiance$setVertexSource(vertexSource);
            ext.radiance$setFragmentSource(fragmentSource);
            ext.radiance$setSamplerNamesValue(BindGroupLayout.flattenSamplers(
                pipeline.getBindGroupLayouts()));
            ext.radiance$setVirtualProgram(true);
        }
        cir.setReturnValue(program);
    }

    @Unique
    private static String radiance$shaderSource(ShaderSource shaderSource, Identifier id,
        ShaderType type, ShaderDefines defines) {
        String source = shaderSource.get(id, type);
        if (source == null) {
            return null;
        }
        return GlslPreprocessor.injectDefines(source, defines);
    }

    @Unique
    private static VertexFormat radiance$primaryVertexFormat(RenderPipeline pipeline) {
        VertexFormat[] bindings = pipeline.getVertexFormatBindings();
        return bindings.length == 0 ? null : bindings[0];
    }

    @Unique
    private static GlProgram radiance$newProgram(String debugLabel) {
        try {
            return PROGRAM_CONSTRUCTOR.newInstance(NEXT_VIRTUAL_PROGRAM_ID.getAndIncrement(),
                debugLabel);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create virtual GlProgram: " + debugLabel, e);
        }
    }

    @Unique
    private static Constructor<GlProgram> radiance$programConstructor() {
        try {
            Constructor<GlProgram> constructor = GlProgram.class.getDeclaredConstructor(int.class,
                String.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access GlProgram constructor", e);
        }
    }

    @Unique
    private static <T> T radiance$keyValue(Object key, String methodName, Class<T> type) {
        try {
            Method method = key.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return type.cast(method.invoke(key));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read shader compilation key " + methodName, e);
        }
    }
}

package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.radiance.client.renderpass.RenderPassPipelineState.RenderPassSetupState;

public interface IRenderSetupStateExt {

    RenderPassSetupState radiance$captureRenderPassSetupState();
}

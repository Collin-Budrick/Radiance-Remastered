package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ShaderPackSettingsScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;
    private static final int HEADER_HEIGHT = 32;
    private static final String TITLE = "shader_pack_settings_screen.title";
    private static final String NO_ATTRIBUTES = "module_attribute_screen.no_attributes";

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private Module module;
    private int scrollY = 0;

    public ShaderPackSettingsScreen(Screen parent) {
        super(Component.translatable(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(
            Button.builder(Component.translatable("Back"), button -> onClose())
                .bounds(10, 6, 60, 20)
                .build());

        rows.clear();
        module = Pipeline.getRayTracingModule();
        List<AttributeConfig> list = Pipeline.getRayTracingShaderPackAttributes();
        if (module == null || list.isEmpty()) {
            return;
        }

        for (AttributeConfig cfg : list) {
            List<AbstractWidget> ws = AttributeWidgetUtil.buildWidgets(module, cfg, this.font,
                WIDGET_WIDTH, VEC3_COMPONENT_WIDTH);
            for (AbstractWidget w : ws) {
                addRenderableWidget(w);
            }
            rows.add(new Row(cfg, ws));
        }
    }

    @Override
    public void onClose() {
        if (module != null) {
            Pipeline.getModuleAttributes(module);
            Pipeline.savePipeline();
        }
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.text(this.font, Component.translatable(TITLE), 10, HEADER_HEIGHT + 8,
            0xFFEAEAEA);

        if (rows.isEmpty()) {
            context.text(this.font, Component.translatable(NO_ATTRIBUTES), 10, 60,
                0xFFB0B0B0);
            return;
        }

        int baseY = (HEADER_HEIGHT + 28) + scrollY;
        int rowH = 22;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = baseY + i * rowH;
            boolean visible = y >= (HEADER_HEIGHT + 18) && y <= (this.height - 24);
            if (visible) {
                context.text(this.font, module.translateText(row.cfg.name), ROW_LEFT,
                    y + 6, 0xFFD0D0D0);
            }

            layoutRowWidgets(row, y);
            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            for (AbstractWidget w : row.widgets) {
                w.visible = visible;
                w.active = visible;

                if (!doBorder) {
                    continue;
                }

                boolean ok = true;
                if (type.equals("vec3")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getValue());
                    }
                } else if (type.equals("int")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictInt(tf.getValue());
                    }
                } else if (type.equals("float")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getValue());
                    }
                }

                if (w instanceof EditBox tf) {
                    int c = ok ? OK_BORDER : BAD_BORDER;
                    AttributeWidgetUtil.drawBorder(context, tf.getX(), tf.getY(), tf.getWidth(),
                        tf.getHeight(), c);
                }
            }
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH,
            VEC3_COMPONENT_WIDTH, VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
        double verticalAmount) {
        if (rows.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int rowH = 22;
        int contentH = rows.size() * rowH;
        int viewportH = this.height - (HEADER_HEIGHT + 56);
        int minScroll = Math.min(0, viewportH - contentH);
        scrollY += (int) (verticalAmount * 18);
        if (scrollY > 0) {
            scrollY = 0;
        }
        if (scrollY < minScroll) {
            scrollY = minScroll;
        }
        return true;
    }

    private record Row(AttributeConfig cfg, List<AbstractWidget> widgets) {
    }
}

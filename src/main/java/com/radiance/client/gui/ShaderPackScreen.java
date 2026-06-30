package com.radiance.client.gui;

import com.radiance.client.pipeline.Pipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class ShaderPackScreen extends Screen {

    private static final int LIST_TOP = 36;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 32;
    private static final int FOOTER_BUTTON_WIDTH = 120;
    private static final int FOOTER_BUTTON_GAP = 8;

    private static final String TITLE = "shader_pack_screen.title";
    private static final String BACK = "render_pipeline_screen.back";
    private static final String SHADER_SETTINGS = "shader_pack_screen.shader_settings";
    private static final String EMPTY = "shader_pack_screen.empty";

    private final Screen parent;
    private final List<Pipeline.ShaderPackChoice> entries = new ArrayList<>();
    private ShaderPackListWidget shaderPackList;

    public ShaderPackScreen(Screen parent) {
        super(Component.translatable(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        entries.addAll(Pipeline.getAvailableShaderPacks());

        int listHeight = this.height - LIST_TOP - FOOTER_HEIGHT;
        shaderPackList = addRenderableWidget(new ShaderPackListWidget(this.minecraft, this.width, listHeight, LIST_TOP, ROW_HEIGHT));

        int footerY = this.height - 28;
        int footerX = (this.width - (FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP)) / 2;

        addRenderableWidget(Button.builder(Component.translatable(BACK), button -> onClose())
            .bounds(footerX, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable(SHADER_SETTINGS),
                button -> Minecraft.getInstance().setScreenAndShow(new ShaderPackSettingsScreen(this)))
            .bounds(footerX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractBackground(context, mouseX, mouseY, delta);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, Component.translatable(TITLE), this.width / 2, 16, 0xFFFFFF);

        Component hoveredTooltip = shaderPackList == null ? null : shaderPackList.getHoveredTooltip();
        if (hoveredTooltip != null) {
            context.setTooltipForNextFrame(this.font, hoveredTooltip, mouseX, mouseY);
        }

        if (entries.isEmpty()) {
            context.centeredText(this.font, Component.translatable(EMPTY), this.width / 2, LIST_TOP + 10, 0xB0B0B0);
        }
    }

    private Component buildLabel(Pipeline.ShaderPackChoice choice) {
        Component label = parseLegacyFormattedText(choice.displayName());
        if (Pipeline.isShaderPackActive(choice)) {
            label = Component.literal("> ").append(label);
        }
        return label;
    }

    private Component parseLegacyFormattedText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == ChatFormatting.PREFIX_CODE && i + 1 < raw.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (formatting != null) {
                    if (segment.length() > 0) {
                        result.append(Component.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }

                    if (formatting == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else {
                        style = style.applyLegacyFormat(formatting);
                    }
                    i++;
                    continue;
                }
            }
            segment.append(current);
        }

        if (segment.length() > 0) {
            result.append(Component.literal(segment.toString()).setStyle(style));
        }

        return result;
    }

    class ShaderPackListWidget extends ObjectSelectionList<ShaderPackListWidget.ShaderPackEntry> {
        ShaderPackListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
            this.centerListVertically = false;

            for (Pipeline.ShaderPackChoice choice : entries) {
                ShaderPackEntry entry = new ShaderPackEntry(choice);
                this.addEntry(entry);
                if (Pipeline.isShaderPackActive(choice)) {
                    this.setSelected(entry);
                }
            }

            if (this.getSelected() != null) {
                this.centerScrollOn(this.getSelected());
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(340, ShaderPackScreen.this.width - 20);
        }

        @Override
        public void setSelected(ShaderPackEntry entry) {
            if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return;
            }
            super.setSelected(entry);
        }

        @Override
        public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
            if (focused instanceof ShaderPackEntry entry && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return;
            }
            super.setFocused(focused);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ShaderPackEntry entry = this.getEntryAtPosition(event.x(), event.y());
            if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        private Component getHoveredTooltip() {
            ShaderPackEntry entry = this.getHovered();
            return entry == null ? null : entry.getUnavailableReason();
        }

        class ShaderPackEntry extends ObjectSelectionList.Entry<ShaderPackEntry> {
            private final Pipeline.ShaderPackChoice choice;

            ShaderPackEntry(Pipeline.ShaderPackChoice choice) {
                this.choice = choice;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY,
                boolean hovered, float tickDelta) {
                int textColor = Pipeline.isShaderPackSelectable(this.choice) ? 0xFFFFFF : 0x9A9A9A;
                context.centeredText(
                    ShaderPackScreen.this.font,
                    ShaderPackScreen.this.buildLabel(this.choice),
                    ShaderPackListWidget.this.width / 2,
                    this.getContentYMiddle() - 9 / 2,
                    textColor
                );
            }

            private Component getUnavailableReason() {
                String translationKey = Pipeline.getShaderPackUnavailabilityReasonTranslationKey(this.choice);
                return translationKey == null ? null : Component.translatable(translationKey);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (!Pipeline.isShaderPackSelectable(this.choice)) {
                    return true;
                }
                ShaderPackListWidget.this.setSelected(this);
                Pipeline.setShaderPack(this.choice, false);
                return super.mouseClicked(event, doubleClick);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", ShaderPackScreen.this.buildLabel(this.choice));
            }
        }
    }
}

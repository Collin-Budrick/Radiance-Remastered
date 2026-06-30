package com.radiance.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public class CategoryVideoOptionEntry extends ObjectSelectionList.Entry<CategoryVideoOptionEntry> {

    private final Component text;
    private final int textWidth;
    private final Font font;
    private final OptionsList parent;

    public CategoryVideoOptionEntry(Component text, OptionsList parent) {
        this.parent = parent;

        this.text = text;
        this.font = Minecraft.getInstance().font;
        this.textWidth = this.font.width(this.text);
    }

    @Override
    public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY,
        boolean hovered, float tickDelta) {
        context.text(
            this.font, this.text, parent.getWidth() / 2 - this.textWidth / 2,
            this.getContentBottom() - 10, CommonColors.WHITE
        );
    }

    @Override
    public Component getNarration() {
        return this.text;
    }
}

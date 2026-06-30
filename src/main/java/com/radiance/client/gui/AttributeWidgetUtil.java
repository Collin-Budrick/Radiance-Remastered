package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class AttributeWidgetUtil {

    private AttributeWidgetUtil() {
    }

    static boolean shouldValidateBorder(String type) {
        return type.equals("int") || type.equals("float") || type.equals("string") || type.equals(
            "vec3");
    }

    static boolean isStrictInt(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isStrictFloat(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    static void layoutWidgets(List<AbstractWidget> widgets, int x, int y, int singleWidth,
        int tripleWidth, int gap) {
        if (widgets.size() == 1) {
            AbstractWidget w = widgets.get(0);
            w.setX(x);
            w.setY(y);
            w.setWidth(singleWidth);
            return;
        }

        if (widgets.size() == 3) {
            for (int i = 0; i < 3; i++) {
                AbstractWidget cw = widgets.get(i);
                cw.setX(x + i * (tripleWidth + gap));
                cw.setY(y);
                cw.setWidth(tripleWidth);
            }
        }
    }

    static int totalWidgetWidth(List<AbstractWidget> widgets, int singleWidth, int tripleWidth, int gap) {
        if (widgets.size() == 3) {
            return (tripleWidth * 3) + (gap * 2);
        }
        return singleWidth;
    }

    static List<AbstractWidget> buildWidgets(Module module, AttributeConfig cfg, Font textRenderer,
        int width,
        int vec3ComponentWidth) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(module, cfg, cfg.type.substring(5), width));
        }

        if (type.startsWith("int_range:")) {
            return List.of(buildIntRange(cfg, cfg.type.substring(10), width));
        }

        if (type.startsWith("float_range:")) {
            return List.of(buildFloatRange(cfg, cfg.type.substring(12), width));
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(module, cfg, width));
            case "int" -> List.of(buildIntWidget(cfg, textRenderer, width));
            case "float" -> List.of(buildFloatWidget(cfg, textRenderer, width));
            case "string" -> List.of(buildStringWidget(cfg, textRenderer, width));
            case "vec3" -> buildVec3Widget(cfg, textRenderer, vec3ComponentWidth);
            default -> List.of(buildStringWidget(cfg, textRenderer, width));
        };
    }

    private static AbstractWidget buildBoolWidget(Module module, AttributeConfig cfg, int width) {
        boolean b = "render_pipeline.true".equalsIgnoreCase(cfg.value);
        return Button.builder(
            module.translateText(b ? "render_pipeline.true" : "render_pipeline.false"), btn -> {
                boolean nv = !"render_pipeline.true".equalsIgnoreCase(cfg.value);
                cfg.value = nv ? "render_pipeline.true" : "render_pipeline.false";
                btn.setMessage(module.translateText(cfg.value));
            }).bounds(0, 0, width, 20).build();
    }

    private static AbstractWidget buildEnumWidget(Module module, AttributeConfig cfg, String raw, int width) {
        String[] values = raw.isEmpty() ? new String[]{"<empty>"} : raw.split("-");
        int idx = 0;
        if (cfg.value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(cfg.value)) {
                    idx = i;
                    break;
                }
            }
        } else {
            cfg.value = values[0];
        }

        int[] index = new int[]{idx};
        return Button.builder(module.translateText(values[index[0]]), btn -> {
            index[0] = (index[0] + 1) % values.length;
            cfg.value = values[index[0]];
            btn.setMessage(module.translateText(cfg.value));
        }).bounds(0, 0, width, 20).build();
    }

    private static AbstractWidget buildIntWidget(AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(64);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setResponder(text -> {
            if (isStrictInt(text)) {
                cfg.value = text;
            }
        });
        return tf;
    }

    private static AbstractWidget buildFloatWidget(AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(64);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setResponder(text -> {
            if (isStrictFloat(text)) {
                cfg.value = text;
            }
        });
        return tf;
    }

    private static AbstractWidget buildStringWidget(AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(128);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setResponder(text -> cfg.value = text);
        return tf;
    }

    private static List<AbstractWidget> buildVec3Widget(AttributeConfig cfg,
        Font textRenderer,
        int componentWidth) {
        if (cfg.value == null || cfg.value.isEmpty()) {
            cfg.value = "0,0,0";
        }

        float[] v = parseVec3(cfg.value);
        EditBox x = vecField(textRenderer, v[0], componentWidth);
        EditBox y = vecField(textRenderer, v[1], componentWidth);
        EditBox z = vecField(textRenderer, v[2], componentWidth);

        Runnable syncIfValid = () -> {
            String sx = x.getValue();
            String sy = y.getValue();
            String sz = z.getValue();

            if (isStrictFloat(sx) && isStrictFloat(sy) && isStrictFloat(sz)) {
                cfg.value = sx + "," + sy + "," + sz;
            }
        };

        x.setResponder(s -> syncIfValid.run());
        y.setResponder(s -> syncIfValid.run());
        z.setResponder(s -> syncIfValid.run());

        syncIfValid.run();
        return List.of(x, y, z);
    }

    private static EditBox vecField(Font textRenderer, float v, int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(32);
        tf.setValue(trimFloat(v));
        return tf;
    }

    private static AbstractWidget buildIntRange(AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        int start = (int) r.start;
        int end = (int) r.end;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }

        int cur = start;
        if (isInt(cfg.value)) {
            cur = Integer.parseInt(cfg.value);
        } else {
            cfg.value = String.valueOf(start);
        }
        cur = Mth.clamp(cur, start, end);

        IntRangeSlider slider = new IntRangeSlider(0, 0, width, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private static AbstractWidget buildFloatRange(AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        float start = (float) r.start;
        float end = (float) r.end;
        if (start > end) {
            float t = start;
            start = end;
            end = t;
        }

        float cur = start;
        if (isFloat(cfg.value)) {
            cur = Float.parseFloat(cfg.value);
        } else {
            cfg.value = formatTwoDecimals(start);
        }
        cur = Mth.clamp(cur, start, end);

        FloatRangeSlider slider = new FloatRangeSlider(0, 0, width, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFloat(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float[] parseVec3(String s) {
        if (s == null || s.isEmpty()) {
            return new float[]{0, 0, 0};
        }

        String[] p = s.split("[,\\s]+");
        float x = p.length > 0 && isFloat(p[0]) ? Float.parseFloat(p[0]) : 0;
        float y = p.length > 1 && isFloat(p[1]) ? Float.parseFloat(p[1]) : 0;
        float z = p.length > 2 && isFloat(p[2]) ? Float.parseFloat(p[2]) : 0;
        return new float[]{x, y, z};
    }

    private static String formatTwoDecimals(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String trimFloat(float v) {
        String s = Float.toString(v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static Range parseRange(String raw) {
        int dash = raw.lastIndexOf('-');
        if (dash <= 0) {
            return new Range(0, 1);
        }
        String a = raw.substring(0, dash);
        String b = raw.substring(dash + 1);
        double start = 0;
        double end = 1;
        try {
            start = Double.parseDouble(a);
            end = Double.parseDouble(b);
        } catch (Exception ignored) {
        }
        return new Range(start, end);
    }

    private record Range(double start, double end) {

    }

    private static class IntRangeSlider extends AbstractSliderButton {

        private final int start;
        private final int end;
        private final AttributeConfig cfg;

        public IntRangeSlider(int x, int y, int width, int height, int start, int end, int cur,
            AttributeConfig cfg) {
            super(x, y, width, height, Component.empty(),
                (cur - (double) start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.value = (cur - (double) start) / (double) (end - start);
        }

        private int current() {
            if (end == start) {
                return start;
            }
            return start + (int) Math.round(this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(Integer.toString(current())));
        }

        @Override
        protected void applyValue() {
            int v = Mth.clamp(current(), start, end);
            cfg.value = Integer.toString(v);
        }
    }

    private static class FloatRangeSlider extends AbstractSliderButton {

        private final float start;
        private final float end;
        private final AttributeConfig cfg;

        public FloatRangeSlider(int x, int y, int width, int height, float start, float end,
            float cur,
            AttributeConfig cfg) {
            super(x, y, width, height, Component.empty(), (cur - start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.value = (cur - start) / (double) (end - start);
        }

        private float current() {
            if (end == start) {
                return start;
            }
            return (float) (start + this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(formatTwoDecimals(current())));
        }

        @Override
        protected void applyValue() {
            float v = Mth.clamp(current(), start, end);
            cfg.value = formatTwoDecimals(v);
        }
    }
}

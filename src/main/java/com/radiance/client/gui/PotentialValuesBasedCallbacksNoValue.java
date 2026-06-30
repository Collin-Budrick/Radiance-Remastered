package com.radiance.client.gui;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.OptionInstance;

@Environment(EnvType.CLIENT)
public record PotentialValuesBasedCallbacksNoValue<T>(List<T> values, Codec<T> codec) implements
    OptionInstance.CycleableValueSet<T> {

    @Override
    public Optional<T> validateValue(T value) {
        return this.values.contains(value) ? Optional.of(value) : Optional.empty();
    }

    @Override
    public CycleButton.ValueListSupplier<T> valueListSupplier() {
        return CycleButton.ValueListSupplier.create(this.values);
    }
}

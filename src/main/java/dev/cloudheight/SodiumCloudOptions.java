// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SodiumCloudOptions implements ConfigEntryPoint {
    private final StorageEventHandler storage = CloudHeightCompat::saveFromMenu;
    private static final ResourceLocation ENABLED = id("enabled");
    private static final ResourceLocation MANUAL = id("manual_height_enabled");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CloudHeightCompat.ID, path);
    }

    private static Component text(String key) {
        return Component.translatable("cloud_height_compat.options." + key);
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions().addPage(builder.createOptionPage()
                .setName(text("page"))
                .addOptionGroup(builder.createOptionGroup()
                        .addOption(builder.createBooleanOption(ENABLED)
                                .setName(text("enabled"))
                                .setTooltip(text("enabled.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(CloudHeightCompat::setEnabled, CloudHeightCompat::getEnabled)
                                .setStorageHandler(storage))
                        .addOption(builder.createBooleanOption(MANUAL)
                                .setName(text("manual"))
                                .setTooltip(text("manual.tooltip"))
                                .setDefaultValue(false)
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)
                                .setBinding(CloudHeightCompat::setManualEnabled, CloudHeightCompat::getManualEnabled)
                                .setStorageHandler(storage))
                        .addOption(builder.createIntegerOption(id("manual_height"))
                                .setName(text("height"))
                                .setTooltip(text("height.tooltip"))
                                .setRange(CloudHeightCompat.MIN_MANUAL_HEIGHT, CloudHeightCompat.MAX_MANUAL_HEIGHT, 1)
                                .setDefaultValue(CloudHeightCompat.DEFAULT_MANUAL_HEIGHT)
                                .setValueFormatter(value -> Component.literal("Y = " + value))
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED)
                                                && state.readBooleanOption(MANUAL), ENABLED, MANUAL)
                                .setBinding(CloudHeightCompat::setManualHeight, CloudHeightCompat::getManualHeight)
                                .setStorageHandler(storage))));
    }
}

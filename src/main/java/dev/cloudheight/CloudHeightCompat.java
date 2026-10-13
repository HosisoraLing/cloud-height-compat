// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Vector3f;

@Mod(value = CloudHeightCompat.ID, dist = Dist.CLIENT)
public final class CloudHeightCompat {
    public static final String ID = "cloud_height_compat";
    public static final int DEFAULT_MANUAL_HEIGHT = 832;
    public static final int MIN_MANUAL_HEIGHT = -512;
    public static final int MAX_MANUAL_HEIGHT = 8192;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue AUTOMATIC;
    private static final ModConfigSpec.IntValue CLEARANCE;
    private static final ModConfigSpec.DoubleValue OFFSET;
    private static final ModConfigSpec.BooleanValue MANUAL_ENABLED;
    private static final ModConfigSpec.IntValue MANUAL_HEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;
    private static final ModConfigSpec SPEC;
    private static volatile Settings settings = new Settings(true, true, 64, 0, false, DEFAULT_MANUAL_HEIGHT, Map.of());
    // Iris requests these values on the render thread; config reload only replaces settings.
    private static final Vector3f PARAMETERS = new Vector3f();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Enable shader cloud height correction.").define("enabled", true);
        AUTOMATIC = b.comment("When the dimension top exceeds Y=320, raise clouds above it.",
                "Uses the actual dimension height received from the server, not a mod-specific config.")
                .define("automatic", true);
        CLEARANCE = b.comment("Distance above the exclusive build height limit, in blocks.")
                .defineInRange("clearance", 64, 0, 4096);
        OFFSET = b.comment("Extra offset applied after automatic correction, in blocks.")
                .defineInRange("extraOffset", 0.0, -32768.0, 32768.0);
        MANUAL_ENABLED = b.comment("Use the global manual cloud base height instead of automatic correction.",
                "False preserves the behavior of version 1.0.0. Dimension overrides still take priority.")
                .define("manualHeightEnabled", false);
        MANUAL_HEIGHT = b.comment("Cloud base Y coordinate used when manualHeightEnabled is true.")
                .defineInRange("manualHeight", DEFAULT_MANUAL_HEIGHT, MIN_MANUAL_HEIGHT, MAX_MANUAL_HEIGHT);
        OVERRIDES = b.comment("Absolute cloud base heights per dimension; override automatic and extraOffset.",
                "Example: [\"minecraft:overworld=900\", \"example:sky=1200\"]")
                .defineListAllowEmpty("dimensionOverrides", List.<String>of(),
                        value -> value instanceof String s && HeightPolicy.validOverride(s));
        SPEC = b.build();
    }

    public CloudHeightCompat(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, "cloud-height-compat-client.toml");
        modBus.addListener(this::onLoad);
        modBus.addListener(this::onReload);
    }

    private void onLoad(ModConfigEvent.Loading event) { update(event); }
    private void onReload(ModConfigEvent.Reloading event) { update(event); }

    private void update(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refreshSettings();
    }

    private static void refreshSettings() {
        Map<String, Float> overrides = new HashMap<>();
        for (String entry : OVERRIDES.get()) {
            int split = entry.indexOf('=');
            overrides.put(entry.substring(0, split).trim(), Float.parseFloat(entry.substring(split + 1).trim()));
        }
        settings = new Settings(ENABLED.get(), AUTOMATIC.get(), CLEARANCE.get(),
                OFFSET.get().floatValue(), MANUAL_ENABLED.get(), MANUAL_HEIGHT.get(), Map.copyOf(overrides));
    }

    public static boolean getEnabled() { return ENABLED.get(); }
    public static void setEnabled(boolean value) { ENABLED.set(value); }
    public static boolean getManualEnabled() { return MANUAL_ENABLED.get(); }
    public static void setManualEnabled(boolean value) { MANUAL_ENABLED.set(value); }
    public static int getManualHeight() { return MANUAL_HEIGHT.get(); }
    public static void setManualHeight(int value) { MANUAL_HEIGHT.set(value); }

    public static void saveFromMenu() {
        SPEC.save();
        refreshSettings();
    }

    public static Vector3f parameters() {
        ClientLevel level = Minecraft.getInstance().level;
        Settings s = settings;
        if (level == null) return HeightPolicy.identity(PARAMETERS);
        boolean eligible = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && Float.isFinite(level.effects().getCloudHeight());
        return HeightPolicy.parameters(PARAMETERS, s.enabled, eligible, level.getMaxBuildHeight(),
                s.automatic, s.clearance, s.offset, HeightPolicy.absoluteHeight(s.manualEnabled, s.manualHeight,
                        s.overrides.get(level.dimension().location().toString())));
    }

    public static double adjust(double original) {
        if (!Double.isFinite(original)) return original;
        Vector3f p = parameters();
        return p.z == 1.0f ? p.x : Math.max(original, p.y) + p.x;
    }

    private record Settings(boolean enabled, boolean automatic, int clearance, float offset,
                            boolean manualEnabled, int manualHeight,
                            Map<String, Float> overrides) {}
}

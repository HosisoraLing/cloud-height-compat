// SPDX-License-Identifier: AGPL-3.0-only
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.cloudheight.CloudHeightCompat;
import dev.cloudheight.HeightPolicy;
import dev.cloudheight.SodiumCloudOptions;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import net.caffeinemc.mods.sodium.client.config.ConfigManager.ModMetadata;
import net.caffeinemc.mods.sodium.client.config.builder.ConfigBuilderImpl;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SodiumChecks {
    private static int checks;
    private static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CloudHeightCompat.ID, path);
    }
    private static ModConfigSpec spec() throws Exception {
        Field field = CloudHeightCompat.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        return (ModConfigSpec) field.get(null);
    }
    private static final class TestContainer extends ModContainer {
        private final IEventBus bus = BusBuilder.builder().build();
        private int saves;
        TestContainer() {
            super((IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[]{IModInfo.class},
                    (proxy, method, args) -> method.getName().equals("getModId") ? CloudHeightCompat.ID : null));
            bus.addListener((ModConfigEvent.Reloading event) -> saves++);
        }
        @Override public IEventBus getEventBus() { return bus; }
    }
    private record Fixture(IConfigSpec.ILoadedConfig loaded, TestContainer container) {}
    private static Fixture load(ModConfigSpec spec, CommentedConfig config, Path path) throws Exception {
        TestContainer container = new TestContainer();
        var modConstructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                ModContainer.class, String.class, ReentrantLock.class);
        modConstructor.setAccessible(true);
        ModConfig modConfig = modConstructor.newInstance(ModConfig.Type.CLIENT, spec, container, path.getFileName().toString(), new ReentrantLock());
        Class<?> loadedClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor = loadedClass.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        constructor.setAccessible(true);
        IConfigSpec.ILoadedConfig loaded = (IConfigSpec.ILoadedConfig) constructor.newInstance(config, path, modConfig);
        Field field = ModConfig.class.getDeclaredField("loadedConfig");
        field.setAccessible(true);
        field.set(modConfig, loaded);
        spec.acceptConfig(loaded);
        return new Fixture(loaded, container);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        Files.createDirectories(path.getParent());
        FMLPaths.loadAbsolutePaths(path.getParent());
        ModConfigSpec spec = spec();
        if (args.length > 1 && args[1].equals("reload")) {
            try (var reader = Files.newBufferedReader(path)) {
                load(spec, new TomlParser().parse(reader), path);
            }
            check("fresh JVM restores saved manual mode and height", CloudHeightCompat.getManualEnabled()
                    && CloudHeightCompat.getManualHeight() == 960);
            System.out.println("ALL PASS: " + checks + " reload checks; no game launched.");
            return;
        }
        CommentedConfig old = new TomlParser().parse(new StringReader("""
                enabled=true
                automatic=true
                clearance=64
                extraOffset=0.0
                dimensionOverrides=[]
                """));
        spec.correct(old);
        Fixture loaded = load(spec, old, path);
        check("1.0.0 config migration keeps automatic mode and defaults manual height to 832",
                !CloudHeightCompat.getManualEnabled() && CloudHeightCompat.getManualHeight() == 832
                        && old.<Boolean>get("automatic") && old.<Integer>get("clearance") == 64);
        check("manual mode disabled preserves existing policy", HeightPolicy.absoluteHeight(false, 832, null) == null);
        check("manual mode selects the slider height", HeightPolicy.absoluteHeight(true, 960, null) == 960f);
        check("dimension override takes priority", HeightPolicy.absoluteHeight(true, 960, 600f) == 600f);
        ConfigBuilderImpl builder = new ConfigBuilderImpl(mod -> new ModMetadata("Cloud Height Compat", "1.1.1"), CloudHeightCompat.ID);
        new SodiumCloudOptions().registerConfigLate(builder);
        Config menu = new Config(new ArrayList<>(builder.build()));
        check("native Sodium builder validates page and dependencies", menu.getModOptions().size() == 1
                && menu.getModOptions().getFirst().pages().size() == 1);
        BooleanOption enabled = (BooleanOption) menu.getOption(id("enabled"));
        BooleanOption manual = (BooleanOption) menu.getOption(id("manual_height_enabled"));
        IntegerOption height = (IntegerOption) menu.getOption(id("manual_height"));
        check("native Sodium slider is registered", height.getControl() instanceof SliderControl);
        check("slider defaults to 832 and stays disabled in automatic mode", height.getValidatedValue() == 832 && !height.isEnabled());
        check("slider supports -512 to 8192 in one-block steps", height.getSteppedValidator().min() == -512
                && height.getSteppedValidator().max() == 8192 && height.getSteppedValidator().step() == 1);
        check("slider displays Y coordinate", height.formatValue(832).getString().equals("Y = 832"));
        manual.modifyValue(true);
        check("manual toggle enables slider before apply", height.isEnabled());
        height.modifyValue(960);
        check("pending slider edit does not change active config", height.getValidatedValue() == 960
                && CloudHeightCompat.getManualHeight() == 832 && !CloudHeightCompat.getManualEnabled());
        menu.applyAllOptions();
        check("apply commits both bindings and flushes the shared storage once", loaded.container.saves == 1
                && CloudHeightCompat.getManualEnabled() && CloudHeightCompat.getManualHeight() == 960);
        try (var reader = Files.newBufferedReader(path)) {
            CommentedConfig saved = new TomlParser().parse(reader);
            check("saved TOML contains manual height and retains automatic settings", saved.<Boolean>get("manualHeightEnabled")
                    && saved.<Integer>get("manualHeight") == 960 && saved.<Integer>get("clearance") == 64);
        }
        height.modifyValue(1200);
        menu.resetAllOptionsFromBindings();
        check("discard restores applied value without writing", height.getValidatedValue() == 960 && loaded.container.saves == 1);
        height.modifyValue(-512);
        check("slider lower endpoint is accepted", height.getValidatedValue() == -512);
        height.modifyValue(8192);
        check("slider upper endpoint is accepted", height.getValidatedValue() == 8192);
        height.modifyValue(-1000);
        check("Sodium rejects out-of-range values to default", height.getValidatedValue() == 832);
        enabled.modifyValue(false);
        check("disabling compatibility disables manual toggle and slider", !manual.isEnabled() && !height.isEnabled());
        menu.resetAllOptionsFromBindings();
        manual.resetToDefault();
        height.resetToDefault();
        menu.applyAllOptions();
        check("reset defaults restores automatic mode and 832", !CloudHeightCompat.getManualEnabled()
                && CloudHeightCompat.getManualHeight() == 832);
        manual.modifyValue(true);
        height.modifyValue(960);
        menu.applyAllOptions();
        System.out.println("ALL PASS: " + checks + " Sodium registration and persistence checks; no game launched.");
    }
}

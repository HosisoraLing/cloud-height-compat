// SPDX-License-Identifier: AGPL-3.0-only
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cloudheight.BslPatch;
import dev.cloudheight.HeightPolicy;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.helpers.StringPair;
import org.joml.Vector3f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class Checks {
    private static int checks;
    private static void check(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    private static float resolve(float base, Vector3f p) {
        return p.z > 0.5f ? p.x : Math.max(base, p.y) + p.x;
    }

    public static void main(String[] args) throws Exception {
        Vector3f p = new Vector3f();
        HeightPolicy.parameters(p, true, true, 320, true, 64, 0, null);
        check("vanilla automatic remains 192", resolve(192, p) == 192);
        HeightPolicy.parameters(p, true, true, 768, true, 64, 0, null);
        check("SkyExplorer automatic and fixed 320 both become 832", resolve(192, p) == 832 && resolve(320, p) == 832);
        check("already higher shader cloud remains higher", resolve(1200, p) == 1200);
        HeightPolicy.parameters(p, true, true, 768, true, 64, -32, null);
        check("negative offset applies after floor", resolve(320, p) == 800);
        HeightPolicy.parameters(p, true, true, 1024, true, 64, 100, 900f);
        check("absolute dimension override beats offset and floor", resolve(1200, p) == 900);
        HeightPolicy.parameters(p, true, true, 768, false, 64, 40, null);
        check("manual-only offset", resolve(192, p) == 232);
        HeightPolicy.parameters(p, false, true, 768, true, 64, 100, 900f);
        check("disabled identity", resolve(320, p) == 320);
        HeightPolicy.parameters(p, true, false, 768, true, 64, 100, 900f);
        check("no-cloud or ceiling dimension identity", resolve(320, p) == 320);
        HeightPolicy.parameters(p, true, true, 256, true, 64, 0, null);
        check("lower dimension does not move clouds", resolve(192, p) == 192);
        HeightPolicy.parameters(p, true, true, 4096, true, 64, 0, null);
        check("extended height reaches 4160", resolve(320, p) == 4160);
        HeightPolicy.parameters(p, true, true, 320, true, 64, 0, null);
        check("dimension transition clears previous floor", resolve(192, p) == 192);
        check("valid dimension overrides", HeightPolicy.validOverride("minecraft:overworld=900")
                && HeightPolicy.validOverride("example:sky/island = -64.5"));
        check("invalid overrides rejected", List.of("overworld=900", "x:y=NaN", "x:y=Infinity", "x:y=1=2",
                "x:y=40000", "x:y=", "X:y=900").stream().noneMatch(HeightPolicy::validOverride));
        check("unknown shaders untouched", BslPatch.patch("/other.glsl", List.of("float lowerY = float(CLOUD_HEIGHT);"))
                .equals(List.of("float lowerY = float(CLOUD_HEIGHT);")));
        check("missing shader remains missing", BslPatch.patch("/lib/atmospherics/clouds.glsl", null) == null);
        checkIrisTargets();
        for (String arg : args) checkPack(Path.of(arg));
        System.out.println("ALL PASS: " + checks + " checks; offline only, no game launched.");
    }

    private static void checkIrisTargets() throws Exception {
        String world = "net/irisshaders/iris/uniforms/IrisExclusiveUniforms$WorldInfoUniforms.class";
        ClassNode node = new ClassNode();
        new ClassReader(Checks.class.getClassLoader().getResourceAsStream(world)).accept(node, 0);
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("addWorldInfoUniforms")).findFirst().orElseThrow();
        long count = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/irisshaders/iris/gl/uniform/UniformHolder")
                    && call.name.equals("uniform1f") && call.desc.equals("(Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;Ljava/lang/String;Ljava/util/function/DoubleSupplier;)Lnet/irisshaders/iris/gl/uniform/UniformHolder;")) count++;
        }
        check("installed Iris DoubleSupplier injection target exists exactly once", count == 1);
        check("installed Iris include return type matches mixin", IncludeProcessor.class.getDeclaredMethod(
                "getIncludedFile", AbsolutePackPath.class).getReturnType() == ImmutableList.class);
    }

    private static void checkPack(Path pack) throws Exception {
        Map<AbsolutePackPath, FileNode> nodes = new HashMap<>();
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            for (var entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (name.startsWith("shaders/") && (name.endsWith(".glsl") || name.endsWith(".fsh") || name.endsWith(".vsh"))) {
                    AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/" + name.substring(8));
                    String source = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    nodes.put(path, new FileNode(path, ImmutableList.copyOf(source.split("\\R"))));
                }
            }
        }
        for (String target : List.of("/lib/atmospherics/clouds.glsl", "/lib/lighting/shadows.glsl")) {
            List<String> original = nodes.get(AbsolutePackPath.fromAbsolutePath(target)).getLines();
            List<String> patched = BslPatch.patch(target, original);
            check(pack.getFileName() + " patches " + target, patched != original);
            check("source patch is idempotent", BslPatch.patch(target, patched) == patched);
            check("source retains auto uniform and adds fixed-height correction", patched.stream().anyMatch(l -> l.contains("= cloudHeight;"))
                    && patched.stream().anyMatch(l -> l.contains("= shccResolveCloudHeight(float(CLOUD_HEIGHT));")));
        }
        // Exercise the real installed Iris include processor. Override exactly the Mixin RETURN hook.
        Constructor<IncludeGraph> constructor = IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class, ImmutableMap.class);
        constructor.setAccessible(true);
        IncludeGraph graph = constructor.newInstance(ImmutableMap.copyOf(nodes), ImmutableMap.of());
        IncludeProcessor processor = new IncludeProcessor(graph) {
            @Override public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
                ImmutableList<String> original = super.getIncludedFile(path);
                List<String> patched = BslPatch.patch(path.getPathString(), original);
                return patched == original ? original : ImmutableList.copyOf(patched);
            }
        };
        int programs = 0, patchedPrograms = 0;
        for (AbsolutePackPath path : nodes.keySet()) {
            if (!path.getPathString().endsWith(".fsh") && !path.getPathString().endsWith(".vsh")) continue;
            ImmutableList<String> source = processor.getIncludedFile(path);
            checkIncludeSource(path, source);
            programs++;
            if (source.stream().anyMatch(l -> l.contains("uniform vec3 shccCloudAdjustment"))) patchedPrograms++;
        }
        check(pack.getFileName() + " expands " + programs + " real shader programs", programs > 100);
        check(pack.getFileName() + " helper reaches cloud and shadow programs", patchedPrograms > 20);
        String deferred = String.join("\n", processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath("/world0/deferred1.fsh")));
        check(pack.getFileName() + " volumetric pass receives height helper", deferred.contains("shccResolveCloudHeight(float(CLOUD_HEIGHT))"));
        String terrain = String.join("\n", processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath("/world0/gbuffers_terrain.fsh")));
        check(pack.getFileName() + " terrain shadow pass receives height helper", terrain.contains("cloudLowerY = shccResolveCloudHeight"));
        List<StringPair> environment = List.of(new StringPair("IS_IRIS", "1"),
                new StringPair("MC_VERSION", "12101"), new StringPair("MC_GL_VERSION", "460"),
                new StringPair("MC_GLSL_VERSION", "460"));
        for (String program : List.of("deferred1", "gbuffers_terrain", "gbuffers_water")) {
            String expanded = String.join("\n", processor.getIncludedFile(
                    AbsolutePackPath.fromAbsolutePath("/world0/" + program + ".fsh")));
            for (int height : new int[]{-1, 320}) {
                String options = expanded.replaceAll("(?m)^(\\s*#define CLOUD_HEIGHT) -1", "$1 " + height)
                        .replace("//#define SHADOW_CLOUD", "#define SHADOW_CLOUD");
                String preprocessed = JcppProcessor.glslPreprocessSource(options + "\n", environment);
                check(pack.getFileName() + " " + program + " height=" + height + " helper defined once after Iris preprocessing",
                        preprocessed.split("uniform vec3 shccCloudAdjustment;", -1).length - 1 == 1);
                check("correct automatic/fixed height branch survives preprocessing",
                        height == -1 ? preprocessed.contains("= cloudHeight;")
                                : preprocessed.contains("shccResolveCloudHeight(float(320))"));
            }
        }
    }

    private static void checkIncludeSource(AbsolutePackPath path, List<String> lines) {
        if (lines.stream().anyMatch(l -> l.trim().startsWith("#include "))) throw new AssertionError("Unexpanded include " + path);
        long versions = lines.stream().filter(l -> l.trim().startsWith("#version")).count();
        if (versions != 1) throw new AssertionError("Wrong version directive count " + path);
    }
}

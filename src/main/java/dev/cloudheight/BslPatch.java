// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight;

import java.util.ArrayList;
import java.util.List;

public final class BslPatch {
    private static final String GUARD = "SHCC_CLOUD_HEIGHT_HELPER";
    private static final List<String> HELPER = List.of(
            "#ifndef " + GUARD,
            "#define " + GUARD,
            "uniform vec3 shccCloudAdjustment;",
            "float shccResolveCloudHeight(float baseHeight) {",
            "    if (shccCloudAdjustment.z > 0.5) return shccCloudAdjustment.x;",
            "    return max(baseHeight, shccCloudAdjustment.y) + shccCloudAdjustment.x;",
            "}",
            "#endif");

    private BslPatch() {}

    public static List<String> patch(String path, List<String> lines) {
        if (lines == null) return null;
        String declaration;
        String signature;
        if (path.equals("/lib/atmospherics/clouds.glsl")) {
            declaration = "float lowerY = float(CLOUD_HEIGHT);";
            signature = "vec4 DrawCloudVolumetric(";
        } else if (path.equals("/lib/lighting/shadows.glsl")) {
            declaration = "float cloudLowerY = float(CLOUD_HEIGHT);";
            signature = "float GetCloudShadow(";
        } else return lines;
        // Match BSL's actual source structure, never unrelated shaders with a similar option name.
        if (lines.stream().anyMatch(line -> line.contains(GUARD))
                || lines.stream().noneMatch(line -> line.contains(signature))) return lines;
        long matches = lines.stream().filter(line -> line.trim().equals(declaration)).count();
        if (matches != 1) return lines;
        List<String> patched = new ArrayList<>(lines.size() + HELPER.size());
        patched.addAll(HELPER);
        for (String line : lines) {
            patched.add(line.trim().equals(declaration)
                    ? line.replace("float(CLOUD_HEIGHT)", "shccResolveCloudHeight(float(CLOUD_HEIGHT))") : line);
        }
        return patched;
    }
}

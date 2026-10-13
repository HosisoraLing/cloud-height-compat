// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight;

import org.joml.Vector3f;

public final class HeightPolicy {
    private HeightPolicy() {}

    // x = additive offset or absolute height; y = minimum base; z = absolute override flag.
    public static Vector3f identity(Vector3f out) {
        return out.set(0, -Float.MAX_VALUE, 0);
    }

    public static Vector3f parameters(Vector3f out, boolean enabled, boolean eligible, int top,
                                      boolean automatic, int clearance, float offset, Float absolute) {
        if (!enabled || !eligible) return identity(out);
        if (absolute != null) return out.set(absolute, 0, 1);
        return out.set(offset, automatic && top > 320 ? (float) top + clearance : -Float.MAX_VALUE, 0);
    }

    public static Float absoluteHeight(boolean manualEnabled, int manualHeight, Float dimensionOverride) {
        if (dimensionOverride != null) return dimensionOverride;
        return manualEnabled ? (float) manualHeight : null;
    }

    public static boolean validOverride(String entry) {
        int split = entry.indexOf('=');
        if (split < 1 || !entry.substring(0, split).trim().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) return false;
        try {
            float height = Float.parseFloat(entry.substring(split + 1).trim());
            return Float.isFinite(height) && height >= -32768 && height <= 32768;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

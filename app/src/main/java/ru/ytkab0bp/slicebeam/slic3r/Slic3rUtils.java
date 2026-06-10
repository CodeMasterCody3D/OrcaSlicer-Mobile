package ru.ytkab0bp.slicebeam.slic3r;

import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class Slic3rUtils {
    // Matches a nozzle size embedded in a preset / printer name, e.g. "... 0.4 nozzle" -> "0.4".
    private static final Pattern NOZZLE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*nozzle");

    /** Extract the nozzle size token from a preset/printer name, or null if it has none. */
    public static String extractNozzle(String name) {
        if (TextUtils.isEmpty(name)) return null;
        Matcher m = NOZZLE_PATTERN.matcher(name);
        return m.find() ? m.group(1) : null;
    }

    /** Remove a trailing " <size> nozzle" suffix from a printer name, leaving its model portion. */
    public static String stripNozzleSuffix(String name) {
        if (TextUtils.isEmpty(name)) return name;
        Matcher m = NOZZLE_PATTERN.matcher(name);
        if (m.find() && m.end() >= name.length() - 1) {
            return name.substring(0, m.start()).trim();
        }
        return name;
    }

    /** The first nozzle diameter from a (possibly multi-extruder) nozzle_diameter value like "0.4" or "0.4,0.4". */
    public static String firstNozzleDiameter(String nozzleDiameter) {
        if (TextUtils.isEmpty(nozzleDiameter)) return null;
        return nozzleDiameter.split("[;,]")[0].trim();
    }

    /**
     * Whether a process preset's layer height is printable on the given nozzle. OrcaSlicer hard-fails
     * slicing when layer_height &gt; nozzle_diameter ("layer height cannot exceed nozzle diameter"), and
     * the bundled processes are named by layer height (e.g. "0.56mm SuperDraft") rather than nozzle, so
     * this is the reliable way to keep oversized-layer processes out of a smaller nozzle's list.
     */
    public static boolean layerHeightFitsNozzle(String layerHeight, String nozzleDiameter) {
        if (TextUtils.isEmpty(layerHeight) || TextUtils.isEmpty(nozzleDiameter)) return true;
        try {
            return Float.parseFloat(layerHeight) <= Float.parseFloat(nozzleDiameter) + 1e-4f;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Whether a process preset's extrusion line width belongs to the given nozzle. The bundled
     * processes carry no printer/nozzle linkage, but their absolute line_width tracks the nozzle
     * closely (0.4 nozzle -> ~0.4-0.5, 0.6 -> ~0.55-0.62, 0.8 -> ~0.8). Keeping line_width within a
     * band around the nozzle removes other-nozzle processes (e.g. 0.6 processes on a 0.4 printer).
     * A line_width of 0 means "auto/derived", which we can't judge, so it passes.
     */
    public static boolean lineWidthFitsNozzle(String lineWidth, String nozzleDiameter) {
        if (TextUtils.isEmpty(lineWidth) || TextUtils.isEmpty(nozzleDiameter)) return true;
        try {
            float lw = Float.parseFloat(lineWidth);
            float nd = Float.parseFloat(nozzleDiameter);
            if (lw <= 0 || nd <= 0) return true;
            return lw >= nd * 0.65f - 1e-4f && lw <= nd * 1.3f + 1e-4f;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static boolean nozzleEquals(String a, String b) {
        if (a == null || b == null) return false;
        try {
            return Math.abs(Float.parseFloat(a) - Float.parseFloat(b)) < 1e-4f;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    /**
     * Whether a preset is compatible with the active printer, OrcaSlicer-style but resilient to the
     * way bundled/imported profiles encode it:
     *   - compatible_printers list non-empty -> compatible if it names the active printer exactly,
     *     OR an entry belongs to the same printer model (so sibling presets stay visible);
     *   - otherwise the boolean condition is evaluated (empty == compatible).
     * In all cases, if the preset name and the printer both declare a nozzle size, they must match,
     * so e.g. a "0.6 nozzle" process never shows on a 0.4 nozzle printer.
     */
    public static boolean isPrinterCompatible(String presetName, String compatibleList, String condition,
                                              String activePrinterName, String activeModel, String activeNozzle,
                                              ConfigChecker checker) {
        boolean printerOk;
        List<String> list = parseStringList(compatibleList);
        if (list.isEmpty()) {
            printerOk = checker == null || checker.checkCompatibility(condition);
        } else {
            printerOk = false;
            for (String entry : list) {
                if (entry.equals(activePrinterName)
                        || (!TextUtils.isEmpty(activeModel) && stripNozzleSuffix(entry).equals(activeModel))) {
                    printerOk = true;
                    break;
                }
            }
        }
        if (!printerOk) return false;

        if (!TextUtils.isEmpty(activeNozzle)) {
            String presetNozzle = extractNozzle(presetName);
            if (presetNozzle != null && !nozzleEquals(presetNozzle, activeNozzle)) return false;
        }
        return true;
    }
    /**
     * Parse a serialized ConfigOptionStrings value (semicolon-separated, individual values may be
     * double-quoted with backslash escapes) into the list of strings it represents.
     */
    public static List<String> parseStringList(String serialized) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(serialized)) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false, quoted = false, hasToken = false;
        for (int i = 0, n = serialized.length(); i < n; i++) {
            char c = serialized.charAt(i);
            if (inQuotes) {
                if (c == '\\' && i + 1 < n) { cur.append(serialized.charAt(++i)); }
                else if (c == '"') { inQuotes = false; }
                else cur.append(c);
            } else if (c == '"') {
                inQuotes = true; quoted = true; hasToken = true;
            } else if (c == ';') {
                out.add(quoted ? cur.toString() : cur.toString().trim());
                cur.setLength(0); quoted = false; hasToken = false;
            } else {
                cur.append(c); hasToken = true;
            }
        }
        if (hasToken || cur.length() > 0) out.add(quoted ? cur.toString() : cur.toString().trim());
        return out;
    }

    /**
     * OrcaSlicer/PrusaSlicer preset compatibility. When the explicit compatible list (e.g.
     * compatible_printers / compatible_prints) is non-empty, the preset is compatible only if that
     * list contains the active preset's name. Otherwise the boolean condition is evaluated against
     * the active preset (an empty condition means "compatible with everything").
     */
    public static boolean isCompatible(String compatibleList, String condition, String activeName, ConfigChecker checker) {
        if (!TextUtils.isEmpty(compatibleList)) {
            for (String name : parseStringList(compatibleList)) {
                if (name.equals(activeName)) return true;
            }
            return false;
        }
        return checker == null || checker.checkCompatibility(condition);
    }

    public static void calcViewNormalMatrix(double[] viewMatrix, double[] worldMatrix, double[] normalMatrix) {
        assertTrue(viewMatrix.length == 16);
        assertTrue(worldMatrix.length == 16);
        assertTrue(normalMatrix.length == 12);

        Native.utils_calc_view_normal_matrix(viewMatrix, worldMatrix, normalMatrix);
    }

    public static Vec3d unproject(double[] viewMatrix, double[] projectionMatrix, int screenWidth, int screenHeight, double x, double y) {
        assertTrue(viewMatrix.length == 16);
        assertTrue(projectionMatrix.length == 16);

        double[] v = Native.utils_unproject(viewMatrix, projectionMatrix, screenWidth, screenHeight, x, y);
        return new Vec3d(v[0], v[1], v[2]);
    }

    public final static class ConfigChecker {
        private final long pointer;

        public ConfigChecker(String config) {
            pointer = Native.utils_config_create(config);
        }

        public boolean checkCompatibility(String condition) {
            if (TextUtils.isEmpty(condition)) return true;
            return Native.utils_config_check_compatibility(pointer, condition);
        }

        public String eval(String condition) throws Slic3rRuntimeError {
            return Native.utils_config_eval(pointer, condition);
        }

        public void release() {
            Native.utils_config_release(pointer);
        }
    }
}

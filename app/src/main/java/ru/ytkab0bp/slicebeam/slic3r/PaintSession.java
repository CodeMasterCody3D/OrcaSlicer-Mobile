package ru.ytkab0bp.slicebeam.slic3r;

/**
 * A multi-color painting session bound to one object's first volume. All calls must happen on the
 * GL thread (they touch the native model/selector). See Native.paint_* for the engine bridge.
 */
public class PaintSession {
    private long ptr;

    public PaintSession(Model model, int objIdx, int mode) {
        ptr = Native.paint_begin(model.pointer, objIdx, mode);
    }

    public boolean isValid() {
        return ptr != 0;
    }

    /** Ray-cast a camera ray (mesh-local coords) -> {facetIdx, x, y, z} or empty if no hit. */
    public double[] raycast(double[] origin, double[] dir) {
        return Native.paint_raycast(ptr, origin, dir);
    }

    public void brush(double[] hit, int facetStart, double radius, int filamentIdx, double[] cameraPos, boolean sphere) {
        Native.paint_brush(ptr, hit, facetStart, radius, filamentIdx, cameraPos, sphere);
    }

    public void bucket(double[] hit, int facetStart, int filamentIdx, boolean propagate, double angle) {
        Native.paint_bucket(ptr, hit, facetStart, filamentIdx, propagate, angle);
    }

    public void height(double zMin, double zMax, int filamentIdx) {
        Native.paint_height(ptr, zMin, zMax, filamentIdx);
    }

    public void clear() {
        Native.paint_clear(ptr);
    }

    /** Write the painting back into the model so it slices to multi-color gcode. */
    public void commit() {
        Native.paint_commit(ptr);
    }

    /** Fill a GLModel with the facets of the given filament; returns triangle count. */
    public int buildOverlay(GLModel gl, int filamentIdx) {
        return gl.initFromPaint(ptr, filamentIdx);
    }

    public void end() {
        if (ptr != 0) {
            Native.paint_end(ptr);
            ptr = 0;
        }
    }
}

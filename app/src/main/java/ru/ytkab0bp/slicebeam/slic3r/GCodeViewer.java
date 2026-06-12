package ru.ytkab0bp.slicebeam.slic3r;

import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;

import android.graphics.Color;

import androidx.annotation.IntDef;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;

public class GCodeViewer {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            EXTRUSION_ROLE_NONE,
            EXTRUSION_ROLE_PERIMETER,
            EXTRUSION_ROLE_EXTERNAL_PERIMETER,
            EXTRUSION_ROLE_OVERHANG_PERIMETER,
            EXTRUSION_ROLE_INTERNAL_INFILL,
            EXTRUSION_ROLE_SOLID_INFILL,
            EXTRUSION_ROLE_TOP_SOLID_INFILL,
            EXTRUSION_ROLE_IRONING,
            EXTRUSION_ROLE_BRIDGE_INFILL,
            EXTRUSION_ROLE_GAP_FILL,
            EXTRUSION_ROLE_SKIRT,
            EXTRUSION_ROLE_SUPPORT_MATERIAL,
            EXTRUSION_ROLE_SUPPORT_MATERIAL_INTERFACE,
            EXTRUSION_ROLE_WIPE_TOWER,
            EXTRUSION_ROLE_CUSTOM
    })
    public @interface ExtrusionRole{}

    public final static int EXTRUSION_ROLE_NONE = 0,
            EXTRUSION_ROLE_PERIMETER = 1,
            EXTRUSION_ROLE_EXTERNAL_PERIMETER = 2,
            EXTRUSION_ROLE_OVERHANG_PERIMETER = 3,
            EXTRUSION_ROLE_INTERNAL_INFILL = 4,
            EXTRUSION_ROLE_SOLID_INFILL = 5,
            EXTRUSION_ROLE_TOP_SOLID_INFILL = 6,
            EXTRUSION_ROLE_IRONING = 7,
            EXTRUSION_ROLE_BRIDGE_INFILL = 8,
            EXTRUSION_ROLE_GAP_FILL = 9,
            EXTRUSION_ROLE_SKIRT = 10,
            EXTRUSION_ROLE_SUPPORT_MATERIAL = 11,
            EXTRUSION_ROLE_SUPPORT_MATERIAL_INTERFACE = 12,
            EXTRUSION_ROLE_WIPE_TOWER = 13,
            EXTRUSION_ROLE_CUSTOM = 14,

            EXTRUSION_ROLES_COUNT = 15;

    private ThreadLocal<float[]> viewMatrixBuffer = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[16];
        }
    };
    private ThreadLocal<float[]> projectionMatrixBuffer = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[16];
        }
    };

    private long pointer;

    public GCodeViewer() {
        pointer = Native.vgcode_create();
    }

    public boolean isInitialized() {
        return Native.vgcode_is_initialized(pointer);
    }

    public void initGL() {
        Native.vgcode_init(pointer);
    }

    public void load(GCodeProcessorResult result) {
        Native.vgcode_load(pointer, result.pointer);
    }

    public void setLayersViewRange(long min, long max) {
        Native.vgcode_set_layers_view_range(pointer, min, max);
    }

    public void setInfillVisibilityDepth(int depth) {
        Native.vgcode_set_infill_visibility_depth(pointer, depth);
    }

    // gcode preview color modes (see libvgcode EViewType)
    public static final int VIEW_TYPE_FEATURE = 0;
    public static final int VIEW_TYPE_TOOL = 11; // color by filament/tool

    // Option types (see libvgcode EOptionType)
    public static final int OPTION_TYPE_SEAMS = 4;

    public boolean isOptionVisible(int optionType) {
        return Native.vgcode_is_option_visible(pointer, optionType);
    }

    public void toggleOptionVisibility(int optionType) {
        Native.vgcode_toggle_option_visibility(pointer, optionType);
    }

    public void setViewType(int type) {
        Native.vgcode_set_view_type(pointer, type);
    }

    public int getViewType() {
        return Native.vgcode_get_view_type(pointer);
    }

    /** Per-filament colors used by the Tool (filament) view; each int is 0xRRGGBB. */
    public void setToolColors(int[] colors) {
        Native.vgcode_set_tool_colors(pointer, colors);
    }

    public void setFastMode(boolean fastMode) {
        Native.vgcode_set_fast_mode(pointer, fastMode);
    }

    public void setSelectedObjectId(int id) {
        Native.vgcode_set_selected_object_id(pointer, id);
    }

    public Pair<Long, Long> getLayersViewRange() {
        long[] data = Native.vgcode_get_layers_view_range(pointer);
        return new Pair<>(data[0], data[1] < 0 ? getLayersCount() : data[1]);
    }

    public long getLayersCount() {
        return Native.vgcode_get_layers_count(pointer);
    }

    public float getEstimatedTime() {
        return Native.vgcode_get_estimated_time(pointer);
    }

    public float getEstimatedTime(@ExtrusionRole int extrusionRole) {
        return Native.vgcode_get_estimated_time_role(pointer, extrusionRole);
    }

    public boolean isExtrusionRoleVisible(@ExtrusionRole int extrusionRole) {
        return Native.vgcode_is_extrusion_role_visible(pointer, extrusionRole);
    }

    public void toggleExtrusionRoleVisible(@ExtrusionRole int extrusionRole) {
        Native.vgcode_toggle_extrusion_role_visibility(pointer, extrusionRole);
    }

    public void render(double[] viewMatrix, double[] projectionMatrix) {
        assertTrue(viewMatrix.length == 16);
        assertTrue(projectionMatrix.length == 16);

        float[] vmFloats = viewMatrixBuffer.get();
        for (int i = 0; i < viewMatrix.length; i++) {
            vmFloats[i] = (float) viewMatrix[i];
        }
        float[] pmFloats = projectionMatrixBuffer.get();
        for (int i = 0; i < projectionMatrix.length; i++) {
            pmFloats[i] = (float) projectionMatrix[i];
        }
        Native.vgcode_render(pointer, vmFloats, pmFloats);
    }

    public void setThemeColors() {
        setColors(
                ThemesRepo.getColor(R.attr.gcodeViewerSkirt),
                ThemesRepo.getColor(R.attr.gcodeViewerExternalPerimeter),
                ThemesRepo.getColor(R.attr.gcodeViewerSupportMaterial),
                ThemesRepo.getColor(R.attr.gcodeViewerSupportMaterialInterface),
                ThemesRepo.getColor(R.attr.gcodeViewerInternalInfill),
                ThemesRepo.getColor(R.attr.gcodeViewerSolidInfill),
                ThemesRepo.getColor(R.attr.gcodeViewerWipeTower)
        );
    }

    public void setColors(int skirt, int externalPerimeter, int supportMaterial, int supportMaterialInterface, int internalInfill, int solidInfill, int wipeTower) {
        Native.vgcode_set_colors(pointer, new int[] {
                Color.red(skirt), Color.green(skirt), Color.blue(skirt),
                Color.red(externalPerimeter), Color.green(externalPerimeter), Color.blue(externalPerimeter),
                Color.red(supportMaterial), Color.green(supportMaterial), Color.blue(supportMaterial),
                Color.red(supportMaterialInterface), Color.green(supportMaterialInterface), Color.blue(supportMaterialInterface),
                Color.red(internalInfill), Color.green(internalInfill), Color.blue(internalInfill),
                Color.red(solidInfill), Color.green(solidInfill), Color.blue(solidInfill),
                Color.red(wipeTower), Color.green(wipeTower), Color.blue(wipeTower)
        });
    }

    public void reset() {
        Native.vgcode_reset(pointer);
    }

    public void release() {
        if (pointer != 0) {
            Native.vgcode_release(pointer);
            pointer = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        release();
    }
}

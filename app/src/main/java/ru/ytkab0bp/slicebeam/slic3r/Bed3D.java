package ru.ytkab0bp.slicebeam.slic3r;

import static android.opengl.GLES30.*;
import static ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue;


import java.io.File;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.render.CoordAxes;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.DoubleMatrix;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class Bed3D {
    private final static float GROUND_Z = -0.02f;

    private long pointer;

    private GLModel triangles;
    private GLModel gridlines;
    private GLModel contourlines;

    private CoordAxes axes = new CoordAxes();
    private double[] boundingVolume;
    private Vec3d min, max;

    private boolean likelyDelta;

    private double[] modelMatrix = new double[16];
    private double[] outModelMatrix = new double[16];

    public Bed3D() {
        long[] data = new long[3];
        pointer = Native.bed_create(data);
        triangles = new GLModel(data[0]);
        gridlines = new GLModel(data[1]);
        contourlines = new GLModel(data[2]);
    }

    public void configure(File f) {
        configure(f.getAbsolutePath());
    }

    private void configure(String path) {
        Native.bed_configure(pointer, path);
        Native.bed_init_triangles_mesh(pointer, triangles.pointer);
        boundingVolume = Native.bed_get_bounding_volume(pointer);

        min = max = null;

        axes.origin.set(0, 0, GROUND_Z);
        axes.setStemLength(0.1f * Native.bed_get_bounding_volume_max_size(pointer));

        if (isValid()) {
            Vec3d center = getVolumeMin().center(getVolumeMax());
            likelyDelta = (center.x == 0 || center.y == 0) && getVolumeMin().x < 0 && getVolumeMin().y < 0;
        } else {
            likelyDelta = false;
        }
    }

    public boolean arrange(Model model) {
        return Native.bed_arrange(pointer, model.pointer);
    }

    public Vec3d getVolumeMin() {
        if (min == null && boundingVolume != null) min = new Vec3d(boundingVolume[0], boundingVolume[1], boundingVolume[2]);
        return min;
    }

    public Vec3d getVolumeMax() {
        if (max == null && boundingVolume != null) max = new Vec3d(boundingVolume[3], boundingVolume[4], boundingVolume[5]);
        return max;
    }

    public boolean isValid() {
        return boundingVolume != null;
    }

    public GLModel.MeshRaycaster getRaycaster() {
        return triangles.getRaycaster();
    }

    public void render(GLShadersManager shadersManager, boolean bottom, double[] viewModelMatrix, double[] projectionMatrix, float invZoom) {
        assertTrue(viewModelMatrix.length == 16);
        assertTrue(projectionMatrix.length == 16);

        DoubleMatrix.setIdentityM(modelMatrix, 0);
        if (!likelyDelta) {
            DoubleMatrix.translateM(modelMatrix, 0, -getVolumeMin().x * 2, -getVolumeMin().y * 2, -getVolumeMin().z);
        }
        DoubleMatrix.multiplyMM(outModelMatrix, 0, viewModelMatrix, 0, modelMatrix, 0);
        renderDefaultBed(shadersManager, bottom, outModelMatrix, projectionMatrix);
        axes.render(shadersManager, viewModelMatrix, projectionMatrix, 0.25f, invZoom);
    }

    private void renderDefaultBed(GLShadersManager shadersManager, boolean bottom, double[] viewModelMatrix, double[] projectionMatrix) {
        GLShaderProgram shader = shadersManager.get(GLShadersManager.SHADER_FLAT);
        shader.startUsing();

        shader.setUniformMatrix4fv("view_model_matrix", viewModelMatrix);
        shader.setUniformMatrix4fv("projection_matrix", projectionMatrix);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (!bottom) {
            glDepthMask(false);
            triangles.setColor(ThemesRepo.getColor(R.attr.defaultBedColor));
            triangles.render();
            glDepthMask(true);
        }

        glLineWidth(ViewUtils.dp(1));
        gridlines.setColor(ThemesRepo.getColor(R.attr.bedGridlinesColor));
        gridlines.render();

        contourlines.setColor(ThemesRepo.getColor(R.attr.bedContourlinesColor));
        contourlines.render();

        glDisable(GL_BLEND);

        shader.stopUsing();
    }

    private void renderTexturedBed(GLShadersManager shadersManager, boolean bottom, float[] viewModelMatrix, float[] projectionMatrix) {
        GLShaderProgram shader = shadersManager.get(GLShadersManager.SHADER_PRINTBED);
        shader.startUsing();

        shader.setUniform3f("view_model_matrix", viewModelMatrix);
        shader.setUniform3f("projection_matrix", projectionMatrix);
        shader.setUniform("transparent_background", bottom);
        shader.setUniform("svg_source", false);

        glEnable(GL_DEPTH_TEST);
        if (bottom) {
            glDepthMask(false);
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (bottom) {
            glFrontFace(GL_CW);
        }

        // TODO: glBindTexture(GL_TEXTURE_2D, tex_id);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (bottom) {
            glFrontFace(GL_CCW);
        }
        glDisable(GL_BLEND);
        if (bottom) {
            glDepthMask(true);
        }

        shader.stopUsing();
    }

    public void release() {
        if (pointer != 0) {
            Native.bed_release(pointer);
            axes.release();
            pointer = 0;

//        triangles.release();
//        gridlines.release();
//        contourlines.release();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        release();
    }
}

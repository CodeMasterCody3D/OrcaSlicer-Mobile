package ru.ytkab0bp.slicebeam.render;

import androidx.core.math.MathUtils;

import ru.ytkab0bp.slicebeam.utils.DoubleMatrix;
import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class Camera {
    private double[] viewMatrix = new double[16];
    private boolean viewMatrixDirty = true;

    public Vec3d position = new Vec3d(0, 0, 0);
    public Vec3d origin = new Vec3d(0, 0, 0);
    public Vec3d up = new Vec3d(0, 0, 1);

    private double[] tempMatrix = new double[16];
    private float zoom = 1f;

    // Pre-allocated scratch buffers — avoids Vec3d allocation on every gesture event
    private final Vec3d scratchDir = new Vec3d();
    private final Vec3d scratchUpMod = new Vec3d();
    private final Vec3d scratchRight = new Vec3d();
    private final Vec3d scratchScreenY = new Vec3d();
    private final double[] rotBuf = new double[4];

    public void invalidate() {
        viewMatrixDirty = true;
    }

    public Vec3d getDirToBed() {
        // origin*(1,1,0) + (-position) = (origin.x-position.x, origin.y-position.y, -position.z)
        scratchDir.x = origin.x - position.x;
        scratchDir.y = origin.y - position.y;
        scratchDir.z = -position.z;
        return scratchDir.normalize();
    }

    public Vec3d getDirForward() {
        scratchDir.x = origin.x - position.x;
        scratchDir.y = origin.y - position.y;
        scratchDir.z = origin.z - position.z;
        return scratchDir.normalize();
    }

    public double[] getViewModelMatrix() {
        if (viewMatrixDirty) {
            DoubleMatrix.setLookAtM(viewMatrix, 0,
                    position.x, position.y, position.z,
                    origin.x, origin.y, origin.z,
                    up.x, up.y, up.z);
            viewMatrixDirty = false;
        }
        return viewMatrix;
    }

    public float getZoom() {
        return zoom;
    }

    public void zoom(float zoom) {
        this.zoom = MathUtils.clamp(this.zoom + zoom / 25f, 1f, 10f);
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public Vec3d calcScreenMovement(float x, float y) {
        x /= zoom;
        y /= zoom;
        computeScreenBasis();
        return new Vec3d(
                scratchRight.x * x + scratchScreenY.x * y,
                scratchRight.y * x + scratchScreenY.y * y,
                scratchRight.z * x + scratchScreenY.z * y
        );
    }

    // Computes scratchRight and scratchScreenY from current camera direction — no allocation.
    private void computeScreenBasis() {
        scratchDir.x = origin.x - position.x;
        scratchDir.y = origin.y - position.y;
        scratchDir.z = origin.z - position.z;
        scratchDir.normalize();

        double yaw = Math.atan2(scratchDir.x, scratchDir.y);
        double pitch = Math.asin(-scratchDir.z);
        double sinPitch = Math.sin(pitch);

        scratchUpMod.x = sinPitch * Math.sin(yaw);
        scratchUpMod.y = sinPitch * Math.cos(yaw);
        scratchUpMod.z = Math.cos(pitch);

        // right = dir × upMod
        scratchRight.x = scratchDir.y * scratchUpMod.z - scratchDir.z * scratchUpMod.y;
        scratchRight.y = scratchDir.z * scratchUpMod.x - scratchDir.x * scratchUpMod.z;
        scratchRight.z = scratchDir.x * scratchUpMod.y - scratchDir.y * scratchUpMod.x;

        // screenY = dir × right
        scratchScreenY.x = scratchDir.y * scratchRight.z - scratchDir.z * scratchRight.y;
        scratchScreenY.y = scratchDir.z * scratchRight.x - scratchDir.x * scratchRight.z;
        scratchScreenY.z = scratchDir.x * scratchRight.y - scratchDir.y * scratchRight.x;
    }

    public void move(float x, float y) {
        x /= zoom;
        y /= zoom;
        computeScreenBasis();

        double mx = scratchRight.x * x + scratchScreenY.x * y;
        double my = scratchRight.y * x + scratchScreenY.y * y;
        double mz = scratchRight.z * x + scratchScreenY.z * y;

        position.x += mx; position.y += my; position.z += mz;
        origin.x += mx; origin.y += my; origin.z += mz;
        viewMatrixDirty = true;
    }

    public void rotateAround(double rx, double ry) {
        rotBuf[0] = position.x - origin.x;
        rotBuf[1] = position.y - origin.y;
        rotBuf[2] = position.z - origin.z;
        rotBuf[3] = 1.0;

        DoubleMatrix.setIdentityM(tempMatrix, 0);

        scratchDir.x = origin.x - position.x;
        scratchDir.y = origin.y - position.y;
        scratchDir.z = origin.z - position.z;
        scratchDir.normalize();

        double yaw = Math.atan2(scratchDir.x, scratchDir.y);
        double pitch = Math.toDegrees(Math.asin(-scratchDir.z));

        double mry = -ry;
        if (pitch + mry > 90) {
            mry = 0;
        } else if (pitch + mry < -90) {
            mry = 0;
        }

        DoubleMatrix.rotateM(tempMatrix, 0, -mry * Math.cos(yaw), 1, 0, 0);
        DoubleMatrix.rotateM(tempMatrix, 0, mry * Math.sin(yaw), 0, 1, 0);
        DoubleMatrix.rotateM(tempMatrix, 0, rx, 0, 0, 1);

        DoubleMatrix.multiplyMV(rotBuf, 0, tempMatrix, 0, rotBuf, 0);
        position.x = rotBuf[0] / rotBuf[3] + origin.x;
        position.y = rotBuf[1] / rotBuf[3] + origin.y;
        position.z = rotBuf[2] / rotBuf[3] + origin.z;
        viewMatrixDirty = true;
    }
}

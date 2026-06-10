package android.opengl;
import android.content.Context;

public class GLSurfaceView {
    public static final int RENDERMODE_WHEN_DIRTY = 0;
    public static final int RENDERMODE_CONTINUOUSLY = 1;

    public GLSurfaceView(Context context) {}
    public void setEGLContextClientVersion(int version) {}
    public void setRenderer(Object renderer) {}
    public void setRenderMode(int renderMode) {}
    public void requestRender() {}
    public void queueEvent(Runnable r) {
        if (r != null) r.run();
    }
    public void setWillNotDraw(boolean willNotDraw) {}
    public float getTranslationX() { return 0.0f; }
    public float getTranslationY() { return 0.0f; }
    public int getWidth() { return 1080; }
    public int getHeight() { return 1920; }
    public void performHapticFeedback(int feedbackConstant) {}
    public void postDelayed(Runnable action, long delayMillis) {
        if (action != null) action.run();
    }
}

package android.content.res;
import android.util.DisplayMetrics;

public class Resources {
    private DisplayMetrics metrics = new DisplayMetrics();
    public DisplayMetrics getDisplayMetrics() {
        return metrics;
    }
    public String getString(int id) {
        return "MockString";
    }
}

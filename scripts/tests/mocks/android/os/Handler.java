package android.os;

public class Handler {
    public Handler() {}
    public Handler(Looper looper) {}
    public boolean post(Runnable r) {
        if (r != null) {
            r.run();
        }
        return true;
    }
    public boolean postDelayed(Runnable r, long delayMillis) {
        if (r != null) {
            r.run();
        }
        return true;
    }
    public void removeCallbacks(Runnable r) {}
}

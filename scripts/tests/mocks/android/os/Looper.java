package android.os;

public class Looper {
    private static final Looper mainLooper = new Looper();
    public static Looper getMainLooper() {
        return mainLooper;
    }
    public static void prepare() {}
    public static void loop() {}
}

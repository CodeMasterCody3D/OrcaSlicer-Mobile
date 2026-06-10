package android.content;

public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 268435456;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 32768;

    public Intent() {}
    public Intent(Context packageContext, Class<?> cls) {}
    public Intent(String action) {}
    public Intent addFlags(int flags) { return this; }
}

package android.content;
import android.content.res.Resources;
import java.io.File;

public abstract class Context {
    public abstract Resources getResources();
    public abstract File getCacheDir();
    public abstract File getFilesDir();
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
}

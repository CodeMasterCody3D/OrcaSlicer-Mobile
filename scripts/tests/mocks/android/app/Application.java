package android.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Application extends Context {
    private Resources resources = new Resources();
    private static Map<String, MockSharedPreferences> prefsMap = new HashMap<>();

    public void onCreate() {}

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public File getCacheDir() {
        File f = new File("build/tmp/test_cache");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    @Override
    public File getFilesDir() {
        File f = new File("build/tmp/test_files");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        MockSharedPreferences prefs = prefsMap.get(name);
        if (prefs == null) {
            prefs = new MockSharedPreferences();
            prefsMap.put(name, prefs);
        }
        return prefs;
    }

    private static class MockSharedPreferences implements SharedPreferences {
        private Map<String, Object> map = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return map;
        }

        @Override
        public String getString(String key, String defValue) {
            return map.containsKey(key) ? (String) map.get(key) : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            return map.containsKey(key) ? (Integer) map.get(key) : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            return map.containsKey(key) ? (Long) map.get(key) : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            return map.containsKey(key) ? (Float) map.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return map.containsKey(key) ? (Boolean) map.get(key) : defValue;
        }

        @Override
        public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MockEditor(this);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }

    private static class MockEditor implements SharedPreferences.Editor {
        private MockSharedPreferences parent;
        private Map<String, Object> temp = new HashMap<>();

        MockEditor(MockSharedPreferences parent) {
            this.parent = parent;
            this.temp.putAll(parent.map);
        }

        @Override
        public SharedPreferences.Editor putString(String key, String value) {
            temp.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            temp.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            temp.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            temp.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            temp.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            temp.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            temp.clear();
            return this;
        }

        @Override
        public boolean commit() {
            parent.map.clear();
            parent.map.putAll(temp);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}

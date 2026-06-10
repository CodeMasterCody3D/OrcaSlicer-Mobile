package ru.ytkab0bp.slicebeam.slic3r;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.ytkab0bp.slicebeam.SliceBeam;

public class Slic3rLocalization {
    private static Map<String, Slic3rLocalization> localesMap = new HashMap<String, Slic3rLocalization>() {
        @Override
        public Slic3rLocalization get(@Nullable Object key) {
            Slic3rLocalization locale = super.get(key);
            if (locale == null) {
                try {
                    put((String) key, locale = new Slic3rLocalization((String) key));
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        put((String) key, locale = new Slic3rLocalization("en"));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            return locale;
        }
    };

    private Map<String, String> map = new HashMap<>();

    public Slic3rLocalization(String key) throws IOException {
        InputStream in = SliceBeam.INSTANCE.getAssets().open("localization/" + key + ".po");
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        StringBuilder msgId = null;
        StringBuilder msgStr = null;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("#")) continue;

            if (line.startsWith("msgid")) {
                msgId = new StringBuilder(line.substring(7, line.length() - 1));
            } else if (line.startsWith("msgstr")) {
                msgStr = new StringBuilder(line.substring(8, line.length() - 1));
            } else if (line.isEmpty()) {
                if (!TextUtils.isEmpty(msgId) && !TextUtils.isEmpty(msgStr)) {
                    // This hack allows us to maintain vanilla strings in native code while using our app name at the same time
                    map.put(msgId.toString(), replaceStr(msgStr.toString()));
                }

                msgId = null;
                msgStr = null;
            } else if (line.startsWith("\"") && line.endsWith("\"")) {
                if (msgStr != null) {
                    msgStr.append(line.substring(1, line.length() - 1));
                } else if (msgId != null) {
                    msgId.append(line.substring(1, line.length() - 1));
                }
            }
        }
        r.close();
        in.close();
    }

    private static String replaceStr(String val) {
        if (val == null) return "";
        return val.replace("\\n", "\n").replaceAll("\\\\(.)", "$1")
                .replaceAll("(?i)slic3r", "OrcaSlicer Mobile")
                .replaceAll("(?i)prusasl?i[sc]er", "OrcaSlicer Mobile");
    }

    public static String getString(String key) {
        return getInstance().get(key);
    }

    public String get(String key) {
        String val = map.get(key);
        if (TextUtils.isEmpty(val)) {
            map.put(key, val = replaceStr(key));
        }
        return val;
    }

    public static Slic3rLocalization getInstance(String key) {
        return localesMap.get(key);
    }

    public static Slic3rLocalization getInstance() {
        return getInstance(Locale.getDefault().getLanguage());
    }
}

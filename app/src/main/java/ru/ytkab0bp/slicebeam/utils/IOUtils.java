package ru.ytkab0bp.slicebeam.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.config.ConfigObject;

public class IOUtils {
    public static ExecutorService IO_POOL = Executors.newCachedThreadPool();

    public static String getDisplayName(Uri uri) {
        ContentResolver resolver = SliceBeam.INSTANCE.getContentResolver();

        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = resolver.query(uri, projection, null, null, null);
        String fileName = null;
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    fileName = metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }
        return fileName;
    }
    public static String readString(InputStream in) throws IOException {
        return readString(in, false);
    }

    public static String readString(InputStream in, boolean close) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[10240]; int c;
        while ((c = in.read(buffer)) != -1) {
            bos.write(buffer, 0, c);
        }
        if (close) {
            in.close();
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String configJsonToString(Object obj) throws JSONException {
        if (obj instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                if (sb.length() != 0) sb.append(",");
                sb.append(arr.getString(i));
            }
            return sb.toString();
        } else {
            return obj.toString();
        }
    }

    private static String mapOrcaConfigKey(String key) {
        switch (key) {
            // Cases ConfigObject.KEY_MIGRATION can't express:
            // Orca's bundled JSONs use the plural form; the engine key (and the whitelist entry) is singular.
            case "chamber_temperatures": return "chamber_temperature";
        }
        // KEY_MIGRATION (legacy <-> Orca engine names) is the single source of truth for renames;
        // a key the whitelist knows under its legacy name is resolved here, all others pass through.
        return ConfigObject.legacyKey(key);
    }

    private static ConfigObject downloadProfilesRecursively(String vendor, String type, String profile, List<String> supportedKeys) throws IOException, JSONException, MissingProfileException {
        ConfigObject cfg = new ConfigObject();

        HttpURLConnection con = (HttpURLConnection) new URL(String.format("https://raw.githubusercontent.com/SoftFever/OrcaSlicer/main/resources/profiles/%s/%s/%s.json", vendor, type, profile)).openConnection();
        if (con.getResponseCode() == 404) {
            throw new MissingProfileException(profile);
        }
        JSONObject obj = new JSONObject(readString(con.getInputStream()));
        if (!TextUtils.isEmpty(obj.optString("inherits", null))) {
            ConfigObject o = downloadProfilesRecursively(vendor, type, obj.getString("inherits"), supportedKeys);

            for (Map.Entry<String, String> en : o.values.entrySet()) {
                if (supportedKeys.contains(en.getKey())) {
                    if (!en.getKey().equals("thumbnails")) {
                        cfg.values.put(en.getKey(), en.getValue());
                    }
                }
            }
        }

        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();

            if (key.equals("print_settings_id") || key.equals("filament_settings_id") || key.equals("printer_settings_id")) {
                cfg.setTitle(obj.getString(key));
            } else if (!key.equals("inherits")) {
                cfg.put(key, configJsonToString(obj.get(key)));
            }
        }
        return cfg;
    }

    public static ConfigObject configJsonToIni(JSONObject obj, String type, List<String> supportedKeys, List<String> inBundle) throws JSONException, IOException, MissingProfileException {
        ConfigObject cfg = new ConfigObject();
        // profileListType drives isSelected(); without it every imported profile defaults to
        // PROFILE_LIST_PRINT and the printer/filament selectors never show a selection.
        switch (type) {
            case "process":  cfg.profileListType = ConfigObject.PROFILE_LIST_PRINT;    break;
            case "filament": cfg.profileListType = ConfigObject.PROFILE_LIST_FILAMENT; break;
            case "machine":  cfg.profileListType = ConfigObject.PROFILE_LIST_PRINTER;  break;
        }
        if (!TextUtils.isEmpty(obj.optString("inherits", null))) {
            String inherit = obj.getString("inherits");

            if (inBundle.contains(inherit)) {
                // Will do it later then
                cfg.put("inherits", inherit);
            } else if (inherit.indexOf(' ') == -1) {
                throw new MissingProfileException(inherit);
            } else {
                String vendor;
                if (inherit.contains("@BBL")) {
                    vendor = "BBL";
                } else if (type.equals("process")) {
                    int i = inherit.indexOf('@') + 1;
                    int j = inherit.indexOf(' ', i);
                    if (j == -1) j = inherit.length();
                    vendor = inherit.substring(i, j);
                } else {
                    vendor = inherit.substring(0, inherit.indexOf(' '));
                }

                if (vendor.equals("Generic") || inherit.startsWith("Bambu Lab")) vendor = "BBL";

                ConfigObject inherited = null;
                try {
                    inherited = downloadProfilesRecursively(vendor, type, inherit, supportedKeys);
                } catch (MissingProfileException e) {
                    if (!inherit.endsWith("@System")) {
                        throw e;
                    }
                }

                if (inherited != null) {
                    for (Map.Entry<String, String> en : inherited.values.entrySet()) {
                        String key = mapOrcaConfigKey(en.getKey());

                        if (key.equals("pressure_advance")) {
                            StringBuilder sb = new StringBuilder("SET_PRESSURE_ADVANCE ADVANCE=").append(en.getValue());
                            if (cfg.values.containsKey("start_filament_gcode")) {
                                sb.append("\n").append(cfg.get("start_filament_gcode"));
                            }
                            cfg.values.put("start_filament_gcode", sb.toString());
                        }

                        if (supportedKeys.contains(key)) {
                            if (key.equals("start_filament_gcode") || key.equals("end_filament_gcode") ||
                                key.equals("start_gcode") || key.equals("end_gcode")) {

                                String val = en.getValue();
                                if (key.equals("start_filament_gcode")) {
                                    if (cfg.values.containsKey("start_filament_gcode")) {
                                        val = cfg.get("start_filament_gcode") + "\n" + val;
                                    }
                                }

                                val = val.replace("nozzle_temperature_initial_layer", "first_layer_temperature")
                                        .replace("bed_temperature_initial_layer_single", "first_layer_bed_temperature");
                                cfg.values.put(key, val);
                            } else if (!key.equals("thumbnails")) {
                                cfg.values.put(key, en.getValue());
                            }
                        }
                    }
                }
            }
        }

        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();

            if (key.equals("print_settings_id") || key.equals("filament_settings_id") || key.equals("printer_settings_id")) {
                String v = obj.getString(key);
                if (v.length() > 3 && v.charAt(0) == '[' && v.charAt(1) == '"' && v.charAt(v.length() - 2) == '"' && v.charAt(v.length() - 1) == ']') v = v.substring(2, v.length() - 2);
                cfg.setTitle(v);
            } else if (!key.equals("inherits")) {
                String mappedKey = mapOrcaConfigKey(key);
                if (supportedKeys.contains(mappedKey)) {
                    String val = configJsonToString(obj.get(key));
                    if (mappedKey.equals("start_filament_gcode") || mappedKey.equals("end_filament_gcode") ||
                            mappedKey.equals("start_gcode") || mappedKey.equals("end_gcode")) {
                        val = val.replace("nozzle_temperature_initial_layer", "first_layer_temperature")
                                .replace("bed_temperature_initial_layer_single", "first_layer_bed_temperature");
                    }
                    cfg.put(mappedKey, val);
                }
            }
        }
        return cfg;
    }

    public static class MissingProfileException extends Exception {
        public final String profile;

        public MissingProfileException(String profile) {
            this.profile = profile;
        }

        @Override
        public String toString() {
            return "MissingProfileException{" +
                    "profile='" + profile + '\'' +
                    '}';
        }
    }
}

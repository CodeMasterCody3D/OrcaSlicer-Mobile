package ru.ytkab0bp.slicebeam;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ru.ytkab0bp.sapil.APICallback;
import ru.ytkab0bp.slicebeam.cloud.CloudAPI;
import ru.ytkab0bp.slicebeam.cloud.CloudController;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.ChangeLogBottomSheet;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.NeedDismissAIGeneratorMenu;
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent;
import ru.ytkab0bp.slicebeam.fragment.BedFragment;
import ru.ytkab0bp.slicebeam.navigation.Fragment;
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate;
import ru.ytkab0bp.slicebeam.navigation.NavigationDelegate;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.slic3r.Native;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.IOUtils;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout;

public class MainActivity extends AppCompatActivity {
    // Activity result
    public final static int REQUEST_CODE_OPEN_FILE = 1, REQUEST_CODE_EXPORT_GCODE = 2,
                            REQUEST_CODE_IMPORT_PROFILES = 3, REQUEST_CODE_EXPORT_PROFILES = 4,
                            REQUEST_CODE_EXPORT_3MF = 5,
                            REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO = 6, REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO = 7;

    private static MainActivity activeInstance;

    public static List<ConfigObject> EXPORTING_PRINTS;
    public static List<ConfigObject> EXPORTING_FILAMENTS;
    public static List<ConfigObject> EXPORTING_PRINTERS;

    public static boolean IS_GENERATING_AI_MODEL;

    public static File aiTempFile;

    private interface BundleProfileFinder {
        ConfigObject find(String name);
    }

    private static SparseArray<NavigationDelegate> liveDelegate = new SparseArray<>();
    private static int lastId;

    private int id;
    private NavigationDelegate delegate;
    private boolean landscape;
    private UnfoldMenu unfoldMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.getPrefs().contains("crash")) {
            startActivity(new Intent(this, SafeStartActivity.class));
            finish();
            return;
        }
        if (SliceBeam.CONFIG == null) {
            Prefs.setLastCommit();
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        if (activeInstance == null) {
            activeInstance = this;
        } else {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (getIntent() != null) {
                i.setAction(getIntent().getAction());
                i.putExtras(getIntent());
                i.setDataAndType(getIntent().getData(), getIntent().getType());
            }
            startActivity(i);

            finish();
            return;
        }

        id = savedInstanceState == null ? lastId++ : savedInstanceState.getInt("id");

        if (delegate == null) {
            NavigationDelegate saved = liveDelegate.get(id);
            liveDelegate.remove(id);
            if (saved != null && isCompatible(saved)) {
                delegate = saved;
            } else {
                delegate = onCreateDelegate();
            }
        }
        delegate.setContext(this);

        delegate.onCreate();
        View v = delegate.onCreateView(this);
        if (delegate.getContainerView() == null || delegate.getContainerView().getParent() == null) {
            throw new IllegalArgumentException("Delegate hasn't created container view!");
        }
        ViewCompat.setOnApplyWindowInsetsListener(v, (v2, insets) -> {
            Insets systemBars = insets.getSystemWindowInsets();
            v2.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets.consumeSystemWindowInsets();
        });
        setContentView(v);

        if (getIntent() != null && getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            loadFile(getIntent().getData());
            setIntent(null);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        landscape = dm.widthPixels > dm.heightPixels;
        View decorView = getWindow().getDecorView();
        decorView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (landscape) {
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);

            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    int finalVisibility = visibility;
                    ViewUtils.postOnMainThread(() -> decorView.setSystemUiVisibility(finalVisibility), 500);
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setStatusBarContrastEnforced(false);
                getWindow().setNavigationBarContrastEnforced(false);
            }
            if (ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f) {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        if (!Objects.equals(Prefs.getLastCommit(), BuildConfig.COMMIT) && SliceBeam.hasUpdateInfo) {
            Prefs.setLastCommit();
            BeamServerData.load();
            new ChangeLogBottomSheet(this).show();
        }
    }

    @NonNull
    public NavigationDelegate getNavigationDelegate() {
        return delegate;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        loadFile(intent.getData());
        setIntent(null);
    }

    /** @noinspection ResultOfMethodCallIgnored*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == MainActivity.REQUEST_CODE_EXPORT_3MF) {
                Fragment fragment = getNavigationDelegate().getCurrentFragment();
                if (fragment instanceof BedFragment) {
                    try {
                        OutputStream out = getContentResolver().openOutputStream(data.getData());
                        Model model = ((BedFragment) fragment).getGlView().getRenderer().getModel();
                        File tempFile = File.createTempFile("temp_project", ".3mf");
                        SliceBeam.genCurrentConfig();
                        File cfg = SliceBeam.getCurrentConfigFile();
                        model.export3mf(cfg.getAbsolutePath(), tempFile.getAbsolutePath());

                        InputStream in = new FileInputStream(tempFile);
                        byte[] buffer = new byte[10240];
                        int c;
                        while ((c = in.read(buffer)) != -1) {
                            out.write(buffer, 0, c);
                        }
                        in.close();
                        out.close();
                        tempFile.delete();

                        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileExport3mfSuccess));
                    } catch (IOException | Slic3rRuntimeError e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_GCODE) {
                try {
                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    InputStream in = new FileInputStream(BedFragment.getTempGCodePath());
                    byte[] buffer = new byte[10240];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    in.close();
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (requestCode == MainActivity.REQUEST_CODE_OPEN_FILE) {
                loadFile(data.getData());
            } else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_PROFILES) {
                try {
                    Slic3rConfigWrapper w = new Slic3rConfigWrapper();
                    w.printConfigs.addAll(EXPORTING_PRINTS);
                    w.filamentConfigs.addAll(EXPORTING_FILAMENTS);
                    w.printerConfigs.addAll(EXPORTING_PRINTERS);

                    EXPORTING_PRINTS = null;
                    EXPORTING_FILAMENTS = null;
                    EXPORTING_PRINTERS = null;

                    w.presets = new ConfigObject();
                    if (w.findPrint(SliceBeam.CONFIG.presets.get("print")) != null) {
                        w.presets.put("print", SliceBeam.CONFIG.presets.get("print"));
                    }
                    if (w.findFilament(SliceBeam.CONFIG.presets.get("filament")) != null) {
                        w.presets.put("filament", SliceBeam.CONFIG.presets.get("filament"));
                    }
                    if (w.findPrinter(SliceBeam.CONFIG.presets.get("printer")) != null) {
                        w.presets.put("printer", SliceBeam.CONFIG.presets.get("printer"));
                    }

                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    out.write(w.serialize().getBytes(StandardCharsets.UTF_8));
                    out.close();

                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileExportProfilesSuccess));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (requestCode == MainActivity.REQUEST_CODE_IMPORT_PROFILES) {
                Uri uri = data.getData();
                String fileName = IOUtils.getDisplayName(uri);

                if (fileName == null) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(R.string.MenuFileOpenFileFailedNullName)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                if (isOrcaBundleFile(fileName)) {
                    loadConvertedProfile(uri);
                    return;
                }

                if (!fileName.endsWith(".ini")) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(R.string.MenuFileImportProfilesFailedNotIni)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                try {
                    loadIniForImport(getContentResolver().openInputStream(uri));
                } catch (FileNotFoundException e) {
                    new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            } else if (requestCode == REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissAIGeneratorMenu());

                Bitmap bm = BitmapFactory.decodeFile(aiTempFile.getAbsolutePath());
                generateAiModel(bm);
                aiTempFile.delete();
                aiTempFile = null;
            } else if (requestCode == REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissAIGeneratorMenu());

                try {
                    InputStream in = getContentResolver().openInputStream(data.getData());
                    Bitmap bm = BitmapFactory.decodeStream(in);
                    generateAiModel(bm);
                } catch (Exception e) {
                    Log.e("ai_generator", "Failed to write to downloads", e);
                }
            }
        }
    }

    private void loadConvertedProfile(Uri uri) {
        String tag = UUID.randomUUID().toString();
        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.OrcaConversionPleaseWait).tag(tag));

        IOUtils.IO_POOL.submit(() -> {
            File copiedArchive = null;
            File extractDir = new File(SliceBeam.getModelCacheDir(), "orca_conv_" + UUID.randomUUID());
            try {
                copiedArchive = File.createTempFile("orca_conv_", ".zip", SliceBeam.getModelCacheDir());
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(copiedArchive)) {
                    if (in == null) {
                        throw new FileNotFoundException(String.valueOf(uri));
                    }

                    byte[] buffer = new byte[10240];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, c);
                    }
                }

                if (!extractDir.mkdirs() && !extractDir.isDirectory()) {
                    throw new IOException("Failed to create temporary extraction directory");
                }

                String manifest = Native.orca_bundle_read(copiedArchive.getAbsolutePath(), extractDir.getAbsolutePath());
                if (manifest == null) {
                    throw new IOException("Failed to read Orca bundle");
                }

                JSONObject root = new JSONObject(manifest);
                JSONObject bundle = new JSONObject(root.getString("bundle_structure_json"));
                if (!bundle.optString("bundle_type", "").endsWith("config bundle")) {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                            .setTitle(R.string.MenuFileImportProfilesFailed)
                            .setMessage(R.string.OrcaConversionNotAConfigBundle)
                            .setPositiveButton(android.R.string.ok, null)
                            .show());
                    return;
                }

                importOrcaBundle(root);
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
            } catch (IOUtils.MissingProfileException ep) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(getString(R.string.MenuFileImportProfilesFailedBaseProfileNotFound, ep.profile))
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            } catch (IOException e) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            } catch (Exception e) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            } finally {
                if (copiedArchive != null) {
                    //noinspection ResultOfMethodCallIgnored
                    copiedArchive.delete();
                }
                deleteRecursively(extractDir);
            }
        });
    }

    private boolean isOrcaBundleFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".orca_printer") || lower.endsWith(".orca_filament") || lower.endsWith(".zip");
    }

    private boolean is3mfFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".3mf");
    }

    private boolean isGcodeFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".gcode") || lower.endsWith(".g") || lower.endsWith(".gco") || lower.endsWith(".bgcode");
    }

    private boolean isSupportedModelFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        // Keep this list in sync with libslic3r::Model::read_from_file() on Android.
        return lower.endsWith(".stl") || lower.endsWith(".oltp") || lower.endsWith(".obj") ||
                lower.endsWith(".3mf") || lower.endsWith(".amf") || lower.endsWith(".svg") ||
                lower.endsWith(".drc") || isGcodeFile(lower);
    }

    private File getSafeCachedModelFile(String fileName) {
        String safe = fileName.replaceAll("[\\/:*?\"<>|]", "_");
        if (safe.trim().isEmpty()) {
            safe = "model";
        }
        File f = new File(SliceBeam.getModelCacheDir(), safe);
        if (!f.exists()) return f;

        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return new File(SliceBeam.getModelCacheDir(), base + "_" + UUID.randomUUID() + ext);
    }

    private static class Project3mfImportResult {
        int importedProfiles;
        int plateCount;
    }

    private String readZipEntryText(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[10240];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            return out.toString("UTF-8");
        }
    }

    private String embeddedPresetFallbackName(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.endsWith(".config")) name = name.substring(0, name.length() - ".config".length());
        return name;
    }

    private String jsonProfileName(JSONObject obj, String fallback) {
        String name = obj.optString("name", null);
        if (name == null || name.trim().isEmpty()) {
            name = obj.optString("print_settings_id", null);
        }
        if (name == null || name.trim().isEmpty()) {
            name = obj.optString("filament_settings_id", null);
        }
        if (name == null || name.trim().isEmpty()) {
            name = obj.optString("printer_settings_id", null);
        }
        if (name == null || name.trim().isEmpty()) {
            name = fallback;
        }
        if (name != null && name.length() > 3 && name.charAt(0) == '[' && name.charAt(1) == '"' && name.charAt(name.length() - 2) == '"' && name.charAt(name.length() - 1) == ']') {
            name = name.substring(2, name.length() - 2);
        }
        return name;
    }

    private ConfigObject embeddedJsonToConfig(JSONObject obj, String type, List<String> supportedKeys, List<String> inBundle, String fallbackName) throws Exception {
        ConfigObject cfg = IOUtils.configJsonToIni(obj, type, supportedKeys, inBundle);
        String title = cfg.getTitle();
        if (title == null || title.trim().isEmpty()) {
            cfg.setTitle(jsonProfileName(obj, fallbackName));
        }
        return cfg;
    }

    private boolean hasConfigTitle(List<ConfigObject> configs, String title) {
        if (title == null) return false;
        for (ConfigObject cfg : configs) {
            if (cfg != null && Objects.equals(cfg.getTitle(), title)) return true;
        }
        return false;
    }

    private ConfigObject projectSettingsToConfig(JSONObject projectSettings, String type, List<String> supportedKeys, String idKey, String fallbackName) throws Exception {
        String selectedName = firstProjectValue(projectSettings, idKey);
        if (selectedName == null || selectedName.trim().isEmpty()) selectedName = fallbackName;

        JSONObject profile = new JSONObject(projectSettings.toString());
        profile.remove("print_settings_id");
        profile.remove("filament_settings_id");
        profile.remove("printer_settings_id");
        profile.put(idKey, selectedName);

        List<String> inBundle = new ArrayList<>();
        inBundle.add(selectedName);
        return embeddedJsonToConfig(profile, type, supportedKeys, inBundle, selectedName);
    }

    private void addProjectSettingsFallbackProfiles(Slic3rConfigWrapper wrapper, JSONObject projectSettings) {
        if (projectSettings == null) return;
        try {
            String printName = firstProjectValue(projectSettings, "print_settings_id");
            if (!TextUtils.isEmpty(printName) && !hasConfigTitle(wrapper.printConfigs, printName)) {
                wrapper.printConfigs.add(projectSettingsToConfig(projectSettings, "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, "print_settings_id", printName));
            }
        } catch (Exception e) {
            Log.w("MainActivity", "Skipping project_settings print profile fallback", e);
        }
        try {
            String filamentName = firstProjectValue(projectSettings, "filament_settings_id");
            if (!TextUtils.isEmpty(filamentName) && !hasConfigTitle(wrapper.filamentConfigs, filamentName)) {
                wrapper.filamentConfigs.add(projectSettingsToConfig(projectSettings, "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, "filament_settings_id", filamentName));
            }
        } catch (Exception e) {
            Log.w("MainActivity", "Skipping project_settings filament profile fallback", e);
        }
        try {
            String printerName = firstProjectValue(projectSettings, "printer_settings_id");
            if (!TextUtils.isEmpty(printerName) && !hasConfigTitle(wrapper.printerConfigs, printerName)) {
                wrapper.printerConfigs.add(projectSettingsToConfig(projectSettings, "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, "printer_settings_id", printerName));
            }
        } catch (Exception e) {
            Log.w("MainActivity", "Skipping project_settings printer profile fallback", e);
        }
    }

    private String firstProjectValue(JSONObject project, String key) {
        if (project == null || !project.has(key)) return null;
        try {
            Object value = project.get(key);
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                return arr.length() > 0 ? arr.optString(0, null) : null;
            }
            String s = String.valueOf(value);
            if (s.startsWith("[") && s.endsWith("]")) {
                JSONArray arr = new JSONArray(s);
                return arr.length() > 0 ? arr.optString(0, null) : null;
            }
            return s;
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to read project setting " + key, e);
            return null;
        }
    }

    private int countPlates(ZipFile zip) throws IOException {
        int maxPlate = 0;
        ZipEntry modelSettings = zip.getEntry("Metadata/model_settings.config");
        if (modelSettings != null) {
            String xml = readZipEntryText(zip, modelSettings);
            int idx = 0;
            while ((idx = xml.indexOf("<plate", idx)) != -1) {
                maxPlate++;
                idx += 6;
            }
        }
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("Metadata/plate_") && (name.endsWith(".png") || name.endsWith(".json") || name.endsWith(".gcode"))) {
                String num = name.substring("Metadata/plate_".length());
                int end = 0;
                while (end < num.length() && Character.isDigit(num.charAt(end))) end++;
                if (end > 0) {
                    try {
                        maxPlate = Math.max(maxPlate, Integer.parseInt(num.substring(0, end)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return Math.max(maxPlate, 1);
    }

    private Project3mfImportResult importEmbedded3mfProfiles(File file) {
        Project3mfImportResult result = new Project3mfImportResult();
        Slic3rConfigWrapper w = new Slic3rConfigWrapper();
        JSONObject projectSettings = null;
        HashMap<String, JSONObject> processJson = new HashMap<>();
        HashMap<String, JSONObject> filamentJson = new HashMap<>();
        HashMap<String, JSONObject> printerJson = new HashMap<>();

        try (ZipFile zip = new ZipFile(file)) {
            result.plateCount = countPlates(zip);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith("Metadata/") || !name.endsWith(".config")) continue;

                if (name.equals("Metadata/project_settings.config")) {
                    projectSettings = new JSONObject(readZipEntryText(zip, entry));
                } else if (name.startsWith("Metadata/process_settings_") || name.startsWith("Metadata/print_setting_")) {
                    processJson.put(name, new JSONObject(readZipEntryText(zip, entry)));
                } else if (name.startsWith("Metadata/filament_settings_")) {
                    filamentJson.put(name, new JSONObject(readZipEntryText(zip, entry)));
                } else if (name.startsWith("Metadata/machine_settings_")) {
                    printerJson.put(name, new JSONObject(readZipEntryText(zip, entry)));
                }
            }

            List<String> processNames = new ArrayList<>();
            for (String name : processJson.keySet()) processNames.add(jsonProfileName(processJson.get(name), embeddedPresetFallbackName(name)));
            addInstalledProfileTitles(processNames, SliceBeam.CONFIG.printConfigs);
            for (String name : processJson.keySet()) {
                try {
                    w.printConfigs.add(embeddedJsonToConfig(processJson.get(name), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, processNames, embeddedPresetFallbackName(name)));
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping embedded process profile " + name, e);
                }
            }
            resolveBundleInherits(w.printConfigs, w::findPrint, name -> SliceBeam.CONFIG.findPrint(name));

            List<String> filamentNames = new ArrayList<>();
            for (String name : filamentJson.keySet()) filamentNames.add(jsonProfileName(filamentJson.get(name), embeddedPresetFallbackName(name)));
            addInstalledProfileTitles(filamentNames, SliceBeam.CONFIG.filamentConfigs);
            for (String name : filamentJson.keySet()) {
                try {
                    w.filamentConfigs.add(embeddedJsonToConfig(filamentJson.get(name), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, filamentNames, embeddedPresetFallbackName(name)));
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping embedded filament profile " + name, e);
                }
            }
            resolveBundleInherits(w.filamentConfigs, w::findFilament, name -> SliceBeam.CONFIG.findFilament(name));

            List<String> printerNames = new ArrayList<>();
            for (String name : printerJson.keySet()) printerNames.add(jsonProfileName(printerJson.get(name), embeddedPresetFallbackName(name)));
            addInstalledProfileTitles(printerNames, SliceBeam.CONFIG.printerConfigs);
            for (String name : printerJson.keySet()) {
                try {
                    w.printerConfigs.add(embeddedJsonToConfig(printerJson.get(name), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, printerNames, embeddedPresetFallbackName(name)));
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping embedded printer profile " + name, e);
                }
            }
            resolveBundleInherits(w.printerConfigs, w::findPrinter, name -> SliceBeam.CONFIG.findPrinter(name));

            // Some Orca/Bambu project 3MFs (including desktop-saved projects with DRC geometry)
            // only store the active settings in Metadata/project_settings.config instead of
            // separate process/filament/machine profile files. Convert that JSON into normal
            // mobile ConfigObjects so the loaded project uses the printer, filament, and print
            // settings it was saved with.
            addProjectSettingsFallbackProfiles(w, projectSettings);

            for (ConfigObject cfg : w.printConfigs) SliceBeam.CONFIG.importPrint(cfg);
            for (ConfigObject cfg : w.filamentConfigs) SliceBeam.CONFIG.importFilament(cfg);
            for (ConfigObject cfg : w.printerConfigs) SliceBeam.CONFIG.importPrinter(cfg);

            String selectedPrint = firstProjectValue(projectSettings, "print_settings_id");
            String selectedFilament = firstProjectValue(projectSettings, "filament_settings_id");
            String selectedPrinter = firstProjectValue(projectSettings, "printer_settings_id");
            if ((selectedPrint == null || selectedPrint.trim().isEmpty()) && !w.printConfigs.isEmpty()) selectedPrint = w.printConfigs.get(0).getTitle();
            if ((selectedFilament == null || selectedFilament.trim().isEmpty()) && !w.filamentConfigs.isEmpty()) selectedFilament = w.filamentConfigs.get(0).getTitle();
            if ((selectedPrinter == null || selectedPrinter.trim().isEmpty()) && !w.printerConfigs.isEmpty()) selectedPrinter = w.printerConfigs.get(0).getTitle();

            boolean changed = false;
            if (selectedPrint != null && SliceBeam.CONFIG.findPrint(selectedPrint) != null) {
                SliceBeam.CONFIG.presets.put("print", selectedPrint);
                changed = true;
            }
            if (selectedFilament != null && SliceBeam.CONFIG.findFilament(selectedFilament) != null) {
                SliceBeam.CONFIG.presets.put("filament", selectedFilament);
                changed = true;
            }
            if (selectedPrinter != null && SliceBeam.CONFIG.findPrinter(selectedPrinter) != null) {
                SliceBeam.CONFIG.presets.put("printer", selectedPrinter);
                changed = true;
            }

            result.importedProfiles = w.printConfigs.size() + w.filamentConfigs.size() + w.printerConfigs.size();
            if (changed || result.importedProfiles > 0) {
                SliceBeam.clearLiveDiffs();
                SliceBeam.saveConfig();
            }
        } catch (Exception e) {
            // Geometry loading should still proceed for a valid 3MF even when embedded settings are absent
            // or too new for the mobile converter.
            Log.w("MainActivity", "Failed to import embedded 3MF profiles from " + file, e);
        }
        return result;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void addInstalledProfileTitles(List<String> names, List<ConfigObject> installed) {
        if (installed == null) {
            return;
        }

        for (ConfigObject obj : installed) {
            if (obj != null && obj.getTitle() != null && !names.contains(obj.getTitle())) {
                names.add(obj.getTitle());
            }
        }
    }

    private void resolveBundleInherits(List<ConfigObject> configs, BundleProfileFinder bundleFinder, BundleProfileFinder fallbackFinder) throws IOUtils.MissingProfileException {
        for (ConfigObject obj : configs) {
            String inherit = obj.get("inherits");
            while (inherit != null) {
                ConfigObject base = bundleFinder.find(inherit);
                if (base == null && fallbackFinder != null) {
                    base = fallbackFinder.find(inherit);
                }
                if (base == null) {
                    obj.values.remove("inherits");
                    break;
                }

                obj.values.remove("inherits");
                HashMap<String, String> newMap = new HashMap<>();
                newMap.putAll(base.values);
                newMap.putAll(obj.values);
                obj.values = newMap;

                inherit = obj.values.get("inherits");
            }
        }
    }

    private void importOrcaBundle(JSONObject root) throws Exception {
        JSONObject bundle = new JSONObject(root.getString("bundle_structure_json"));

        HashMap<String, String> files = new HashMap<>();
        JSONArray fileArray = root.getJSONArray("files");
        for (int i = 0; i < fileArray.length(); i++) {
            JSONObject file = fileArray.getJSONObject(i);
            files.put(file.getString("path"), file.getString("content"));
        }

        Slic3rConfigWrapper w = new Slic3rConfigWrapper();
        if (bundle.has("process_config")) {
            JSONArray arr = bundle.getJSONArray("process_config");
            List<String> names = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getString(i);
                names.add(name);
                stripped.add(name.substring(name.indexOf('/') + 1, name.length() - 5));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG.printConfigs);

            for (String name : names) {
                String content = files.get(name);
                if (content == null) {
                    throw new FileNotFoundException(name);
                }
                w.printConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.printConfigs, w::findPrint, name -> SliceBeam.CONFIG.findPrint(name));
        }
        if (bundle.has("filament_config")) {
            JSONArray arr = bundle.getJSONArray("filament_config");
            List<String> names = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getString(i);
                names.add(name);
                stripped.add(name.substring(name.indexOf('/') + 1, name.length() - 5));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG.filamentConfigs);

            for (String name : names) {
                String content = files.get(name);
                if (content == null) {
                    throw new FileNotFoundException(name);
                }
                w.filamentConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.filamentConfigs, w::findFilament, name -> SliceBeam.CONFIG.findFilament(name));
        }
        if (bundle.has("printer_config")) {
            JSONArray arr = bundle.getJSONArray("printer_config");
            List<String> names = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getString(i);
                names.add(name);
                stripped.add(name.substring(name.indexOf('/') + 1, name.length() - 5));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG.printerConfigs);

            for (String name : names) {
                String content = files.get(name);
                if (content == null) {
                    throw new FileNotFoundException(name);
                }
                w.printerConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.printerConfigs, w::findPrinter, name -> SliceBeam.CONFIG.findPrinter(name));
        }

        loadIniForImport(new ByteArrayInputStream(w.serialize().getBytes(StandardCharsets.UTF_8)));
    }

    private void generateAiModel(Bitmap bm) {
        IS_GENERATING_AI_MODEL = true;
        String uploadTag = UUID.randomUUID().toString();
        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorUploading).tag(uploadTag));
        IOUtils.IO_POOL.submit(()->{
            Bitmap scaled;
            if (bm.getWidth() > 1024 || bm.getHeight() > 1024) {
                if (bm.getWidth() > bm.getHeight()) {
                    int w = 1024;
                    int h = (int) ((float) w * bm.getHeight() / bm.getWidth());
                    scaled = Bitmap.createScaledBitmap(bm, w, h, true);
                } else {
                    int h = 1024;
                    int w = (int) ((float) h * bm.getWidth() / bm.getHeight());
                    scaled = Bitmap.createScaledBitmap(bm, w, h, true);
                }
                bm.recycle();
            } else {
                scaled = bm;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            scaled.recycle();

            String processTag = UUID.randomUUID().toString();
            CloudAPI.INSTANCE.modelsGenerate(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/png", new APICallback<InputStream>() {
                @Override
                public void onResponse(InputStream in) {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(processTag));

                    String downloadTag = UUID.randomUUID().toString();
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorDownloading).tag(downloadTag));
                    String fileName = "generated_" + UUID.randomUUID() + ".stl";

                    File f = new File(SliceBeam.getModelCacheDir(), fileName);
                    try {
                        FileOutputStream fos = new FileOutputStream(f);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-pki.stl");
                            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

                            if (uri != null) {
                                try {
                                    OutputStream out = getContentResolver().openOutputStream(uri);
                                    byte[] buf = new byte[10240];
                                    int c;
                                    while ((c = in.read(buf)) != -1) {
                                        out.write(buf, 0, c);
                                        fos.write(buf, 0, c);
                                    }
                                    out.close();
                                } catch (IOException e) {
                                    Log.e("ai_generator", "Failed to write to downloads", e);
                                }
                            }
                        } else {
                            File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            File file = new File(downloadsDirectory, fileName);

                            try {
                                FileOutputStream out = new FileOutputStream(file);
                                byte[] buf = new byte[10240];
                                int c;
                                while ((c = in.read(buf)) != -1) {
                                    out.write(buf, 0, c);
                                    fos.write(buf, 0, c);
                                }
                                out.close();
                            } catch (IOException e) {
                                Log.e("ai_generator", "Failed to write to downloads", e);
                            }
                        }
                        fos.close();
                    } catch (Exception e) {
                        Log.e("ai_generator", "Failed to write to downloads", e);
                    }
                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(downloadTag));
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileAIGeneratorSavedAs, fileName));
                    loadFile(f, true);
                    CloudController.checkGeneratorRemaining();
                    IS_GENERATING_AI_MODEL = false;
                }

                @Override
                public void onException(Exception e) {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(processTag));
                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.MenuFileAIGeneratorError)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show());
                    IS_GENERATING_AI_MODEL = false;
                }
            });
            SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(uploadTag));
            SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorProcessing).tag(processTag));
        });
    }

    private void loadIniForImport(InputStream in) {
        IOUtils.IO_POOL.submit(()->{
            try {
                Slic3rConfigWrapper w = new Slic3rConfigWrapper(in);

                ViewUtils.postOnMainThread(() -> {
                    CharSequence[] prints = new CharSequence[w.printConfigs.size()];
                    boolean[] enabledPrints = new boolean[prints.length];
                    for (int i = 0; i < prints.length; i++) {
                        prints[i] = w.printConfigs.get(i).getTitle();
                        enabledPrints[i] = true;
                    }

                    CharSequence[] filaments = new CharSequence[w.filamentConfigs.size()];
                    boolean[] enabledFilaments = new boolean[filaments.length];
                    for (int i = 0; i < filaments.length; i++) {
                        filaments[i] = w.filamentConfigs.get(i).getTitle();
                        enabledFilaments[i] = true;
                    }

                    CharSequence[] printers = new CharSequence[w.printerConfigs.size()];
                    boolean[] enabledPrinters = new boolean[printers.length];
                    for (int i = 0; i < printers.length; i++) {
                        printers[i] = w.printerConfigs.get(i).getTitle();
                        enabledPrinters[i] = true;
                    }

                    if (prints.length == 0 && filaments.length == 0 && printers.length == 0) {
                        ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                                .setTitle(R.string.MenuFileImportProfilesFailed)
                                .setMessage(R.string.MenuFileImportProfilesFailedEmpty)
                                .setPositiveButton(android.R.string.ok, null)
                                .show());
                        return;
                    }

                    Runnable finish = () -> {
                        for (int i = 0; i < enabledPrints.length; i++) {
                            if (enabledPrints[i]) {
                                SliceBeam.CONFIG.importPrint(w.printConfigs.get(i));
                            }
                        }
                        for (int i = 0; i < enabledFilaments.length; i++) {
                            if (enabledFilaments[i]) {
                                SliceBeam.CONFIG.importFilament(w.filamentConfigs.get(i));
                            }
                        }
                        for (int i = 0; i < enabledPrinters.length; i++) {
                            if (enabledPrinters[i]) {
                                SliceBeam.CONFIG.importPrinter(w.printerConfigs.get(i));
                            }
                        }
                        SliceBeam.saveConfig();
                    };
                    Runnable printersRun = () -> {
                        if (printers.length == 0) {
                            finish.run();
                            return;
                        }

                        new BeamAlertDialogBuilder(this)
                                .setTitle(R.string.MenuFileExportProfilesPrinters)
                                .setMultiChoiceItems(printers, enabledPrinters, (dialog, which, isChecked) -> enabledPrinters[which] = isChecked)
                                .setPositiveButton(android.R.string.ok, (d3, w3) -> finish.run())
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    };
                    Runnable filamentsRun = () -> {
                        if (filaments.length == 0) {
                            printersRun.run();
                            return;
                        }
                        new BeamAlertDialogBuilder(this)
                                .setTitle(R.string.MenuFileExportProfilesFilaments)
                                .setMultiChoiceItems(filaments, enabledFilaments, (dialog, which, isChecked) -> enabledFilaments[which] = isChecked)
                                .setPositiveButton(android.R.string.ok, (d2, w2) -> printersRun.run())
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    };
                    if (prints.length == 0) {
                        filamentsRun.run();
                    } else {
                        new BeamAlertDialogBuilder(this)
                                .setTitle(R.string.MenuFileExportProfilesPrints)
                                .setMultiChoiceItems(prints, enabledPrints, (dialog, which, isChecked) -> enabledPrints[which] = isChecked)
                                .setPositiveButton(android.R.string.ok, (d1, w1) -> filamentsRun.run())
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to read file", e);

                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        });
    }

    private void loadFile(File f, boolean autoorient) {
        String tag = UUID.randomUUID().toString();
        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag(tag));
        IOUtils.IO_POOL.submit(() -> {
            Process.setThreadPriority(-20);
            if (delegate.getCurrentFragment() instanceof BedFragment) {
                BedFragment fragment = (BedFragment) delegate.getCurrentFragment();
                try {
                    boolean gcode = isGcodeFile(f.getName());
                    boolean project3mf = is3mfFile(f.getName());
                    Project3mfImportResult projectImport = project3mf ? importEmbedded3mfProfiles(f) : new Project3mfImportResult();
                    if (gcode) {
                        fragment.loadGCode(f);
                        fragment.getGlView().queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                Model model = fragment.getGlView().getRenderer().getModel();
                                if (model == null || fragment.getGlView().getRenderer().getBed() == null) {
                                    fragment.getGlView().queueEvent(this);
                                    return;
                                }
                                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded));
                            }
                        });
                    } else {
                        fragment.loadModel(f, project3mf, projectImport != null ? projectImport.plateCount : 1, (model, firstNewObject, addedObjects) -> {
                            android.util.Log.e("MainActivity", "loadModel callback fired: model=" + (model != null ? model.getPointer() : "null") + " firstNew=" + firstNewObject + " added=" + addedObjects);
                            if (model == null || fragment.getGlView().getRenderer().getBed() == null) {
                                android.util.Log.e("MainActivity", "Returning early because model or bed is null");
                                return;
                            }

                            SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
                            boolean bigObject = false;
                            for (int i = firstNewObject; i < firstNewObject + addedObjects; i++) {
                                if (autoorient && !project3mf) {
                                    model.autoOrient(i);
                                    fragment.getGlView().getRenderer().invalidateGlModel(i);
                                }
                                if (model.isBigObject(i)) {
                                    bigObject = true;
                                }
                            }
                            if (autoorient && !project3mf) {
                                fragment.getGlView().requestRender();
                            }
                            SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                            if (project3mf) {
                                StringBuilder message = new StringBuilder("3MF project loaded");
                                if (projectImport.plateCount > 1) {
                                    message.append(" (").append(projectImport.plateCount).append(" plates)");
                                }
                                if (projectImport.importedProfiles > 0) {
                                    message.append("; imported ").append(projectImport.importedProfiles).append(" embedded profiles");
                                }
                                message.append('.');
                                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(message.toString()));
                            } else {
                                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded));
                            }
                            if (bigObject) {
                                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.WARNING, R.string.MenuFileOpenFileBigObject));
                            }
                        });
                    }
                } catch (Slic3rRuntimeError e) {
                    android.util.Log.e("MainActivity", "Failed to load model", e);
                    f.delete();

                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.MenuFileOpenFileFailed)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show());
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Silent crash in loadFile", e);
                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.MenuFileOpenFileFailed)
                            .setMessage(e.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show());
                }
            }
        });
    }

    private void loadFile(Uri uri) {
        if (uri == null) return;

        ContentResolver resolver = getContentResolver();
        String fileName = IOUtils.getDisplayName(uri);
        if (fileName == null && "file".equals(uri.getScheme())) {
            fileName = uri.getLastPathSegment();
        }
        if (fileName == null) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileOpenFileFailed)
                    .setMessage(R.string.MenuFileOpenFileFailedNullName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (isOrcaBundleFile(fileName)) {
            loadConvertedProfile(uri);
            return;
        }
        if (fileName.endsWith(".ini")) {
            try {
                loadIniForImport(resolver.openInputStream(uri));
            } catch (FileNotFoundException e) {
                new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
            return;
        }

        if (!isSupportedModelFile(fileName)) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileOpenFileFailed)
                    .setMessage("Unsupported file type: " + fileName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        File f = getSafeCachedModelFile(fileName);
        IOUtils.IO_POOL.submit(()->{
            try {
                InputStream in = resolver.openInputStream(uri);
                if (in == null) {
                    throw new FileNotFoundException(String.valueOf(uri));
                }
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buffer = new byte[10240]; int c;
                while ((c = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, c);
                }
                fos.close();
                in.close();
                loadFile(f, false);
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to write cache file", e);

                f.delete();
                ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileOpenFileFailed)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if ((newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            ThemesRepo.resetSystemResolvedTheme();
            ThemesRepo.invalidate(this);
        }
    }

    public void onApplyTheme() {
        delegate.onApplyTheme();

        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f) {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        decorView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isChangingConfigurations()) {
            outState.putInt("id", id);
            liveDelegate.put(id, delegate);
        }
    }

    private boolean isCompatible(NavigationDelegate delegate) {
        return true;
    }

    private NavigationDelegate onCreateDelegate() {
        return new MobileNavigationDelegate();
    }

    public void showUnfoldMenu(UnfoldMenu menu, View v) {
        if (unfoldMenu != null) return;
        menu.setOnDismiss(() -> unfoldMenu = null);
        menu.show(v, delegate.getOverlayView());
        unfoldMenu = menu;
    }

    @Override
    public void onBackPressed() {
        if (unfoldMenu != null) {
            unfoldMenu.dismiss();
            return;
        }
        if (delegate.onBackPressed()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        delegate.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        delegate.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (delegate != null) {
            delegate.onDestroy();
        }
    }
}
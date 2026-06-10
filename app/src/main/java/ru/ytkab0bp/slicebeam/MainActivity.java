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
        return fileName.endsWith(".orca_printer") || fileName.endsWith(".orca_filament") || fileName.endsWith(".zip");
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
                    boolean gcode = f.getName().endsWith(".gcode");
                    if (gcode) {
                        fragment.loadGCode(f);
                    } else {
                        fragment.loadModel(f);
                    }
                    fragment.getGlView().queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            Model model = fragment.getGlView().getRenderer().getModel();
                            if (model == null || fragment.getGlView().getRenderer().getBed() == null) {
                                fragment.getGlView().queueEvent(this);
                                return;
                            }

                            if (!gcode) {
                                SliceBeam.EVENT_BUS.fireEvent(new ObjectsListChangedEvent());
                            }
                            int i = model.getObjectsCount() - 1;
                            if (autoorient) {
                                model.autoOrient(i);
                                fragment.getGlView().getRenderer().invalidateGlModel(i);
                                fragment.getGlView().requestRender();
                            }
                            SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                            SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded));
                            if (model.isBigObject(i)) {
                                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.WARNING, R.string.MenuFileOpenFileBigObject));
                            }
                        }
                    });
                } catch (Slic3rRuntimeError e) {
                    Log.e("MainActivity", "Failed to load model", e);
                    f.delete();

                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(tag));
                    ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
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

        File f = new File(SliceBeam.getModelCacheDir(), fileName);
        // TODO: Check if file already exists
        IOUtils.IO_POOL.submit(()->{
            try {
                InputStream in = resolver.openInputStream(uri);
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
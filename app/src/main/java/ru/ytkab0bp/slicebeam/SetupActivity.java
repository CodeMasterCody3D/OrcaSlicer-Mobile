package ru.ytkab0bp.slicebeam;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


import cz.msebera.android.httpclient.Header;
import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.cloud.CloudAPI;
import ru.ytkab0bp.slicebeam.cloud.CloudController;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.CloudManageBottomSheet;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.BeamServerDataUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudFeaturesUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudLoginStateUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudSyncFinishedEvent;
import ru.ytkab0bp.slicebeam.recycler.BigHeaderItem;
import ru.ytkab0bp.slicebeam.recycler.PreferenceItem;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerAdapter;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.recycler.TextHintRecyclerItem;
import ru.ytkab0bp.slicebeam.slic3r.Native;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils;
import ru.ytkab0bp.slicebeam.theme.BeamTheme;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.IOUtils;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.BeamSwitch;
import ru.ytkab0bp.slicebeam.view.BoostySubsView;
import ru.ytkab0bp.slicebeam.view.FadeRecyclerView;
import ru.ytkab0bp.slicebeam.view.MiniColorView;
import ru.ytkab0bp.slicebeam.view.TextColorImageSpan;

public class SetupActivity extends AppCompatActivity {
    public final static String EXTRA_ABOUT = "about";
    public final static String EXTRA_BOOSTY_ONLY = "boosty_only";
    public final static String EXTRA_CLOUD_PROFILE = "cloud_profile";
    public final static String EXTRA_CLOUD_IMPORT_FROM_SETUP = "cloud_import_from_setup";
    public final static String EXTRA_ADD_PRINTER = "add_printer";

    private final static String TAG = "SetupActivity";
    private final static int REQUEST_CODE_IMPORT_SETUP_PROFILE = 1001;

    private static final String ORCA_ASSET_DIR = "orca_profiles";

    private final static int PROFILES_INDEX = 2;
    private static int BOOSTY_INDEX = 3;

    private final static int TYPE_PRINTER = 0, TYPE_PRINT_CONFIG = 1, TYPE_FILAMENT = 2;

    private ViewPager2 pager;
    private SimpleRecyclerAdapter adapter;
    private TextView title;
    private ImageView startupBackground;

    private int titleY;
    private float backgroundProgress;
    private float boostyProgress;

    private SpringAnimation fakeScroller;

    private AsyncHttpClient client = new AsyncHttpClient();

    private List<ProfilesRepo> repos = new ArrayList<>();
    private ProfilesItem profilesItem;
    private FilamentsItem filamentsItem;
    private CloudProfileItem cloudItem;
    private boolean limitProfileFragmentCount = true;
    private boolean isLoading;

    private Map<ProfilesRepo, List<Slic3rConfigWrapper>> profilesMap = new HashMap<>();
    private boolean isProfilesLoaded;
    private boolean about;
    private boolean boostyOnly;
    private boolean cloudProfile;
    private boolean cloudImport;
    private boolean addPrinter;

    private List<ConfigObject> enabledPrinters = new ArrayList<>();
    private List<ConfigObject> enabledFilaments = new ArrayList<>();

    {
        client.setUserAgent(String.format(Locale.ROOT, "SliceBeam/%s-%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        client.setEnableRedirects(true);
        client.setLoggingEnabled(false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SliceBeam.EVENT_BUS.registerListener(this);

        about = getIntent().getBooleanExtra(EXTRA_ABOUT, false);
        boostyOnly = getIntent().getBooleanExtra(EXTRA_BOOSTY_ONLY, false);
        cloudProfile = getIntent().getBooleanExtra(EXTRA_CLOUD_PROFILE, false);
        cloudImport = getIntent().getBooleanExtra(EXTRA_CLOUD_IMPORT_FROM_SETUP, false);
        addPrinter = getIntent().getBooleanExtra(EXTRA_ADD_PRINTER, false);

        if (SliceBeam.CONFIG != null && !about && !boostyOnly && !cloudProfile && !addPrinter) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        if (!about && !boostyOnly && !cloudProfile && !addPrinter) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.IntroEarlyAccess)
                    .setMessage(R.string.IntroEarlyAccessMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        if (boostyOnly || cloudProfile) {
            backgroundProgress = 1f;
        }

        pager = new ViewPager2(this);
        adapter = new SimpleRecyclerAdapter() {
            @Override
            public int getItemCount() {
                return about || boostyOnly || cloudProfile ? 1 : limitProfileFragmentCount ? PROFILES_INDEX + 1 : super.getItemCount();
            }
        };
        setItems();
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (pager.getCurrentItem() <= PROFILES_INDEX && !limitProfileFragmentCount) {
                        ViewUtils.postOnMainThread(() -> {
                            int realCount = adapter.getItemCount();
                            limitProfileFragmentCount = true;
                            adapter.notifyItemRangeRemoved(PROFILES_INDEX + 1, realCount - PROFILES_INDEX - 1);
                        });
                    }
                }
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position == 0 && !boostyOnly && !cloudProfile) {
                    backgroundProgress = positionOffset;
                } else {
                    backgroundProgress = 1f;
                }

                if (boostyOnly) {
                    boostyProgress = 1f;
                } else if (position == BOOSTY_INDEX) {
                    boostyProgress = 1f - positionOffset;
                } else if (position == BOOSTY_INDEX - 1) {
                    boostyProgress = positionOffset;
                } else {
                    boostyProgress = 0f;
                }
                if (profilesItem != null && profilesItem.recyclerView != null) {
                    profilesItem.recyclerView.setOverlayAlpha(1f - boostyProgress);
                }

                if (position == PROFILES_INDEX + 1 && filamentsItem != null) {
                    // Printer selection may have changed since this page was last built.
                    filamentsItem.refreshIfNeeded();
                }

                if (position == PROFILES_INDEX) {
                    if (!isProfilesLoaded && !isLoading && !profilesItem.useCustomProfile) {
                        AtomicInteger loadedCount = new AtomicInteger();
                        AtomicInteger totalNeeded = new AtomicInteger();
                        Runnable onLoadedAll = () -> {
                            isProfilesLoaded = true;
                            isLoading = false;
                            pager.setUserInputEnabled(true);
                            profilesItem.onProfilesLoaded();
                        };

                        for (ProfilesRepo repo : repos) {
                            if (repo.checked) {
                                totalNeeded.incrementAndGet();

                                if (repo.localAssets) {
                                    loadLocalProfiles(repo, loadedCount, totalNeeded, onLoadedAll);
                                    continue;
                                }

                                client.get(repo.indexUrl, new AsyncHttpResponseHandler() {
                                    @Override
                                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                        List<Slic3rConfigWrapper> vendorProfiles = new ArrayList<>();
                                        Runnable onVendorsLoaded = () -> {
                                            profilesMap.put(repo, vendorProfiles);
                                            loadedCount.incrementAndGet();

                                            if (loadedCount.get() == totalNeeded.get()) {
                                                ViewUtils.postOnMainThread(onLoadedAll);
                                            }
                                        };

                                        AtomicInteger loadedVendorsCount = new AtomicInteger();
                                        AtomicInteger totalNeededVendors = new AtomicInteger();
                                        List<Runnable> loadRunners = new ArrayList<>();

                                        try {
                                            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBody));
                                            ZipEntry en;
                                            while ((en = zis.getNextEntry()) != null) {
                                                String version = parseVendorVersion(zis);
                                                String baseUrl = repo.url + "/" + en.getName().substring(0, en.getName().length() - 4);
                                                String iniUrl = baseUrl + "/" + version + ".ini";

                                                totalNeededVendors.incrementAndGet();
                                                loadRunners.add(()-> client.get(iniUrl, new AsyncHttpResponseHandler() {
                                                    @Override
                                                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                                        loadedVendorsCount.incrementAndGet();

                                                        try {
                                                            Slic3rConfigWrapper cfg = new Slic3rConfigWrapper(new ByteArrayInputStream(responseBody));
                                                            for (ConfigObject obj : cfg.printerModels) {
                                                                if (obj.get("thumbnail") != null) {
                                                                    obj.thumbnailUrl = baseUrl + "/" + obj.get("thumbnail");
                                                                }
                                                            }
                                                            vendorProfiles.add(cfg);
                                                        } catch (IOException e) {
                                                            onFailure(statusCode, headers, responseBody, e);
                                                            return;
                                                        }

                                                        if (loadedVendorsCount.get() < totalNeededVendors.get()) {
                                                            loadRunners.get(loadedVendorsCount.get()).run();
                                                        } else {
                                                            onVendorsLoaded.run();
                                                        }
                                                    }

                                                    @Override
                                                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                                        Log.e(TAG, "Failed to load vendor file " + iniUrl, error);
                                                        isLoading = false;
                                                        ViewUtils.postOnMainThread(() -> {
                                                            Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show();
                                                            fakeScroll(-1);
                                                            pager.setUserInputEnabled(true);
                                                        });
                                                    }
                                                }));

                                                zis.closeEntry();
                                            }
                                            zis.close();

                                            if (loadRunners.isEmpty()) {
                                                onVendorsLoaded.run();
                                            } else {
                                                loadRunners.get(0).run();
                                            }
                                        } catch (IOException e) {
                                            Log.e(TAG, "Failed to parse vendor indices", e);
                                            onFailure(statusCode, headers, responseBody, e);
                                        }
                                    }

                                    @Override
                                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                        isLoading = false;
                                        Log.e(TAG, "Failed to load repo", error);
                                        ViewUtils.postOnMainThread(() -> {
                                            Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show();
                                            fakeScroll(-1);
                                            pager.setUserInputEnabled(true);
                                        });
                                    }
                                });
                            }
                        }

                        pager.setUserInputEnabled(false);
                    }
                }

                invalidateTitleY();
                updateStartupBackgroundVisibility();
            }
        });
        pager.setAdapter(adapter);
        pager.getChildAt(0).setOverScrollMode(View.OVER_SCROLL_NEVER);

        FrameLayout fl = new FrameLayout(this) {
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                titleY = h / 4;
                invalidateTitleY();
            }
        };
        fl.setClipChildren(false);
        fl.setClipToPadding(false);
        fl.setBackgroundColor(Color.rgb(68, 63, 69));
        startupBackground = new ImageView(this);
        startupBackground.setImageResource(R.drawable.startup_background);
        startupBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        startupBackground.setAdjustViewBounds(false);
        fl.addView(startupBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateStartupBackgroundVisibility();

        title = new TextView(this);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setText(cloudProfile ? getString(R.string.SettingsCloudManageTitle) : "");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        title.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        title.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        fl.addView(title);

        fl.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewCompat.setOnApplyWindowInsetsListener(fl, (v2, insets) -> {
            Insets systemBars = insets.getSystemWindowInsets();
            pager.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) title.getLayoutParams();
            params.leftMargin = systemBars.left;
            params.topMargin = systemBars.top;
            params.rightMargin = systemBars.right;
            params.bottomMargin = systemBars.bottom;
            return insets.consumeSystemWindowInsets();
        });
        setContentView(fl);

        // Pre-populate OrcaSlicer as the only profile source
        ProfilesRepo orcaRepo = new ProfilesRepo();
        orcaRepo.name = "OrcaSlicer";
        orcaRepo.description = "Bundled OrcaSlicer printer profiles";
        orcaRepo.localAssets = true;
        orcaRepo.checked = true;
        repos.add(orcaRepo);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_SETUP_PROFILE && resultCode == Activity.RESULT_OK && data != null) {
            loadProfileFromPicker(data.getData());
        }
    }

    private void openProfilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/octet-stream",
                "text/plain",
                "application/x-zip-compressed",
                "application/zip"
        });
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT_SETUP_PROFILE);
        } catch (Exception e) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage(e.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void loadProfileFromPicker(Uri uri) {
        if (uri == null) return;
        String fileName = IOUtils.getDisplayName(uri);
        if (fileName == null) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage(R.string.MenuFileOpenFileFailedNullName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);
        Toast.makeText(this, "Loading profile…", Toast.LENGTH_SHORT).show();
        if (isOrcaBundleFile(lowerName)) {
            loadOrcaProfileBundle(uri);
            return;
        }
        if (!lowerName.endsWith(".ini")) {
            new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage("Choose an Orca .orca_printer/.orca_filament profile or an exported .ini profile.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        IOUtils.IO_POOL.submit(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new FileNotFoundException(String.valueOf(uri));
                }
                saveImportedProfile(new Slic3rConfigWrapper(in));
            } catch (Exception e) {
                showProfileImportError(e);
            }
        });
    }

    private boolean isOrcaBundleFile(String fileName) {
        return fileName.endsWith(".orca_printer") || fileName.endsWith(".orca_filament") || fileName.endsWith(".orca_process") || fileName.endsWith(".zip");
    }

    private void loadOrcaProfileBundle(Uri uri) {
        IOUtils.IO_POOL.submit(() -> {
            File copiedArchive = null;
            File extractDir = new File(SliceBeam.getModelCacheDir(), "setup_orca_conv_" + UUID.randomUUID());
            try {
                copiedArchive = File.createTempFile("setup_orca_conv_", ".zip", SliceBeam.getModelCacheDir());
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
                    throw new IOException("Failed to read Orca profile bundle");
                }

                JSONObject root = new JSONObject(manifest);
                JSONObject bundle = new JSONObject(root.getString("bundle_structure_json"));
                if (!bundle.optString("bundle_type", "").endsWith("config bundle")) {
                    throw new IOException(getString(R.string.OrcaConversionNotAConfigBundle));
                }

                saveImportedProfile(convertOrcaBundleToConfig(root));
            } catch (Exception e) {
                showProfileImportError(e);
            } finally {
                if (copiedArchive != null) {
                    //noinspection ResultOfMethodCallIgnored
                    copiedArchive.delete();
                }
                deleteRecursively(extractDir);
            }
        });
    }

    private Slic3rConfigWrapper convertOrcaBundleToConfig(JSONObject root) throws Exception {
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
                stripped.add(stripOrcaProfileName(name));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG != null ? SliceBeam.CONFIG.printConfigs : null);
            for (String name : names) {
                String content = files.get(name);
                if (content == null) throw new FileNotFoundException(name);
                w.printConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.printConfigs, w::findPrint, SliceBeam.CONFIG != null ? name -> SliceBeam.CONFIG.findPrint(name) : null);
        }
        if (bundle.has("filament_config")) {
            JSONArray arr = bundle.getJSONArray("filament_config");
            List<String> names = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getString(i);
                names.add(name);
                stripped.add(stripOrcaProfileName(name));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG != null ? SliceBeam.CONFIG.filamentConfigs : null);
            for (String name : names) {
                String content = files.get(name);
                if (content == null) throw new FileNotFoundException(name);
                w.filamentConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.filamentConfigs, w::findFilament, SliceBeam.CONFIG != null ? name -> SliceBeam.CONFIG.findFilament(name) : null);
        }
        if (bundle.has("printer_config")) {
            JSONArray arr = bundle.getJSONArray("printer_config");
            List<String> names = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getString(i);
                names.add(name);
                stripped.add(stripOrcaProfileName(name));
            }
            addInstalledProfileTitles(stripped, SliceBeam.CONFIG != null ? SliceBeam.CONFIG.printerConfigs : null);
            for (String name : names) {
                String content = files.get(name);
                if (content == null) throw new FileNotFoundException(name);
                w.printerConfigs.add(IOUtils.configJsonToIni(new JSONObject(content), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, stripped));
            }
            resolveBundleInherits(w.printerConfigs, w::findPrinter, SliceBeam.CONFIG != null ? name -> SliceBeam.CONFIG.findPrinter(name) : null);
        }
        return w;
    }

    private String stripOrcaProfileName(String path) {
        int slash = path.indexOf('/');
        int start = slash >= 0 ? slash + 1 : 0;
        int end = path.lastIndexOf('.');
        if (end <= start) end = path.length();
        return path.substring(start, end);
    }

    private void addInstalledProfileTitles(List<String> names, List<ConfigObject> installed) {
        if (installed == null) return;
        for (ConfigObject obj : installed) {
            if (obj != null && obj.getTitle() != null && !names.contains(obj.getTitle())) {
                names.add(obj.getTitle());
            }
        }
    }

    private interface BundleProfileFinder {
        ConfigObject find(String name);
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

    private void saveImportedProfile(Slic3rConfigWrapper imported) throws Exception {
        if (imported.printConfigs.isEmpty() && imported.filamentConfigs.isEmpty() && imported.printerConfigs.isEmpty()) {
            ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage(R.string.MenuFileImportProfilesFailedEmpty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            return;
        }

        ensureImportedDefaults(imported);
        if (SliceBeam.CONFIG == null) {
            SliceBeam.getCurrentConfigFile().delete();
            SliceBeam.CONFIG = imported;
            try (FileOutputStream fos = new FileOutputStream(SliceBeam.getConfigFile())) {
                fos.write(imported.serialize().getBytes(StandardCharsets.UTF_8));
            }
        } else {
            mergeImportedProfiles(imported);
            SliceBeam.getCurrentConfigFile().delete();
            SliceBeam.saveConfig();
        }

        SliceBeam.clearLiveDiffs();
        ViewUtils.postOnMainThread(() -> {
            Toast.makeText(this, "Profile loaded.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(SetupActivity.this, MainActivity.class));
            finish();
        });
    }

    private void showProfileImportError(Exception e) {
        Log.e(TAG, "Failed to import setup profile", e);
        ViewUtils.postOnMainThread(() -> new BeamAlertDialogBuilder(this)
                .setTitle(R.string.MenuFileImportProfilesFailed)
                .setMessage(e.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void ensureImportedDefaults(Slic3rConfigWrapper cfg) {
        if (cfg.presets == null) {
            cfg.presets = new ConfigObject();
        }
        if (cfg.presets.get("printer") == null && !cfg.printerConfigs.isEmpty()) {
            cfg.presets.put("printer", cfg.printerConfigs.get(0).getTitle());
        }
        if (cfg.presets.get("print") == null && !cfg.printConfigs.isEmpty()) {
            cfg.presets.put("print", cfg.printConfigs.get(0).getTitle());
        }
        if (cfg.presets.get("filament") == null && !cfg.filamentConfigs.isEmpty()) {
            String pick = cfg.filamentConfigs.get(0).getTitle();
            for (ConfigObject fil : cfg.filamentConfigs) {
                String t = fil.getTitle();
                if (t != null && t.contains("PLA")) {
                    pick = t;
                    break;
                }
            }
            cfg.presets.put("filament", pick);
        }
    }

    private void mergeImportedProfiles(Slic3rConfigWrapper imported) {
        Slic3rConfigWrapper existing = SliceBeam.CONFIG;
        for (ConfigObject p : imported.printerConfigs) {
            if (existing.findPrinter(p.getTitle()) == null) existing.printerConfigs.add(p);
        }
        for (ConfigObject f : imported.filamentConfigs) {
            if (existing.findFilament(f.getTitle()) == null) existing.filamentConfigs.add(f);
        }
        for (ConfigObject pr : imported.printConfigs) {
            boolean dup = false;
            for (ConfigObject ex : existing.printConfigs) {
                if (ex.getTitle().equals(pr.getTitle())
                        && java.util.Objects.equals(ex.get("compatible_printers"), pr.get("compatible_printers"))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) existing.printConfigs.add(pr);
        }

        if (existing.presets == null) {
            existing.presets = new ConfigObject();
        }
        String printer = imported.presets != null ? imported.presets.get("printer") : null;
        String print = imported.presets != null ? imported.presets.get("print") : null;
        String filament = imported.presets != null ? imported.presets.get("filament") : null;
        if (printer != null && existing.findPrinter(printer) != null) existing.presets.put("printer", printer);
        if (print != null && existing.findPrint(print) != null) existing.presets.put("print", print);
        if (filament != null && existing.findFilament(filament) != null) existing.presets.put("filament", filament);
    }

    private void loadLocalProfiles(ProfilesRepo repo, AtomicInteger loadedCount, AtomicInteger totalNeeded, Runnable onLoadedAll) {
        IOUtils.IO_POOL.execute(() -> {
            List<Slic3rConfigWrapper> vendorProfiles = new ArrayList<>();
            try {
                String[] files = getAssets().list(ORCA_ASSET_DIR);
                if (files != null) {
                    Arrays.sort(files);
                    for (String file : files) {
                        if (!file.endsWith(".ini")) {
                            continue;
                        }
                        try (InputStream in = getAssets().open(ORCA_ASSET_DIR + "/" + file)) {
                            Slic3rConfigWrapper cfg = new Slic3rConfigWrapper(in);
                            vendorProfiles.add(cfg);
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to load local Orca profile asset " + file, e);
                        }
                    }
                }

                ViewUtils.postOnMainThread(() -> {
                    profilesMap.put(repo, vendorProfiles);
                    loadedCount.incrementAndGet();
                    if (loadedCount.get() == totalNeeded.get()) {
                        ViewUtils.postOnMainThread(onLoadedAll);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to load local OrcaSlicer profile assets", e);
                isLoading = false;
                ViewUtils.postOnMainThread(() -> {
                    Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show();
                    fakeScroll(-1);
                    pager.setUserInputEnabled(true);
                });
            }
        });
    }

    private void invalidateTitleY() {
        float sc = ViewUtils.lerp(1, 22 / 32f, backgroundProgress);
        title.setPivotX(title.getWidth() / 2f);
        title.setPivotY(0);
        title.setScaleX(sc);
        title.setScaleY(sc);
        int color = ColorUtils.blendARGB(ThemesRepo.getColor(R.attr.textColorOnAccent), ThemesRepo.getColor(android.R.attr.colorAccent), cloudProfile ? 0f : backgroundProgress - boostyProgress);
        title.setTextColor(color);
        title.setTranslationY(ViewUtils.lerp(titleY, (ViewUtils.dp(52) - title.getHeight() * title.getScaleY()) / 2f, backgroundProgress));
    }

    private void updateStartupBackgroundVisibility() {
        if (startupBackground == null) return;
        float alpha = 1f - backgroundProgress;
        if (boostyOnly || cloudProfile) alpha = 0f;
        alpha = Math.max(0f, Math.min(1f, alpha));
        startupBackground.setAlpha(alpha);
        startupBackground.setVisibility(alpha <= 0.01f ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SliceBeam.EVENT_BUS.unregisterListener(this);
    }

    @EventHandler(runOnMainThread = true)
    public void onDataUpdated(BeamServerDataUpdatedEvent e) {
        if (!about && !boostyOnly && !cloudProfile) {
            boolean wasBoosty = BOOSTY_INDEX != -1;
            if (wasBoosty != BeamServerData.isBoostyAvailable()) {
                setItems();
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @EventHandler(runOnMainThread = true)
    public void onCloudSyncFinished(CloudSyncFinishedEvent e) {
        if (cloudProfile && Prefs.getCloudAPIToken() != null && cloudImport) {
            finish();
        }
        if (!about && !boostyOnly && !cloudProfile) {
            if (Prefs.getCloudAPIToken() != null) {
                limitProfileFragmentCount = false;
                pager.getAdapter().notifyDataSetChanged();
                pager.setCurrentItem(pager.getAdapter().getItemCount() - 1);
            }
        }
    }

    @EventHandler(runOnMainThread = true)
    public void onCloudAuthStateUpdated(CloudLoginStateUpdatedEvent e) {
        if (cloudProfile) {
            cloudItem.bindLoginButton(true);
            cloudItem.bindFeatures();
        }
    }

    @EventHandler(runOnMainThread = true)
    public void onCloudFeaturesUpdated(CloudFeaturesUpdatedEvent e) {
        // cloud features update handled elsewhere
    }

    private void setItems() {
        if (cloudProfile){
            adapter.setItems(Collections.singletonList(cloudItem = new CloudProfileItem()));
        } else if (boostyOnly) {
            adapter.setItems(Collections.singletonList(new BoostyItem()));
        } else if (about) {
            adapter.setItems(Collections.singletonList(new AboutItem()));
        } else {
            List<SimpleRecyclerItem> items = new ArrayList<>(Arrays.asList(
                    new IntroItem(),
                    new PresetChoiceItem(),
                    profilesItem = new ProfilesItem(),
                    filamentsItem = new FilamentsItem()));

            if (BeamServerData.isBoostyAvailable()) {
                BOOSTY_INDEX = items.size();
                items.add(new BoostyItem());
            } else {
                BOOSTY_INDEX = -1;
            }

            items.add(new FinishItem());
            adapter.setItems(items);
        }
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() == PROFILES_INDEX + 1 && profilesItem != null && profilesItem.useCustomProfile) {
            pager.setCurrentItem(1, true);
        } else if (pager.getCurrentItem() > 0) {
            pager.setCurrentItem(pager.getCurrentItem() - 1, true);
        } else {
            super.onBackPressed();
        }
    }

    private void scrollToNext() {
        fakeScroll(1);
    }

    private void fakeScroll(float to) {
        if (fakeScroller != null) return;

        AtomicReference<Float> lastValue = new AtomicReference<>(0f);
        fakeScroller = new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 256f)
                .setSpring(new SpringForce(1f)
                        .setStiffness(600f)
                        .setDampingRatio(1f))
                .addUpdateListener((animation, value, velocity) -> {
                    float delta = value - lastValue.getAndSet(value);
                    pager.fakeDragBy(delta * pager.getWidth() * -to);
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    pager.endFakeDrag();
                    fakeScroller = null;
                });
        pager.beginFakeDrag();
        fakeScroller.start();
    }

    private final class CloudProfileItem extends SimpleRecyclerItem<View> {
        private FrameLayout buttonView;
        private TextView buttonText;
        private ProgressBar buttonProgress;
        private FadeRecyclerView recyclerView;

        @Override
        public View onCreateView(Context ctx) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(0, ViewUtils.dp(42), 0, 0);

            TextView title = new TextView(ctx);
            title.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            title.setText(R.string.SettingsCloudManageDescription);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setGravity(Gravity.CENTER);
            title.setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0);
            ll.addView(title);

            FrameLayout fl = new FrameLayout(ctx);
            recyclerView = new FadeRecyclerView(ctx);
            recyclerView.setBitmapMode();
            recyclerView.setAdapter(adapter = new SimpleRecyclerAdapter());
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            fl.addView(recyclerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            bindFeatures();

            ll.addView(fl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            TextView tosButton = new TextView(ctx);
            SpannableStringBuilder sb = SpannableStringBuilder.valueOf(ctx.getString(R.string.SettingsCloudManageTermsOfService)).append(" ");
            Drawable dr = ContextCompat.getDrawable(ctx, R.drawable.external_link_outline_24);
            int size = ViewUtils.dp(16);
            dr.setBounds(0, 0, size, size);
            sb.append("d", new TextColorImageSpan(dr, ViewUtils.dp(2f)), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            tosButton.setText(sb);
            tosButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            tosButton.setTextColor(Color.WHITE);
            tosButton.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            tosButton.setGravity(Gravity.CENTER);
            tosButton.setPadding(ViewUtils.dp(12), ViewUtils.dp(8), ViewUtils.dp(12), ViewUtils.dp(8));
            tosButton.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 16));
            tosButton.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://beam3d.ru/slicebeam_cloud_tos.html"))));
            ll.addView(tosButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(8);
            }});

            buttonView = new FrameLayout(ctx);
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));

            buttonText = new TextView(ctx);
            buttonText.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonText.setGravity(Gravity.CENTER);
            buttonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.addView(buttonText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            buttonProgress = new ProgressBar(ctx);
            buttonProgress.setIndeterminateTintList(ColorStateList.valueOf(ThemesRepo.getColor(R.attr.textColorOnAccent)));
            buttonView.addView(buttonProgress, new FrameLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28), Gravity.CENTER));

            bindLoginButton(false);

            ll.addView(buttonView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});

            ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return ll;
        }

        private void bindFeatures() {
            List<SimpleRecyclerItem> items = new ArrayList<>();
            if (CloudController.getUserFeatures() != null) {
                for (CloudAPI.SubscriptionLevel lvl : CloudController.getUserFeatures().levels) {
                    items.add(new CloudSubscriptionLevel(lvl));
                }
            }
            adapter.setItems(items);
        }

        private void bindLoginButton(boolean animate) {
            boolean loggedIn = Prefs.getCloudAPIToken() != null;
            boolean loading = !loggedIn && CloudController.isLoggingIn();
            boolean wasLoading = buttonProgress.getTag() != null;
            if (animate) {
                if (wasLoading != loading) {
                    buttonProgress.setTag(loading ? 1 : null);

                    buttonProgress.animate().cancel();
                    buttonProgress.animate().scaleX(loading ? 1f : 0.4f).scaleY(loading ? 1f : 0.4f).alpha(loading ? 1f : 0f).setDuration(150).setInterpolator(ViewUtils.CUBIC_INTERPOLATOR).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (loading) {
                                buttonProgress.setVisibility(View.VISIBLE);
                                buttonProgress.setAlpha(0f);
                                buttonProgress.setScaleX(0.4f);
                                buttonProgress.setScaleY(0.4f);
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!loading) {
                                buttonProgress.setVisibility(View.GONE);
                            }
                        }
                    }).start();

                    buttonText.animate().cancel();
                    buttonText.animate().scaleX(!loading ? 1f : 0.4f).scaleY(!loading ? 1f : 0.4f).alpha(!loading ? 1f : 0f).setDuration(150).setInterpolator(ViewUtils.CUBIC_INTERPOLATOR).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (!loading) {
                                buttonText.setVisibility(View.VISIBLE);
                                buttonText.setAlpha(0f);
                                buttonText.setScaleX(0.4f);
                                buttonText.setScaleY(0.4f);
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (loading) {
                                buttonText.setVisibility(View.GONE);
                            }
                        }
                    }).start();
                }
            } else {
                buttonProgress.setTag(loading ? 1 : null);
                buttonProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
                buttonText.setVisibility(loading ? View.GONE : View.VISIBLE);
            }
            buttonText.setText(loggedIn ? R.string.SettingsCloudManageButtonManage : R.string.SettingsCloudManageButtonLogIn);
            buttonView.setOnClickListener(v-> {
                if (loading) {
                    new BeamAlertDialogBuilder(v.getContext())
                            .setTitle(R.string.SettingsCloudManageButtonLogInCancelTitle)
                            .setMessage(R.string.SettingsCloudManageButtonLogInCancel)
                            .setNegativeButton(R.string.No, null)
                            .setPositiveButton(R.string.Yes, (dialog, which) -> CloudController.cancelLogin())
                            .show();
                } else if (Prefs.getCloudAPIToken() != null) {
                    new CloudManageBottomSheet(v.getContext()).show();
                } else {
                    CloudController.beginLogin();
                }
            });
        }
    }

    private final static class CloudSubscriptionLevel extends SimpleRecyclerItem<CloudSubscriptionLevel.LevelHolderView> {
        private CloudAPI.SubscriptionLevel level;

        private CloudSubscriptionLevel(CloudAPI.SubscriptionLevel level) {
            this.level = level;
        }

        @Override
        public LevelHolderView onCreateView(Context ctx) {
            return new LevelHolderView(ctx);
        }

        @Override
        public void onBindView(LevelHolderView view) {
            view.bind(this);
        }

        public final static class LevelHolderView extends LinearLayout implements IThemeView {
            private ImageView icon;
            private TextView title;
            private TextView price;

            private RecyclerView featuresLayout;
            private SimpleRecyclerAdapter featuresAdapter;

            public LevelHolderView(@NonNull Context context) {
                super(context);

                setOrientation(VERTICAL);
                setPadding(0, ViewUtils.dp(16), 0, ViewUtils.dp(8));

                LinearLayout inner = new LinearLayout(context);
                inner.setOrientation(HORIZONTAL);
                inner.setGravity(Gravity.CENTER_VERTICAL);
                inner.setPadding(ViewUtils.dp(28), 0, ViewUtils.dp(28), 0);
                addView(inner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    bottomMargin = ViewUtils.dp(8);
                }});

                icon = new ImageView(context);
                inner.addView(icon, new LayoutParams(ViewUtils.dp(26), ViewUtils.dp(26)));

                title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                inner.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{
                    leftMargin = ViewUtils.dp(12);
                }});

                price = new TextView(context);
                price.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                price.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                inner.addView(price);

                featuresLayout = new RecyclerView(context) {
                    @Override
                    public boolean dispatchTouchEvent(MotionEvent ev) {
                        return false;
                    }

                    @Override
                    protected boolean dispatchHoverEvent(MotionEvent event) {
                        return false;
                    }
                };
                featuresLayout.setLayoutManager(new LinearLayoutManager(context));
                featuresLayout.setAdapter(featuresAdapter = new SimpleRecyclerAdapter());
                addView(featuresLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    topMargin = ViewUtils.dp(3);
                    leftMargin = rightMargin = ViewUtils.dp(16);
                    bottomMargin = ViewUtils.dp(8);
                }});

                setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    leftMargin = rightMargin = ViewUtils.dp(12);
                    topMargin = ViewUtils.dp(12);
                }});
                onApplyTheme();
            }

            public void bind(CloudSubscriptionLevel item) {
                CloudAPI.SubscriptionLevel lvl = item.level;
                title.setText(lvl.title);
                price.setText(lvl.price);
                if (lvl.level <= 0) {
                    icon.setImageResource(R.drawable.zero_ruble_outline_28);
                    price.setText(R.string.SettingsCloudManageFree);
                } else if (lvl.level == 1) {
                    icon.setImageResource(R.drawable.stars_outline_28);
                } else {
                    icon.setImageResource(R.drawable.cloud_plus_outline_28);
                }

                List<SimpleRecyclerItem> items = new ArrayList<>();
                CloudAPI.UserFeatures features = CloudController.getUserFeatures();
                CloudAPI.UserInfo info = CloudController.getUserInfo();
                Context ctx = getContext();
                if (!BuildConfig.IS_GOOGLE_PLAY && features.earlyAccessLevel != -1 && lvl.level >= features.earlyAccessLevel) {
                    items.add(new PreferenceItem()
                            .setForceDark(true)
                            .setPaddings(ViewUtils.dp(8))
                            .setIcon(R.drawable.clock_circle_dashed_outline_24)
                            .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureEarlyAccess))
                            .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureEarlyAccessDescription)));
                }
                if (features.syncRequiredLevel != -1 && lvl.level >= features.syncRequiredLevel) {
                    items.add(new PreferenceItem()
                            .setForceDark(true)
                            .setPaddings(ViewUtils.dp(8))
                            .setIcon(R.drawable.sync_outline_28)
                            .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureCloudSync))
                            .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureCloudSyncDescription)));
                }
                if (features.aiGeneratorRequiredLevel != -1 && lvl.level >= features.aiGeneratorRequiredLevel) {
                    items.add(new PreferenceItem()
                            .setForceDark(true)
                            .setPaddings(ViewUtils.dp(8))
                            .setIcon(R.drawable.brain_outline_28)
                            .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureAIGenerator))
                            .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureAIGeneratorDescription, features.aiGeneratorModelsPerMonth)));
                }
                if (lvl.level > 0) {
                    items.add(new PreferenceItem()
                            .setForceDark(true)
                            .setPaddings(ViewUtils.dp(8))
                            .setIcon(R.drawable.box_heart_outline_28)
                            .setTitle(ctx.getString(R.string.SettingsCloudManageFeatureFreeForAll))
                            .setSubtitle(ctx.getString(R.string.SettingsCloudManageFeatureFreeForAllDescription)));
                }
                featuresAdapter.setItems(items);
                featuresLayout.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

                boolean subscribed = lvl.level > 0 && info != null && lvl.level == info.currentLevel;
                boolean allowSubscribe = lvl.level > 0 && (info == null || lvl.level > info.currentLevel);
                if (subscribed) {
                    price.setText(R.string.SettingsCloudManageSubscribed);
                }
                price.setVisibility(allowSubscribe || subscribed ? View.VISIBLE : View.GONE);
                setOnClickListener(v -> {
                    if (subscribed) {
                        v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lvl.manageUrl)));
                    } else {
                        new BeamAlertDialogBuilder(getContext())
                                .setTitle(lvl.title)
                                .setMessage(R.string.SettingsCloudManageLevelRedirectMessage)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lvl.subscribeOrUpgradeUrl))))
                                .setNegativeButton(R.string.SettingsCloudManageLevelRedirectAlreadySubscribed, (dialog, which) -> v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(features.alreadySubscribedInfoUrl))))
                                .show();
                    }
                });
                setClickable(allowSubscribe || subscribed);
                onApplyTheme();
            }

            @Override
            public void onApplyTheme() {
                int accent = ThemesRepo.getColor(android.R.attr.colorAccent);
                if (ColorUtils.calculateLuminance(accent) >= 0.6f) {
                    accent = ColorUtils.blendARGB(accent, Color.BLACK, 0.075f);
                }
                boolean tooLight = ColorUtils.calculateLuminance(accent) >= 0.6f;
                title.setTextColor(0xffffffff);
                price.setTextColor(0xffffffff);
                icon.setImageTintList(ColorStateList.valueOf(0xffffffff));
                featuresLayout.setBackground(ViewUtils.createRipple(0, tooLight ? 0x33ffffff : 0x21ffffff, 24));
                setBackground(ViewUtils.createRipple(0x21000000, ColorUtils.blendARGB(0xffffffff, accent, tooLight ? 0.9f : 0.75f), 32));
            }
        }
    }

    private final class AboutItem extends SimpleRecyclerItem<View> {

        @Override
        public View onCreateView(Context ctx) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setGravity(Gravity.BOTTOM);

            String versionStr = null;
            PackageManager pm = ctx.getPackageManager();
            try {
                PackageInfo info = pm.getPackageInfo(ctx.getPackageName(), 0);
                versionStr = info.versionName;
            } catch (PackageManager.NameNotFoundException ignored) {}

            TextView subtitle = new TextView(ctx);
            subtitle.setText(ctx.getString(R.string.SettingsAboutVersion, versionStr));
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            ll.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                bottomMargin = ViewUtils.dp(12);
            }});

            TextView buttonView = new TextView(ctx);
            buttonView.setText(android.R.string.ok);
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
            buttonView.setOnClickListener(v-> finish());
            ll.addView(buttonView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});
            ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return ll;
        }
    }

    private final class IntroItem extends SimpleRecyclerItem<View> {
        private TextView buttonView;

        @Override
        public View onCreateView(Context ctx) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setGravity(Gravity.BOTTOM);

            TextView versionLabel = new TextView(ctx);
            versionLabel.setText("OrcaSlicer v2.4 Beta");
            versionLabel.setGravity(Gravity.CENTER);
            versionLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            versionLabel.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            versionLabel.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            ll.addView(versionLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                bottomMargin = ViewUtils.dp(16);
            }});

            buttonView = new TextView(ctx);
            buttonView.setText(R.string.IntroStart);
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
            buttonView.setOnClickListener(v-> scrollToNext());
            ll.addView(buttonView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});
            ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return ll;
        }
    }

    private final class PresetChoiceItem extends SimpleRecyclerItem<View> {
        private TextView buttonView;
        private LinearLayout builtInButton;
        private LinearLayout loadProfileButton;
        private BeamSwitch builtInSwitch;
        private TextView browseText;

        @Override
        public View onCreateView(Context ctx) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setGravity(Gravity.TOP);
            ll.setPadding(0, ViewUtils.dp(42), 0, 0);

            TextView hint = new TextView(ctx);
            hint.setText("Select preset source:");
            hint.setGravity(Gravity.CENTER);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            hint.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            hint.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            ll.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                bottomMargin = ViewUtils.dp(24);
            }});

            builtInButton = new LinearLayout(ctx);
            builtInButton.setOrientation(LinearLayout.HORIZONTAL);
            builtInButton.setGravity(Gravity.CENTER_VERTICAL);
            builtInButton.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16));
            
            TextView builtInText = new TextView(ctx);
            builtInText.setText("Built-in Presets");
            builtInText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            builtInText.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            builtInText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            builtInButton.addView(builtInText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            builtInSwitch = new BeamSwitch(ctx);
            builtInSwitch.setClickable(false);
            builtInButton.addView(builtInSwitch);

            ll.addView(builtInButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(12);
            }});

            loadProfileButton = new LinearLayout(ctx);
            loadProfileButton.setOrientation(LinearLayout.HORIZONTAL);
            loadProfileButton.setGravity(Gravity.CENTER_VERTICAL);
            loadProfileButton.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16));

            LinearLayout loadTextColumn = new LinearLayout(ctx);
            loadTextColumn.setOrientation(LinearLayout.VERTICAL);
            TextView loadTitle = new TextView(ctx);
            loadTitle.setText("Load Profile");
            loadTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            loadTitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            loadTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            loadTextColumn.addView(loadTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView loadSubtitle = new TextView(ctx);
            loadSubtitle.setText("Choose an .orca_printer profile file");
            loadSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            loadSubtitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            loadTextColumn.addView(loadSubtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            loadProfileButton.addView(loadTextColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            browseText = new TextView(ctx);
            browseText.setText("Browse");
            browseText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            browseText.setGravity(Gravity.CENTER);
            browseText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            browseText.setTextColor(ThemesRepo.getColor(android.R.attr.colorAccent));
            loadProfileButton.addView(browseText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = ViewUtils.dp(12);
            }});

            ll.addView(loadProfileButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(24);
            }});

            builtInButton.setOnClickListener(v -> selectBuiltIn());
            loadProfileButton.setOnClickListener(v -> openProfilePicker());

            ll.addView(new View(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            buttonView = new TextView(ctx);
            buttonView.setText(R.string.IntroNext);
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setOnClickListener(v -> scrollToNext());
            ll.addView(buttonView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});

            ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return ll;
        }

        private void selectBuiltIn() {
            profilesItem.useCustomProfile = false;
            builtInSwitch.setChecked(true);
            if (profilesItem.adapter != null) {
                profilesItem.adapter.setItems(profilesItem.getItems());
            }
        }

        @Override
        public void onBindView(View view) {
            int accent = ThemesRepo.getColor(android.R.attr.colorAccent);
            int controlHighlight = ThemesRepo.getColor(android.R.attr.colorControlHighlight);
            
            builtInButton.setBackground(ViewUtils.createRipple(controlHighlight, 16));
            loadProfileButton.setBackground(ViewUtils.createRipple(controlHighlight, 16));
            
            buttonView.setBackground(ViewUtils.createRipple(controlHighlight, accent, 16));
            browseText.setTextColor(accent);
            builtInSwitch.onApplyTheme();
            builtInSwitch.setChecked(true);
        }
    }

    private final class ProfilesItem extends SimpleRecyclerItem<View> {
        private ProgressBar progressBar;
        private FrameLayout loadedLayout;
        private FadeRecyclerView recyclerView;
        private final SimpleRecyclerAdapter adapter = new SimpleRecyclerAdapter();
        private TextView buttonView;

        private boolean useCustomProfile;

        @Override
        public View onCreateView(Context ctx) {
            FrameLayout fl = new FrameLayout(ctx);
            progressBar = new ProgressBar(ctx);
            fl.addView(progressBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            loadedLayout = new FrameLayout(ctx);
            recyclerView = new FadeRecyclerView(ctx);
            recyclerView.setAdapter(adapter);
            loadedLayout.addView(recyclerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                topMargin = ViewUtils.dp(52);
                bottomMargin = ViewUtils.dp(72);
            }});

            buttonView = new TextView(ctx);
            buttonView.setText(R.string.IntroNext);
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setOnClickListener(v-> {
                boolean noChecked = enabledPrinters.isEmpty();
                if (noChecked && !useCustomProfile) {
                    new BeamAlertDialogBuilder(SetupActivity.this)
                            .setTitle(R.string.IntroNoProfiles)
                            .setMessage(R.string.IntroNoProfilesDescription)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    limitProfileFragmentCount = false;
                    SetupActivity.this.adapter.notifyItemRangeInserted(PROFILES_INDEX + 1, SetupActivity.this.adapter.getItemCount() - PROFILES_INDEX - 1);
                    scrollToNext();
                }
            });
            loadedLayout.addView(buttonView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52), Gravity.BOTTOM) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});

            loadedLayout.setAlpha(0f);
            fl.addView(loadedLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            fl.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return fl;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onBindView(View view) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.colorAccent)));
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
            if (adapter.getItemCount() == 0 && !repos.isEmpty()) {
                adapter.setItems(getItems());
            } else if (useCustomProfile) {
                adapter.setItems(getItems());
            } else {
                adapter.notifyDataSetChanged();
            }

            if (useCustomProfile) {
                progressBar.setAlpha(0f);
                loadedLayout.setAlpha(1f);
            } else {
                if (isProfilesLoaded) {
                    progressBar.setVisibility(View.GONE);
                    loadedLayout.setAlpha(1f);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setAlpha(1f);
                    progressBar.setScaleX(1f);
                    progressBar.setScaleY(1f);
                    loadedLayout.setAlpha(0f);
                }
            }
        }

        private List<SimpleRecyclerItem> getItems() {
            if (useCustomProfile) {
                List<SimpleRecyclerItem> items = new ArrayList<>();
                items.add(new BigHeaderItem(getString(R.string.IntroCustomProfileHeader)));
                items.add(new ProfileItem());
                return items;
            }

            List<Slic3rConfigWrapper> vendors = new ArrayList<>();
            for (List<Slic3rConfigWrapper> w : profilesMap.values()) {
                vendors.addAll(w);
            }
            Collections.sort(vendors, (o1, o2) -> o1.vendor.values.get("name").compareToIgnoreCase(o2.vendor.values.get("name")));

            List<SimpleRecyclerItem> items = new ArrayList<>();
            for (Slic3rConfigWrapper w : vendors) {
                items.add(new BigHeaderItem(w.vendor.values.get("name")));

                for (ConfigObject printer : w.printerModels) {
                    if (printer.getTitle().startsWith("*") && printer.getTitle().endsWith("*")) continue;

                    items.add(new ProfileItem(printer, TYPE_PRINTER));
                }
            }
            return items;
        }

        public void onProfilesLoaded() {
            adapter.setItems(getItems());
            new SpringAnimation(new FloatValueHolder(0))
                    .setMinimumVisibleChange(1 / 256f)
                    .setSpring(new SpringForce(1f)
                            .setStiffness(1000f)
                            .setDampingRatio(1f))
                    .addUpdateListener((animation, value, velocity) -> {
                        progressBar.setAlpha(1f - value);
                        progressBar.setScaleX(1f - value * 0.5f);
                        progressBar.setScaleY(1f - value * 0.5f);

                        loadedLayout.setAlpha(value);
                        loadedLayout.setScaleX(0.5f + value * 0.5f);
                        loadedLayout.setScaleY(0.5f + value * 0.5f);
                    })
                    .addEndListener((animation, canceled, value, velocity) -> progressBar.setVisibility(View.GONE))
                    .start();
        }
    }

    private final class FilamentsItem extends SimpleRecyclerItem<View> {
        private FadeRecyclerView recyclerView;
        private final SimpleRecyclerAdapter adapter = new SimpleRecyclerAdapter();
        private TextView buttonView;
        private final List<ConfigObject> currentFilaments = new ArrayList<>();
        private String builtFor;

        @Override
        public View onCreateView(Context ctx) {
            FrameLayout fl = new FrameLayout(ctx);
            recyclerView = new FadeRecyclerView(ctx);
            recyclerView.setAdapter(adapter);
            fl.addView(recyclerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                topMargin = ViewUtils.dp(52);
                bottomMargin = ViewUtils.dp(72);
            }});

            buttonView = new TextView(ctx);
            buttonView.setText(R.string.IntroNext);
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setOnClickListener(v -> scrollToNext());
            fl.addView(buttonView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52), Gravity.BOTTOM) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});

            fl.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return fl;
        }

        @Override
        public void onBindView(View view) {
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
            refreshIfNeeded();
        }

        /** Rebuild the filament list when the printer selection changed since the last build. */
        private void refreshIfNeeded() {
            if (recyclerView == null) return;

            StringBuilder kb = new StringBuilder(profilesItem != null && profilesItem.useCustomProfile ? "custom\n" : "preset\n");
            for (ConfigObject p : enabledPrinters) kb.append(p.getTitle()).append('\n');
            String key = kb.toString();
            if (key.equals(builtFor) && adapter.getItemCount() > 0) return;
            builtFor = key;

            List<SimpleRecyclerItem> items = new ArrayList<>();
            items.add(new BigHeaderItem(getString(R.string.IntroFilamentsHeader)));
            currentFilaments.clear();

            if (profilesItem == null || !profilesItem.useCustomProfile) {
                List<Slic3rConfigWrapper> vendors = new ArrayList<>();
                for (List<Slic3rConfigWrapper> w : profilesMap.values()) {
                    vendors.addAll(w);
                }
                Collections.sort(vendors, (o1, o2) -> o1.vendor.values.get("name").compareToIgnoreCase(o2.vendor.values.get("name")));

                for (Slic3rConfigWrapper w : vendors) {
                    boolean hasEnabledPrinter = false;
                    for (ConfigObject pm : w.printerModels) {
                        if (enabledPrinters.contains(pm)) {
                            hasEnabledPrinter = true;
                            break;
                        }
                    }
                    if (!hasEnabledPrinter) continue;

                    List<SimpleRecyclerItem> rows = new ArrayList<>();
                    for (ConfigObject fil : w.filamentConfigs) {
                        String t = fil.getTitle();
                        if (t == null || (t.startsWith("*") && t.endsWith("*"))) continue;
                        rows.add(new ProfileItem(fil, TYPE_FILAMENT));
                        currentFilaments.add(fil);
                    }
                    if (rows.isEmpty()) continue;
                    items.add(new BigHeaderItem(w.vendor.values.get("name")));
                    items.addAll(rows);
                }
            }

            if (currentFilaments.isEmpty()) {
                items.add(new BigHeaderItem(getString(R.string.IntroFilamentsNone)));
            } else {
                items.add(1, new SelectAllItem());
            }

            // Pre-check the default materials of the selected printer models.
            enabledFilaments.clear();
            for (ConfigObject printerModel : enabledPrinters) {
                String dm = printerModel.get("default_materials");
                if (TextUtils.isEmpty(dm)) continue;
                for (String mat : dm.split(";")) {
                    String matTitle = mat.trim();
                    for (ConfigObject fil : currentFilaments) {
                        if (matTitle.equals(fil.getTitle()) && !enabledFilaments.contains(fil)) {
                            enabledFilaments.add(fil);
                        }
                    }
                }
            }

            adapter.setItems(items);
        }

        private final class SelectAllItem extends SimpleRecyclerItem<LinearLayout> {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public LinearLayout onCreateView(Context ctx) {
                LinearLayout ll = new LinearLayout(ctx);
                ll.setOrientation(LinearLayout.HORIZONTAL);

                TextView selectAll = makeTextButton(ctx, R.string.IntroFilamentsSelectAll, v -> {
                    for (ConfigObject fil : currentFilaments) {
                        if (!enabledFilaments.contains(fil)) enabledFilaments.add(fil);
                    }
                    adapter.notifyDataSetChanged();
                });
                TextView deselectAll = makeTextButton(ctx, R.string.IntroFilamentsDeselectAll, v -> {
                    enabledFilaments.removeAll(currentFilaments);
                    adapter.notifyDataSetChanged();
                });
                ll.addView(selectAll, new LinearLayout.LayoutParams(0, ViewUtils.dp(40), 1f));
                ll.addView(deselectAll, new LinearLayout.LayoutParams(0, ViewUtils.dp(40), 1f) {{
                    leftMargin = ViewUtils.dp(8);
                }});

                ll.setPadding(ViewUtils.dp(16), 0, ViewUtils.dp(16), ViewUtils.dp(8));
                ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return ll;
            }

            @Override
            public void onBindView(LinearLayout view) {}

            private TextView makeTextButton(Context ctx, int textRes, View.OnClickListener listener) {
                TextView tv = new TextView(ctx);
                tv.setText(textRes);
                tv.setTextColor(ThemesRepo.getColor(android.R.attr.colorAccent));
                tv.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                tv.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorAccent), 0x18), 12));
                tv.setOnClickListener(listener);
                return tv;
            }
        }
    }

    private final class BoostyItem extends SimpleRecyclerItem<View> {

        @Override
        public View onCreateView(Context ctx) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(0, ViewUtils.dp(42), 0, 0);

            TextView title = new TextView(ctx);
            title.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            title.setText(R.string.IntroBoostyTitle);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0);
            ll.addView(title);

            BoostySubsView subsView = new BoostySubsView(ctx);
            if (SliceBeam.SERVER_DATA != null) {
                List<String> list = new ArrayList<>(SliceBeam.SERVER_DATA.boostySubscribers);
                Collections.shuffle(list);
                subsView.setStrings(list);
            }
            ll.addView(subsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) {{
                bottomMargin = ViewUtils.dp(64);
            }});

            TextView subscribeButton = new TextView(ctx);
            SpannableStringBuilder sb = SpannableStringBuilder.valueOf(ctx.getString(R.string.IntroBoostySupport)).append(" ");
            Drawable dr = ContextCompat.getDrawable(ctx, R.drawable.external_link_outline_24);
            int size = ViewUtils.dp(16);
            dr.setBounds(0, 0, size, size);
            sb.append("d", new TextColorImageSpan(dr, ViewUtils.dp(2f)), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            subscribeButton.setText(sb);
            subscribeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            subscribeButton.setTextColor(Color.WHITE);
            subscribeButton.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            subscribeButton.setGravity(Gravity.CENTER);
            subscribeButton.setPadding(ViewUtils.dp(12), ViewUtils.dp(8), ViewUtils.dp(12), ViewUtils.dp(8));
            subscribeButton.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 16));
            subscribeButton.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://boosty.to/ytkab0bp"))));
            ll.addView(subscribeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(8);
            }});

            TextView buttonView = new TextView(ctx);
            if (boostyOnly) {
                buttonView.setText(android.R.string.ok);
            } else {
                buttonView.setText(R.string.IntroNext);
            }
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(R.attr.boostyColorTop), 16));
            buttonView.setOnClickListener(v-> {
                if (boostyOnly) {
                    finish();
                    return;
                }
                scrollToNext();
            });
            ll.addView(buttonView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});

            ll.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return ll;
        }
    }

    private final class FinishItem extends SimpleRecyclerItem<View> {
        private TextView buttonView;

        @Override
        public View onCreateView(Context ctx) {
            FrameLayout fl = new FrameLayout(ctx);
            TextView title = new TextView(ctx);
            title.setText(R.string.IntroConfigured);
            title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setGravity(Gravity.CENTER);
            fl.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            buttonView = new TextView(ctx);
            buttonView.setText(R.string.IntroFinish);
            buttonView.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            buttonView.setOnClickListener(v-> {
                Slic3rConfigWrapper cfg = new Slic3rConfigWrapper();
                if (profilesItem.useCustomProfile) {
                    ConfigObject custom = ConfigObject.createCustomPrinterProfile();
                    custom.profileListType = ConfigObject.PROFILE_LIST_PRINTER;
                    cfg.printerConfigs.add(custom);

                    ConfigObject genericFilament = ConfigObject.createCustomFilamentProfile();
                    cfg.filamentConfigs.add(genericFilament);

                    ConfigObject genericPrint = new ConfigObject(getString(R.string.IntroCustomProfileName));
                    genericPrint.profileListType = ConfigObject.PROFILE_LIST_PRINT;
                    cfg.printConfigs.add(genericPrint);

                    cfg.presets = new ConfigObject();
                    cfg.presets.put("printer", custom.getTitle());
                    cfg.presets.put("print", genericPrint.getTitle());
                    cfg.presets.put("filament", genericFilament.getTitle());
                } else {
                    for (ConfigObject printerModel : enabledPrinters) {
                        String model = printerModel.getTitle();
                        String[] variants = !TextUtils.isEmpty(printerModel.get("variants")) ? printerModel.get("variants").split(";") : new String[]{};
                        String[] materials = !TextUtils.isEmpty(printerModel.get("default_materials")) ? printerModel.get("default_materials").split(";") : new String[]{};

                        for (String variant : variants) {
                            variant = variant.trim();

                            for (List<Slic3rConfigWrapper> wrappers : profilesMap.values()) {
                                for (Slic3rConfigWrapper w : wrappers) {
                                    ConfigObject obj = w.findPrinterVariant(model, variant);
                                    if (obj != null) {
                                        cfg.printerConfigs.add(obj);

                                        Slic3rUtils.ConfigChecker checker = new Slic3rUtils.ConfigChecker(obj.serialize());
                                        String printerName = obj.getTitle();
                                        String nozzle = obj.get("printer_variant");
                                        if (nozzle == null) nozzle = Slic3rUtils.firstNozzleDiameter(obj.get("nozzle_diameter"));
                                        for (ConfigObject printConfig : w.printConfigs) {
                                            if (printConfig.getTitle().startsWith("*") && printConfig.getTitle().endsWith("*")) continue;
                                            if (!checker.checkCompatibility(printConfig.get("compatible_printers_condition"))) continue;
                                            // The bundled profiles carry no per-printer link, so only keep processes whose
                                            // nozzle (inferred from layer height + line width) matches this printer variant,
                                            // and tag each with the printer it belongs to so the app can filter per-printer.
                                            if (!Slic3rUtils.layerHeightFitsNozzle(printConfig.get("layer_height"), nozzle)
                                                    || !Slic3rUtils.lineWidthFitsNozzle(printConfig.get("line_width"), nozzle)) continue;
                                            ConfigObject tagged = new ConfigObject(printConfig);
                                            if (android.text.TextUtils.isEmpty(tagged.get("compatible_printers"))) {
                                                tagged.put("compatible_printers", printerName);
                                            }
                                            cfg.printConfigs.add(tagged);
                                        }
                                        checker.release();
                                    }
                                }
                            }
                        }

                        // Filaments are normally chosen on the wizard's filament page; this
                        // default_materials lookup only runs if that page was never built.
                        if (enabledFilaments.isEmpty()) {
                            for (String mat : materials) {
                                mat = mat.trim();

                                for (List<Slic3rConfigWrapper> wrappers : profilesMap.values()) {
                                    for (Slic3rConfigWrapper w : wrappers) {
                                        ConfigObject obj = w.findFilament(mat);
                                        if (obj != null) cfg.filamentConfigs.add(obj);
                                    }
                                }
                            }
                        }
                    }

                    for (ConfigObject fil : enabledFilaments) {
                        if (cfg.findFilament(fil.getTitle()) == null) {
                            cfg.filamentConfigs.add(fil);
                        }
                    }
                    cfg.presets = new ConfigObject();
                    if (!cfg.printerConfigs.isEmpty()) {
                        boolean foundDefault = false;
                        for (ConfigObject obj : cfg.printerConfigs) {
                            if (obj.getTitle().contains("0.4")) {
                                foundDefault = true;
                                cfg.presets.put("printer", obj.getTitle());
                                break;
                            }
                        }
                        if (!foundDefault && !cfg.printerConfigs.isEmpty()) {
                            cfg.presets.put("printer", cfg.printerConfigs.get(0).getTitle());
                        }
                    }

                    ConfigObject defPrinter = cfg.printerConfigs.isEmpty() ? null : cfg.findPrinter(cfg.presets.get("printer"));
                    if (defPrinter != null) {
                        Slic3rUtils.ConfigChecker checker = new Slic3rUtils.ConfigChecker(defPrinter.serialize());
                        if (defPrinter.get("default_print_profile") != null && cfg.findPrint(defPrinter.get("default_print_profile")) != null) {
                            cfg.presets.put("print", defPrinter.get("default_print_profile"));
                        } else {
                            if (!cfg.printConfigs.isEmpty()) {
                                boolean foundDefault = false;
                                for (ConfigObject obj : cfg.printConfigs) {
                                    if (obj.get("layer_height") != null && checker.checkCompatibility(obj.get("compatible_printers_condition")) && Float.parseFloat(obj.get("layer_height")) == 0.2f) {
                                        foundDefault = true;
                                        cfg.presets.put("print", obj.getTitle());
                                        break;
                                    }
                                }
                                if (!foundDefault && !cfg.printConfigs.isEmpty()) {
                                    cfg.presets.put("print", cfg.printConfigs.get(0).getTitle());
                                }
                            }
                        }
                        if (defPrinter.get("default_filament_profile") != null && cfg.findFilament(defPrinter.get("default_filament_profile")) != null) {
                            cfg.presets.put("filament", defPrinter.get("default_filament_profile"));
                        } else {
                            if (!cfg.filamentConfigs.isEmpty()) {
                                boolean foundDefault = false;
                                for (ConfigObject obj : cfg.filamentConfigs) {
                                    if (obj.getTitle().contains("Generic PLA") && checker.checkCompatibility(obj.get("compatible_printers_condition"))) { // TODO: Slic3rUtils.checkCompatibility(obj.get("compatible_prints_condition"), serialized)
                                        foundDefault = true;
                                        cfg.presets.put("filament", obj.getTitle());
                                        break;
                                    }
                                }
                                if (!foundDefault && !cfg.filamentConfigs.isEmpty()) {
                                    cfg.presets.put("filament", cfg.filamentConfigs.get(0).getTitle());
                                }
                            }
                        }
                        checker.release();
                    }
                }
                try {
                    if (SliceBeam.CONFIG == null) {
                        // First-time setup: adopt the freshly built config wholesale.
                        SliceBeam.getCurrentConfigFile().delete();
                        SliceBeam.CONFIG = cfg;
                        FileOutputStream fos = new FileOutputStream(SliceBeam.getConfigFile());
                        fos.write(cfg.serialize().getBytes(StandardCharsets.UTF_8));
                        fos.close();
                    } else if (Prefs.getCloudAPIToken() == null) {
                        // Re-running setup with an existing config (e.g. "Add printer"): merge the
                        // newly selected presets into what the user already has instead of wiping it.
                        Slic3rConfigWrapper existing = SliceBeam.CONFIG;
                        for (ConfigObject p : cfg.printerConfigs)
                            if (existing.findPrinter(p.getTitle()) == null) existing.printerConfigs.add(p);
                        for (ConfigObject f : cfg.filamentConfigs)
                            if (existing.findFilament(f.getTitle()) == null) existing.filamentConfigs.add(f);
                        // Processes are tagged per-printer (compatible_printers), so a same-named process
                        // for a different printer is NOT a duplicate — key on title + compatible_printers.
                        for (ConfigObject pr : cfg.printConfigs) {
                            boolean dup = false;
                            for (ConfigObject ex : existing.printConfigs) {
                                if (ex.getTitle().equals(pr.getTitle())
                                        && java.util.Objects.equals(ex.get("compatible_printers"), pr.get("compatible_printers"))) {
                                    dup = true;
                                    break;
                                }
                            }
                            if (!dup) existing.printConfigs.add(pr);
                        }

                        // Switch the active selection to the newly added printer and its defaults.
                        String np = cfg.presets != null ? cfg.presets.get("printer") : null;
                        if (np != null && existing.findPrinter(np) != null) {
                            existing.presets.put("printer", np);
                            String npr = cfg.presets.get("print");
                            if (npr != null && existing.findPrint(npr) != null) existing.presets.put("print", npr);
                            String nf = cfg.presets.get("filament");
                            if (nf != null && existing.findFilament(nf) != null) existing.presets.put("filament", nf);
                        }
                        SliceBeam.getCurrentConfigFile().delete();
                        SliceBeam.saveConfig();
                    }

                    // The active selection just changed — drop any stale unsaved edits.
                    SliceBeam.clearLiveDiffs();
                    startActivity(new Intent(SetupActivity.this, MainActivity.class));
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save config", e);
                }
            });
            fl.addView(buttonView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52), Gravity.BOTTOM) {{
                leftMargin = rightMargin = ViewUtils.dp(16);
                bottomMargin = ViewUtils.dp(16);
            }});
            fl.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return fl;
        }

        @Override
        public void onBindView(View view) {
            buttonView.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
            buttonView.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
        }
    }

    public enum AccentColors {
        DEFAULT(0xff1ac5a2),    // OrcaSlicer signature blue-green teal
        BLUE(0xff5492f5),
        LIGHT_BLUE(0xff6dd5fa),
        RED(0xffe94056),
        ORANGE(0xffff4b2c),
        YELLOW(0xfffdc831),
        PINK(0xfff2709b),
        PURPLE(0xff6e74e1);

        public final int color;

        AccentColors(int color) {
            this.color = color;
        }
    }

    private static String parseVendorVersion(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.contains(" = ")) continue;
            return line.substring(0, line.indexOf(' '));
        }
        return null;
    }

    private final static class ProfilesRepo extends SimpleRecyclerItem<ProfilesRepo.RepoHolderView> {
        private String url;
        private String name;
        private String description;
        private String indexUrl;
        private boolean checked;
        private boolean localAssets;

        @Override
        public RepoHolderView onCreateView(Context ctx) {
            return new RepoHolderView(ctx);
        }

        @Override
        public void onBindView(RepoHolderView view) {
            view.bind(this);
        }

        public final static class RepoHolderView extends LinearLayout implements IThemeView {
            private TextView title;
            private TextView subtitle;
            private BeamSwitch mSwitch;

            public RepoHolderView(@NonNull Context context) {
                super(context);

                setOrientation(HORIZONTAL);
                setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout inner = new LinearLayout(context);
                inner.setOrientation(VERTICAL);
                title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                inner.addView(title);

                subtitle = new TextView(context);
                subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                inner.addView(subtitle);

                addView(inner, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                mSwitch = new BeamSwitch(context);
                addView(mSwitch);

                setPadding(ViewUtils.dp(21), ViewUtils.dp(16), ViewUtils.dp(21), ViewUtils.dp(16));
                setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    leftMargin = rightMargin = ViewUtils.dp(12);
                    topMargin = ViewUtils.dp(12);
                }});
                onApplyTheme();
            }

            public void bind(ProfilesRepo item) {
                title.setText(item.name);
                subtitle.setText(item.description);
                mSwitch.setChecked(item.checked);
                setOnClickListener(v -> {
                    item.checked = !item.checked;
                    mSwitch.setChecked(item.checked);
                });
                mSwitch.onApplyTheme();
                onApplyTheme();
            }

            @Override
            public void onApplyTheme() {
                title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
                subtitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
                setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10), 32));
            }
        }
    }

    private final class ProfileItem extends SimpleRecyclerItem<ProfileItem.ProfileHolderView> {
        private ConfigObject object;
        private int type = TYPE_PRINTER;

        private ProfileItem(ConfigObject obj, int type) {
            this.object = obj;
            this.type = type;
        }

        private ProfileItem() {}

        @Override
        public ProfileHolderView onCreateView(Context ctx) {
            return new ProfileHolderView(ctx);
        }

        @Override
        public void onBindView(ProfileHolderView view) {
            view.bind(this);
        }

        public final class ProfileHolderView extends LinearLayout implements IThemeView {
            private ImageView icon;
            private TextView title;
            private BeamSwitch mSwitch;

            public ProfileHolderView(@NonNull Context context) {
                super(context);

                setOrientation(HORIZONTAL);
                setGravity(Gravity.CENTER_VERTICAL);

                icon = new ImageView(context);
                addView(icon, new LinearLayout.LayoutParams(ViewUtils.dp(36), ViewUtils.dp(36)) {{
                    rightMargin = ViewUtils.dp(16);
                }});

                LinearLayout inner = new LinearLayout(context);
                inner.setOrientation(VERTICAL);
                title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
                inner.addView(title);

                addView(inner, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                mSwitch = new BeamSwitch(context);
                addView(mSwitch, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    leftMargin = ViewUtils.dp(12);
                }});

                setPadding(ViewUtils.dp(16), ViewUtils.dp(12), ViewUtils.dp(21), ViewUtils.dp(12));
                setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    leftMargin = rightMargin = ViewUtils.dp(12);
                    bottomMargin = ViewUtils.dp(8);
                }});
                onApplyTheme();
            }

            private List<ConfigObject> getList(ProfileItem item) {
                return item.type == TYPE_FILAMENT ? enabledFilaments : enabledPrinters;
            }

            public void bind(ProfileItem item) {
                LayoutParams params = (LayoutParams) icon.getLayoutParams();
                if (item.object == null || item.object.thumbnailUrl == null) {
                    params.width = params.height = ViewUtils.dp(36);
                    icon.setColorFilter(ThemesRepo.getColor(android.R.attr.colorAccent));
                    switch (item.type) {
                        case TYPE_PRINTER:
                            icon.setImageResource(R.drawable.printer_outline_28);
                            break;
                        case TYPE_PRINT_CONFIG:
                            icon.setImageResource(R.drawable.wrench_outline_28);
                            break;
                        case TYPE_FILAMENT:
                            icon.setImageResource(R.drawable.slot_filament_28);
                            break;
                    }
                } else {
                    params.width = params.height = ViewUtils.dp(52);

                    icon.setColorFilter(null);
                    Glide.with(icon)
                            .load(item.object.thumbnailUrl)
                            .transform(new RoundedCorners(ViewUtils.dp(12)))
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(icon);
                }
                icon.requestLayout();
                mSwitch.onApplyTheme();

                if (item.object == null) {
                    title.setText(R.string.IntroCustomProfileName);
                    mSwitch.setChecked(true);
                    setOnClickListener(null);
                    setClickable(false);
                    return;
                }

                title.setText(item.object.get("name") != null ? item.object.get("name") : item.object.getTitle());
                boolean checked = getList(item).contains(item.object);

                mSwitch.setChecked(checked);
                setOnClickListener(v -> {
                    boolean _checked = getList(item).contains(item.object);
                    _checked = !_checked;
                    mSwitch.setChecked(_checked);

                    if (_checked) {
                        getList(item).add(item.object);
                    } else {
                        getList(item).remove(item.object);
                    }
                });
                onApplyTheme();
            }

            @Override
            public void onApplyTheme() {
                title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
                setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10), 32));
            }
        }
    }
}

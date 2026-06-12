package ru.ytkab0bp.slicebeam.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Process;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.navigation.NavigationBarView;

import java.io.File;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.SliceProgressBottomSheet;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.BedMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.OrientationMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceMenu;
import ru.ytkab0bp.slicebeam.components.bed_menu.TransformMenu;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent;
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.SlicingProgressEvent;
import ru.ytkab0bp.slicebeam.navigation.Fragment;
import ru.ytkab0bp.slicebeam.slic3r.Bed3D;
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.BedSwipeDownLayout;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.GLView;
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout;
import ru.ytkab0bp.slicebeam.view.ThemeBottomNavigationView;
import ru.ytkab0bp.slicebeam.view.ThemeRailNavigationView;

public class BedFragment extends Fragment {
    private final static boolean DEBUG_VIEWER = false;
    private final static int MENU_SIZE_DP = 80;

    private FrameLayout overlayLayout;
    private SnackbarsLayout snackbarsLayout;
    private GLView glView;
    private NavigationBarView navigationView;

    private boolean isAnimatingMenu;
    private boolean isChangingByCode;
    private int currentMenuSlot;
    private FrameLayout menuView;
    private SparseArray<BedMenu> menuMap = new SparseArray<BedMenu>() {
        @Override
        public BedMenu get(int key) {
            BedMenu menu = super.get(key);
            if (menu == null) {
                switch (MenuCategory.values()[key]) {
                    default:
                    case FILE:
                        menu = new FileMenu();
                        break;
                    case CAMERA:
                        menu = new CameraMenu();
                        break;
                    case TRANSFORM:
                        menu = new TransformMenu();
                        break;
                    case SLICE_AND_EXPORT:
                        menu = new SliceMenu();
                        break;
                }

                put(key, menu);
            }
            return menu;
        }
    };

    private View contentView;

    // Refactored plate management
    private java.util.ArrayList<Model> platesModels = new java.util.ArrayList<>();
    private int currentPlateIndex = 0;
    
    private GCodeProcessorResult gCodeResult;
    private UnfoldMenu currentUnfoldMenu;

    private int totalPlates = 1; // used for importing
    private File currentProjectFile;
    private TextView plateIndicatorText;

    public Model getCurrentModel() {
        if (platesModels.isEmpty()) return null;
        return platesModels.get(currentPlateIndex);
    }

    private ru.ytkab0bp.slicebeam.view.PaintModeView paintModeView;
    private ru.ytkab0bp.slicebeam.view.MeasureModeView measureModeView;
    private ru.ytkab0bp.slicebeam.view.VariableLayerHeightModeView variableLayerHeightModeView;

    private BedSwipeDownLayout swipeDownLayout;
    private boolean hasWebError;
    private WebView panelWebView;
    private LinearLayout panelWebViewError;
    private ImageView webViewErrIcon;
    private TextView webViewErrDescription;
    private ProgressBar webViewProgressBar;

    private static String tempFileName;
    private static File tempExportingFile;

    public static String getTempFileName() {
        return tempFileName;
    }

    public static File getTempGCodePath() {
        return tempExportingFile != null ? tempExportingFile : new File(SliceBeam.INSTANCE.getCacheDir(), "temp.gcode");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SliceBeam.EVENT_BUS.registerListener(this);
    }

    @EventHandler(runOnMainThread = true)
    public void onNeedSnackbar(NeedSnackbarEvent e) {
        SnackbarsLayout.Snackbar s = new SnackbarsLayout.Snackbar(e.type, e.title);
        if (e.tag != null) {
            s.tag(e.tag);
        }
        if (e.buttonTitle != null) {
            s.lifetime = 0;
            s.buttonTitle = e.buttonTitle;
            s.buttonClick = e.buttonClick;
        }
        snackbarsLayout.show(s);
    }

    @EventHandler(runOnMainThread = true)
    public void onDismissSnackbar(NeedDismissSnackbarEvent e) {
        snackbarsLayout.dismiss(e.tag);
    }

    public void showUnfoldMenu(UnfoldMenu menu, View from) {
        if (currentUnfoldMenu != null) return;

        menu.setOnDismiss(()-> {
            if (menu.isAttached()) return;
            currentUnfoldMenu = null;
        });
        currentUnfoldMenu = menu;
        menu.show(from, this);
    }

    private void showModelContextMenu(int objectIndex) {
        Context ctx = getContext();
        if (ctx == null || glView == null || objectIndex == -1) return;
        if (currentUnfoldMenu != null) {
            currentUnfoldMenu.dismiss();
        }

        new BeamAlertDialogBuilder(ctx)
                .setTitle(R.string.ModelContextMenuTitle)
                .setItems(new CharSequence[] {
                        ctx.getString(R.string.ModelContextDuplicate),
                        ctx.getString(R.string.ModelContextDelete),
                        ctx.getString(R.string.ModelContextFillBed),
                        ctx.getString(R.string.ModelContextCenterOnBed),
                        ctx.getString(R.string.ModelContextAutoOrient),
                        ctx.getString(R.string.ModelContextResetRotation),
                        ctx.getString(R.string.ModelContextResetScale),
                        ctx.getString(R.string.ModelContextSetOnFace),
                        ctx.getString(R.string.ModelContextSelectAll),
                        ctx.getString(R.string.ModelContextDeleteAll)
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: // Duplicate
                            glView.duplicateSelectedModel(() -> {
                                updateModel();
                                Toast.makeText(ctx, R.string.ModelContextDuplicated, Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case 1: // Delete
                            glView.deleteSelectedModel(() -> {
                                updateModel();
                                Toast.makeText(ctx, R.string.ModelContextDeleted, Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case 2: // Fill Bed
                            glView.fillBedWithSelectedModel(addedCopies -> {
                                updateModel();
                                Toast.makeText(ctx,
                                        addedCopies > 0
                                                ? ctx.getString(R.string.ModelContextFillBedFinished, addedCopies)
                                                : ctx.getString(R.string.ModelContextFillBedNoRoom),
                                        Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case 3: // Center on Bed
                            glView.centerSelectedOnBed(() -> {
                                updateModel();
                                Toast.makeText(ctx, R.string.ModelContextCentered, Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case 4: // Auto-orient
                            glView.queueEvent(() -> {
                                glView.getRenderer().getModel().autoOrient(objectIndex);
                                glView.getRenderer().getModel().ensureOnBed(objectIndex);
                                glView.getRenderer().invalidateGlModel(objectIndex);
                                glView.requestRender();
                                ViewUtils.postOnMainThread(() -> {
                                    updateModel();
                                    Toast.makeText(ctx, R.string.ModelContextAutoOriented, Toast.LENGTH_SHORT).show();
                                });
                            });
                            break;
                        case 5: // Reset Rotation
                            glView.queueEvent(() -> {
                                glView.getRenderer().setSelectionRotation(0, 0, 0);
                                glView.getRenderer().getModel().ensureOnBed(objectIndex);
                                glView.getRenderer().invalidateSelectionObject();
                                glView.requestRender();
                                ViewUtils.postOnMainThread(() -> {
                                    updateModel();
                                    Toast.makeText(ctx, R.string.ModelContextResetRotationDone, Toast.LENGTH_SHORT).show();
                                });
                            });
                            break;
                        case 6: // Reset Scale
                            glView.queueEvent(() -> {
                                glView.getRenderer().setSelectionScale(1, 1, 1);
                                glView.getRenderer().getModel().ensureOnBed(objectIndex);
                                glView.getRenderer().invalidateSelectionObject();
                                glView.requestRender();
                                ViewUtils.postOnMainThread(() -> {
                                    updateModel();
                                    Toast.makeText(ctx, R.string.ModelContextResetScaleDone, Toast.LENGTH_SHORT).show();
                                });
                            });
                            break;
                        case 7: // Lay on face (enter flatten mode)
                            glView.queueEvent(() -> {
                                glView.getRenderer().setInFlattenMode(true);
                                glView.requestRender();
                            });
                            break;
                        case 8: // Select All
                            glView.selectAllModels(() -> {
                                updateModel();
                                Toast.makeText(ctx, R.string.ModelContextSelectedAll, Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case 9: // Delete All
                            glView.deleteAllModels(() -> {
                                updateModel();
                                Toast.makeText(ctx, R.string.ModelContextDeletedAll, Toast.LENGTH_SHORT).show();
                            });
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showBedContextMenu() {
        Context ctx = getContext();
        if (ctx == null || glView == null || glView.getRenderer().getModel() == null) return;
        if (currentUnfoldMenu != null) {
            currentUnfoldMenu.dismiss();
        }

        new BeamAlertDialogBuilder(ctx)
                .setTitle(R.string.BedContextMenuTitle)
                .setItems(new CharSequence[] {
                        ctx.getString(R.string.MenuOrientationArrange)
                }, (dialog, which) -> {
                    if (which != 0) return;
                    glView.arrange();
                    Toast.makeText(ctx, R.string.MenuOrientationArrangeFinished, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void enterPaintMode(int objectIndex) {
        enterPaintMode(objectIndex, ru.ytkab0bp.slicebeam.render.GLRenderer.PAINT_MODE_COLOR);
    }

    public void enterPaintMode(int objectIndex, int mode) {
        Context ctx = getContext();
        if (ctx == null || glView == null || objectIndex == -1 || paintModeView != null) return;
        glView.queueEvent(() -> {
            glView.getRenderer().beginPaint(objectIndex, mode);
            glView.requestRender();
        });
        paintModeView = new ru.ytkab0bp.slicebeam.view.PaintModeView(ctx, glView, this::exitPaintMode, mode);
        overlayLayout.addView(paintModeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exitPaintMode() {
        if (paintModeView != null) {
            overlayLayout.removeView(paintModeView);
            paintModeView = null;
        }
    }

    public boolean isPaintMode() {
        return paintModeView != null;
    }

    public void enterMeasureMode() {
        Context ctx = getContext();
        if (ctx == null || glView == null || measureModeView != null) return;
        glView.queueEvent(() -> {
            glView.getRenderer().setInMeasureMode(true);
            glView.requestRender();
        });
        measureModeView = new ru.ytkab0bp.slicebeam.view.MeasureModeView(ctx, glView, this::exitMeasureMode);
        overlayLayout.addView(measureModeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exitMeasureMode() {
        if (measureModeView != null) {
            overlayLayout.removeView(measureModeView);
            measureModeView = null;
        }
    }

    public boolean isMeasureMode() {
        return measureModeView != null;
    }

    public void enterVariableLayerHeightMode() {
        Context ctx = getContext();
        if (ctx == null || glView == null || variableLayerHeightModeView != null) return;
        variableLayerHeightModeView = new ru.ytkab0bp.slicebeam.view.VariableLayerHeightModeView(ctx, glView, this::exitVariableLayerHeightMode);
        overlayLayout.addView(variableLayerHeightModeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exitVariableLayerHeightMode() {
        if (variableLayerHeightModeView != null) {
            overlayLayout.removeView(variableLayerHeightModeView);
            variableLayerHeightModeView = null;
        }
    }

    public boolean isVariableLayerHeightMode() {
        return variableLayerHeightModeView != null;
    }

    public void loadGCode(File f) {
        gCodeResult = new GCodeProcessorResult(f);
        ViewUtils.postOnMainThread(()-> {
            glView.queueEvent(()->{
                glView.getRenderer().setGCodeViewer(gCodeResult);
                glView.requestRender();
            });

            tempFileName = gCodeResult.getRecommendedName();
            tempExportingFile = f;

            isChangingByCode = true;
            navigationView.setSelectedItemId(MenuCategory.SLICE_AND_EXPORT.ordinal());
            isChangingByCode = false;

            DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
            boolean portrait = dm.widthPixels < dm.heightPixels;
            selectMenu(getContext(), portrait, MenuCategory.SLICE_AND_EXPORT.ordinal());
        });
    }

    @Override
    public boolean onBackPressed() {
        if (variableLayerHeightModeView != null) {
            exitVariableLayerHeightMode();
            return true;
        }
        if (measureModeView != null) {
            glView.queueEvent(() -> {
                glView.getRenderer().setInMeasureMode(false);
                glView.getRenderer().clearMeasurePoints();
                glView.requestRender();
            });
            exitMeasureMode();
            return true;
        }
        if (paintModeView != null) {
            glView.queueEvent(() -> {
                glView.getRenderer().endPaint(true);
                glView.requestRender();
            });
            exitPaintMode();
            return true;
        }
        if (currentUnfoldMenu != null) {
            currentUnfoldMenu.dismiss();
            return true;
        }
        if (swipeDownLayout.onBackPressed()) {
            return true;
        }
        if (currentMenuSlot != 0) {
            navigationView.setSelectedItemId(0);
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SliceBeam.EVENT_BUS.unregisterListener(this);

        for (int i = 0; i < menuMap.size(); i++) {
            menuMap.valueAt(i).onViewDestroyed();
        }

        if (!(getContext() instanceof Activity && ((Activity) getContext()).isChangingConfigurations())) {
            for (Model m : platesModels) {
                if (m != null) m.release();
            }
            platesModels.clear();
            if (gCodeResult != null) {
                gCodeResult.release();
                gCodeResult = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        glView.onResume();
        ConfigObject cfg = SliceBeam.CONFIG.findPrinter(SliceBeam.CONFIG.presets.get("printer"));
        boolean enable = cfg != null && cfg.get("host_type") != null && !TextUtils.isEmpty(cfg.get("print_host")) && panelWebView != null;
        swipeDownLayout.setEnableTop(enable);
        if (enable) {
            String host = cfg.get("print_host");
            if (host.contains(":")) {
                try {
                    int port = Integer.parseInt(host.split(":")[1]);
                    if (port >= 7125 && port <= 7200) {
                        host = host.split(":")[0];
                    }
                } catch (Exception ignored) {}
            }
            if (!host.startsWith("http://")) {
                host = "http://" + host;
            }
            webViewProgressBar.animate().alpha(1).setDuration(150).start();
            panelWebView.setAlpha(0f);
            hasWebError = false;
            panelWebView.loadUrl(host);
            panelWebViewError.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    panelWebViewError.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        glView.onPause();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(Context ctx) {
        if (platesModels.isEmpty()) platesModels.add(new Model());
        glView = new GLView(ctx);
        glView.getRenderer().setModel(getCurrentModel());
        glView.getRenderer().setGCodeViewer(gCodeResult);
        glView.setOnModelLongPressListener((view, objectIndex, x, y) -> showModelContextMenu(objectIndex));
        glView.setOnBedLongPressListener((view, x, y) -> showBedContextMenu());
        overlayLayout = new FrameLayout(ctx) {
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                if (currentUnfoldMenu != null) {
                    currentUnfoldMenu.relayout();
                }
            }
        };

        plateIndicatorText = new TextView(ctx);
        plateIndicatorText.setTextSize(16);
        plateIndicatorText.setTextColor(0xAAFFFFFF);
        plateIndicatorText.setShadowLayer(4, 0, 0, 0xFF000000);
        plateIndicatorText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        plateIndicatorText.setText("Plate " + (currentPlateIndex + 1));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.TOP | Gravity.START;
        indicatorParams.topMargin = ViewUtils.dp(80);
        indicatorParams.leftMargin = ViewUtils.dp(16);
        LinearLayout ll = new LinearLayout(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        boolean portrait = dm.widthPixels < dm.heightPixels;

        ll.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigationView = null;
        constructMenuView(ctx, portrait);

        if (!portrait) {
            ll.addView(navigationView = new ThemeRailNavigationView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewUtils.dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
            ll.addView(menuView, new LinearLayout.LayoutParams(ViewUtils.dp(MENU_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        }

        swipeDownLayout = new BedSwipeDownLayout(ctx);
        FrameLayout wfl = new FrameLayout(ctx);
        try {
            panelWebView = new WebView(ctx);
            panelWebView.getSettings().setJavaScriptEnabled(true);
            panelWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    hasWebError = true;
                    webViewErrDescription.setText(description);
                    panelWebViewError.setVisibility(View.VISIBLE);
                    panelWebViewError.setAlpha(0f);
                    panelWebViewError.animate().alpha(1).setDuration(150).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            panelWebView.setVisibility(View.GONE);
                        }
                    }).start();
                    webViewProgressBar.animate().alpha(0).setDuration(150).start();
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!hasWebError) {
                        panelWebView.animate().alpha(1).setDuration(150).start();
                        webViewProgressBar.animate().alpha(0).setDuration(150).start();
                    }
                }
            });

            wfl.addView(panelWebView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panelWebViewError = new LinearLayout(ctx);
            panelWebViewError.setVisibility(View.GONE);
            panelWebViewError.setOrientation(LinearLayout.VERTICAL);
            panelWebViewError.setGravity(Gravity.CENTER);
            panelWebViewError.setPadding(ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12));
            webViewErrIcon = new ImageView(ctx);
            webViewErrIcon.setImageResource(R.drawable.globe_cross_outline_28);
            panelWebViewError.addView(webViewErrIcon, new LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)) {{
                bottomMargin = ViewUtils.dp(8);
            }});
            webViewErrDescription = new TextView(ctx);
            webViewErrDescription.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            webViewErrDescription.setGravity(Gravity.CENTER);
            panelWebViewError.addView(webViewErrDescription);
            wfl.addView(panelWebViewError, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            webViewProgressBar = new ProgressBar(ctx);
            webViewProgressBar.setAlpha(0f);
            wfl.addView(webViewProgressBar, new FrameLayout.LayoutParams(ViewUtils.dp(36), ViewUtils.dp(36), Gravity.CENTER));
        } catch (Exception e) {
            Log.wtf("BedFragment", "Failed to initialize webview", e);
        }

        if (portrait) {
            LinearLayout inner = new LinearLayout(ctx);
            inner.setOrientation(LinearLayout.VERTICAL);
            ll = inner;

            inner.addView(glView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            swipeDownLayout.addView(inner);
            swipeDownLayout.addView(wfl);
        } else {
            swipeDownLayout.addView(glView);
            swipeDownLayout.addView(wfl);
            ll.addView(swipeDownLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        if (portrait) {
            ll.addView(menuView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(MENU_SIZE_DP)));
            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1)));
            ll.addView(navigationView = new ThemeBottomNavigationView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        navigationView.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        for (MenuCategory cat : MenuCategory.values()) {
            navigationView.getMenu().add(0, cat.ordinal(), 0, cat.titleRes).setIcon(cat.iconRes);
        }
        navigationView.setSelectedItemId(currentMenuSlot);
        navigationView.setOnItemSelectedListener(item -> {
            if (currentMenuSlot == item.getItemId() || isChangingByCode) return true;
            if (isAnimatingMenu) return false;
            if (item.getItemId() == MenuCategory.SLICE_AND_EXPORT.ordinal()) {
                if (glView.getRenderer().getModel() == null && !DEBUG_VIEWER) {
                    new BeamAlertDialogBuilder(ctx)
                            .setTitle(R.string.SliceFailed)
                            .setMessage(R.string.SliceFailedNoModels)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    tempExportingFile = null;
                    File cfg = SliceBeam.getCurrentConfigFile();
                    File gcode = getTempGCodePath();

                    if (!DEBUG_VIEWER) {
                        new SliceProgressBottomSheet(ctx).show();
                    }
                    new Thread(()->{
                        try {
                            Process.setThreadPriority(-20);

                            try {
                                SliceBeam.ensureCompatibleSelection();
                                SliceBeam.genCurrentConfig();
                            } catch (Exception e) {
                                Log.e("BedFragment", "Failed to write config", e);

                                ViewUtils.postOnMainThread(()->{
                                    SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                                    new BeamAlertDialogBuilder(ctx)
                                            .setTitle(R.string.SliceFailed)
                                            .setMessage(e.getMessage())
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                });
                            }

                            if (!DEBUG_VIEWER) {
                                gCodeResult = glView.getRenderer().getModel().slice(cfg.getAbsolutePath(), gcode.getAbsolutePath(), (progress, text) -> SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(progress, text)),
                                        SliceBeam.PENDING_CALIB_MODE, SliceBeam.PENDING_CALIB_START, SliceBeam.PENDING_CALIB_END, SliceBeam.PENDING_CALIB_STEP);
                                SliceBeam.PENDING_CALIB_MODE = 0; // consume the calibration after one slice
                                SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                            } else {
                                gCodeResult = new GCodeProcessorResult(gcode);
                            }
                            ViewUtils.postOnMainThread(()-> {
                                glView.queueEvent(()->{
                                    glView.getRenderer().setGCodeViewer(gCodeResult);
                                    glView.requestRender();
                                });

                                tempFileName = gCodeResult.getRecommendedName();
                                tempExportingFile = null;

                                isChangingByCode = true;
                                navigationView.setSelectedItemId(item.getItemId());
                                isChangingByCode = false;
                                selectMenu(ctx, portrait, item.getItemId());
                            });
                        } catch (Exception e) {
                            Log.e("BedFragment", "Slice failed", e);
                            ViewUtils.postOnMainThread(()->{
                                SliceBeam.EVENT_BUS.fireEvent(new SlicingProgressEvent(100, ""));
                                new BeamAlertDialogBuilder(ctx)
                                        .setTitle(R.string.SliceFailed)
                                        .setMessage(e.getMessage())
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            });
                        }
                    }).start();
                }
                return false;
            } else {
                glView.queueEvent(()->{
                    if (gCodeResult != null) {
                        gCodeResult.release();
                        gCodeResult = null;
                    }

                    glView.getRenderer().setGCodeViewer(null);
                    glView.requestRender();
                });
            }

            selectMenu(ctx, portrait, item.getItemId());
            return true;
        });

        if (portrait) {
            overlayLayout.addView(contentView = swipeDownLayout);
        } else {
            overlayLayout.addView(contentView = ll);
        }
        overlayLayout.addView(snackbarsLayout = new SnackbarsLayout(ctx), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) {{
            if (portrait) {
                bottomMargin = ViewUtils.dp(80 * 2);
            } else {
                leftMargin = ViewUtils.dp(80 * 2);
            }
        }});
        indicatorParams.gravity = Gravity.TOP | Gravity.START;
        indicatorParams.topMargin = 0;
        indicatorParams.leftMargin = 0;
        overlayLayout.addView(plateIndicatorText, indicatorParams);

        android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback() {
            private double[] vpMatrix = new double[16];
            private double[] inVec = new double[4];
            private double[] outVec = new double[4];

            @Override
            public void doFrame(long frameTimeNanos) {
                if (glView != null && glView.getRenderer() != null && plateIndicatorText != null) {
                    ru.ytkab0bp.slicebeam.render.GLRenderer r = glView.getRenderer();
                    ru.ytkab0bp.slicebeam.slic3r.Bed3D bed = r.getBed();
                    if (bed != null && bed.isValid() && r.getCamera() != null) {
                        ru.ytkab0bp.slicebeam.utils.Vec3d min = bed.getVolumeMin();
                        ru.ytkab0bp.slicebeam.utils.Vec3d max = bed.getVolumeMax();
                        
                        // Place it above the top left corner, aligned with the left edge
                        inVec[0] = min.x + 30;
                        inVec[1] = max.y + 15;
                        inVec[2] = 0;
                        inVec[3] = 1.0;

                        double[] view = r.getCamera().getViewModelMatrix();
                        double[] proj = r.getProjectionMatrix();
                        ru.ytkab0bp.slicebeam.utils.DoubleMatrix.multiplyMM(vpMatrix, 0, proj, 0, view, 0);
                        ru.ytkab0bp.slicebeam.utils.DoubleMatrix.multiplyMV(outVec, 0, vpMatrix, 0, inVec, 0);

                        if (outVec[3] != 0 && outVec[2] > 0 && outVec[2] < outVec[3]) {
                            float ndcX = (float) (outVec[0] / outVec[3]);
                            float ndcY = (float) (outVec[1] / outVec[3]);
                            
                            float screenX = (ndcX + 1.0f) / 2.0f * glView.getWidth();
                            float screenY = (1.0f - ndcY) / 2.0f * glView.getHeight();
                            
                            plateIndicatorText.setTranslationX(screenX - plateIndicatorText.getWidth() / 2f);
                            plateIndicatorText.setTranslationY(screenY - plateIndicatorText.getHeight() / 2f);

                            ru.ytkab0bp.slicebeam.utils.Vec3d dir = r.getCamera().getDirForward();
                            double yaw = Math.atan2(dir.x, dir.y);
                            double pitch = Math.asin(-dir.z);

                            plateIndicatorText.setRotationX((float) Math.toDegrees(Math.PI / 2 - pitch));
                            plateIndicatorText.setRotation((float) Math.toDegrees(-yaw));
                            
                            float scale = Math.max(0.2f, r.getCamera().getZoom() * 0.8f);
                            plateIndicatorText.setScaleX(scale);
                            plateIndicatorText.setScaleY(scale);
                            
                            plateIndicatorText.setAlpha(1.0f);
                        } else {
                            plateIndicatorText.setAlpha(0.0f);
                        }
                    }
                }
                if (glView != null) android.view.Choreographer.getInstance().postFrameCallback(this);
            }
        });

        return overlayLayout;
    }

    public SnackbarsLayout getSnackbarsLayout() {
        return snackbarsLayout;
    }

    public FrameLayout getOverlayLayout() {
        return overlayLayout;
    }

    private void selectMenu(Context ctx, boolean portrait, int slot) {
        if (glView.getRenderer().resetFlattenMode()) {
            glView.requestRender();
            SliceBeam.EVENT_BUS.fireEvent(new FlattenModeResetEvent());
        }
        isAnimatingMenu = true;

        BedMenu prevMenu = menuMap.get(currentMenuSlot);
        boolean forward = slot > currentMenuSlot;
        currentMenuSlot = slot;

        BedMenu currentMenu = menuMap.get(currentMenuSlot);
        if (currentMenu.getView() == null) {
            currentMenu.onSetBed(this);
            currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait));
        }
        View v = currentMenu.getView();
        if (v.getParent() != null) {
            menuView.removeView(v);
        }
        menuView.addView(v, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Runnable next = ()->{
            if (portrait) {
                v.setTranslationX(v.getWidth() * (forward ? 1 : -1));
            } else {
                v.setTranslationY(v.getHeight() * (forward ? 1 : -1));
            }
            v.setAlpha(0f);

            View prevView = prevMenu.getView();

            new SpringAnimation(new FloatValueHolder(0))
                    .setMinimumVisibleChange(1 / 256f)
                    .setSpring(new SpringForce(1f)
                            .setStiffness(1000f)
                            .setDampingRatio(1f))
                    .addUpdateListener((animation, value, velocity) -> {
                        prevView.setAlpha(1f - value);
                        v.setAlpha(value);

                        if (portrait) {
                            prevView.setTranslationX(-v.getWidth() * value * 0.5f * (forward ? 1 : -1));
                            v.setTranslationX(v.getWidth() * (1f - value) * 0.5f * (forward ? 1 : -1));
                        } else {
                            prevView.setTranslationY(-prevView.getHeight() * value * 0.5f * (forward ? 1 : -1));
                            v.setTranslationY(v.getHeight() * (1f - value) * 0.5f * (forward ? 1 : -1));
                        }
                    })
                    .addEndListener((animation, canceled, value, velocity) -> {
                        menuView.removeView(prevMenu.getView());
                        isAnimatingMenu = false;
                    })
                    .start();
        };

        if (!v.isLaidOut()) {
            v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    next.run();
                }
            });
        } else {
            next.run();
        }
    }

    public GLView getGlView() {
        return glView;
    }

    public interface ModelLoadCallback {
        void onLoaded(Model loadedModel, int firstNewObject, int addedObjects);
    }

    public void loadModel(File f) throws Slic3rRuntimeError {
        loadModel(f, false, 1, null);
    }

    private void addNewPlate() {
        platesModels.add(new Model());
        totalPlates = platesModels.size();
        switchPlate(platesModels.size() - 1);
    }

    private void removeCurrentPlate() {
        if (platesModels.size() <= 1) {
            Toast.makeText(getContext(), "Cannot remove the only plate", Toast.LENGTH_SHORT).show();
            return;
        }
        Model m = platesModels.get(currentPlateIndex);
        platesModels.remove(currentPlateIndex);
        totalPlates = platesModels.size();
        
        // Use false so we don't try to save the deleted model back into the array
        switchPlate(Math.max(0, currentPlateIndex - 1), false);
        
        if (m != null) {
            glView.queueEvent(() -> m.release());
        }
    }

    public void showPlatesMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.MenuAddPlate);
        
        android.view.SubMenu switchMenu = popup.getMenu().addSubMenu(0, 2, 0, R.string.MenuSwitchPlate);
        for (int i = 0; i < platesModels.size(); i++) {
            switchMenu.add(1, 100 + i, 0, "Plate " + (i + 1));
        }
        
        popup.getMenu().add(0, 3, 0, R.string.MenuRemovePlate);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                addNewPlate();
                return true;
            } else if (item.getItemId() == 3) {
                removeCurrentPlate();
                return true;
            } else if (item.getGroupId() == 1) {
                switchPlate(item.getItemId() - 100);
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** Desktop OrcaSlicer plate grid column count (PartPlateList::compute_colum_count). */
    private static int computePlateCols(int count) {
        float value = (float) Math.sqrt(count);
        float round = Math.round(value);
        return value > round ? (int) round + 1 : (int) round;
    }

    /**
     * Plate origin in project world space, matching desktop PartPlateList::compute_origin:
     * stride = bed size * (1 + LOGICAL_PART_PLATE_GAP=1/5), columns extend +X, rows extend -Y.
     */
    private static void computePlateOrigin(int index, int cols, double bedWidth, double bedDepth, double[] out) {
        out[0] = (index % cols) * bedWidth * 1.2;
        out[1] = -(index / cols) * bedDepth * 1.2;
    }

    private static void computePlateOrigin(int index, int cols, Vec3d bedMin, Vec3d bedMax, double[] out) {
        computePlateOrigin(index, cols, bedMax.x - bedMin.x, bedMax.y - bedMin.y, out);
    }

    /**
     * Bed size the project file was laid out for, from its embedded printable_area. The world
     * offsets of objects on plate 2+ are multiples of THIS bed's stride — which may differ from
     * the app's configured bed. Returns true and fills out[0]=width, out[1]=depth on success.
     */
    private static boolean readProjectBedSize(File f, double[] out) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
            java.util.zip.ZipEntry en = zip.getEntry("Metadata/project_settings.config");
            if (en == null) return false;
            org.json.JSONObject cfg = new org.json.JSONObject(
                    ru.ytkab0bp.slicebeam.utils.IOUtils.readString(zip.getInputStream(en), true));
            org.json.JSONArray area = cfg.optJSONArray("printable_area");
            if (area == null || area.length() == 0) return false;
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int i = 0; i < area.length(); i++) {
                String[] pt = area.getString(i).split("x");
                if (pt.length != 2) return false;
                double x = Double.parseDouble(pt[0]), y = Double.parseDouble(pt[1]);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (maxX <= minX || maxY <= minY) return false;
            out[0] = maxX - minX;
            out[1] = maxY - minY;
            return true;
        } catch (Exception e) {
            android.util.Log.w("BedFragment", "Could not read project bed size", e);
            return false;
        }
    }

    /**
     * Hand the renderer every plate except the active one, with grid offsets relative to the
     * active plate (which always renders at the origin), so all plates are visible at once.
     */
    private void pushPlatesToRenderer() {
        final int active = currentPlateIndex;
        final java.util.List<Model> models = new java.util.ArrayList<>(platesModels);
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                Bed3D bed = glView.getRenderer().getBed();
                if (bed == null) {
                    ViewUtils.postOnMainThread(() -> glView.queueEvent(this));
                    return;
                }
                java.util.List<Model> inactive = new java.util.ArrayList<>();
                java.util.List<double[]> offsets = new java.util.ArrayList<>();
                if (models.size() > 1) {
                    int cols = computePlateCols(models.size());
                    double[] activeOrigin = new double[2];
                    double[] tmp = new double[2];
                    computePlateOrigin(active, cols, bed.getVolumeMin(), bed.getVolumeMax(), activeOrigin);
                    for (int i = 0; i < models.size(); i++) {
                        if (i == active || models.get(i) == null) continue;
                        computePlateOrigin(i, cols, bed.getVolumeMin(), bed.getVolumeMax(), tmp);
                        inactive.add(models.get(i));
                        offsets.add(new double[]{tmp[0] - activeOrigin[0], tmp[1] - activeOrigin[1]});
                    }
                }
                glView.getRenderer().setCurrentPlateIndex(active);
                glView.getRenderer().setInactivePlates(inactive, offsets);
                glView.requestRender();
            }
        });
    }

    private void switchPlate(int newPlateIndex) {
        switchPlate(newPlateIndex, true, null);
    }

    private void switchPlate(int newPlateIndex, boolean saveCurrent) {
        switchPlate(newPlateIndex, saveCurrent, null);
    }

    private void switchPlate(int newPlateIndex, boolean saveCurrent, ModelLoadCallback loadCallback) {
        if (newPlateIndex < 0 || newPlateIndex >= platesModels.size()) return;
        
        // Save current model only if requested
        if (saveCurrent) {
            updateModel();
        }
        
        currentPlateIndex = newPlateIndex;
        updatePlateIndicator();
        Model nextModel = platesModels.get(currentPlateIndex);
        
        // Lazy load from 3MF project if it's null (not yet loaded)
        if (nextModel == null && currentProjectFile != null) {
            android.util.Log.d("BedFragment", "Lazy loading plate " + currentPlateIndex + " from " + currentProjectFile);
            try {
                ModelLoadCallback internalCallback = (loadedModel, firstNewObject, addedObjects) -> {
                    android.util.Log.d("BedFragment", "internalCallback fired with loadedModel=" + loadedModel);
                    if (loadCallback != null) {
                        loadCallback.onLoaded(loadedModel, firstNewObject, addedObjects);
                    }
                };
                loadModelInternal(currentProjectFile, true, internalCallback);
                android.util.Log.d("BedFragment", "loadModelInternal returned");
                pushPlatesToRenderer();
                return; // loadModelInternal handles setModel and render
            } catch (Slic3rRuntimeError e) {
                android.util.Log.e("BedFragment", "Failed to lazy load plate", e);
                nextModel = new Model();
                android.util.Log.e("BedFragment", "Created new Model in Slic3rRuntimeError with pointer " + nextModel.getPointer());
                platesModels.set(currentPlateIndex, nextModel);
            } catch (Exception e) {
                android.util.Log.e("BedFragment", "Unexpected exception during lazy load", e);
                nextModel = new Model();
                android.util.Log.e("BedFragment", "Created new Model in Exception with pointer " + nextModel.getPointer());
                platesModels.set(currentPlateIndex, nextModel);
            }
        }
        
        // If it's already loaded or freshly created fallback
        if (nextModel == null) {
            nextModel = new Model();
            platesModels.set(currentPlateIndex, nextModel);
        }
        
        Model finalNext = nextModel;
        android.util.Log.e("BedFragment", "Queueing setModel with finalNext pointer: " + (finalNext != null ? finalNext.getPointer() : "null"));
        glView.queueEvent(() -> {
            android.util.Log.e("BedFragment", "Executing setModel with finalNext pointer: " + (finalNext != null ? finalNext.getPointer() : "null"));
            glView.getRenderer().setCurrentPlateIndex(currentPlateIndex);
            glView.getRenderer().setModel(finalNext);
            glView.requestRender();
        });
        pushPlatesToRenderer();
    }

    private void updatePlateIndicator() {
        if (plateIndicatorText != null) {
            ru.ytkab0bp.slicebeam.utils.ViewUtils.postOnMainThread(() -> {
                plateIndicatorText.setText("Plate " + (currentPlateIndex + 1));
            });
        }
    }

    public void loadModel(File f, boolean preserveProjectLayout, int plateCount, ModelLoadCallback callback) throws Slic3rRuntimeError {
        this.currentProjectFile = f;
        this.totalPlates = plateCount;
        
        if (plateCount > 1) {
            // Re-initialize for new 3MF project
            java.util.List<ru.ytkab0bp.slicebeam.slic3r.Model> oldModels = new java.util.ArrayList<>(platesModels);
            platesModels.clear();
            glView.queueEvent(() -> {
                glView.getRenderer().setModel(null);
                glView.getRenderer().resetGlModels();
                for (ru.ytkab0bp.slicebeam.slic3r.Model m : oldModels) {
                    if (m != null) m.release();
                }
            });
            for (int i = 0; i < plateCount; i++) {
                platesModels.add(null);
            }
            this.currentPlateIndex = 0;

            // Eagerly load every plate. The 3MF stores objects at desktop's world coordinates
            // (plate i offset by its grid origin), so normalize each plate back to bed-local
            // coordinates; the renderer then draws inactive plates at their grid offsets.
            // The stride must come from the bed size the FILE was laid out for, not the app's.
            final int cols = computePlateCols(plateCount);
            final double[] fileBed = new double[2];
            final boolean haveFileBed = readProjectBedSize(f, fileBed);
            for (int i = 0; i < plateCount; i++) {
                try {
                    platesModels.set(i, new Model(f, i + 1));
                } catch (Exception e) {
                    android.util.Log.e("BedFragment", "Failed to load plate " + (i + 1), e);
                    platesModels.set(i, new Model());
                }
            }
            final ModelLoadCallback finalCallback = callback;
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    Bed3D bed = glView.getRenderer().getBed();
                    if (bed == null) {
                        ViewUtils.postOnMainThread(() -> glView.queueEvent(this));
                        return;
                    }
                    double bedW = haveFileBed ? fileBed[0] : bed.getVolumeMax().x - bed.getVolumeMin().x;
                    double bedD = haveFileBed ? fileBed[1] : bed.getVolumeMax().y - bed.getVolumeMin().y;
                    double[] origin = new double[2];
                    for (int i = 0; i < platesModels.size(); i++) {
                        Model m = platesModels.get(i);
                        if (m == null) continue;
                        computePlateOrigin(i, cols, bedW, bedD, origin);
                        if (origin[0] != 0 || origin[1] != 0) {
                            for (int o = 0; o < m.getObjectsCount(); o++) {
                                m.translate(o, -origin[0], -origin[1], 0);
                            }
                        }
                        m.resetBoundingBox();
                    }
                    Model first = platesModels.get(0);
                    glView.getRenderer().setModel(first);
                    glView.getRenderer().resetGlModels();
                    if (finalCallback != null) {
                        finalCallback.onLoaded(first, 0, first != null ? first.getObjectsCount() : 0);
                    }
                }
            });
            pushPlatesToRenderer();
        } else {
            // Loading an STL or 1-plate 3MF. Just load into current plate.
            if (platesModels.isEmpty()) {
                platesModels.add(new Model());
                currentPlateIndex = 0;
            }
            if (platesModels.get(currentPlateIndex) == null) {
                platesModels.set(currentPlateIndex, new Model());
            }
            loadModelInternal(f, preserveProjectLayout, callback);
        }
        updatePlateIndicator();
    }

    private void loadModelInternal(File f, boolean preserveProjectLayout, ModelLoadCallback callback) throws Slic3rRuntimeError {
        // Wait, if it's a 3mf project, it already passed plate count, so currentPlateIndex is correct.
        Model m = new Model(f, currentPlateIndex + 1); // 1-based in JNI for specific plate, or 0 for default
        Model currentModel = getCurrentModel();
        if (currentModel != null && currentModel.getObjectsCount() > 0) {
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    Bed3D bed = glView.getRenderer().getBed();
                    if (bed == null) {
                        ViewUtils.postOnMainThread(()-> glView.queueEvent(this));
                        return;
                    }
                    int firstNewObject = currentModel.getObjectsCount();
                    int addedObjects = m.getObjectsCount();
                    if (!preserveProjectLayout) {
                        Vec3d objMin = new Vec3d(), objMax = new Vec3d();
                        Vec3d objTranslate = new Vec3d();
                        for (int i = 0; i < addedObjects; i++) {
                            m.getTranslation(i, objTranslate);
                            m.getBoundingBoxExact(i, objMin, objMax);
                            // Only fix Z (floor level); arrange will handle X/Y placement.
                            m.translate(i, objTranslate.x, objTranslate.y, -objTranslate.z + (objMax.z - objMin.z) / 2);
                        }
                    }

                    for (int i = 0; i < addedObjects; i++) {
                        currentModel.addObject(m, i);
                    }
                    m.release();
                    currentModel.resetBoundingBox();
                    if (!preserveProjectLayout) {
                        bed.arrange(currentModel);
                    }
                    glView.getRenderer().resetGlModels();
                    if (callback != null) {
                        callback.onLoaded(currentModel, firstNewObject, addedObjects);
                    }
                }
            });
        } else {
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    Bed3D bed = glView.getRenderer().getBed();
                    if (bed == null) {
                        ViewUtils.postOnMainThread(()-> glView.queueEvent(this));
                        return;
                    }
                    if (currentModel != null) currentModel.release();
                    platesModels.set(currentPlateIndex, m);
                    glView.getRenderer().setModel(m);
                    int addedObjects = m.getObjectsCount();

                    if (!preserveProjectLayout) {
                        Vec3d center = bed.getVolumeMin().center(bed.getVolumeMax());
                        Vec3d objMin = new Vec3d(), objMax = new Vec3d();
                        Vec3d objTranslate = new Vec3d();
                        for (int i = 0; i < addedObjects; i++) {
                            m.getTranslation(i, objTranslate);
                            m.getBoundingBoxExact(i, objMin, objMax);
                            // Move to center, fix Z
                            m.translate(i, center.x - (objMax.x + objMin.x) / 2, center.y - (objMax.y + objMin.y) / 2, -objTranslate.z + (objMax.z - objMin.z) / 2);
                        }
                        bed.arrange(m);
                    }

                    glView.getRenderer().resetGlModels();
                    if (callback != null) {
                        callback.onLoaded(m, 0, addedObjects);
                    }
                }
            });
        }
        glView.requestRender();
    }

    @Override
    public void onApplyTheme() {
        super.onApplyTheme();

        if (panelWebView != null) {
            webViewErrIcon.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
            webViewErrDescription.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            webViewProgressBar.setIndeterminateTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
        }
        menuView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground));
        for (int i = 0; i < MenuCategory.values().length; i++) {
            if (i != currentMenuSlot) {
                ThemesRepo.invalidateView(menuMap.get(i).getView());
            }
        }
    }

    private void constructMenuView(Context ctx, boolean portrait) {
        menuView = new FrameLayout(ctx);
        BedMenu currentMenu = menuMap.get(currentMenuSlot);
        if (currentMenu.getView() == null) {
            currentMenu.onSetBed(this);
            currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait));
        }
        menuView.addView(currentMenu.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void updateModel() {
        if (platesModels.isEmpty()) return;
        Model currentModel = glView.getRenderer().getModel();
        platesModels.set(currentPlateIndex, currentModel);
    }

    /** Split the selected object into separate objects (connected parts / volumes), then re-arrange. */
    public void splitSelectedObject() {
        int idx = glView.getRenderer().getSelectedObject();
        Model model = glView.getRenderer().getModel();
        if (model == null || idx == -1) return;
        glView.queueEvent(() -> {
            int count = model.split(idx);
            if (count <= 1) {
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuToolbarSplitNothing));
                return;
            }
            glView.getRenderer().setSelectedObject(-1);
            Bed3D bed = glView.getRenderer().getBed();
            if (bed != null) bed.arrange(model);
            glView.getRenderer().resetGlModels();
            glView.requestRender();
            ViewUtils.postOnMainThread(() -> {
                updateModel();
                SliceBeam.EVENT_BUS.fireEvent(new ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent());
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.MenuToolbarSplitDone, count));
            });
        });
    }

    public enum MenuCategory {
        FILE(R.string.MenuFile, R.drawable.folder_simple_outline_28),
        CAMERA(R.string.MenuCamera, R.drawable.camera_outline_28),
        TRANSFORM(R.string.MenuToolbar, R.drawable.menu_scale_28),
//        MODIFIERS(R.string.MenuModifiers, R.drawable.sliders_outline_28),
        SLICE_AND_EXPORT(R.string.MenuSlice, R.drawable.magic_wand_outline_28);

        final int titleRes;
        final int iconRes;

        MenuCategory(int titleRes, int iconRes) {
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }
    }
}

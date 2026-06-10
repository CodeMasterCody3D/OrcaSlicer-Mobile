package ru.ytkab0bp.slicebeam;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.instacart.truetime.time.TrueTimeImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import ru.ytkab0bp.eventbus.EventBus;
import ru.ytkab0bp.slicebeam.boot.AppBoot;
import ru.ytkab0bp.slicebeam.boot.BeamServerDataTask;
import ru.ytkab0bp.slicebeam.boot.CheckUpdateJsonTask;
import ru.ytkab0bp.slicebeam.boot.ClearModelCacheTask;
import ru.ytkab0bp.slicebeam.boot.CloudInitTask;
import ru.ytkab0bp.slicebeam.boot.EventBusTask;
import ru.ytkab0bp.slicebeam.boot.LoadSlic3rConfigTask;
import ru.ytkab0bp.slicebeam.boot.PrefsTask;
import ru.ytkab0bp.slicebeam.boot.PrintConfigWarmupTask;
import ru.ytkab0bp.slicebeam.boot.TrueTimeTask;
import ru.ytkab0bp.slicebeam.boot.VibrationUtilsTask;
import ru.ytkab0bp.slicebeam.cloud.CloudController;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef;
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils;
import ru.ytkab0bp.slicebeam.utils.Prefs;

public class SliceBeam extends Application {
    public static SliceBeam INSTANCE;
    public static EventBus EVENT_BUS = EventBus.newBus("main");
    public static TrueTimeImpl TRUE_TIME;
    public static Slic3rConfigWrapper CONFIG;
    public static int CONFIG_UID = 0;

    // Unsaved, live edits to the active printer/print/filament presets. Applied on top of the saved
    // presets when slicing/previewing, so a changed setting takes effect immediately without having to
    // save (overwrite) the profile. Cleared when the preset is switched or the edits are saved/reset.
    public static final ConfigObject LIVE_DIFF_PRINTER = new ConfigObject();
    public static final ConfigObject LIVE_DIFF_PRINT = new ConfigObject();
    public static final ConfigObject LIVE_DIFF_FILAMENT = new ConfigObject();

    // Tracks whether the user selected auto_brim before genCurrentConfig() converts it to outer_only
    // for Bed3D compatibility. Restored by Model.slice() so the native engine runs its btAutoBrim logic.
    public static boolean AUTO_BRIM_SELECTED = false;

    // Pending OrcaSlicer calibration for the next slice (CalibMode ordinal; 0 = normal print).
    // Consumed and reset by BedFragment after slicing.
    public static int PENDING_CALIB_MODE = 0;
    public static double PENDING_CALIB_START = 0, PENDING_CALIB_END = 0, PENDING_CALIB_STEP = 0;

    public static void clearLiveDiffs() {
        LIVE_DIFF_PRINTER.values.clear();
        LIVE_DIFF_PRINT.values.clear();
        LIVE_DIFF_FILAMENT.values.clear();
    }

    public static ConfigObject liveDiffFor(int profileListType) {
        switch (profileListType) {
            case ConfigObject.PROFILE_LIST_PRINTER: return LIVE_DIFF_PRINTER;
            case ConfigObject.PROFILE_LIST_FILAMENT: return LIVE_DIFF_FILAMENT;
            case ConfigObject.PROFILE_LIST_PRINT: return LIVE_DIFF_PRINT;
            default: return new ConfigObject(); // e.g. SettingsFragment — isolated, never sliced
        }
    }
    public static BeamServerData SERVER_DATA;
    public static boolean hasUpdateInfo;

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        AppBoot.run(Arrays.asList(
                new EventBusTask(),
                new PrefsTask(),
                new VibrationUtilsTask(),
                new TrueTimeTask(),
                new BeamServerDataTask(),
                new PrintConfigWarmupTask(),
                new CheckUpdateJsonTask(),
                new ClearModelCacheTask(),
                new LoadSlic3rConfigTask(),
                new CloudInitTask()
        ));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            Prefs.getPrefs().edit().putString("crash", sw.toString()).commit();
            Intent intent = new Intent(this, SafeStartActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            Runtime.getRuntime().exit(0);
        });
    }

    public static void saveConfig() {
        SliceBeam.CONFIG_UID++;
        File f = getConfigFile();
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(CONFIG.serialize().getBytes(StandardCharsets.UTF_8));
            fos.close();

            getCurrentConfigFile().delete();
        } catch (Exception e) {
            Log.e("Config", "Failed to save config", e);
        }
        CloudController.notifyDataChanged();
    }

    public static File getModelCacheDir() {
        File f = new File(INSTANCE.getCacheDir(), "model");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File getConfigFile() {
        return new File(INSTANCE.getFilesDir(), "slic3r.ini");
    }

    /**
     * Make sure the active process/filament selection is actually compatible with the active printer
     * (and that the process's layer height fits the nozzle). If not, switch to the first compatible
     * preset. Prevents slicing with a leftover incompatible selection — e.g. a 0.56mm process on a
     * 0.4mm nozzle, which the engine rejects with "layer height cannot exceed nozzle diameter".
     */
    public static void ensureCompatibleSelection() {
        if (CONFIG == null) return;
        ConfigObject printer = CONFIG.findPrinter(CONFIG.presets.get("printer"));
        if (printer == null) return;

        String printerName = printer.getTitle();
        String model = printer.get("printer_model");
        String nozzle = printer.get("printer_variant");
        if (nozzle == null) nozzle = Slic3rUtils.firstNozzleDiameter(printer.get("nozzle_diameter"));

        boolean changed = false;
        Slic3rUtils.ConfigChecker checker = new Slic3rUtils.ConfigChecker(printer.serialize());
        try {
            ConfigObject print = CONFIG.findPrint(CONFIG.presets.get("print"));
            boolean printOk = print != null
                    && Slic3rUtils.isPrinterCompatible(print.getTitle(), print.get("compatible_printers"), print.get("compatible_printers_condition"), printerName, model, nozzle, checker)
                    && Slic3rUtils.layerHeightFitsNozzle(print.get("layer_height"), nozzle)
                    && Slic3rUtils.lineWidthFitsNozzle(print.get("line_width"), nozzle);
            if (!printOk) {
                for (ConfigObject obj : CONFIG.printConfigs) {
                    if (Slic3rUtils.isPrinterCompatible(obj.getTitle(), obj.get("compatible_printers"), obj.get("compatible_printers_condition"), printerName, model, nozzle, checker)
                            && Slic3rUtils.layerHeightFitsNozzle(obj.get("layer_height"), nozzle)
                            && Slic3rUtils.lineWidthFitsNozzle(obj.get("line_width"), nozzle)) {
                        CONFIG.presets.put("print", obj.getTitle());
                        changed = true;
                        break;
                    }
                }
            }

            ConfigObject filament = CONFIG.findFilament(CONFIG.presets.get("filament"));
            boolean filamentOk = filament != null
                    && Slic3rUtils.isPrinterCompatible(filament.getTitle(), filament.get("compatible_printers"), filament.get("compatible_printers_condition"), printerName, model, nozzle, checker);
            if (!filamentOk) {
                for (ConfigObject obj : CONFIG.filamentConfigs) {
                    if (Slic3rUtils.isPrinterCompatible(obj.getTitle(), obj.get("compatible_printers"), obj.get("compatible_printers_condition"), printerName, model, nozzle, checker)) {
                        CONFIG.presets.put("filament", obj.getTitle());
                        changed = true;
                        break;
                    }
                }
            }
        } finally {
            checker.release();
        }

        if (changed) saveConfig();
    }

    public static ConfigObject buildCurrentConfigObject() {
        ConfigObject singleObject = new ConfigObject();
        ConfigObject printer = SliceBeam.CONFIG.findPrinter(SliceBeam.CONFIG.presets.get("printer"));
        if (printer == null) {
            printer = !SliceBeam.CONFIG.printerConfigs.isEmpty() ? SliceBeam.CONFIG.printerConfigs.get(0) : ConfigObject.createCustomPrinterProfile();
        }
        singleObject.values.putAll(printer.values);

        ConfigObject print = SliceBeam.CONFIG.findPrint(SliceBeam.CONFIG.presets.get("print"));
        if (print != null) {
            singleObject.values.putAll(print.values);
        }
        // TODO: MMU. Detect by printerConfig#getExtruderCount()
        ConfigObject filament = SliceBeam.CONFIG.findFilament(SliceBeam.CONFIG.presets.get("filament"));
        if (filament != null) {
            singleObject.values.putAll(filament.values);
        }

        // Apply unsaved live edits on top so a changed setting is used immediately, without saving.
        singleObject.values.putAll(LIVE_DIFF_PRINTER.values);
        singleObject.values.putAll(LIVE_DIFF_PRINT.values);
        singleObject.values.putAll(LIVE_DIFF_FILAMENT.values);

        PrintConfigDef def = PrintConfigDef.getInstance();
        for (Map.Entry<String, ConfigOptionDef> en : def.options.entrySet()) {
            if (singleObject.get(en.getKey()) == null && !PrintConfigDef.SKIP_DEFAULT_OPTIONS.contains(en.getKey()) && en.getValue().defaultValue != null) {
                singleObject.put(en.getKey(), en.getValue().defaultValue);
            }
        }


        return singleObject;
    }

    public static void genCurrentConfig() throws IOException {
        File cfg = getCurrentConfigFile();
        FileOutputStream fos = new FileOutputStream(cfg);
        ConfigObject singleObject = buildCurrentConfigObject();
        
        // Remember if user wants auto_brim (native engine will restore it at slice time).
        AUTO_BRIM_SELECTED = "auto_brim".equals(singleObject.get("brim_type"));
        // Bed3D native code crashes if it sees 'auto_brim' as it does not understand the enum.
        // We write it out as 'outer_only' to prevent rendering crashes.
        // The native slicer's btAutoBrim is restored by Model.slice() via the AUTO_BRIM_SELECTED flag.
        if (AUTO_BRIM_SELECTED) {
            singleObject.put("brim_type", "outer_only");
        }
        
        fos.write(singleObject.serialize().getBytes(StandardCharsets.UTF_8));
        fos.close();
    }

    public static File getCurrentConfigFile() {
        return new File(INSTANCE.getFilesDir(), "slic3r_current.ini");
    }
}

package ru.ytkab0bp.slicebeam.fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.recycler.SpaceItem;
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class PrintConfigFragment extends ProfileListFragment {
    private List<ProfileListItem> compatItems;
    private String lastPrinter;
    private int lastUid;

    private ConfigObject currentConfig;

    @Override
    protected int getProfileListType() {
        return ConfigObject.PROFILE_LIST_PRINT;
    }

    @Override
    protected boolean useTabs() {
        return true;
    }

    @Override
    protected boolean showFilamentPalette() {
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        onResetConfig();
    }

    @Override
    protected List<ProfileListItem> getItems(boolean filter) {
        List<ConfigObject> list = SliceBeam.CONFIG.printConfigs;
        if (filter) {
            String printer = SliceBeam.CONFIG.presets.get("printer");
            if (Objects.equals(lastPrinter, printer) && compatItems != null && lastUid == SliceBeam.CONFIG_UID) {
                return compatItems;
            }

            List<ConfigObject> nList = new ArrayList<>(list.size());
            ConfigObject printerObj = SliceBeam.CONFIG.findPrinter(printer);
            String model = printerObj != null ? printerObj.get("printer_model") : null;
            String nozzle = printerObj != null ? printerObj.get("printer_variant") : null;
            if (printerObj != null && nozzle == null) nozzle = Slic3rUtils.firstNozzleDiameter(printerObj.get("nozzle_diameter"));
            Slic3rUtils.ConfigChecker checker = new Slic3rUtils.ConfigChecker(printerObj.serialize());
            java.util.Set<String> seenTitles = new java.util.HashSet<>();
            for (ConfigObject obj : list) {
                if (Slic3rUtils.isPrinterCompatible(obj.getTitle(), obj.get("compatible_printers"), obj.get("compatible_printers_condition"), printer, model, nozzle, checker)
                        && Slic3rUtils.layerHeightFitsNozzle(obj.get("layer_height"), nozzle)
                        && Slic3rUtils.lineWidthFitsNozzle(obj.get("line_width"), nozzle)
                        && seenTitles.add(obj.getTitle())) {
                    // The bundled import duplicates every process onto every printer; collapse
                    // identical names so the user sees one clean list for the active nozzle.
                    nList.add(obj);
                }
            }
            checker.release();
            lastPrinter = printer;
            lastUid = SliceBeam.CONFIG_UID;
            return compatItems = (List) nList;
        }
        return (List) list;
    }

    @Override
    protected List<OptionElement> getConfigItems() {
        return OrcaPrintSettingsBuilder.build(this);
    }

    @Override
    protected void cloneCurrentProfile() {
        ConfigObject obj = new ConfigObject(SliceBeam.INSTANCE.getString(R.string.SettingsProfileCopy, currentConfig.getTitle()));
        obj.values.putAll(currentConfig.values);
        currentConfig = new ConfigObject(obj);

        SliceBeam.CONFIG.printConfigs.add(obj);
        SliceBeam.CONFIG.presets.put("print", obj.getTitle());
        SliceBeam.saveConfig();
        SliceBeam.getCurrentConfigFile().delete();

        currentConfig = new ConfigObject(obj);
        dropdownView.setTitle(getCurrentConfig().getTitle());
        compatItems = null;
    }

    @Override
    protected void deleteCurrentProfile() {
        compatItems = null;
        SliceBeam.CONFIG.printConfigs.remove(SliceBeam.CONFIG.findPrint(currentConfig.getTitle()));
        selectItem(getItems(true).get(0));

        dropdownView.setTitle(getCurrentConfig().getTitle());
    }

    @Override
    protected void onApplyConfig(String title) {
        compatItems = null;
        ConfigObject obj = SliceBeam.CONFIG.findPrint(currentConfig.getTitle());
        obj.setTitle(title);
        obj.values.putAll(currentConfig.values);
        currentConfig.setTitle(title);

        SliceBeam.CONFIG.presets.put("print", title);
        SliceBeam.saveConfig();
        SliceBeam.getCurrentConfigFile().delete();

        dropdownView.setTitle(title);
    }

    @Override
    protected void onResetConfig() {
        ConfigObject print = SliceBeam.CONFIG.findPrint(SliceBeam.CONFIG.presets.get("print"));
        if (print != null) {
            currentConfig = new ConfigObject(print);
        } else {
            currentConfig = new ConfigObject(SliceBeam.INSTANCE.getString(R.string.IntroCustomProfileName));
            SliceBeam.CONFIG.printConfigs.add(new ConfigObject(currentConfig));
            SliceBeam.saveConfig();
            SliceBeam.getCurrentConfigFile().delete();
        }
    }

    @Override
    protected ConfigObject getCurrentConfig() {
        return currentConfig;
    }

    @Override
    protected int getTitle() {
        return R.string.SlotPrintConfigTooltip;
    }

    @Override
    protected void selectItem(ProfileListItem item) {
        currentConfig = new ConfigObject((ConfigObject) item);
        SliceBeam.CONFIG.presets.put("print", item.getTitle());
        SliceBeam.saveConfig();
    }
}

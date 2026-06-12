package ru.ytkab0bp.slicebeam.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.recycler.SpaceItem;
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef;
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef;
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.BeamButton;

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
    protected View createBelowFilamentPaletteView(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        BeamButton tower = new BeamButton(ctx);
        tower.setText("Prime tower position");
        tower.setPadding(ViewUtils.dp(14), ViewUtils.dp(8), ViewUtils.dp(14), ViewUtils.dp(8));
        tower.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        tower.setShadowLayer(ViewUtils.dp(2), 0, 0, 0xCC000000);
        tower.setOnClickListener(v -> showPrimeTowerPositionDialog(ctx));
        bar.addView(tower, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{
            rightMargin = ViewUtils.dp(8);
        }});

        BeamButton flush = new BeamButton(ctx);
        flush.setText("Flush volumes");
        flush.setPadding(ViewUtils.dp(14), ViewUtils.dp(8), ViewUtils.dp(14), ViewUtils.dp(8));
        flush.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        flush.setShadowLayer(ViewUtils.dp(2), 0, 0, 0xCC000000);
        flush.setOnClickListener(v -> showFlushVolumesDialog(ctx));
        bar.addView(flush, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private String optionValue(String key) {
        ConfigOptionDef def = PrintConfigDef.getInstance().options.get(key);
        String v = diffObject.has(key) ? diffObject.get(key) : getCurrentConfig().get(key);
        if (v == null && def != null) v = def.defaultValue;
        return v != null ? v : "";
    }

    private void applyOptionValue(String key, String value) {
        ConfigOptionDef def = PrintConfigDef.getInstance().options.get(key);
        if (def != null) {
            updateConfigField(def, -1, value);
        } else {
            diffObject.put(key, value);
        }
        onUpdateConfigItems();
    }

    private static String formatFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) return String.valueOf(Math.round(value));
        return String.format(Locale.US, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static float[] parseFloatList(String value, int minCount, float fallbackOffDiag, boolean diagonalZero) {
        String[] parts = value == null || value.trim().isEmpty() ? new String[0] : value.split("[,;]");
        int count = Math.max(minCount, parts.length);
        float[] out = new float[count];
        int side = (int) Math.round(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            float fallback = diagonalZero && side > 0 && i / side == i % side ? 0f : fallbackOffDiag;
            if (i < parts.length) {
                try {
                    out[i] = Float.parseFloat(parts[i].trim());
                } catch (Exception e) {
                    out[i] = fallback;
                }
            } else {
                out[i] = fallback;
            }
        }
        return out;
    }

    private static String joinFloats(float[] values) {
        StringBuilder sb = new StringBuilder();
        for (float v : values) {
            if (sb.length() > 0) sb.append(',');
            sb.append(formatFloat(v));
        }
        return sb.toString();
    }

    private static EditText numericCell(Context ctx, String value, int widthDp) {
        EditText text = new EditText(ctx);
        text.setSingleLine(true);
        text.setTextColor(ru.ytkab0bp.slicebeam.theme.ThemesRepo.getCurrent() == ru.ytkab0bp.slicebeam.theme.BeamTheme.DARK ? 0xffffffff : 0xff000000);
        text.setHintTextColor(ru.ytkab0bp.slicebeam.theme.ThemesRepo.getCurrent() == ru.ytkab0bp.slicebeam.theme.BeamTheme.DARK ? 0x99ffffff : 0x99000000);
        text.setBackgroundTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.colorAccent)));
        text.setSelectAllOnFocus(true);
        text.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        text.setText(value);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(13);
        text.setPadding(ViewUtils.dp(4), 0, ViewUtils.dp(4), 0);
        text.setMinWidth(ViewUtils.dp(widthDp));
        return text;
    }

    private static TextView smallLabel(Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(ru.ytkab0bp.slicebeam.theme.ThemesRepo.getCurrent() == ru.ytkab0bp.slicebeam.theme.BeamTheme.DARK ? 0xffffffff : 0xff000000);
        label.setTextSize(12);
        label.setPadding(ViewUtils.dp(4), ViewUtils.dp(4), ViewUtils.dp(4), ViewUtils.dp(4));
        return label;
    }

    private static void styleDialogButtons(AlertDialog dialog) {
        int accent = ThemesRepo.getColor(android.R.attr.colorAccent);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent);
    }

    private int flushMatrixSide() {
        int slots = Math.max(2, Math.min(Prefs.MAX_FILAMENT_COLORS, Prefs.getFilamentSlots().size()));
        String value = optionValue("flush_volumes_matrix");
        int existing = 0;
        if (value != null && !value.trim().isEmpty()) {
            existing = (int) Math.ceil(Math.sqrt(value.split("[,;]").length));
        }
        return Math.max(slots, existing);
    }

    private void showFlushVolumesDialog(Context ctx) {
        int n = flushMatrixSide();
        float[] values = parseFloatList(optionValue("flush_volumes_matrix"), n * n, 280f, true);
        EditText[][] fields = new EditText[n][n];

        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(ViewUtils.dp(12), ViewUtils.dp(8), ViewUtils.dp(12), ViewUtils.dp(8));

        TextView help = smallLabel(ctx, "Purge volume in mm³ for each filament change. Rows are FROM, columns are TO. Smaller numbers shrink the prime tower; larger numbers purge more.");
        help.setGravity(Gravity.START);
        grid.addView(help, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(smallLabel(ctx, "From\\To"), new LinearLayout.LayoutParams(ViewUtils.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int col = 0; col < n; col++) {
            header.addView(smallLabel(ctx, String.valueOf(col + 1)), new LinearLayout.LayoutParams(ViewUtils.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        grid.addView(header);

        for (int row = 0; row < n; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(smallLabel(ctx, String.valueOf(row + 1)), new LinearLayout.LayoutParams(ViewUtils.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int col = 0; col < n; col++) {
                EditText text = numericCell(ctx, formatFloat(values[row * n + col]), 72);
                fields[row][col] = text;
                line.addView(text, new LinearLayout.LayoutParams(ViewUtils.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            grid.addView(line);
        }

        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        hScroll.addView(grid);
        ScrollView vScroll = new ScrollView(ctx);
        vScroll.addView(hScroll);

        AlertDialog dialog = new BeamAlertDialogBuilder(ctx)
                .setTitle("Flush volumes")
                .setView(vScroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        styleDialogButtons(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            float[] out = new float[n * n];
            try {
                for (int row = 0; row < n; row++) {
                    for (int col = 0; col < n; col++) {
                        float f = Float.parseFloat(fields[row][col].getText().toString().trim());
                        if (f < 0) throw new NumberFormatException("negative");
                        out[row * n + col] = f;
                    }
                }
            } catch (Exception e) {
                Toast.makeText(ctx, "Enter non-negative numbers for every flush volume", Toast.LENGTH_SHORT).show();
                return;
            }
            applyOptionValue("flush_volumes_matrix", joinFloats(out));
            Toast.makeText(ctx, "Flush volumes updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private int towerPlateCount(float[] xs, float[] ys) {
        return Math.max(1, Math.max(xs.length, ys.length));
    }

    private void showPrimeTowerPositionDialog(Context ctx) {
        float[] xsRaw = parseFloatList(optionValue("wipe_tower_x"), 1, 15f, false);
        float[] ysRaw = parseFloatList(optionValue("wipe_tower_y"), 1, 15f, false);
        int n = towerPlateCount(xsRaw, ysRaw);
        EditText[] xFields = new EditText[n];
        EditText[] yFields = new EditText[n];

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ViewUtils.dp(21), ViewUtils.dp(8), ViewUtils.dp(21), ViewUtils.dp(8));

        TextView help = smallLabel(ctx, "Move the prime tower left-front corner in bed millimeters. Multi-plate projects store one X/Y pair per plate.");
        help.setGravity(Gravity.START);
        list.addView(help, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(smallLabel(ctx, "Plate"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        header.addView(smallLabel(ctx, "X mm"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(smallLabel(ctx, "Y mm"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(header);

        for (int i = 0; i < n; i++) {
            float x = i < xsRaw.length ? xsRaw[i] : xsRaw[xsRaw.length - 1];
            float y = i < ysRaw.length ? ysRaw[i] : ysRaw[ysRaw.length - 1];
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(smallLabel(ctx, String.valueOf(i + 1)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
            xFields[i] = numericCell(ctx, formatFloat(x), 96);
            yFields[i] = numericCell(ctx, formatFloat(y), 96);
            row.addView(xFields[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(yFields[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(row);
        }

        AlertDialog dialog = new BeamAlertDialogBuilder(ctx)
                .setTitle("Prime tower position")
                .setView(list)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        styleDialogButtons(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            float[] xs = new float[n];
            float[] ys = new float[n];
            try {
                for (int i = 0; i < n; i++) {
                    xs[i] = Float.parseFloat(xFields[i].getText().toString().trim());
                    ys[i] = Float.parseFloat(yFields[i].getText().toString().trim());
                }
            } catch (Exception e) {
                Toast.makeText(ctx, "Enter valid X/Y numbers", Toast.LENGTH_SHORT).show();
                return;
            }
            applyOptionValue("wipe_tower_x", joinFloats(xs));
            applyOptionValue("wipe_tower_y", joinFloats(ys));
            Toast.makeText(ctx, "Prime tower position updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
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

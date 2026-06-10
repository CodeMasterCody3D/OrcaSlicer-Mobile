package ru.ytkab0bp.slicebeam.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.List;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.components.FilamentSlotSheet;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.FilamentSlot;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

/**
 * Horizontal bar of filament chips (color + material type) shown on the print-settings screen,
 * between the process-preset dropdown and the category tabs. Tap a chip to edit its color/type,
 * tap + to add another filament (up to {@link Prefs#MAX_FILAMENT_COLORS}).
 */
public class FilamentPaletteBar extends HorizontalScrollView {
    private final LinearLayout strip;
    private List<FilamentSlot> slots;

    public FilamentPaletteBar(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        addView(strip, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        reload();
    }

    public void reload() {
        slots = Prefs.getFilamentSlots();
        rebuild();
    }

    private void persist() {
        Prefs.setFilamentSlots(slots);
    }

    private void rebuild() {
        strip.removeAllViews();
        for (int i = 0; i < slots.size(); i++) {
            strip.addView(chip(i));
        }
        if (slots.size() < Prefs.MAX_FILAMENT_COLORS) {
            strip.addView(addChip());
        }
    }

    private LinearLayout.LayoutParams chipParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            rightMargin = ViewUtils.dp(8);
        }};
    }

    private View chip(int index) {
        Context ctx = getContext();
        FilamentSlot slot = slots.get(index);
        int color = slot.color | 0xFF000000;

        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        TextView square = new TextView(ctx);
        square.setText(String.valueOf(index + 1));
        square.setGravity(Gravity.CENTER);
        square.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        square.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        square.setTextColor(ColorUtils.calculateLuminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(ViewUtils.dp(10));
        if (ColorUtils.calculateLuminance(color) > 0.85f) {
            bg.setStroke(ViewUtils.dp(1), ThemesRepo.getColor(R.attr.dividerColor));
        }
        square.setBackground(bg);
        cell.addView(square, new LinearLayout.LayoutParams(ViewUtils.dp(40), ViewUtils.dp(40)));

        TextView type = new TextView(ctx);
        type.setText(slot.type);
        type.setGravity(Gravity.CENTER);
        type.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        type.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        cell.addView(type, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            topMargin = ViewUtils.dp(2);
        }});

        cell.setOnClickListener(v -> new FilamentSlotSheet(ctx, slot, slots.size() > 1, new FilamentSlotSheet.Listener() {
            @Override
            public void onChanged(FilamentSlot s) {
                persist();
                rebuild();
            }

            @Override
            public void onDelete() {
                slots.remove(index);
                persist();
                rebuild();
            }
        }).show());
        cell.setLayoutParams(chipParams());
        return cell;
    }

    private View addChip() {
        Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        TextView add = new TextView(ctx);
        add.setText("+");
        add.setGravity(Gravity.CENTER);
        add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        add.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(ViewUtils.dp(10));
        bg.setStroke(ViewUtils.dp(2), ThemesRepo.getColor(R.attr.dividerColor));
        add.setBackground(bg);
        cell.addView(add, new LinearLayout.LayoutParams(ViewUtils.dp(40), ViewUtils.dp(40)));

        // Spacer to align with chips that have a type label below the swatch.
        TextView spacer = new TextView(ctx);
        spacer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        spacer.setText(" ");
        cell.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            topMargin = ViewUtils.dp(2);
        }});

        cell.setOnClickListener(v -> {
            FilamentSlot s = new FilamentSlot(0xFF1AC5A2, "PLA");
            new FilamentSlotSheet(ctx, s, false, new FilamentSlotSheet.Listener() {
                boolean added;
                @Override
                public void onChanged(FilamentSlot changed) {
                    if (!added) { slots.add(changed); added = true; }
                    persist();
                    rebuild();
                }
                @Override
                public void onDelete() {}
            }).show();
        });
        cell.setLayoutParams(chipParams());
        return cell;
    }
}

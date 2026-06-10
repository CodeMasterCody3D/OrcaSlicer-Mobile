package ru.ytkab0bp.slicebeam.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrudultora.colorpicker.ColorPickerPopUp;

import java.util.Map;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.CssColors;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

/**
 * Bottom sheet for picking a filament color: a custom color wheel, a row of the most common colors,
 * and the full CSS named-color library. Colors are shown as squares only (no names), per design.
 */
public class ColorChooserSheet extends BottomSheetDialog {
    public interface OnColorChosen {
        void onColorChosen(int color);
    }

    private final int initialColor;
    private final OnColorChosen callback;

    public ColorChooserSheet(Context ctx, int initialColor, OnColorChosen callback) {
        super(ctx);
        this.initialColor = initialColor;
        this.callback = callback;
        build(ctx);
    }

    private void choose(int color) {
        if (callback != null) callback.onColorChosen(color | 0xFF000000);
        dismiss();
    }

    private void build(Context ctx) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(24));
        ll.setBackgroundResource(R.drawable.bottom_sheet_rounded_background);
        ll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemesRepo.getColor(R.attr.dialogBackground)));

        ll.addView(header(ctx, "Custom color"));
        TextView wheel = new TextView(ctx);
        wheel.setText("Open color wheel…");
        wheel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        wheel.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        wheel.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        wheel.setGravity(Gravity.CENTER);
        wheel.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), ThemesRepo.getColor(android.R.attr.colorAccent), 16));
        wheel.setOnClickListener(v -> openWheel(ctx));
        ll.addView(wheel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(48)) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        ll.addView(header(ctx, "Common colors"));
        GridLayout common = new GridLayout(ctx);
        common.setColumnCount(6);
        for (String name : CssColors.COMMON) {
            Integer c = CssColors.ALL.get(name);
            if (c != null) common.addView(swatch(ctx, c));
        }
        ll.addView(common);

        ll.addView(header(ctx, "CSS colors"));
        GridLayout css = new GridLayout(ctx);
        css.setColumnCount(6);
        for (Map.Entry<String, Integer> e : CssColors.ALL.entrySet()) {
            css.addView(swatch(ctx, e.getValue()));
        }
        ll.addView(css);

        scroll.addView(ll);
        setContentView(scroll);
    }

    private TextView header(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        t.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        t.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        t.setPadding(0, ViewUtils.dp(8), 0, ViewUtils.dp(8));
        return t;
    }

    private View swatch(Context ctx, int color) {
        View v = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color | 0xFF000000);
        bg.setCornerRadius(ViewUtils.dp(8));
        // Outline so near-white swatches stay visible.
        if (ColorUtils.calculateLuminance(color | 0xFF000000) > 0.85f) {
            bg.setStroke(ViewUtils.dp(1), ThemesRepo.getColor(R.attr.dividerColor));
        }
        v.setBackground(bg);
        v.setOnClickListener(view -> choose(color));
        int size = ViewUtils.dp(44);
        int margin = ViewUtils.dp(4);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = size;
        lp.height = size;
        lp.setMargins(margin, margin, margin, margin);
        v.setLayoutParams(lp);
        return v;
    }

    private void openWheel(Context ctx) {
        dismiss();
        new BeamColorPickerPopUp(ctx)
                .setDialogTitle("Custom color")
                .setDefaultColor(initialColor)
                .setShowAlpha(false)
                .setOnPickColorListener(new ColorPickerPopUp.OnPickColorListener() {
                    @Override
                    public void onColorPicked(int color) {
                        if (callback != null) callback.onColorChosen(color | 0xFF000000);
                    }

                    @Override
                    public void onCancel() {}
                })
                .show();
    }
}

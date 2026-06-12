package ru.ytkab0bp.slicebeam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

@SuppressLint("ViewConstructor")
public class VariableLayerHeightModeView extends FrameLayout {
    private final GLView glView;
    private final Runnable onExit;
    private final TextView qualityText;
    private final SeekBar seekBar;
    private final TextView statusValue;

    public VariableLayerHeightModeView(Context ctx, GLView glView, Runnable onExit) {
        super(ctx);
        this.glView = glView;
        this.onExit = onExit;

        // Bottom Panel
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16));
        panel.setBackgroundResource(R.drawable.bottom_sheet_rounded_background);
        panel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemesRepo.getColor(R.attr.dialogBackground)));

        // Title
        TextView title = new TextView(ctx);
        title.setText("Variable Layer Height");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        title.setPadding(0, 0, 0, ViewUtils.dp(8));
        panel.addView(title);

        // Status Row
        statusValue = createValueRow(ctx, panel, "Profile Status:");

        // Spacer
        View spacer1 = new View(ctx);
        panel.addView(spacer1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(8)));

        // Quality Value Text
        qualityText = new TextView(ctx);
        qualityText.setText("Quality: 0.50 (Lower is higher quality)");
        qualityText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        qualityText.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        panel.addView(qualityText);

        // SeekBar
        seekBar = new SeekBar(ctx);
        seekBar.setMax(100);
        seekBar.setProgress(50);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double val = progress / 100.0;
                qualityText.setText(String.format(Locale.US, "Quality: %.2f (Lower is higher quality)", val));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(seekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(32)));

        // Actions: Clear + Apply Adaptive + Done
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ViewUtils.dp(16), 0, 0);

        TextView clear = textButton(ctx, "Clear", false);
        clear.setOnClickListener(v -> {
            int selectedObject = glView.getRenderer().getSelectedObject();
            if (selectedObject != -1) {
                glView.getRenderer().getModel().clearAdaptiveLayerHeight(selectedObject);
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Layer height profile cleared"));
                updateStatus();
            } else {
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("No object selected"));
            }
        });

        TextView apply = textButton(ctx, "Adaptive", true);
        apply.setOnClickListener(v -> {
            int selectedObject = glView.getRenderer().getSelectedObject();
            if (selectedObject != -1) {
                try {
                    SliceBeam.genCurrentConfig();
                    File cfg = SliceBeam.getCurrentConfigFile();
                    float q = seekBar.getProgress() / 100.0f;
                    glView.getRenderer().getModel().applyAdaptiveLayerHeight(selectedObject, cfg.getAbsolutePath(), q);
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Adaptive layer height applied successfully"));
                    updateStatus();
                } catch (Exception e) {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Failed to calculate adaptive layer height: " + e.getMessage()));
                }
            } else {
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("No object selected"));
            }
        });

        TextView done = textButton(ctx, "Done", false);
        done.setOnClickListener(v -> {
            if (onExit != null) onExit.run();
        });

        actions.addView(clear, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ rightMargin = ViewUtils.dp(4); }});
        actions.addView(apply, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1.2f) {{ leftMargin = ViewUtils.dp(4); rightMargin = ViewUtils.dp(4); }});
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ leftMargin = ViewUtils.dp(4); }});
        panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.BOTTOM;
        }});

        updateStatus();
    }

    private TextView createValueRow(Context ctx, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ViewUtils.dp(4), 0, ViewUtils.dp(4));

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        lbl.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        row.addView(lbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(ctx);
        val.setText("--");
        val.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        val.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        val.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        row.addView(val, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return val;
    }

    private void updateStatus() {
        int selectedObject = glView.getRenderer().getSelectedObject();
        if (selectedObject != -1) {
            boolean hasProfile = glView.getRenderer().getModel().hasAdaptiveLayerHeight(selectedObject);
            if (hasProfile) {
                statusValue.setText("Adaptive");
                statusValue.setTextColor(ThemesRepo.getColor(android.R.attr.colorAccent));
            } else {
                statusValue.setText("Default (Constant)");
                statusValue.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            }
        } else {
            statusValue.setText("No object selected");
            statusValue.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        }
    }

    private TextView textButton(Context ctx, String text, boolean accent) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(12));
        if (accent) {
            bg.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
            t.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        } else {
            bg.setColor(ThemesRepo.getColor(android.R.attr.colorControlHighlight));
            t.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        }
        t.setBackground(bg);
        return t;
    }
}

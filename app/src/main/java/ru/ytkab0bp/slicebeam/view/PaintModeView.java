package ru.ytkab0bp.slicebeam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.List;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.render.GLRenderer;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.FilamentSlot;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

/**
 * Overlay shown while painting an object's colors: a filament palette, brush/bucket/height tools,
 * brush size, clear/done, and a floating button to toggle between painting and moving the view.
 */
@SuppressLint("ViewConstructor")
public class PaintModeView extends FrameLayout {
    private final GLView glView;
    private final Runnable onExit;
    private final List<FilamentSlot> slots;

    private LinearLayout swatchRow;
    private int selectedFilament = 2; // 1-based; 1 = base color
    private SeekBar brushSize;
    private ImageView fab;
    private boolean brushActive = true;

    private TextView brushBtn, bucketBtn, heightBtn;
    private TextView heightBandLabel;
    private SeekBar heightBandWidthSeek;
    private TextView bucketAngleLabel;
    private SeekBar bucketAngleSeek;
    private TextView brushTypeBtn;

    public PaintModeView(Context ctx, GLView glView, Runnable onExit) {
        super(ctx);
        this.glView = glView;
        this.onExit = onExit;
        this.slots = Prefs.getFilamentSlots();
        if (slots.size() < 2) selectedFilament = 1;
        build(ctx);
        glView.setPaintBrushActive(true);
    }

    private void runOnGl(Runnable r) {
        glView.queueEvent(() -> {
            r.run();
            glView.requestRender();
        });
    }

    private void build(Context ctx) {
        // Floating move/paint toggle
        fab = new ImageView(ctx);
        updateFab();
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
        fbg.setShape(GradientDrawable.OVAL);
        fab.setBackground(fbg);
        fab.setPadding(ViewUtils.dp(14), ViewUtils.dp(14), ViewUtils.dp(14), ViewUtils.dp(14));
        fab.setOnClickListener(v -> {
            brushActive = !brushActive;
            glView.setPaintBrushActive(brushActive);
            updateFab();
        });
        addView(fab, new LayoutParams(ViewUtils.dp(56), ViewUtils.dp(56)) {{
            gravity = Gravity.END | Gravity.TOP;
            topMargin = ViewUtils.dp(12);
            rightMargin = ViewUtils.dp(12);
        }});

        // Bottom panel
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ViewUtils.dp(12), ViewUtils.dp(10), ViewUtils.dp(12), ViewUtils.dp(12));
        panel.setBackgroundResource(R.drawable.bottom_sheet_rounded_background);
        panel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemesRepo.getColor(R.attr.dialogBackground)));

        // Base filament selector
        panel.addView(label(ctx, "Base filament"));
        HorizontalScrollView baseScroll = new HorizontalScrollView(ctx);
        baseScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout baseRow = new LinearLayout(ctx);
        baseRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < slots.size(); i++) {
            final int filament = i + 1;
            int color = slots.get(i).color | 0xFF000000;
            View sw = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(ViewUtils.dp(10));
            if (ColorUtils.calculateLuminance(color) > 0.85f) bg.setStroke(ViewUtils.dp(1), ThemesRepo.getColor(R.attr.dividerColor));
            sw.setBackground(bg);
            sw.setOnClickListener(v -> runOnGl(() -> {
                int obj = glView.getRenderer().getPaintObject();
                if (obj != -1) glView.getRenderer().setObjectBaseFilament(obj, filament);
            }));
            baseRow.addView(sw, new LinearLayout.LayoutParams(ViewUtils.dp(36), ViewUtils.dp(36)) {{ rightMargin = ViewUtils.dp(8); }});
        }
        baseScroll.addView(baseRow);
        panel.addView(baseScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(10);
        }});

        // Paint color swatches
        panel.addView(label(ctx, "Paint with"));
        HorizontalScrollView scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        swatchRow = new LinearLayout(ctx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(swatchRow);
        rebuildSwatches(ctx);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(10);
        }});

        // Tools row
        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        brushBtn = toolButton(ctx, "Brush", GLRenderer.PAINT_TOOL_BRUSH);
        bucketBtn = toolButton(ctx, "Bucket", GLRenderer.PAINT_TOOL_BUCKET);
        heightBtn = toolButton(ctx, "Height", GLRenderer.PAINT_TOOL_HEIGHT);
        tools.addView(brushBtn, toolLp());
        tools.addView(bucketBtn, toolLp());
        tools.addView(heightBtn, toolLp());
        panel.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        // Brush size
        brushSize = new SeekBar(ctx);
        brushSize.setMax(20);
        brushSize.setProgress(4);
        brushSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                double r = Math.max(1, p);
                runOnGl(() -> glView.getRenderer().setPaintBrushRadius(r));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        // Brush type toggle (Circle / Sphere)
        brushTypeBtn = new TextView(ctx);
        brushTypeBtn.setGravity(Gravity.CENTER);
        brushTypeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        brushTypeBtn.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        brushTypeBtn.setPadding(ViewUtils.dp(8), ViewUtils.dp(8), ViewUtils.dp(8), ViewUtils.dp(8));
        updateBrushTypeBtnStyle();
        brushTypeBtn.setOnClickListener(v -> {
            runOnGl(() -> {
                boolean s = !glView.getRenderer().isBrushSphere();
                glView.getRenderer().setBrushSphere(s);
                post(() -> {
                    updateBrushTypeBtnStyle();
                });
            });
        });
        panel.addView(brushTypeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(40)) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        // Height band label & seek
        heightBandLabel = label(ctx, "Height band: 10.0 mm");
        panel.addView(heightBandLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        heightBandWidthSeek = new SeekBar(ctx);
        heightBandWidthSeek.setMax(100); // 1 to 100 mm
        heightBandWidthSeek.setProgress(10);
        heightBandWidthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                double w = Math.max(1, p);
                heightBandLabel.setText("Height band: " + String.format(java.util.Locale.US, "%.1f", w) + " mm");
                runOnGl(() -> glView.getRenderer().setHeightBandWidth(w));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(heightBandWidthSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        // Bucket angle label & seek
        bucketAngleLabel = label(ctx, "Angle limit: 30°");
        panel.addView(bucketAngleLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bucketAngleSeek = new SeekBar(ctx);
        bucketAngleSeek.setMax(90); // 1 to 90 degrees
        bucketAngleSeek.setProgress(30);
        bucketAngleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                double a = Math.max(1, p);
                bucketAngleLabel.setText("Angle limit: " + (int) a + "°");
                runOnGl(() -> glView.getRenderer().setBucketAngleThreshold(a));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        panel.addView(bucketAngleSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(8);
        }});

        selectTool(GLRenderer.PAINT_TOOL_BRUSH);

        // Clear + Done
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView clear = textButton(ctx, "Clear", false);
        clear.setOnClickListener(v -> runOnGl(() -> glView.getRenderer().clearPaint()));
        TextView done = textButton(ctx, "Done", true);
        done.setOnClickListener(v -> {
            runOnGl(() -> glView.getRenderer().endPaint(true));
            glView.setPaintBrushActive(false);
            if (onExit != null) onExit.run();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ rightMargin = ViewUtils.dp(6); }});
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ leftMargin = ViewUtils.dp(6); }});
        panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.BOTTOM;
        }});
    }

    private TextView label(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        t.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        t.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        t.setPadding(ViewUtils.dp(2), 0, 0, ViewUtils.dp(4));
        return t;
    }

    private void updateFab() {
        fab.setImageResource(brushActive ? R.drawable.edit_outline_28 : R.drawable.rectangle_hand_point_up_28);
        fab.setColorFilter(ThemesRepo.getColor(R.attr.textColorOnAccent));
    }

    private LinearLayout.LayoutParams toolLp() {
        return new LinearLayout.LayoutParams(0, ViewUtils.dp(44), 1f) {{ leftMargin = rightMargin = ViewUtils.dp(4); }};
    }

    private TextView toolButton(Context ctx, String label, int tool) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setOnClickListener(v -> {
            selectTool(tool);
            runOnGl(() -> glView.getRenderer().setPaintTool(tool));
        });
        return t;
    }

    private void updateBrushTypeBtnStyle() {
        boolean sphere = false;
        if (glView != null && glView.getRenderer() != null) {
            sphere = glView.getRenderer().isBrushSphere();
        }
        brushTypeBtn.setText(sphere ? "Brush Type: Sphere (3D)" : "Brush Type: Circle (Projected)");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(12));
        bg.setColor(ThemesRepo.getColor(android.R.attr.colorControlHighlight));
        brushTypeBtn.setBackground(bg);
        brushTypeBtn.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
    }

    private void selectTool(int tool) {
        styleTool(brushBtn, tool == GLRenderer.PAINT_TOOL_BRUSH);
        styleTool(bucketBtn, tool == GLRenderer.PAINT_TOOL_BUCKET);
        styleTool(heightBtn, tool == GLRenderer.PAINT_TOOL_HEIGHT);
        
        brushSize.setVisibility(tool == GLRenderer.PAINT_TOOL_BRUSH ? VISIBLE : GONE);
        if (brushTypeBtn != null) {
            brushTypeBtn.setVisibility(tool == GLRenderer.PAINT_TOOL_BRUSH ? VISIBLE : GONE);
        }
        
        if (heightBandLabel != null && heightBandWidthSeek != null) {
            heightBandLabel.setVisibility(tool == GLRenderer.PAINT_TOOL_HEIGHT ? VISIBLE : GONE);
            heightBandWidthSeek.setVisibility(tool == GLRenderer.PAINT_TOOL_HEIGHT ? VISIBLE : GONE);
        }
        
        if (bucketAngleLabel != null && bucketAngleSeek != null) {
            bucketAngleLabel.setVisibility(tool == GLRenderer.PAINT_TOOL_BUCKET ? VISIBLE : GONE);
            bucketAngleSeek.setVisibility(tool == GLRenderer.PAINT_TOOL_BUCKET ? VISIBLE : GONE);
        }
    }

    private void styleTool(TextView t, boolean sel) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(12));
        bg.setColor(sel ? ThemesRepo.getColor(android.R.attr.colorAccent) : ThemesRepo.getColor(android.R.attr.colorControlHighlight));
        t.setBackground(bg);
        t.setTextColor(sel ? ThemesRepo.getColor(R.attr.textColorOnAccent) : ThemesRepo.getColor(android.R.attr.textColorPrimary));
    }

    private TextView textButton(Context ctx, String label, boolean accent) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        t.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(16));
        bg.setColor(accent ? ThemesRepo.getColor(android.R.attr.colorAccent) : ThemesRepo.getColor(android.R.attr.colorControlHighlight));
        t.setBackground(bg);
        t.setTextColor(accent ? ThemesRepo.getColor(R.attr.textColorOnAccent) : ThemesRepo.getColor(android.R.attr.textColorPrimary));
        return t;
    }

    private void rebuildSwatches(Context ctx) {
        swatchRow.removeAllViews();
        for (int i = 0; i < slots.size(); i++) {
            int filament = i + 1;
            int color = slots.get(i).color | 0xFF000000;
            TextView sw = new TextView(ctx);
            sw.setText(String.valueOf(filament));
            sw.setGravity(Gravity.CENTER);
            sw.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            sw.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            sw.setTextColor(ColorUtils.calculateLuminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
            applySwatchBg(sw, color, filament == selectedFilament);
            sw.setOnClickListener(v -> {
                selectedFilament = filament;
                runOnGl(() -> glView.getRenderer().setPaintFilament(filament));
                rebuildSwatches(ctx);
            });
            swatchRow.addView(sw, new LinearLayout.LayoutParams(ViewUtils.dp(44), ViewUtils.dp(44)) {{ rightMargin = ViewUtils.dp(8); }});
        }
    }

    private void applySwatchBg(View v, int color, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(ViewUtils.dp(10));
        if (selected) {
            bg.setStroke(ViewUtils.dp(3), ThemesRepo.getColor(android.R.attr.colorAccent));
        } else if (ColorUtils.calculateLuminance(color) > 0.85f) {
            bg.setStroke(ViewUtils.dp(1), ThemesRepo.getColor(R.attr.dividerColor));
        }
        v.setBackground(bg);
    }
}

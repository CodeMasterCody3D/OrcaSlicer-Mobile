package ru.ytkab0bp.slicebeam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.events.MeasurePointsChangedEvent;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

@SuppressLint("ViewConstructor")
public class MeasureModeView extends FrameLayout {
    private final GLView glView;
    private final Runnable onExit;
    private final TextView distanceValue;
    private final TextView dxValue;
    private final TextView dyValue;
    private final TextView dzValue;
    private static final DecimalFormat format = new DecimalFormat("0.##");

    public MeasureModeView(Context ctx, GLView glView, Runnable onExit) {
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
        title.setText("Measure Tool");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        title.setPadding(0, 0, 0, ViewUtils.dp(12));
        panel.addView(title);

        // Distance rows
        distanceValue = createValueRow(ctx, panel, "Distance:");
        dxValue = createValueRow(ctx, panel, "ΔX:");
        dyValue = createValueRow(ctx, panel, "ΔY:");
        dzValue = createValueRow(ctx, panel, "ΔZ:");

        // Actions: Clear + Done
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ViewUtils.dp(16), 0, 0);

        TextView clear = textButton(ctx, "Clear", false);
        clear.setOnClickListener(v -> {
            glView.queueEvent(() -> {
                glView.getRenderer().clearMeasurePoints();
                glView.requestRender();
            });
            updateValues(null, null);
        });

        TextView done = textButton(ctx, "Done", true);
        done.setOnClickListener(v -> {
            glView.queueEvent(() -> {
                glView.getRenderer().setInMeasureMode(false);
                glView.getRenderer().clearMeasurePoints();
                glView.requestRender();
            });
            if (onExit != null) onExit.run();
        });

        actions.addView(clear, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ rightMargin = ViewUtils.dp(6); }});
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewUtils.dp(48), 1f) {{ leftMargin = ViewUtils.dp(6); }});
        panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.BOTTOM;
        }});

        updateValues(null, null);
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

    @SuppressLint("SetTextI18n")
    private void updateValues(Vec3d a, Vec3d b) {
        if (a == null || b == null) {
            distanceValue.setText("--");
            dxValue.setText("--");
            dyValue.setText("--");
            dzValue.setText("--");
        } else {
            double distance = a.distance(b);
            double dx = Math.abs(b.x - a.x);
            double dy = Math.abs(b.y - a.y);
            double dz = Math.abs(b.z - a.z);

            distanceValue.setText(format.format(distance) + " mm");
            dxValue.setText(format.format(dx) + " mm");
            dyValue.setText(format.format(dy) + " mm");
            dzValue.setText(format.format(dz) + " mm");
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        SliceBeam.EVENT_BUS.registerListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        SliceBeam.EVENT_BUS.unregisterListener(this);
    }

    @EventHandler(runOnMainThread = true)
    public void onMeasurePointsChanged(MeasurePointsChangedEvent e) {
        updateValues(e.pointA, e.pointB);
    }
}

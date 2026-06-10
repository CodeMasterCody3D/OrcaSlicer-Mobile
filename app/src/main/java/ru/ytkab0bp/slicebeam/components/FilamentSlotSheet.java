package ru.ytkab0bp.slicebeam.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Arrays;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.FilamentSlot;
import ru.ytkab0bp.slicebeam.utils.FilamentTypes;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

/** Editor for one filament: choose its material type and color, or delete it. */
public class FilamentSlotSheet extends BottomSheetDialog {
    public interface Listener {
        void onChanged(FilamentSlot slot);
        void onDelete();
    }

    private final FilamentSlot slot;
    private final boolean canDelete;
    private final Listener listener;

    private View preview;
    private TextView typeValue;

    public FilamentSlotSheet(Context ctx, FilamentSlot slot, boolean canDelete, Listener listener) {
        super(ctx);
        this.slot = slot;
        this.canDelete = canDelete;
        this.listener = listener;
        build(ctx);
    }

    private void build(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(24));
        ll.setBackgroundResource(R.drawable.bottom_sheet_rounded_background);
        ll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemesRepo.getColor(R.attr.dialogBackground)));

        TextView title = new TextView(ctx);
        title.setText("Filament");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        ll.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = ViewUtils.dp(12);
        }});

        preview = new View(ctx);
        ll.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(56)) {{
            bottomMargin = ViewUtils.dp(16);
        }});
        refreshPreview();

        ll.addView(actionRow(ctx, "Color", () -> new ColorChooserSheet(ctx, slot.color, c -> {
            slot.color = c;
            refreshPreview();
            if (listener != null) listener.onChanged(slot);
        }).show()));

        typeValue = new TextView(ctx);
        typeValue.setText(slot.type);
        ll.addView(actionRowWithValue(ctx, "Filament type", typeValue, () -> {
            new BeamAlertDialogBuilder(ctx)
                    .setTitle("Filament type")
                    .setItems(FilamentTypes.ALL, (dialog, which) -> {
                        slot.type = FilamentTypes.ALL[which];
                        typeValue.setText(slot.type);
                        if (listener != null) listener.onChanged(slot);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }));

        if (canDelete) {
            TextView del = new TextView(ctx);
            del.setText("Remove filament");
            del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            del.setGravity(Gravity.CENTER);
            del.setTextColor(0xFFE5533C);
            del.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 16));
            del.setOnClickListener(v -> {
                if (listener != null) listener.onDelete();
                dismiss();
            });
            ll.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(48)) {{
                topMargin = ViewUtils.dp(8);
            }});
        }

        setContentView(ll);
    }

    private void refreshPreview() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(slot.color | 0xFF000000);
        bg.setCornerRadius(ViewUtils.dp(12));
        if (ColorUtils.calculateLuminance(slot.color | 0xFF000000) > 0.85f) {
            bg.setStroke(ViewUtils.dp(1), ThemesRepo.getColor(R.attr.dividerColor));
        }
        preview.setBackground(bg);
    }

    private View actionRow(Context ctx, String label, Runnable onClick) {
        return actionRowWithValue(ctx, label, null, onClick);
    }

    private View actionRowWithValue(Context ctx, String label, TextView valueView, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ViewUtils.dp(4), ViewUtils.dp(12), ViewUtils.dp(4), ViewUtils.dp(12));
        row.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 12));
        row.setOnClickListener(v -> onClick.run());

        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        t.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (valueView != null) {
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            valueView.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            row.addView(valueView);
        }
        return row;
    }
}

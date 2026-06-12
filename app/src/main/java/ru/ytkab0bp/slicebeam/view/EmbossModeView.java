package ru.ytkab0bp.slicebeam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.events.EmbossSurfaceClickedEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

@SuppressLint("ViewConstructor")
public class EmbossModeView extends FrameLayout {
    private final GLView glView;
    private final Runnable onExit;
    private final EditText textInput;
    private final EditText sizeInput;
    private final EditText depthInput;
    private final TextView btnAdd;
    private final TextView btnSub;

    private boolean isAddMode = true;
    private String fontFilePath = null;

    public EmbossModeView(Context ctx, GLView glView, Runnable onExit) {
        super(ctx);
        this.glView = glView;
        this.onExit = onExit;

        prepareFontFile(ctx);

        // Bottom Panel
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16), ViewUtils.dp(16));
        panel.setBackgroundResource(R.drawable.bottom_sheet_rounded_background);
        panel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemesRepo.getColor(R.attr.dialogBackground)));

        // Title
        TextView title = new TextView(ctx);
        title.setText("Emboss Text");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        title.setPadding(0, 0, 0, ViewUtils.dp(4));
        panel.addView(title);

        // Subtitle instruction
        TextView subtitle = new TextView(ctx);
        subtitle.setText("Enter settings and tap on the model surface to place text");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        subtitle.setPadding(0, 0, 0, ViewUtils.dp(12));
        panel.addView(subtitle);

        // Text input field
        textInput = new EditText(ctx);
        textInput.setHint("Text to emboss");
        textInput.setText("OrcaSlicer Mobile");
        textInput.setSingleLine(true);
        panel.addView(createStyledInput(ctx, "Text:", textInput), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Settings row (Size + Depth)
        LinearLayout settingsRow = new LinearLayout(ctx);
        settingsRow.setOrientation(LinearLayout.HORIZONTAL);
        settingsRow.setPadding(0, ViewUtils.dp(8), 0, ViewUtils.dp(8));

        sizeInput = new EditText(ctx);
        sizeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sizeInput.setText("10.0");
        View sizeInputView = createStyledInput(ctx, "Font Size (mm):", sizeInput);

        depthInput = new EditText(ctx);
        depthInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        depthInput.setText("1.0");
        View depthInputView = createStyledInput(ctx, "Depth (mm):", depthInput);

        settingsRow.addView(sizeInputView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{ rightMargin = ViewUtils.dp(6); }});
        settingsRow.addView(depthInputView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{ leftMargin = ViewUtils.dp(6); }});
        panel.addView(settingsRow);

        // Mode row (Add vs Subtract)
        TextView modeLbl = new TextView(ctx);
        modeLbl.setText("Operation:");
        modeLbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        modeLbl.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        panel.addView(modeLbl);

        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, 0, 0, ViewUtils.dp(16));

        btnAdd = textButton(ctx, "Raise (Add)", true);
        btnSub = textButton(ctx, "Engrave (Subtract)", false);

        btnAdd.setOnClickListener(v -> {
            isAddMode = true;
            updateModeButtons();
        });
        btnSub.setOnClickListener(v -> {
            isAddMode = false;
            updateModeButtons();
        });

        modeRow.addView(btnAdd, new LinearLayout.LayoutParams(0, ViewUtils.dp(44), 1f) {{ rightMargin = ViewUtils.dp(4); }});
        modeRow.addView(btnSub, new LinearLayout.LayoutParams(0, ViewUtils.dp(44), 1f) {{ leftMargin = ViewUtils.dp(4); }});
        panel.addView(modeRow);

        // Action done button
        TextView done = textButton(ctx, "Done", true);
        done.setOnClickListener(v -> {
            if (onExit != null) onExit.run();
        });
        panel.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(48)));

        addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.BOTTOM;
        }});

        updateModeButtons();
    }

    private View createStyledInput(Context ctx, String label, EditText et) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, ViewUtils.dp(4), 0, ViewUtils.dp(4));

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        lbl.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary));
        lbl.setPadding(0, 0, 0, ViewUtils.dp(4));
        container.addView(lbl);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(ThemesRepo.getColor(android.R.attr.colorControlHighlight));
        gd.setCornerRadius(ViewUtils.dp(8));
        et.setBackground(gd);
        et.setPadding(ViewUtils.dp(12), ViewUtils.dp(10), ViewUtils.dp(12), ViewUtils.dp(10));
        et.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        container.addView(et);
        return container;
    }

    private void updateModeButtons() {
        setButtonState(btnAdd, isAddMode);
        setButtonState(btnSub, !isAddMode);
    }

    private void setButtonState(TextView t, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(10));
        if (active) {
            bg.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
            t.setTextColor(ThemesRepo.getColor(R.attr.textColorOnAccent));
        } else {
            bg.setColor(ThemesRepo.getColor(android.R.attr.colorControlHighlight));
            t.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
        }
        t.setBackground(bg);
    }

    private TextView textButton(Context ctx, String text, boolean accent) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ViewUtils.dp(10));
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

    private void prepareFontFile(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "roboto_medium.ttf");
            if (!f.exists()) {
                try (java.io.InputStream in = ctx.getAssets().open("font/roboto_medium.ttf");
                     java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
            fontFilePath = f.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    public void onEmbossSurfaceClicked(EmbossSurfaceClickedEvent e) {
        int selectedObject = glView.getRenderer().getSelectedObject();
        if (selectedObject == -1) {
            SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Please select an object first"));
            return;
        }

        if (fontFilePath == null) {
            prepareFontFile(getContext());
        }

        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            text = "text";
        }

        float size = 10.0f;
        try {
            size = Float.parseFloat(sizeInput.getText().toString());
        } catch (Exception ex) {}

        float depth = 1.0f;
        try {
            depth = Float.parseFloat(depthInput.getText().toString());
        } catch (Exception ex) {}

        int type = isAddMode ? 0 : 1; // 0 = MODEL_PART, 1 = NEGATIVE_VOLUME

        final String finalText = text;
        final float finalSize = size;
        final float finalDepth = depth;
        final int finalType = type;

        glView.queueEvent(() -> {
            try {
                glView.getRenderer().getModel().embossText(selectedObject, fontFilePath, finalText, finalSize, finalDepth, finalType, e.position, e.normal);
                glView.getRenderer().invalidateGlModel(selectedObject);
                glView.requestRender();
                ViewUtils.postOnMainThread(() -> {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Text embossed successfully"));
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                ViewUtils.postOnMainThread(() -> {
                    SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent("Failed to emboss: " + ex.getMessage()));
                });
            }
        });
    }
}

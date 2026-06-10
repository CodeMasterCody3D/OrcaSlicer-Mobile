package ru.ytkab0bp.slicebeam.components.bed_menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.theme.IThemeView;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.RandomUtils;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;

public class BedMenuItem extends SimpleRecyclerItem<BedMenuItem.BedMenuItemHolderView> {
    public final int titleRes;
    public final int iconRes;
    public boolean isSingleLine;

    public boolean isEnabled = true;
    public boolean isChecked = false;
    public boolean isCheckable = false;
    public boolean isShiny = false;
    public float titleTextSizeDp = 9.5f;
    public View.OnClickListener clickListener;
    public CompoundButton.OnCheckedChangeListener checkedChangeListener;

    public BedMenuItem(int titleRes, int iconRes) {
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }

    public BedMenuItem onClick(View.OnClickListener listener) {
        clickListener = listener;
        return this;
    }

    public BedMenuItem setCheckable(CompoundButton.OnCheckedChangeListener checkedChangeListener, boolean checked) {
        this.checkedChangeListener = checkedChangeListener;
        isCheckable = true;
        isChecked = checked;
        return this;
    }

    public BedMenuItem setEnabled(boolean enabled) {
        isEnabled = enabled;
        return this;
    }

    public BedMenuItem setShiny(boolean shiny) {
        isShiny = shiny;
        return this;
    }

    public BedMenuItem setSingleLine(boolean singleLine) {
        isSingleLine = singleLine;
        return this;
    }

    public BedMenuItem setTitleTextSize(float dp) {
        titleTextSizeDp = dp;
        return this;
    }

    @Override
    public BedMenuItemHolderView onCreateView(Context ctx) {
        return new BedMenuItemHolderView(ctx);
    }

    @Override
    public void onBindView(BedMenuItemHolderView view) {
        view.bind(this);
    }

    public final static class BedMenuItemHolderView extends LinearLayout implements IThemeView {
        private final static float IN_BOUND = 0.05f;
        private final static float OUT_BOUND = 0.1f;

        private ImageView icon;
        private TextView title;

        private Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Path path = new Path();
        private Path path2 = new Path();
        private float checkedProgress;
        private boolean enabled;
        private boolean shiny;
        private List<Sparkle> sparkles;
        private long lastDraw;
        private Drawable sparkleDrawable;

        public BedMenuItemHolderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            icon = new ImageView(context);
            addView(icon, new LinearLayout.LayoutParams(ViewUtils.dp(24), ViewUtils.dp(24)));

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9.5f);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                topMargin = ViewUtils.dp(2);
            }});

            setPadding(ViewUtils.dp(8), ViewUtils.dp(6), ViewUtils.dp(8), ViewUtils.dp(6));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT) {{
                leftMargin = topMargin = bottomMargin = ViewUtils.dp(6);
            }});
            setClipToPadding(false);
            setClipChildren(false);
            setWillNotDraw(false);
            onApplyTheme();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            long dt = Math.min(System.currentTimeMillis() - lastDraw, 16);
            lastDraw = System.currentTimeMillis();

            int rad = ViewUtils.dp(16);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), rad, rad, bgPaint);

            if (enabled && checkedProgress != 0f) {
                if (checkedProgress == 1f) {
                    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), rad, rad, accentPaint);
                } else {
                    path.rewind();
                    path.addRoundRect(0, 0, getWidth(), getHeight(), rad, rad, Path.Direction.CW);

                    canvas.save();
                    canvas.clipPath(path);
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (float) (Math.sqrt(getWidth() * getWidth() + getHeight() * getHeight()) / 2f * checkedProgress), accentPaint);
                    canvas.restore();
                }
            }

            super.draw(canvas);

            if (shiny) {
                float side = Math.min(getWidth(), getHeight());
                canvas.save();
                if (sparkles == null) sparkles = new ArrayList<>();
                if (sparkleDrawable == null) {
                    sparkleDrawable = ContextCompat.getDrawable(SliceBeam.INSTANCE, R.drawable.sparkle_28);
                    sparkleDrawable.setColorFilter(new PorterDuffColorFilter(ThemesRepo.getColor(android.R.attr.colorAccent), PorterDuff.Mode.SRC_IN));
                }

                float p = dt / 1000f;
                for (Iterator<Sparkle> iterator = sparkles.iterator(); iterator.hasNext(); ) {
                    Sparkle sparkle = iterator.next();
                    sparkle.position.x += sparkle.velocity.x * p;
                    sparkle.position.y += sparkle.velocity.y * p;
                    sparkle.velocity.x *= 0.9999f;
                    sparkle.velocity.y *= 0.9999f;
                    sparkle.living += dt;

                    int size = (int) (side * sparkle.size);

                    float fadems = 200;
                    if ((sparkle.position.x - sparkle.size > 0 && sparkle.position.x + sparkle.size < 1f) &&
                        sparkle.lifetime - sparkle.living > fadems) {
                        sparkle.living = (long) (sparkle.lifetime - fadems);
                    }
                    if (sparkle.living >= sparkle.lifetime) {
                        iterator.remove();
                    } else {
                        float alpha = sparkle.living < fadems ? sparkle.living / fadems : sparkle.living > sparkle.lifetime - fadems ? (sparkle.lifetime - sparkle.living) / fadems : 1f;
                        canvas.saveLayerAlpha(-OUT_BOUND * side, -OUT_BOUND * side, getWidth() + OUT_BOUND * side, getHeight() + OUT_BOUND * side, (int) (alpha * sparkle.alpha * 0xFF));
                        canvas.translate(sparkle.position.x * side, sparkle.position.y * side);
                        sparkleDrawable.setBounds(-size / 2, -size / 2, size / 2, size / 2);
                        sparkleDrawable.draw(canvas);
                        canvas.restore();
                    }
                }
                if (sparkles.size() < 20) {
                    int s = 20 - sparkles.size();
                    for (int i = 0; i < s; i++) {
                        if (RandomUtils.RANDOM.nextFloat() < 0.01f) {
                            Sparkle sparkle = new Sparkle();
                            boolean leftSide = RandomUtils.RANDOM.nextBoolean();
                            sparkle.position = new PointF(leftSide ? RandomUtils.randomf(-OUT_BOUND, 0) : RandomUtils.randomf(1, 1 + OUT_BOUND), RandomUtils.randomf(-OUT_BOUND, 1 + OUT_BOUND));
                            sparkle.velocity = new PointF(RandomUtils.randomf(-0.05f, 0.05f), RandomUtils.randomf(-0.05f, 0.05f));
                            sparkle.size = RandomUtils.randomf(0.1f, 0.12f);
                            sparkle.alpha = RandomUtils.randomf(0.5f, 1f);
                            sparkle.lifetime = RandomUtils.randoml(4000, 10000);
                            sparkles.add(sparkle);
                        }
                    }
                }
                invalidate();
                canvas.restore();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.getMode(heightMeasureSpec)), heightMeasureSpec);
        }

        public void bind(BedMenuItem item) {
            enabled = item.isEnabled;
            shiny = item.isShiny;
            title.setMaxLines(item.isSingleLine ? 1 : 2);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, item.titleTextSizeDp);
            title.setText(item.titleRes);
            icon.setImageResource(item.iconRes);
            checkedProgress = item.isCheckable && item.isChecked ? 1 : 0;
            onApplyTheme();
            title.setTextColor(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorPrimary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress));
            icon.setImageTintList(ColorStateList.valueOf(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorSecondary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress)));

            if (item.checkedChangeListener != null) {
                setOnClickListener(v -> {
                    item.isChecked = !item.isChecked;
                    new SpringAnimation(new FloatValueHolder(item.isChecked ? 0 : 1))
                            .setMinimumVisibleChange(1 / 256f)
                            .setSpring(new SpringForce(item.isChecked ? 1 : 0)
                                    .setStiffness(1000f)
                                    .setDampingRatio(1f))
                            .addUpdateListener((animation, value, velocity) -> {
                                checkedProgress = value;
                                title.setTextColor(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorPrimary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress));
                                icon.setImageTintList(ColorStateList.valueOf(ColorUtils.blendARGB(ThemesRepo.getColor(android.R.attr.textColorSecondary), ThemesRepo.getColor(R.attr.textColorOnAccent), checkedProgress)));

                                invalidate();
                            })
                            .start();

                    item.checkedChangeListener.onCheckedChanged(null, item.isChecked);
                });
            } else {
                setOnClickListener(item.clickListener);
            }
            setClickable(item.isEnabled);
            setAlpha(item.isEnabled ? 1f : 0.6f);
        }

        @Override
        public void onApplyTheme() {
            title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            icon.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)));
            setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 16));
            bgPaint.setColor(ColorUtils.setAlphaComponent(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0x10));
            accentPaint.setColor(ThemesRepo.getColor(android.R.attr.colorAccent));
        }

        private final static class Sparkle {
            private PointF position;
            private PointF velocity;
            private float size;
            private float alpha;
            private long lifetime;
            private long living;
        }
    }
}

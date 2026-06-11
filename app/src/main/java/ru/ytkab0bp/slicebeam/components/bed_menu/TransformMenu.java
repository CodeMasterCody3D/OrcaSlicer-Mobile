package ru.ytkab0bp.slicebeam.components.bed_menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.UnfoldMenu;
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent;
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent;
import ru.ytkab0bp.slicebeam.recycler.PreferenceSwitchItem;
import ru.ytkab0bp.slicebeam.recycler.SimpleRecyclerItem;
import ru.ytkab0bp.slicebeam.slic3r.Model;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.DoubleMatrix;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.Vec3d;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.DividerView;
import ru.ytkab0bp.slicebeam.view.PositionScrollView;
import ru.ytkab0bp.slicebeam.view.TextColorImageSpan;

public class TransformMenu extends ListBedMenu {
    private double[] tempMatrix = new double[16];
    private double[] tempVecArr = new double[4];

    private boolean hasSelection() {
        return fragment.getGlView().getRenderer().getModel() != null && fragment.getGlView().getRenderer().getSelectedObject() != -1;
    }

    @Override
    protected List<SimpleRecyclerItem> onCreateItems(boolean portrait) {
        return Arrays.asList(
                new BedMenuItem(R.string.MenuTransformScale, R.drawable.arrow_up_right_corner_outline_24).setEnabled(hasSelection()).onClick(v -> fragment.showUnfoldMenu(new ScaleMenu(), v)),
                new BedMenuItem(R.string.MenuTransformCut, R.drawable.menu_transform_cut_or_mirror_28).setEnabled(hasSelection()).onClick(v -> fragment.showUnfoldMenu(new CutMenu(), v)),
                new BedMenuItem(R.string.MenuTransformMirror, R.drawable.menu_transform_cut_or_mirror_28).setEnabled(hasSelection()).onClick(v -> {
                    Context ctx = fragment.getContext();
                    new BeamAlertDialogBuilder(ctx)
                            .setTitle(R.string.MenuTransformMirror)
                            .setItems(new CharSequence[] {
                                    ctx.getString(R.string.MenuTransformMirrorX),
                                    ctx.getString(R.string.MenuTransformMirrorY),
                                    ctx.getString(R.string.MenuTransformMirrorZ)
                            }, (dialog, which) -> {
                                Model model = fragment.getGlView().getRenderer().getModel();
                                Vec3d tempVec = new Vec3d();
                                int j = fragment.getGlView().getRenderer().getSelectedObject();
                                model.getMirror(j, tempVec);

                                double dx = tempVec.x, dy = tempVec.y, dz = tempVec.z;

                                switch (which) {
                                    case 0:
                                        dx = -dx;
                                        break;
                                    case 1:
                                        dy = -dy;
                                        break;
                                    case 2:
                                        dz = -dz;
                                        break;
                                }

                                model.getScale(j, tempVec);
                                dx *= tempVec.x;
                                dy *= tempVec.y;
                                dz *= tempVec.z;

                                model.scale(j, dx, dy, dz);
                                fragment.getGlView().getRenderer().invalidateGlModel(j);
                                fragment.getGlView().requestRender();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
        );
    }

    @EventHandler(runOnMainThread = true)
    public void onObjectsChanged(ObjectsListChangedEvent e) {
        ((BedMenuItem) adapter.getItems().get(0)).setEnabled(hasSelection());
        adapter.notifyItemChanged(0);

        ((BedMenuItem) adapter.getItems().get(1)).setEnabled(hasSelection());
        adapter.notifyItemChanged(1);

        ((BedMenuItem) adapter.getItems().get(2)).setEnabled(hasSelection());
        adapter.notifyItemChanged(2);
    }

    @EventHandler(runOnMainThread = true)
    public void onSelectionChanged(SelectedObjectChangedEvent e) {
        ((BedMenuItem) adapter.getItems().get(0)).setEnabled(hasSelection());
        adapter.notifyItemChanged(0);

        ((BedMenuItem) adapter.getItems().get(1)).setEnabled(hasSelection());
        adapter.notifyItemChanged(1);

        ((BedMenuItem) adapter.getItems().get(2)).setEnabled(hasSelection());
        adapter.notifyItemChanged(2);
    }

    public final class ScaleMenu extends UnfoldMenu {
        private PositionScrollView xTrack, yTrack, zTrack;
        private TextView xTitle, yTitle, zTitle;
        private Vec3d tempVec = new Vec3d(), tempVec2 = new Vec3d();
        private int startedScrollObject;
        private boolean isLinked;

        public void setLinked(boolean linked) {
            if (isLinked == linked) return;
            isLinked = linked;

            if (isLinked) {
                xTrack.addSynced(yTrack);
                xTrack.addSynced(zTrack);

                yTrack.addSynced(xTrack);
                yTrack.addSynced(zTrack);

                zTrack.addSynced(xTrack);
                zTrack.addSynced(yTrack);
            } else {
                xTrack.removeSynced(yTrack);
                xTrack.removeSynced(zTrack);

                yTrack.removeSynced(xTrack);
                yTrack.removeSynced(zTrack);

                zTrack.removeSynced(xTrack);
                zTrack.removeSynced(yTrack);
            }
        }

        private void scaleVisual(Double x, Double y, Double z) {
            int j = fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;
            startedScrollObject = j;

            Model model = fragment.getGlView().getRenderer().getModel();
            model.getScale(j, tempVec);
            double scaleX = tempVec.x, scaleY = tempVec.y, scaleZ = tempVec.z;
            model.getBoundingBoxExact(j, tempVec, tempVec2);

            if (x != null) {
                xTitle.setText(formatTrackTitle(R.string.MenuTransformScaleXValue, x * 100, (tempVec2.x - tempVec.x) / scaleX * x));
            }
            if (y != null) {
                yTitle.setText(formatTrackTitle(R.string.MenuTransformScaleYValue, y * 100, (tempVec2.y - tempVec.y) / scaleY * y));
            }
            if (z != null) {
                zTitle.setText(formatTrackTitle(R.string.MenuTransformScaleZValue, z * 100, (tempVec2.z - tempVec.z) / scaleZ * z));
            }

            model.getRotation(j, tempVec);
            DoubleMatrix.setIdentityM(tempMatrix, 0);
            DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.x), 1, 0, 0);
            DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.y), 0, 1, 0);
            DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.z), 0, 0, 1);

            model.getScale(j, tempVec);
            tempVecArr[0] = tempVec.x;
            tempVecArr[1] = tempVec.y;
            tempVecArr[2] = tempVec.z;
            tempVecArr[3] = 1;
            DoubleMatrix.multiplyMV(tempVecArr, 0, tempMatrix, 0, tempVecArr, 0);
            double sx = Math.abs(tempVecArr[0] / tempVecArr[3]);
            double sy = Math.abs(tempVecArr[1] / tempVecArr[3]);
            double sz = Math.abs(tempVecArr[2] / tempVecArr[3]);

            double dx = 1, dy = 1, dz = 1;
            if (x != null) dx = 1 / sx * x;
            if (y != null) dy = 1 / sy * y;
            if (z != null) dz = 1 / sz * z;

            fragment.getGlView().getRenderer().setSelectionScale(dx, dy, dz);
            fragment.getGlView().requestRender();
        }

        private void scale(Double x, Double y, Double z) {
            int j = fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;

            startedScrollObject = -1;

            fragment.getGlView().queueEvent(() -> {
                Model model = fragment.getGlView().getRenderer().getModel();
                model.getScale(j, tempVec);
                double scaleX = tempVec.x, scaleY = tempVec.y, scaleZ = tempVec.z;
                model.getBoundingBoxExact(j, tempVec, tempVec2);

                double dx = 1f, dy = 1f, dz = 1f;
                if (x != null) {
                    dx = x;
                    xTitle.setText(formatTrackTitle(R.string.MenuTransformScaleXValue, x * 100, (tempVec2.x - tempVec.x) / scaleX * x));
                }
                if (y != null) {
                    dy = y;
                    yTitle.setText(formatTrackTitle(R.string.MenuTransformScaleYValue, y * 100, (tempVec2.y - tempVec.y) / scaleY * y));
                }
                if (z != null) {
                    dz = z;
                    zTitle.setText(formatTrackTitle(R.string.MenuTransformScaleZValue, z * 100, (tempVec2.z - tempVec.z) / scaleZ * z));
                }

                model.getRotation(j, tempVec);
                DoubleMatrix.setIdentityM(tempMatrix, 0);
                DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.x), 1, 0, 0);
                DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.y), 0, 1, 0);
                DoubleMatrix.rotateM(tempMatrix, 0, Math.toDegrees(tempVec.z), 0, 0, 1);
                tempVecArr[0] = dx;
                tempVecArr[1] = dy;
                tempVecArr[2] = dz;
                tempVecArr[3] = 1;
                DoubleMatrix.multiplyMV(tempVecArr, 0, tempMatrix, 0, tempVecArr, 0);
                dx = Math.abs(tempVecArr[0] / tempVecArr[3]);
                dy = Math.abs(tempVecArr[1] / tempVecArr[3]);
                dz = Math.abs(tempVecArr[2] / tempVecArr[3]);

                model.getMirror(j, tempVec);
                dx *= tempVec.x;
                dy *= tempVec.y;
                dz *= tempVec.z;

                model.getScale(j, tempVec);
                model.scale(j, dx, dy, dz);
                model.ensureOnBed(j);

                fragment.getGlView().getRenderer().invalidateSelectionObject();
                fragment.getGlView().getRenderer().setSelectionScale(1, 1, 1);
                fragment.getGlView().getRenderer().invalidateGlModel(j);
                fragment.getGlView().requestRender();
            });
            fragment.getGlView().requestRender();
        }

        private CharSequence formatTrackTitle(int res, double value, double mm) {
            SpannableStringBuilder sb = SpannableStringBuilder.valueOf(SliceBeam.INSTANCE.getString(res, value, mm));
            sb.append(" d");
            int size = ViewUtils.dp(14);
            Drawable dr = ContextCompat.getDrawable(SliceBeam.INSTANCE, R.drawable.edit_outline_28);
            dr.setTint(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            dr.setBounds(0, 0, size, size);
            sb.setSpan(new TextColorImageSpan(dr, 0), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }

        private void doScale(double sx, double sy, double sz) {
            int j = fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;
            Model model = fragment.getGlView().getRenderer().getModel();
            model.scale(j, sx, sy, sz);
        }

        private void showManualEditor(int title, boolean x, boolean y, boolean z) {
            int j = fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;

            Model model = fragment.getGlView().getRenderer().getModel();
            model.getScale(j, tempVec);

            Context ctx = getView().getContext();
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);

            AtomicBoolean inputInMM = new AtomicBoolean(Prefs.isScaleInputInMM());

            double cur;
            if (x) {
                cur = tempVec.x * 100;
            } else if (y) {
                cur = tempVec.y * 100;
            } else {
                cur = tempVec.z * 100;
            }

            model.getScale(j, tempVec);
            double scaleX = tempVec.x, scaleY = tempVec.y, scaleZ = tempVec.z;
            model.getBoundingBoxExact(j, tempVec, tempVec2);
            if (inputInMM.get()) {
                cur /= 100.0;

                if (x) {
                    cur *= (tempVec2.x - tempVec.x) / scaleX;
                } else if (y) {
                    cur *= (tempVec2.y - tempVec.y) / scaleY;
                } else {
                    cur *= (tempVec2.z - tempVec.z) / scaleZ;
                }
            }
            double current = cur;

            EditText text = new EditText(ctx);
            text.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            text.setText(String.format(Locale.ROOT, "%.2f", current));
            text.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            ll.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = rightMargin = ViewUtils.dp(21);
            }});

            PreferenceSwitchItem.SwitchPreferenceHolderView holderView = new PreferenceSwitchItem.SwitchPreferenceHolderView(ctx);
            holderView.title.setText(R.string.MenuTransformScaleMM);
            holderView.title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            holderView.matSwitch.setChecked(inputInMM.get());
            holderView.subtitle.setVisibility(View.GONE);
            holderView.setOnClickListener(v -> {
                inputInMM.set(!inputInMM.get());

                double value;
                try {
                    value = Double.parseDouble(text.getText().toString());
                } catch (NumberFormatException e) {
                    value = current;
                }

                if (inputInMM.get()) {
                    value /= 100.0;

                    if (x) {
                        value *= (tempVec2.x - tempVec.x) / scaleX;
                    } else if (y) {
                        value *= (tempVec2.y - tempVec.y) / scaleY;
                    } else {
                        value *= (tempVec2.z - tempVec.z) / scaleZ;
                    }
                } else {
                    if (x) {
                        value /= (tempVec2.x - tempVec.x) / scaleX;
                    } else if (y) {
                        value /= (tempVec2.y - tempVec.y) / scaleY;
                    } else {
                        value /= (tempVec2.z - tempVec.z) / scaleZ;
                    }
                    value *= 100.0;
                }
                text.setText(String.format(Locale.ROOT, "%.2f", value));
                holderView.matSwitch.setChecked(inputInMM.get());
                Prefs.setScaleInputInMM(inputInMM.get());
            });
            ll.addView(holderView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                leftMargin = rightMargin = ViewUtils.dp(8);
            }});

            new BeamAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setView(ll)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        double value;
                        try {
                            value = Double.parseDouble(text.getText().toString());

                            if (inputInMM.get()) {
                                if (x) {
                                    value /= (tempVec2.x - tempVec.x) / scaleX;
                                } else if (y) {
                                    value /= (tempVec2.y - tempVec.y) / scaleY;
                                } else {
                                    value /= (tempVec2.z - tempVec.z) / scaleZ;
                                }
                            } else {
                                value /= 100.0;
                            }
                        } catch (NumberFormatException e) {
                            value = current;
                        }

                        double dx = tempVec.x, dy = tempVec.y, dz = tempVec.z;
                        if (x || isLinked) xTrack.setCurrentPosition((int) ((dx = value) * 100));
                        if (y || isLinked) yTrack.setCurrentPosition((int) ((dy = value) * 100));
                        if (z || isLinked) zTrack.setCurrentPosition((int) ((dz = value) * 100));

                        scale(dx, dy, dz);
                    })
                    .show();
            ViewUtils.postOnMainThread(() -> {
                text.requestFocus();
                InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(text, 0);
                text.setSelection(text.getText().length());
            }, 200);
        }

        @Override
        protected View onCreateView(Context ctx, boolean portrait) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(0, ViewUtils.dp(12), 0, 0);

            xTitle = new TextView(ctx);
            xTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            xTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            xTitle.setTextColor(ThemesRepo.getColor(R.attr.xTrackColor));
            xTitle.setGravity(Gravity.CENTER);
            xTitle.setOnClickListener(v -> showManualEditor(R.string.MenuOrientationPositionX, true, false, false));
            ll.addView(xTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                bottomMargin = ViewUtils.dp(4);
            }});

            xTrack = new PositionScrollView(ctx);
            xTrack.setActiveColor(R.attr.xTrackColor);
            xTrack.setProgressListener(integer -> scaleVisual(integer.doubleValue() / 100.0, yTrack.getCurrentPosition() / 100.0, zTrack.getCurrentPosition() / 100.0));
            xTrack.setListener(integer -> scale(integer.doubleValue() / 100.0, yTrack.getCurrentPosition() / 100.0, zTrack.getCurrentPosition() / 100.0));
            xTrack.setMinMax(1, Integer.MAX_VALUE);
            ll.addView(xTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            yTitle = new TextView(ctx);
            yTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            yTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            yTitle.setTextColor(ThemesRepo.getColor(R.attr.yTrackColor));
            yTitle.setGravity(Gravity.CENTER);
            yTitle.setOnClickListener(v -> showManualEditor(R.string.MenuOrientationPositionY, false, true, false));
            ll.addView(yTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                bottomMargin = ViewUtils.dp(4);
            }});

            yTrack = new PositionScrollView(ctx);
            yTrack.setActiveColor(R.attr.yTrackColor);
            yTrack.setProgressListener(integer -> scaleVisual(xTrack.getCurrentPosition() / 100.0, integer.doubleValue() / 100.0, zTrack.getCurrentPosition() / 100.0));
            yTrack.setListener(integer -> scale(xTrack.getCurrentPosition() / 100.0, integer.doubleValue() / 100.0, zTrack.getCurrentPosition() / 100.0));
            yTrack.setMinMax(1, Integer.MAX_VALUE);
            ll.addView(yTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            zTitle = new TextView(ctx);
            zTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            zTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            zTitle.setTextColor(ThemesRepo.getColor(R.attr.zTrackColor));
            zTitle.setGravity(Gravity.CENTER);
            zTitle.setOnClickListener(v -> showManualEditor(R.string.MenuOrientationPositionZ, false, false, true));
            ll.addView(zTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                bottomMargin = ViewUtils.dp(4);
            }});

            zTrack = new PositionScrollView(ctx);
            zTrack.setActiveColor(R.attr.zTrackColor);
            zTrack.setProgressListener(integer -> scaleVisual(xTrack.getCurrentPosition() / 100.0, yTrack.getCurrentPosition() / 100.0, integer.doubleValue() / 100.0));
            zTrack.setListener(integer -> scale(xTrack.getCurrentPosition() / 100.0, yTrack.getCurrentPosition() / 100.0, integer.doubleValue() / 100.0));
            zTrack.setMinMax(1, Integer.MAX_VALUE);
            ll.addView(zTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            ll.addView(new Space(ctx), new LinearLayout.LayoutParams(0, 0, 1f));

            PreferenceSwitchItem.SwitchPreferenceHolderView holderView = new PreferenceSwitchItem.SwitchPreferenceHolderView(ctx);
            holderView.title.setText(R.string.MenuTransformScaleLink);
            holderView.title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            holderView.matSwitch.setChecked(Prefs.isScaleLinked());
            holderView.subtitle.setVisibility(View.GONE);
            holderView.setOnClickListener(v -> {
                boolean check = !Prefs.isScaleLinked();
                holderView.matSwitch.setChecked(check);
                Prefs.setScaleLinked(check);
                setLinked(check);
            });
            if (portrait) {
                ll.addView(holderView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(64)));
            }

            ll.addView(new DividerView(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1f)));
            LinearLayout toolbar = new LinearLayout(ctx);
            toolbar.setPadding(ViewUtils.dp(12), 0, ViewUtils.dp(12), 0);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setBackground(ViewUtils.createRipple(ThemesRepo.getColor(android.R.attr.colorControlHighlight), 0));
            toolbar.setOnClickListener(v -> dismiss());

            ImageView icon = new ImageView(ctx);
            icon.setImageResource(R.drawable.arrow_left_outline_28);
            icon.setColorFilter(ThemesRepo.getColor(android.R.attr.textColorSecondary));
            toolbar.addView(icon, new LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)));

            TextView title = new TextView(ctx);
            title.setText(R.string.MenuOrientationPositionBack);
            title.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) {{
                leftMargin = ViewUtils.dp(12);
            }});
            if (!portrait) {
                title.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                ((LinearLayout.LayoutParams) title.getLayoutParams()).weight = 0;

                holderView.title.setMaxLines(1);
                toolbar.addView(holderView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }

            ll.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(52)));

            setLinked(Prefs.isScaleLinked());

            return ll;
        }

        @Override
        public int getRequestedSize(FrameLayout into, boolean portrait) {
            return portrait ? ViewUtils.dp(52) + ViewUtils.dp(64) + ViewUtils.dp(80 + 24) * 3 + ViewUtils.dp(12) : (int) (into.getWidth() * 0.5f);
        }

        @Override
        protected void onCreate() {
            super.onCreate();

            SliceBeam.EVENT_BUS.registerListener(this);
            setSelectionValues();
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();

            SliceBeam.EVENT_BUS.unregisterListener(this);
            stopScroll();
        }

        private void setSelectionValues() {
            int j = fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;

            fragment.getGlView().getRenderer().setSelectionScale(1, 1, 1);

            Model model = fragment.getGlView().getRenderer().getModel();
            model.getScale(j, tempVec);
            double scaleX = tempVec.x, scaleY = tempVec.y, scaleZ = tempVec.z;

            xTrack.setCurrentPosition((int) Math.round(scaleX * 100));
            yTrack.setCurrentPosition((int) Math.round(scaleY * 100));
            zTrack.setCurrentPosition((int) Math.round(scaleZ * 100));

            model.getBoundingBoxExact(j, tempVec, tempVec2);
            xTitle.setText(formatTrackTitle(R.string.MenuTransformScaleXValue, scaleX * 100, (tempVec2.x - tempVec.x)));
            yTitle.setText(formatTrackTitle(R.string.MenuTransformScaleYValue, scaleY * 100, (tempVec2.y - tempVec.y)));
            zTitle.setText(formatTrackTitle(R.string.MenuTransformScaleZValue, scaleZ * 100, (tempVec2.z - tempVec.z)));

            xTrack.updateSyncDeltas();
            yTrack.updateSyncDeltas();
            zTrack.updateSyncDeltas();
        }

        private void stopScroll() {
            xTrack.stopScroll();
            yTrack.stopScroll();
            zTrack.stopScroll();

            if (startedScrollObject != -1) {
                fragment.getGlView().getRenderer().setSelectionScale(1, 1, 1);
            }
            startedScrollObject = -1;
        }

        @EventHandler(runOnMainThread = true)
        public void onSelectedObjectChanged(SelectedObjectChangedEvent e) {
            stopScroll();

            if (fragment.getGlView().getRenderer().getSelectedObject() == -1) {
                dismiss();
            } else {
                setSelectionValues();
            }
        }
    }

    public final class CutMenu extends UnfoldMenu {
        private TextView zTitle, rotXTitle, rotYTitle;
        private PositionScrollView zTrack, rotXTrack, rotYTrack;
        private final Vec3d tempVec = new Vec3d();
        private final Vec3d tempVec2 = new Vec3d();
        private double minZ, maxZ, midZ;

        public CutMenu() {
            int j = TransformMenu.this.fragment.getGlView().getRenderer().getSelectedObject();
            if (j == -1) return;
            Model model = TransformMenu.this.fragment.getGlView().getRenderer().getModel();
            model.getBoundingBoxExact(j, tempVec, tempVec2);
            minZ = tempVec.z;
            maxZ = tempVec2.z;
            midZ = (minZ + maxZ) / 2.0;
        }

        @Override
        protected void onCreate() {
            super.onCreate();
            TransformMenu.this.fragment.getGlView().getRenderer().showCutPlane(true);
            updateCutPlaneInRenderer();
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            TransformMenu.this.fragment.getGlView().getRenderer().showCutPlane(false);
            TransformMenu.this.fragment.getGlView().requestRender();
        }

        private void updateCutPlaneInRenderer() {
            if (zTrack == null || rotXTrack == null || rotYTrack == null) return;
            float z = (float) (zTrack.getCurrentPosition() / 100.0f);
            float rotXRad = (float) Math.toRadians(rotXTrack.getCurrentPosition() / 100.0f);
            float rotYRad = (float) Math.toRadians(rotYTrack.getCurrentPosition() / 100.0f);
            TransformMenu.this.fragment.getGlView().getRenderer().setCutPlane(z, rotXRad, rotYRad, tempVec, tempVec2);
            TransformMenu.this.fragment.getGlView().requestRender();
        }

        @Override
        protected View onCreateView(Context ctx, boolean portrait) {
            LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(0, ViewUtils.dp(12), 0, 0);

            zTitle = new TextView(ctx);
            zTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            zTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            zTitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            zTitle.setGravity(Gravity.CENTER);
            zTitle.setText("Z = " + String.format(java.util.Locale.US, "%.2f", midZ) + " mm");
            ll.addView(zTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                bottomMargin = ViewUtils.dp(4);
            }});

            zTrack = new PositionScrollView(ctx);
            zTrack.setActiveColor(R.attr.zTrackColor);
            zTrack.setMinMax((int)(minZ * 100), (int)(maxZ * 100));
            zTrack.setCurrentPosition((int)(midZ * 100));
            zTrack.setListener(integer -> {
                zTitle.setText("Z = " + String.format(java.util.Locale.US, "%.2f", integer / 100.0) + " mm");
                updateCutPlaneInRenderer();
            });
            ll.addView(zTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            rotXTitle = new TextView(ctx);
            rotXTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            rotXTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            rotXTitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            rotXTitle.setGravity(Gravity.CENTER);
            rotXTitle.setText("Rotate X = 0°");
            ll.addView(rotXTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                topMargin = ViewUtils.dp(8); bottomMargin = ViewUtils.dp(4);
            }});

            rotXTrack = new PositionScrollView(ctx);
            rotXTrack.setActiveColor(R.attr.xTrackColor);
            rotXTrack.setMinMax(-18000, 18000);
            rotXTrack.setCurrentPosition(0);
            rotXTrack.setListener(integer -> {
                rotXTitle.setText("Rotate X = " + (integer / 100) + "°");
                updateCutPlaneInRenderer();
            });
            ll.addView(rotXTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            rotYTitle = new TextView(ctx);
            rotYTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            rotYTitle.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM));
            rotYTitle.setTextColor(ThemesRepo.getColor(android.R.attr.textColorPrimary));
            rotYTitle.setGravity(Gravity.CENTER);
            rotYTitle.setText("Rotate Y = 0°");
            ll.addView(rotYTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(20)) {{
                topMargin = ViewUtils.dp(8); bottomMargin = ViewUtils.dp(4);
            }});

            rotYTrack = new PositionScrollView(ctx);
            rotYTrack.setActiveColor(R.attr.yTrackColor);
            rotYTrack.setMinMax(-18000, 18000);
            rotYTrack.setCurrentPosition(0);
            rotYTrack.setListener(integer -> {
                rotYTitle.setText("Rotate Y = " + (integer / 100) + "°");
                updateCutPlaneInRenderer();
            });
            ll.addView(rotYTrack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(80)));

            android.widget.Button applyBtn = new android.widget.Button(ctx);
            applyBtn.setText("Apply Cut");
            applyBtn.setOnClickListener(v -> {
                int objIdx = fragment.getGlView().getRenderer().getSelectedObject();
                if (objIdx != -1) {
                    double z = zTrack.getCurrentPosition() / 100.0;
                    double rotXRad = Math.toRadians(rotXTrack.getCurrentPosition() / 100.0);
                    double rotYRad = Math.toRadians(rotYTrack.getCurrentPosition() / 100.0);
                    fragment.getGlView().getRenderer().getModel().cut(objIdx, z, rotXRad, rotYRad, true, true);
                    fragment.updateModel();
                }
                dismiss();
            });
            ll.addView(applyBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
                topMargin = ViewUtils.dp(16);
                leftMargin = rightMargin = ViewUtils.dp(32);
            }});

            return ll;
        }

        @Override
        public int getRequestedSize(FrameLayout into, boolean portrait) {
            return portrait ? ViewUtils.dp(400) : (int) (into.getWidth() * 0.5f);
        }
    }
}

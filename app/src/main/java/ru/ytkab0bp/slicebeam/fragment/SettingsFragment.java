package ru.ytkab0bp.slicebeam.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.mrudultora.colorpicker.ColorPickerPopUp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ru.ytkab0bp.eventbus.EventHandler;
import ru.ytkab0bp.slicebeam.BeamServerData;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SetupActivity;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder;
import ru.ytkab0bp.slicebeam.components.BeamColorPickerPopUp;
import ru.ytkab0bp.slicebeam.config.ConfigObject;
import ru.ytkab0bp.slicebeam.events.BeamServerDataUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudUserInfoUpdatedEvent;
import ru.ytkab0bp.slicebeam.recycler.PreferenceItem;
import ru.ytkab0bp.slicebeam.theme.BeamTheme;
import ru.ytkab0bp.slicebeam.theme.ThemesRepo;
import ru.ytkab0bp.slicebeam.utils.Prefs;

public class SettingsFragment extends ProfileListFragment {

    @Override
    public void onViewCreated(View v) {
        super.onViewCreated(v);
        dropdownView.setVisibility(View.GONE);
        ((View) saveButton.getParent()).setVisibility(View.GONE);
    }

    @Override
    protected List<OptionElement> getConfigItems() {
        return Arrays.asList(
                BeamServerData.isCloudAvailable() ? new OptionElement(SPECIAL_TYPE_CLOUD_HEADER).setOnClick(() -> {
                    Activity act = (Activity) getContext();
                    act.startActivity(new Intent(act, SetupActivity.class).putExtra(SetupActivity.EXTRA_CLOUD_PROFILE, true));
                }) : null,
                new OptionElement(R.drawable.paint_roller_outline_28, getContext().getString(R.string.SettingsInterface)),
                new OptionElement(new PreferenceItem().setTitle(getContext().getString(R.string.SettingsInterfaceTheme)).setValueProvider(() -> getContext().getString(Prefs.getThemeMode().title)).setOnClickListener(v -> {
                    String[] items = new String[Prefs.ThemeMode.values().length];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = getContext().getString(Prefs.ThemeMode.values()[i].title);
                    }
                    new BeamAlertDialogBuilder(getContext())
                            .setTitle(R.string.SettingsInterfaceTheme)
                            .setSingleChoiceItems(items, Prefs.getThemeMode().ordinal(), (dialog, which) -> {
                                boolean activity = getContext() instanceof Activity;
                                if (activity) {
                                    Activity act = (Activity) getContext();
                                    ViewGroup decorView = (ViewGroup) act.getWindow().getDecorView();
                                    Bitmap bm = Bitmap.createBitmap(decorView.getWidth(), decorView.getHeight(), Bitmap.Config.ARGB_8888);
                                    Canvas c = new Canvas(bm);
                                    decorView.draw(c);

                                    ImageView overlay = new ImageView(act);
                                    overlay.setImageBitmap(bm);
                                    decorView.addView(overlay);

                                    ValueAnimator anim = ValueAnimator.ofFloat(1, 0).setDuration(250);
                                    anim.addUpdateListener(animation -> overlay.setAlpha((float) animation.getAnimatedValue()));
                                    anim.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            decorView.removeView(overlay);
                                            bm.recycle();
                                        }
                                    });
                                    anim.start();
                                }
                                Prefs.setThemeMode(which);
                                if (activity) {
                                    ThemesRepo.invalidate((Activity) getContext());
                                }
                                dialog.dismiss();
                            })
                            .show();
                })),
                new OptionElement(new PreferenceItem().setTitle(getContext().getString(R.string.SettingsInterfaceColor)).setValueProvider(() -> "#" + String.format("%08X", Prefs.getAccentColor())).setOnClickListener(v -> {
                    new BeamColorPickerPopUp(getContext())
                            .setDialogTitle(getContext().getString(R.string.SettingsInterfaceColor))
                            .setDefaultColor(Prefs.getAccentColor())
                            .setShowAlpha(false)
                            .setOnPickColorListener(new ColorPickerPopUp.OnPickColorListener() {
                                @Override
                                public void onColorPicked(int color) {
                                    Prefs.setAccentColor(color);
                                    onChanged();
                                }

                                @Override
                                public void onCancel() {
                                    Prefs.setAccentColor(SetupActivity.AccentColors.DEFAULT.color);
                                    onChanged();
                                }
                                
                                void onChanged() {
                                    BeamTheme.LIGHT.colors.put(android.R.attr.colorAccent, Prefs.getAccentColor());
                                    BeamTheme.DARK.colors.put(android.R.attr.colorAccent, Prefs.getAccentColor());
                                    ThemesRepo.invalidate((Activity) getContext());
                                    recyclerView.getAdapter().notifyItemChanged(2 - (BeamServerData.isCloudAvailable() ? 0 : 1));
                                }
                            })
                            .setNegativeButtonText(getContext().getString(R.string.SettingsInterfaceColorReset))
                            .show();
                })),
                new OptionElement(new PreferenceItem().setTitle(getContext().getString(R.string.SettingsInterfaceResolutionScale)).setSubtitle(getContext().getString(R.string.SettingsInterfaceResolutionScaleDescription)).setValueProvider(() -> (int) (Prefs.getRenderScale() * 100) + "%").setOnClickListener(v -> {
                    float[] variants = {1, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f};
                    String[] items = new String[variants.length];
                    int j = 0;
                    for (int i = 0; i < variants.length; i++) {
                        items[i] = (int) (variants[i] * 100) + "%";
                        if (variants[i] == Prefs.getRenderScale()) {
                            j = i;
                        }
                    }

                    new BeamAlertDialogBuilder(getContext())
                            .setTitle(R.string.SettingsInterfaceResolutionScale)
                            .setSingleChoiceItems(items, j, (dialog, which) -> {
                                Prefs.setRenderScale(variants[which]);
                                dialog.dismiss();
                                // I'm too lazy to calculate real position for now
                                recyclerView.getAdapter().notifyItemChanged(4 - (BeamServerData.isCloudAvailable() ? 0 : 1));
                            })
                            .show();
                })),
                new OptionElement(R.drawable.info_outline_28, getContext().getString(R.string.SettingsAbout)).setOnClick(() -> {
                    Activity act = (Activity) getContext();
                    act.startActivity(new Intent(act, SetupActivity.class).putExtra(SetupActivity.EXTRA_ABOUT, true));
                }),
                null,
                BeamServerData.isBoostyAvailable() ? new OptionElement(R.drawable.boosty, getContext().getString(R.string.SettingsBoosty)).setColor(R.attr.boostyColorTop, true).setOnClick(() -> {
                    Activity act = (Activity) getContext();
                    act.startActivity(new Intent(act, SetupActivity.class).putExtra(SetupActivity.EXTRA_BOOSTY_ONLY, true));
                }) : null,
                null,
                new OptionElement(R.drawable.refresh_outline_28, getContext().getString(R.string.SettingsResetToDefault)).setColor(R.attr.textColorNegative, false).setOnClick(() -> {
                    Context ctx = getContext();
                    if (ctx instanceof Activity) {
                        Activity act = (Activity) ctx;
                        new BeamAlertDialogBuilder(getContext())
                                .setTitle(R.string.SettingsResetToDefaultTitle)
                                .setMessage(R.string.SettingsResetToDefaultDescription)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    SliceBeam.getConfigFile().delete();
                                    SliceBeam.CONFIG = null;
                                    Prefs.getPrefs().edit().clear().apply();
                                    Prefs.setLastCommit();
                                    act.startActivity(new Intent(act, SetupActivity.class));
                                    act.finish();
                                })
                                .setNegativeButton(android.R.string.cancel, null).
                                show();
                    }
                })
        );
    }

    @EventHandler(runOnMainThread = true)
    public void onDataUpdated(BeamServerDataUpdatedEvent e) {
        setConfigItems(getConfigItems());
    }

    @EventHandler(runOnMainThread = true)
    public void onUserInfoUpdated(CloudUserInfoUpdatedEvent e) {
        if (BeamServerData.isCloudAvailable()) {
            recyclerView.getAdapter().notifyItemChanged(0);
        }
    }

    @Override
    protected void cloneCurrentProfile() {}

    @Override
    protected void deleteCurrentProfile() {}

    @Override
    protected void onApplyConfig(String title) {}

    @Override
    protected void onResetConfig() {}

    @Override
    protected ConfigObject getCurrentConfig() {
        return null;
    }

    @Override
    protected int getTitle() {
        return 0;
    }

    @Override
    protected void selectItem(ProfileListItem item) {}

    @Override
    protected List<ProfileListItem> getItems(boolean filter) {
        return Collections.emptyList();
    }
}

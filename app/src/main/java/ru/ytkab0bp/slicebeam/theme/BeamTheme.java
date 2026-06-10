package ru.ytkab0bp.slicebeam.theme;

import android.util.SparseIntArray;

import androidx.annotation.StringRes;

import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.utils.Prefs;

public class BeamTheme {
    public final static BeamTheme LIGHT = new BeamTheme() {{
        nameRes = R.string.SettingsInterfaceThemeLight;
        colors.put(R.attr.textColorOnAccent, 0xffffffff);
        colors.put(R.attr.defaultBedColor, 0xff404040);
        colors.put(R.attr.bedGridlinesColor, 0x99e5e5e5);
        colors.put(R.attr.bedContourlinesColor, 0x80ffffff);
        colors.put(R.attr.backgroundColorTop, 0xffc0c0c0);
        colors.put(R.attr.backgroundColorBottom, 0xff7a7a7a);
        colors.put(R.attr.dividerColor, 0xffeeeeee);
        colors.put(R.attr.dividerContrastColor, 0xffcccccc);
        colors.put(R.attr.dialogBackground, 0xffffffff);
        colors.put(R.attr.switchThumbUncheckedColor, 0xffeef2f3);
        colors.put(R.attr.boostyColorTop, 0xfff06e2a);
        colors.put(R.attr.boostyColorBottom, 0xff884725);
        colors.put(R.attr.telegramColor, 0xff27a7e7);
        colors.put(R.attr.k3dColor, 0xff039045);
        colors.put(R.attr.modelHoverColor, 0xffffffff);
        colors.put(R.attr.textColorNegative, 0xffff464a);

        colors.put(R.attr.gcodeViewerNone, 0xFFE6B3B3);
        colors.put(R.attr.gcodeViewerPerimeter, 0xFFFFE64D);
        colors.put(R.attr.gcodeViewerExternalPerimeter, 0xFFFF7D38);
        colors.put(R.attr.gcodeViewerOverhangPerimeter, 0xFF1F1FFF);
        colors.put(R.attr.gcodeViewerInternalInfill, 0xFFB03029);
        colors.put(R.attr.gcodeViewerSolidInfill, 0xFF9654CC);
        colors.put(R.attr.gcodeViewerTopSolidInfill, 0xFFF04040);
        colors.put(R.attr.gcodeViewerIroning, 0xFFFF8C69);
        colors.put(R.attr.gcodeViewerBridgeInfill, 0xFF4D80BA);
        colors.put(R.attr.gcodeViewerGapFill, 0xFFFFFFFF);
        colors.put(R.attr.gcodeViewerSkirt, 0xFF00876E);
        colors.put(R.attr.gcodeViewerSupportMaterial, 0xFF00FF00);
        colors.put(R.attr.gcodeViewerSupportMaterialInterface, 0xFF008000);
        colors.put(R.attr.gcodeViewerWipeTower, 0xFFB3E3AB);
        colors.put(R.attr.gcodeViewerCustom, 0xFF5ED194);

        colors.put(R.attr.xTrackColor, 0xffbf0000);
        colors.put(R.attr.yTrackColor, 0xff00bf00);
        colors.put(R.attr.zTrackColor, 0xff0000bf);

        colors.put(R.attr.snackbarBase, 0xFFEEEEEE);
        colors.put(R.attr.snackbarDone, 0xFF56AB2F);
        colors.put(R.attr.snackbarWarning, 0xFFAE660C);
        colors.put(R.attr.snackbarInfo, 0xFF009DC6);
        colors.put(R.attr.snackbarError, 0xFFDC100E);

        colors.put(android.R.attr.textColorPrimary, 0xff000000);
        colors.put(android.R.attr.textColorSecondary, 0x99000000);
        colors.put(android.R.attr.windowBackground, 0xffffffff);
        colors.put(android.R.attr.colorAccent, Prefs.getAccentColor());
        colors.put(android.R.attr.colorControlHighlight, 0x21000000);
    }};
    public final static BeamTheme DARK = new BeamTheme() {{
        nameRes = R.string.SettingsInterfaceThemeDark;
        colors = LIGHT.colors.clone();

        // OrcaSlicer dark grey palette
        colors.put(R.attr.dividerColor, 0xff3d3d3d);
        colors.put(R.attr.dividerContrastColor, 0xff484848);
        colors.put(R.attr.dialogBackground, 0xff2e2e2e);
        colors.put(R.attr.switchThumbUncheckedColor, 0xff3a3a3a);

        colors.put(R.attr.defaultBedColor, 0xff3a3a3a);
        colors.put(R.attr.bedGridlinesColor, 0x80d0d0d0);
        colors.put(R.attr.bedContourlinesColor, 0x33ffffff);
        colors.put(R.attr.backgroundColorTop, 0xff2b2b2b);
        colors.put(R.attr.backgroundColorBottom, 0xff1a1a1a);
        colors.put(R.attr.boostyColorBottom, 0xff884725);

        colors.put(R.attr.xTrackColor, 0xffee0000);
        colors.put(R.attr.yTrackColor, 0xff00ee00);
        colors.put(R.attr.zTrackColor, 0xff0000ee);

        colors.put(R.attr.snackbarBase, 0xFF2d2d2d);

        colors.put(android.R.attr.textColorPrimary, 0xffffffff);
        colors.put(android.R.attr.textColorSecondary, 0x99ffffff);
        colors.put(android.R.attr.windowBackground, 0xff252525);
        colors.put(android.R.attr.colorControlHighlight, 0x21ffffff);
    }};

    String name;
    @StringRes
    int nameRes;

    public SparseIntArray colors = new SparseIntArray();
}

package ru.ytkab0bp.slicebeam.utils;

/** One filament in the multi-color palette: an ARGB color plus a material type (e.g. "PLA"). */
public class FilamentSlot {
    public int color;
    public String type;

    public FilamentSlot(int color, String type) {
        this.color = color | 0xFF000000;
        this.type = type;
    }
}

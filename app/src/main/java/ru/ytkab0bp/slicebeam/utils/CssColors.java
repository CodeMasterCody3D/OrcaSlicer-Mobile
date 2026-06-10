package ru.ytkab0bp.slicebeam.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The standard CSS (Level 4) named color library. Used by the filament color palette picker so the
 * user can choose colors by their well-known CSS names, in addition to the color wheel.
 * Order roughly follows the CSS spec grouping; {@link #COMMON} lists a short set of the most-used ones.
 */
public final class CssColors {
    private CssColors() {}

    /** Full CSS named-color set: name -> 0xFFRRGGBB. Insertion-ordered. */
    public static final Map<String, Integer> ALL = new LinkedHashMap<>();

    /** A short list of the most common color names, for the quick swatch picker. */
    public static final String[] COMMON = {
            "black", "white", "gray", "silver", "red", "orange", "yellow", "lime",
            "green", "teal", "cyan", "blue", "navy", "purple", "magenta", "pink",
            "brown", "gold"
    };

    private static void put(String name, int rgb) {
        ALL.put(name, 0xFF000000 | rgb);
    }

    static {
        put("black", 0x000000); put("silver", 0xC0C0C0); put("gray", 0x808080); put("white", 0xFFFFFF);
        put("maroon", 0x800000); put("red", 0xFF0000); put("purple", 0x800080); put("fuchsia", 0xFF00FF);
        put("green", 0x008000); put("lime", 0x00FF00); put("olive", 0x808000); put("yellow", 0xFFFF00);
        put("navy", 0x000080); put("blue", 0x0000FF); put("teal", 0x008080); put("aqua", 0x00FFFF);
        put("aliceblue", 0xF0F8FF); put("antiquewhite", 0xFAEBD7); put("aquamarine", 0x7FFFD4);
        put("azure", 0xF0FFFF); put("beige", 0xF5F5DC); put("bisque", 0xFFE4C4); put("blanchedalmond", 0xFFEBCD);
        put("blueviolet", 0x8A2BE2); put("brown", 0xA52A2A); put("burlywood", 0xDEB887); put("cadetblue", 0x5F9EA0);
        put("chartreuse", 0x7FFF00); put("chocolate", 0xD2691E); put("coral", 0xFF7F50); put("cornflowerblue", 0x6495ED);
        put("cornsilk", 0xFFF8DC); put("crimson", 0xDC143C); put("cyan", 0x00FFFF); put("darkblue", 0x00008B);
        put("darkcyan", 0x008B8B); put("darkgoldenrod", 0xB8860B); put("darkgray", 0xA9A9A9); put("darkgreen", 0x006400);
        put("darkkhaki", 0xBDB76B); put("darkmagenta", 0x8B008B); put("darkolivegreen", 0x556B2F); put("darkorange", 0xFF8C00);
        put("darkorchid", 0x9932CC); put("darkred", 0x8B0000); put("darksalmon", 0xE9967A); put("darkseagreen", 0x8FBC8F);
        put("darkslateblue", 0x483D8B); put("darkslategray", 0x2F4F4F); put("darkturquoise", 0x00CED1); put("darkviolet", 0x9400D3);
        put("deeppink", 0xFF1493); put("deepskyblue", 0x00BFFF); put("dimgray", 0x696969); put("dodgerblue", 0x1E90FF);
        put("firebrick", 0xB22222); put("floralwhite", 0xFFFAF0); put("forestgreen", 0x228B22); put("gainsboro", 0xDCDCDC);
        put("ghostwhite", 0xF8F8FF); put("gold", 0xFFD700); put("goldenrod", 0xDAA520); put("greenyellow", 0xADFF2F);
        put("honeydew", 0xF0FFF0); put("hotpink", 0xFF69B4); put("indianred", 0xCD5C5C); put("indigo", 0x4B0082);
        put("ivory", 0xFFFFF0); put("khaki", 0xF0E68C); put("lavender", 0xE6E6FA); put("lavenderblush", 0xFFF0F5);
        put("lawngreen", 0x7CFC00); put("lemonchiffon", 0xFFFACD); put("lightblue", 0xADD8E6); put("lightcoral", 0xF08080);
        put("lightcyan", 0xE0FFFF); put("lightgoldenrodyellow", 0xFAFAD2); put("lightgray", 0xD3D3D3); put("lightgreen", 0x90EE90);
        put("lightpink", 0xFFB6C1); put("lightsalmon", 0xFFA07A); put("lightseagreen", 0x20B2AA); put("lightskyblue", 0x87CEFA);
        put("lightslategray", 0x778899); put("lightsteelblue", 0xB0C4DE); put("lightyellow", 0xFFFFE0); put("limegreen", 0x32CD32);
        put("linen", 0xFAF0E6); put("magenta", 0xFF00FF); put("mediumaquamarine", 0x66CDAA); put("mediumblue", 0x0000CD);
        put("mediumorchid", 0xBA55D3); put("mediumpurple", 0x9370DB); put("mediumseagreen", 0x3CB371); put("mediumslateblue", 0x7B68EE);
        put("mediumspringgreen", 0x00FA9A); put("mediumturquoise", 0x48D1CC); put("mediumvioletred", 0xC71585); put("midnightblue", 0x191970);
        put("mintcream", 0xF5FFFA); put("mistyrose", 0xFFE4E1); put("moccasin", 0xFFE4B5); put("navajowhite", 0xFFDEAD);
        put("oldlace", 0xFDF5E6); put("olivedrab", 0x6B8E23); put("orange", 0xFFA500); put("orangered", 0xFF4500);
        put("orchid", 0xDA70D6); put("palegoldenrod", 0xEEE8AA); put("palegreen", 0x98FB98); put("paleturquoise", 0xAFEEEE);
        put("palevioletred", 0xDB7093); put("papayawhip", 0xFFEFD5); put("peachpuff", 0xFFDAB9); put("peru", 0xCD853F);
        put("pink", 0xFFC0CB); put("plum", 0xDDA0DD); put("powderblue", 0xB0E0E6); put("rosybrown", 0xBC8F8F);
        put("royalblue", 0x4169E1); put("saddlebrown", 0x8B4513); put("salmon", 0xFA8072); put("sandybrown", 0xF4A460);
        put("seagreen", 0x2E8B57); put("seashell", 0xFFF5EE); put("sienna", 0xA0522D); put("skyblue", 0x87CEEB);
        put("slateblue", 0x6A5ACD); put("slategray", 0x708090); put("snow", 0xFFFAFA); put("springgreen", 0x00FF7F);
        put("steelblue", 0x4682B4); put("tan", 0xD2B48C); put("thistle", 0xD8BFD8); put("tomato", 0xFF6347);
        put("turquoise", 0x40E0D0); put("violet", 0xEE82EE); put("wheat", 0xF5DEB3); put("whitesmoke", 0xF5F5F5);
        put("yellowgreen", 0x9ACD32); put("rebeccapurple", 0x663399);
    }
}

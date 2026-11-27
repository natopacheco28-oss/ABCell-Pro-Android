package com.abcell.movil;

/** Paper width is NOT printable width. Profiles assume the common 203 dpi print heads. */
public enum PaperProfile {
    MM58("58", "58 mm · 384 puntos (48 mm útiles)", 58, 384),
    MM80("80", "80 mm · 576 puntos (72 mm útiles)", 80, 576),
    MM80_512("80-512", "80 mm · 512 puntos (64 mm útiles)", 80, 512);
    public final String key, label;
    public final int millimeters, dots;
    PaperProfile(String key, String label, int mm, int dots) { this.key=key; this.label=label; this.millimeters=mm; this.dots=dots; }
    public static PaperProfile from(String key) {
        for (PaperProfile p : values()) if (p.key.equals(key)) return p;
        return MM58;
    }
    public int widthMils() { return Math.round(millimeters * 1000f / 25.4f); }
    public int marginMils() { return Math.round((millimeters - dots * 25.4f / 203) * 500f / 25.4f); }
}

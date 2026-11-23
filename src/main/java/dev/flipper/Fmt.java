package dev.flipper;

import java.util.Locale;

/** Small text-formatting helpers shared by the HUD and chat alerts. */
public final class Fmt {
    private Fmt() {}

    public static String trunc(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    public static String coins(double n) {
        double a = Math.abs(n);
        String sign = n < 0 ? "-" : "";
        if (a >= 1e9) return sign + String.format(Locale.ROOT, "%.2fb", a / 1e9);
        if (a >= 1e6) return sign + String.format(Locale.ROOT, "%.2fm", a / 1e6);
        if (a >= 1e3) return sign + String.format(Locale.ROOT, "%.1fk", a / 1e3);
        return sign + Math.round(a);
    }

    /** bazaar order price: keeps one decimal for cheap items, since 0.1 coin undercuts matter there */
    public static String price(double v) { return v >= 1000 ? coins(v) : String.format(Locale.ROOT, "%.1f", v); }

    public static String hours(double h) {
        return h < 1 ? Math.max(1, Math.round(h * 60)) + "m" : String.format(Locale.ROOT, "%.1fh", h);
    }
}

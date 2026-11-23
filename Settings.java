package dev.flipper;

/** All user-tweakable options. Saved as config/skyflipper.json. */
public class Settings {
    public boolean hudVisible = true;
    /** BAZAAR, AUCTION or BOTH */
    public String mode = "BOTH";
    public int hudX = 4;
    public int hudY = 4;
    /** rows shown per section */
    public int rows = 3;
    /** chat alert when a NEW auction deal has at least this much profit (0 = off) */
    public double alertMinProfit = 200_000;
    /** max alerts per scan */
    public int alertMax = 3;
    /** remember learned sales between sessions */
    public boolean saveSales = true;

    // bazaar
    public double bzBudget = 1_000_000;
    public double bzTax = 0.0125;
    public double bzMinVolume = 50;

    // auction house (BIN only)
    public double ahBudget = 5_000_000;
    public double ahMinProfit = 50_000;
    public double ahMinPct = 5;
    public double ahSafetyPct = 5;
    public int ahMinComps = 3;
    public int ahMinSold = 2;
    public double ahMaxHours = 3;
    public boolean ahStrictReforge = true;
    public boolean ahIncludePets = false;
    /** seconds between full auction scans (minimum 60) */
    public int ahRefreshSeconds = 60;
}

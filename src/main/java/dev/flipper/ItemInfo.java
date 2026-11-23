package dev.flipper;

import java.util.*;
import java.util.regex.*;

/** What actually makes one auction item different from another (decoded from item_bytes). */
public final class ItemInfo {
    public record Pet(String type, String tier, String held, String skin, double exp) {}

    public final String id;
    public final int count;
    public final Map<String, Integer> ench;
    public final Map<String, Integer> attrs;
    public final int stars, recomb, gem, hpb, art;
    public final String mod;
    public final Set<String> extras;
    public final String variant;
    public final Pet pet;
    public final double q;

    private static final String[] VALUED_KEYS = {
        "talisman_enrichment", "wood_singularity_count", "farming_for_dummies_count", "skin", "ethermerge",
        "power_ability_scroll", "tuned_transmission", "drill_part_upgrade_module", "drill_part_engine",
        "drill_part_fuel_tank", "dye_item", "transmission_tuner", "jalapeno_count", "mana_disintegrator_count",
        "artifact_of_control", "divan_powder_coating", "rune_item", "mining_experience_bonus"
    };
    private static final Map<String, Integer> GEM_RANK =
        Map.of("ROUGH", 1, "FLAWED", 2, "FINE", 3, "FLAWLESS", 4, "PERFECT", 5);
    private static final Map<String, Double> PET_L100 = Map.of(
        "COMMON", 5624785.0, "UNCOMMON", 8644220.0, "RARE", 12626665.0,
        "EPIC", 18608500.0, "LEGENDARY", 25353230.0, "MYTHIC", 25353230.0);

    private ItemInfo(String id, int count, Map<String, Integer> ench, Map<String, Integer> attrs, int stars,
                     int recomb, String mod, int gem, int hpb, int art, Set<String> extras, String variant, Pet pet) {
        this.id = id; this.count = count; this.ench = ench; this.attrs = attrs; this.stars = stars;
        this.recomb = recomb; this.mod = mod; this.gem = gem; this.hpb = hpb; this.art = art;
        this.extras = extras; this.variant = variant; this.pet = pet;
        double qq = ench.size();
        for (int v : attrs.values()) qq += v * 1.5;
        qq += gem + hpb * 0.3 + art * 0.5 + extras.size() * 2;
        this.q = qq;
    }

    // ---------- decoding ----------

    public static ItemInfo decode(String b64) {
        try {
            return fromNbt(Nbt.parseB64(b64));
        } catch (Exception e) {
            return null;
        }
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static Map<String, Integer> intMap(Object o) {
        Map<String, Integer> out = new TreeMap<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), num(e.getValue(), 0));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static ItemInfo fromNbt(Map<String, Object> root) {
        if (!(root.get("i") instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> itRaw)) return null;
        Map<String, Object> it = (Map<String, Object>) itRaw;
        int count = Math.max(1, num(it.get("Count"), 1));
        Map<String, Object> ea = new HashMap<>();
        if (it.get("tag") instanceof Map<?, ?> tag && tag.get("ExtraAttributes") instanceof Map<?, ?> e)
            ea = (Map<String, Object>) e;

        Map<String, Integer> ench = intMap(ea.get("enchantments"));
        Map<String, Integer> attrs = intMap(ea.get("attributes"));

        Pet pet = null;
        if (ea.get("petInfo") instanceof String pj) {
            pet = new Pet(jsonStr(pj, "type"), jsonStr(pj, "tier"), jsonStr(pj, "heldItem"),
                    firstNonEmpty(jsonStr(pj, "skin"), ea.get("skin") instanceof String s ? s : ""), jsonNum(pj, "exp"));
        }

        int gem = 0;
        if (ea.get("gems") instanceof Map<?, ?> gm) {
            for (Map.Entry<?, ?> e : gm.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (k.equals("unlocked_slots") || k.endsWith("_gem")) continue;
                Object v = e.getValue();
                String quality = v instanceof String s ? s : (v instanceof Map<?, ?> vm && vm.get("quality") instanceof String s2 ? s2 : "");
                gem += GEM_RANK.getOrDefault(quality, 1);
            }
        }

        Set<String> extras = new TreeSet<>();
        for (String k : VALUED_KEYS) {
            Object v = ea.get(k);
            if (v instanceof Number n && n.doubleValue() != 0) extras.add(k + "=" + n);
            else if (v instanceof String s && !s.isEmpty()) extras.add(k + "=" + s);
        }
        if (ea.get("ability_scroll") instanceof List<?> sc) for (Object s : sc) extras.add("scroll:" + s);
        if (ea.get("runes") instanceof Map<?, ?> rm) for (Map.Entry<?, ?> e : rm.entrySet()) extras.add("rune:" + e.getKey() + e.getValue());

        String id = ea.get("id") instanceof String s ? s : "VANILLA_" + it.get("id");
        String variant = "";
        switch (id) {
            case "ENCHANTED_BOOK" -> variant = join(ench);
            case "RUNE" -> variant = join(intMap(ea.get("runes")));
            case "POTION" -> variant = ea.get("potion") + "/" + ea.get("potion_level") + "/" + ea.get("enhanced")
                    + "/" + ea.get("extended") + "/" + ea.get("splash");
            case "ATTRIBUTE_SHARD" -> variant = join(attrs);
            default -> {}
        }
        int stars = ea.containsKey("upgrade_level") ? num(ea.get("upgrade_level"), 0) : num(ea.get("dungeon_item_level"), 0);
        String mod = ea.get("modifier") instanceof String s ? s : "";
        return new ItemInfo(id, count, ench, attrs, stars, num(ea.get("rarity_upgrades"), 0), mod, gem,
                num(ea.get("hot_potato_count"), 0), num(ea.get("art_of_war_count"), 0), extras, variant, pet);
    }

    private static String join(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : new TreeMap<>(m).entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String a, String b) { return a != null && !a.isEmpty() ? a : (b == null ? "" : b); }

    private static String jsonStr(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static double jsonNum(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9.]+(?:[eE][+-]?[0-9]+)?)").matcher(json);
        if (!m.find()) return 0;
        try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return 0; }
    }

    // ---------- saving / restoring (plain fields so Gson can handle it) ----------

    public static final class Data {
        public String id, mod, variant, petType, petTier, petHeld, petSkin;
        public int count, stars, recomb, gem, hpb, art;
        public double petExp;
        public boolean hasPet;
        public Map<String, Integer> ench, attrs;
        public List<String> extras;
    }

    public Data toData() {
        Data d = new Data();
        d.id = id; d.mod = mod; d.variant = variant; d.count = count; d.stars = stars; d.recomb = recomb;
        d.gem = gem; d.hpb = hpb; d.art = art; d.ench = new TreeMap<>(ench); d.attrs = new TreeMap<>(attrs);
        d.extras = new ArrayList<>(extras);
        if (pet != null) {
            d.hasPet = true; d.petType = pet.type(); d.petTier = pet.tier(); d.petHeld = pet.held();
            d.petSkin = pet.skin(); d.petExp = pet.exp();
        }
        return d;
    }

    public static ItemInfo fromData(Data d) {
        if (d == null || d.id == null) return null;
        Pet p = d.hasPet ? new Pet(nz(d.petType), nz(d.petTier), nz(d.petHeld), nz(d.petSkin), d.petExp) : null;
        return new ItemInfo(d.id, Math.max(1, d.count), d.ench == null ? new TreeMap<>() : new TreeMap<>(d.ench),
                d.attrs == null ? new TreeMap<>() : new TreeMap<>(d.attrs), d.stars, d.recomb, nz(d.mod), d.gem,
                d.hpb, d.art, d.extras == null ? new TreeSet<>() : new TreeSet<>(d.extras), nz(d.variant), p);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ---------- grouping and comparison ----------

    public static String petBand(Pet p) {
        double ratio = p.exp() / PET_L100.getOrDefault(p.tier(), 25353230.0);
        if (ratio >= 8) return "200";
        if (ratio >= 1) return "100";
        return "l" + (int) Math.floor(ratio * 10);
    }

    public String baseKey() {
        StringBuilder k = new StringBuilder(id).append('|').append(stars).append('|').append(recomb);
        if (id.equals("PET") && pet != null) {
            k.append('|').append(pet.type()).append('|').append(pet.tier()).append('|').append(petBand(pet))
             .append('|').append(pet.held()).append('|').append(pet.skin());
        } else if (!variant.isEmpty()) {
            k.append('|').append(variant);
        }
        return k.toString();
    }

    public String bucketKey(boolean strict) { return baseKey() + (strict ? "|" + mod : ""); }

    /** Is comp c "not better than" candidate x in every way that matters, and close in overall quality? */
    public static boolean similar(ItemInfo c, ItemInfo x, boolean strict) {
        if (strict && !c.mod.equals(x.mod)) return false;
        for (Map.Entry<String, Integer> e : c.ench.entrySet())
            if (x.ench.getOrDefault(e.getKey(), -1) < e.getValue()) return false;
        for (Map.Entry<String, Integer> e : c.attrs.entrySet())
            if (x.attrs.getOrDefault(e.getKey(), -1) < e.getValue()) return false;
        if (c.gem > x.gem || c.hpb > x.hpb || c.art > x.art) return false;
        if (!x.extras.containsAll(c.extras)) return false;
        return (x.q - c.q) <= Math.max(3, x.q * 0.25);
    }

    /** Looser "same kind of item" check used for demand and queue counting. */
    public static boolean looselySimilar(ItemInfo c, ItemInfo x, boolean strict) {
        if (strict && !c.mod.equals(x.mod)) return false;
        return Math.abs(c.q - x.q) <= Math.max(4, 0.5 * Math.max(c.q, x.q));
    }

    public String summary() {
        List<String> p = new ArrayList<>();
        if (stars > 0) p.add("*" + stars);
        if (recomb > 0) p.add("R");
        return String.join(" ", p);
    }
}

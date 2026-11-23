package dev.flipper;

import dev.flipper.Models.AhAuction;
import dev.flipper.Models.AhDeal;
import dev.flipper.Models.AhSale;
import java.util.*;

/**
 * BIN flips. Decodes every listing's real item data, only compares like with like, learns real sale
 * prices from recently ended auctions, and keeps only items that actually sell.
 */
public final class AuctionAnalyzer {
    private static final int CAND_PER_BUCKET = 5;
    private static final int SOLD_CAP = 60;
    private static final long SOLD_MAX_AGE_MS = 12L * 3600 * 1000;
    private static final double TOO_GOOD_RATIO = 3;
    private static final double UNVERIFIED_HAIRCUT = 0.05;
    private static final long POLL_GAP_CAP_MS = 40_000;

    private static final class Listing {
        String uuid, name;
        long price, end;
        ItemInfo info;
        int count;
        double unit;
    }

    private static final class Sold {
        final String id; final ItemInfo info; final double unit; final long t;
        Sold(String id, ItemInfo info, double unit, long t) { this.id = id; this.info = info; this.unit = unit; this.t = t; }
    }

    /** one remembered sale, in a form that can be written to disk */
    public static final class SavedSale {
        public String id;
        public double unit;
        public long t;
        public ItemInfo.Data i;
    }


    private final Map<String, ItemInfo> cache = new HashMap<>();
    private final Set<String> undecodable = new HashSet<>();
    private final Map<String, List<Sold>> soldByBase = new HashMap<>();
    private final Set<String> soldIds = new HashSet<>();
    private List<Listing> listings = new ArrayList<>();
    private Set<String> prevUuids = new HashSet<>();
    private Set<String> newUuids = new HashSet<>();
    private long observedMs = 0;
    private long lastSalePoll = -1;

    public synchronized int listingCount() { return listings.size(); }
    public synchronized int soldCount() { int n = 0; for (List<Sold> l : soldByBase.values()) n += l.size(); return n; }
    public synchronized long observedMinutes() { return observedMs / 60000; }

    // ---------- ingestion ----------

    public synchronized void ingestListings(List<AhAuction> raw) {
        List<Listing> out = new ArrayList<>(raw.size());
        Set<String> nowUuids = new HashSet<>();
        for (AhAuction a : raw) {
            if (!nowUuids.add(a.uuid())) continue;
            ItemInfo info = cache.get(a.uuid());
            if (info == null && !undecodable.contains(a.uuid())) {
                info = a.b64() == null ? null : ItemInfo.decode(a.b64());
                if (info == null) undecodable.add(a.uuid()); else cache.put(a.uuid(), info);
            }
            if (info == null) continue;
            Listing l = new Listing();
            l.uuid = a.uuid(); l.name = a.name().replaceAll("§.", ""); l.price = a.price(); l.end = a.end();
            l.info = info; l.count = info.count; l.unit = (double) a.price() / info.count;
            out.add(l);
        }
        newUuids = prevUuids.isEmpty() ? new HashSet<>() : diff(nowUuids, prevUuids);
        prevUuids = nowUuids;
        listings = out;
        cache.keySet().retainAll(nowUuids);
        undecodable.retainAll(nowUuids);
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> r = new HashSet<>(a);
        r.removeAll(b);
        return r;
    }

    public synchronized void ingestSales(List<AhSale> sales, long now) {
        if (lastSalePoll >= 0) observedMs += Math.min(now - lastSalePoll, POLL_GAP_CAP_MS);
        lastSalePoll = now;
        for (AhSale s : sales) {
            if (s.price() <= 0 || soldIds.contains(s.id()) || s.b64() == null) continue;
            ItemInfo info = ItemInfo.decode(s.b64());
            if (info == null) continue;
            addSold(info, s.id(), (double) s.price() / info.count, now);
        }
        long cutoff = now - SOLD_MAX_AGE_MS;
        Iterator<Map.Entry<String, List<Sold>>> it = soldByBase.entrySet().iterator();
        while (it.hasNext()) {
            List<Sold> l = it.next().getValue();
            l.removeIf(x -> x.t <= cutoff);
            if (l.isEmpty()) it.remove();
        }
        if (soldIds.size() > 30_000) soldIds.clear();
    }

    synchronized void addSold(ItemInfo info, String id, double unit, long t) {
        List<Sold> l = soldByBase.computeIfAbsent(info.baseKey(), k -> new ArrayList<>());
        l.add(new Sold(id, info, unit, t));
        if (l.size() > SOLD_CAP) l.remove(0);
        soldIds.add(id);
    }

    synchronized void setObservedMs(long ms) { observedMs = ms; }
    public synchronized long observedMs() { return observedMs; }

    /** newest sales first, capped so the file stays small */
    public synchronized List<SavedSale> exportSales(int max) {
        List<Sold> all = new ArrayList<>();
        for (List<Sold> l : soldByBase.values()) all.addAll(l);
        all.sort((a, b) -> Long.compare(b.t, a.t));
        List<SavedSale> out = new ArrayList<>();
        for (Sold s : all) {
            if (out.size() >= max) break;
            SavedSale v = new SavedSale();
            v.id = s.id; v.unit = s.unit; v.t = s.t; v.i = s.info.toData();
            out.add(v);
        }
        return out;
    }

    public synchronized void importSales(List<SavedSale> saved, long observedMsSaved, long now) {
        if (saved == null) return;
        long cutoff = now - SOLD_MAX_AGE_MS;
        List<SavedSale> sorted = new ArrayList<>(saved);
        sorted.sort(Comparator.comparingLong(v -> v.t));
        for (SavedSale v : sorted) {
            if (v == null || v.id == null || v.t <= cutoff || soldIds.contains(v.id)) continue;
            ItemInfo info = ItemInfo.fromData(v.i);
            if (info != null) addSold(info, v.id, v.unit, v.t);
        }
        observedMs = Math.max(observedMs, observedMsSaved);
    }

    /** test hook */
    synchronized void setListingsForTest(List<Object[]> rows) {
        listings = new ArrayList<>();
        for (Object[] r : rows) {
            Listing l = new Listing();
            l.uuid = (String) r[0]; l.name = (String) r[1]; l.price = (Long) r[2]; l.end = (Long) r[3];
            l.info = (ItemInfo) r[4]; l.count = l.info.count; l.unit = (double) l.price / l.count;
            listings.add(l);
        }
    }

    // ---------- maths ----------

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int m = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
    }

    static double trimLowMedian(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int keep = Math.max(2, (int) Math.ceil(s.size() * 0.75));
        return median(s.subList(0, Math.min(keep, s.size())));
    }

    /** AH fees: listing fee 1% (<10m) / 2% (<100m) / 2.5%, plus 1% claim tax on sales of 1m+. */
    static double fees(double sell) {
        double listFee = sell < 1e7 ? 0.01 : (sell < 1e8 ? 0.02 : 0.025);
        double claim = sell >= 1e6 ? 0.01 : 0;
        return sell * (listFee + claim);
    }

    // ---------- the actual flip search ----------

    public synchronized List<AhDeal> computeDeals(Settings s, long now) {
        boolean strict = s.ahStrictReforge;
        double safety = s.ahSafetyPct / 100.0;
        double windowHours = Math.min(12, Math.max(0.25, observedMs / 3_600_000.0));
        int minSold = Math.max(1, s.ahMinSold);

        Map<String, List<Listing>> buckets = new HashMap<>();
        for (Listing l : listings) buckets.computeIfAbsent(l.info.bucketKey(strict), k -> new ArrayList<>()).add(l);

        List<AhDeal> out = new ArrayList<>();
        for (List<Listing> arr : buckets.values()) {
            if (arr.size() < 2) continue;
            arr.sort(Comparator.comparingDouble(l -> l.unit));
            int lim = Math.min(CAND_PER_BUCKET, arr.size());
            for (int ci = 0; ci < lim; ci++) {
                Listing x = arr.get(ci);
                if (!s.ahIncludePets && x.info.id.equals("PET")) continue;
                if (x.price > s.ahBudget) continue;
                if (x.end - now < 90_000) continue;

                // price evidence: the cheapest similar, pricier listings
                List<Double> comps = new ArrayList<>(3);
                for (Listing c : arr) {
                    if (c == x || c.unit <= x.unit) continue;
                    if (ItemInfo.similar(c.info, x.info, strict)) { comps.add(c.unit); if (comps.size() >= 3) break; }
                }
                if (comps.isEmpty()) continue;
                double listRef = median(comps);

                // demand: any sale of this kind of item; price evidence from strictly similar sales
                List<Sold> recs = soldByBase.getOrDefault(x.info.baseKey(), List.of());
                int demandN = 0;
                List<Double> strictPrices = new ArrayList<>();
                for (Sold sd : recs) {
                    if (ItemInfo.looselySimilar(sd.info, x.info, strict)) demandN++;
                    if (ItemInfo.similar(sd.info, x.info, strict)) strictPrices.add(sd.unit);
                }
                if (demandN < minSold) continue;

                Double soldRef = strictPrices.size() >= 2 ? trimLowMedian(strictPrices) : null;
                double refUnit = soldRef != null ? Math.min(listRef, soldRef) : listRef * (1 - UNVERIFIED_HAIRCUT);
                refUnit *= (1 - safety);
                double sellTotal = refUnit * x.count;
                double profit = sellTotal - fees(sellTotal) - x.price;
                double pct = profit / x.price * 100;
                if (profit < s.ahMinProfit || pct < s.ahMinPct) continue;

                int ahead = 0;
                for (Listing c : arr) {
                    if (c.unit > refUnit) break;
                    if (c != x && ItemInfo.looselySimilar(c.info, x.info, strict)) ahead++;
                }
                double hours = (ahead + 1) / (demandN / windowHours);
                if (hours > s.ahMaxHours) continue;

                // suspicious-looking results are dropped, not shown
                if (comps.size() < s.ahMinComps) continue;
                if (refUnit / x.unit > TOO_GOOD_RATIO) continue;
                if (comps.size() >= 3 && comps.get(comps.size() - 1) / comps.get(0) > 1.6) continue;
                if (soldRef != null && soldRef < listRef * 0.7) continue;

                out.add(new AhDeal(x.uuid, x.name, x.count, x.price, sellTotal, profit, pct, hours,
                        soldRef != null, newUuids.contains(x.uuid)));
            }
        }
        out.sort((a, b) -> Double.compare(b.profit(), a.profit()));
        return out;
    }
}

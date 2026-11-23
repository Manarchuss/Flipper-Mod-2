package dev.flipper;

import dev.flipper.Models.BzProduct;
import dev.flipper.Models.BzRow;
import java.util.*;

/** Bazaar flips: ranks items by profit/hour after tax and drops manipulated-looking ones. */
public final class BazaarAnalyzer {
    private static final int HISTORY_MAX = 45;
    private static final double EXTREME_MARGIN_PCT = 35;
    private static final double SPIKE_PCT = 15;
    private static final double SPREAD_DEVIATION_MULT = 1.8;

    private record Snap(double buy, double sell) {}

    private final Map<String, Deque<Snap>> history = new HashMap<>();
    private List<BzProduct> latest = List.of();

    /** coins above/below the best order you undercut it by */
    private static final double UNDERCUT = 0.1;

    /** conservative ask/bid: never trust a lone top-of-book order more than the weighted average */
    private static double ask(BzProduct p) {
        return (p.ask() > 0 && p.quickAsk() > 0) ? Math.min(p.ask(), p.quickAsk()) : Math.max(p.ask(), p.quickAsk());
    }
    private static double bid(BzProduct p) {
        return Math.max(p.bid(), p.quickBid());
    }

    public synchronized void update(List<BzProduct> products) {
        latest = products;
        for (BzProduct p : products) {
            double ask = ask(p), bid = bid(p);
            if (ask <= 0 || bid <= 0) continue;
            Deque<Snap> h = history.computeIfAbsent(p.id(), k -> new ArrayDeque<>());
            h.addLast(new Snap(ask, bid));
            while (h.size() > HISTORY_MAX) h.removeFirst();
        }
    }

    public static String humanName(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.toLowerCase().split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Clean (unflagged) rows, best profit/hour first. Prices assume you undercut/outbid the top order by 0.1. */
    public synchronized List<BzRow> top(Settings s, int limit) {
        List<BzRow> rows = new ArrayList<>();
        for (BzProduct p : latest) {
            double ask = ask(p), bid = bid(p);
            if (ask <= 0 || bid <= 0) continue;
            double hourlyVolume = (p.buyWeek() + p.sellWeek()) / 2 / 168;
            if (hourlyVolume < s.bzMinVolume) continue;

            double buyAt = bid + UNDERCUT, sellAt = ask - UNDERCUT;
            if (sellAt <= buyAt) continue;
            double profitPerUnit = sellAt * (1 - s.bzTax) - buyAt;
            if (profitPerUnit <= 0) continue;
            double marginPct = profitPerUnit / buyAt * 100;
            double units = Math.max(0, Math.min(Math.floor(s.bzBudget / buyAt), hourlyVolume));
            double profitPerHour = profitPerUnit * units;

            if (isFlagged(p, ask, bid, marginPct)) continue;
            rows.add(new BzRow(humanName(p.id()), buyAt, sellAt, marginPct, profitPerHour));
        }
        rows.sort((a, b) -> Double.compare(b.profitPerHour(), a.profitPerHour()));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    private boolean isFlagged(BzProduct p, double ask, double bid, double marginPct) {
        if (marginPct > EXTREME_MARGIN_PCT) return true;
        if (p.buyOrders() > 0 && p.buyOrders() <= 2 || p.sellOrders() > 0 && p.sellOrders() <= 2) return true;
        Deque<Snap> h = history.get(p.id());
        if (h == null) return false;
        List<Snap> list = new ArrayList<>(h);
        if (list.size() >= 2) {
            Snap prev = list.get(list.size() - 2);
            double sellMove = Math.abs(bid - prev.sell()) / prev.sell() * 100;
            double buyMove = Math.abs(ask - prev.buy()) / prev.buy() * 100;
            if (sellMove > SPIKE_PCT || buyMove > SPIKE_PCT) return true;
        }
        if (list.size() >= 4) {
            List<Double> spreads = new ArrayList<>();
            for (Snap sn : list.subList(0, list.size() - 1)) spreads.add((sn.buy() - sn.sell()) / sn.sell() * 100);
            Collections.sort(spreads);
            double median = spreads.get(spreads.size() / 2);
            if (median > 0.05 && marginPct > median * SPREAD_DEVIATION_MULT && marginPct > 3) return true;
        }
        return false;
    }
}

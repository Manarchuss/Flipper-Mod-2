package dev.flipper;

import dev.flipper.Models.AhAuction;
import dev.flipper.Models.AhDeal;
import dev.flipper.Models.BzRow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Background worker: polls the Hypixel API and keeps an immutable snapshot for the HUD to read. */
public final class Engine {
    public record Snapshot(List<BzRow> bz, List<AhDeal> ah, String status) {}

    private final Supplier<Settings> settings;
    private final Api api = new Api();
    private final BazaarAnalyzer bz = new BazaarAnalyzer();
    private final AuctionAnalyzer ah = new AuctionAnalyzer();

    private volatile boolean active = false;
    private volatile Snapshot snapshot = new Snapshot(List.of(), List.of(), "starting...");
    private volatile String bzStatus = "BZ waiting";
    private volatile String ahStatus = "AH waiting";
    private volatile String salesStatus = "";
    private final Queue<String> alerts = new ConcurrentLinkedQueue<>();
    private final Set<String> alerted = new HashSet<>();
    private int saveTick = 0;

    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(3, daemon("skyflipper-sched"));
    private final ExecutorService fetchers = Executors.newFixedThreadPool(4, daemon("skyflipper-fetch"));

    public Engine(Supplier<Settings> settings) { this.settings = settings; }

    private static ThreadFactory daemon(String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }

    public Snapshot snapshot() { return snapshot; }

    /** next pending chat alert, or null */
    public String pollAlert() { return alerts.poll(); }

    /** Only poll the API while the player is actually in a world. */
    public void setActive(boolean a) { active = a; }

    public void start() {
        if (settings.get().saveSales) {
            Persist.load(ah);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> Persist.save(ah), "skyflipper-save"));
        }
        pool.scheduleWithFixedDelay(() -> guard(this::bazaarTask, "BZ"), 1, 20, TimeUnit.SECONDS);
        pool.scheduleWithFixedDelay(() -> guard(this::salesTask, "sales"), 3, 20, TimeUnit.SECONDS);
        pool.scheduleWithFixedDelay(() -> guard(this::auctionTask, "AH"),
                5, Math.max(60, settings.get().ahRefreshSeconds), TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface Task { void run() throws Exception; }

    private void guard(Task r, String label) {
        if (!active) return;
        try {
            r.run();
        } catch (Throwable t) {
            String msg = label + " error: " + t.getClass().getSimpleName() + (t.getMessage() != null ? " " + t.getMessage() : "");
            if (label.equals("BZ")) bzStatus = msg; else if (label.equals("AH")) ahStatus = msg; else salesStatus = msg;
            publish();
        }
    }

    /** Recompute the snapshot from current data and settings (also called after a config reload). */
    public synchronized void publish() {
        Settings s = settings.get();
        long now = System.currentTimeMillis();
        List<BzRow> b = bz.top(s, s.rows);
        List<AhDeal> all = ah.computeDeals(s, now);
        List<AhDeal> a = new ArrayList<>(all.subList(0, Math.min(s.rows, all.size())));
        queueAlerts(s, all);
        String status = ahStatus + " | sales " + ah.soldCount() + " (" + ah.observedMinutes() + "m)"
                + (bzStatus.contains("error") ? " | " + bzStatus : "")
                + (salesStatus.contains("error") ? " | " + salesStatus : "");
        snapshot = new Snapshot(b, a, status);
    }

    private void queueAlerts(Settings s, List<AhDeal> all) {
        if (s.alertMinProfit <= 0) return;
        int n = 0;
        for (AhDeal d : all) {
            if (!d.isNew() || d.profit() < s.alertMinProfit) continue;
            if (!alerted.add(d.uuid())) continue;
            if (n++ >= s.alertMax || alerts.size() > 20) break;
            alerts.add("[Flip] " + d.name() + (d.count() > 1 ? " x" + d.count() : "") + ": buy " + Fmt.coins(d.buy())
                    + ", resell " + (d.verified() ? "" : "~") + Fmt.coins(d.resell()) + ", profit " + Fmt.coins(d.profit())
                    + ", sells in ~" + Fmt.hours(d.hours()) + ". Find it: /ah " + d.name());
        }
        if (alerted.size() > 5000) alerted.clear();
    }

    private void bazaarTask() throws Exception {
        bz.update(api.bazaar());
        bzStatus = "BZ ok";
        publish();
    }

    private void salesTask() throws Exception {
        ah.ingestSales(api.endedSales(), System.currentTimeMillis());
        salesStatus = "";
        if (settings.get().saveSales && ++saveTick % 6 == 0) Persist.save(ah);
        publish();
    }

    private void auctionTask() throws Exception {
        ahStatus = "AH scanning...";
        Api.AuctionPage first = api.auctionPage(0);
        List<AhAuction> all = new ArrayList<>(first.auctions());
        List<Future<Api.AuctionPage>> futures = new ArrayList<>();
        for (int p = 1; p < first.totalPages(); p++) {
            final int page = p;
            futures.add(fetchers.submit(() -> api.auctionPage(page)));
        }
        int failed = 0;
        for (Future<Api.AuctionPage> f : futures) {
            try { all.addAll(f.get(60, TimeUnit.SECONDS).auctions()); } catch (Exception e) { failed++; }
        }
        if (failed > futures.size() / 5) throw new IllegalStateException(failed + " of " + (futures.size() + 1) + " pages failed");
        ahStatus = "AH decoding...";
        ah.ingestListings(all);
        ahStatus = "AH " + ah.listingCount() + " BIN";
        publish();
    }
}

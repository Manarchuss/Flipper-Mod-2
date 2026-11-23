package dev.flipper;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Remembers learned auction sales between sessions so the AH list doesn't start empty every launch. */
public final class Persist {
    private static final Gson GSON = new Gson();

    private Persist() {}

    public static final class FileData {
        public long observedMs;
        public long savedAt;
        public List<AuctionAnalyzer.SavedSale> sales;
    }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("skyflipper-sales.json"); }

    public static void save(AuctionAnalyzer ah) {
        try {
            FileData f = new FileData();
            f.observedMs = ah.observedMs();
            f.savedAt = System.currentTimeMillis();
            f.sales = ah.exportSales(6000);
            Path tmp = path().resolveSibling("skyflipper-sales.json.tmp");
            Files.writeString(tmp, GSON.toJson(f), StandardCharsets.UTF_8);
            Files.move(tmp, path(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            System.err.println("[skyflipper] could not save sales: " + e);
        }
    }

    public static void load(AuctionAnalyzer ah) {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            FileData f = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), FileData.class);
            if (f != null) ah.importSales(f.sales, f.observedMs, System.currentTimeMillis());
        } catch (IOException | RuntimeException e) {
            System.err.println("[skyflipper] could not read saved sales: " + e);
        }
    }
}

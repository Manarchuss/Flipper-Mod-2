package dev.flipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Loads/saves config/skyflipper.json */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("skyflipper.json"); }

    public static Settings load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                Settings s = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Settings.class);
                if (s != null) { sanitize(s); save(s); return s; }
            }
        } catch (Exception e) {
            System.err.println("[skyflipper] could not read config, using defaults: " + e);
        }
        Settings s = new Settings();
        save(s);
        return s;
    }

    private static void sanitize(Settings s) {
        if (s.mode == null || !(s.mode.equals("BAZAAR") || s.mode.equals("AUCTION") || s.mode.equals("BOTH"))) s.mode = "BOTH";
        s.rows = Math.max(1, Math.min(10, s.rows));
        s.ahRefreshSeconds = Math.max(60, s.ahRefreshSeconds);
    }

    public static void save(Settings s) {
        try {
            Files.writeString(path(), GSON.toJson(s), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[skyflipper] could not save config: " + e);
        }
    }
}

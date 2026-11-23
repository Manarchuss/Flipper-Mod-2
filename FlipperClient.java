package dev.flipper;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flipper.Models.AhDeal;
import dev.flipper.Models.BzRow;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Small corner HUD showing the best bazaar and auction-house flips. Display only: it never clicks or sends anything. */
public final class FlipperClient implements ClientModInitializer {
    public static final String MOD_ID = "skyflipper";

    private static final int GOLD = 0xFFFFD36B, WHITE = 0xFFE8E8E8, GREEN = 0xFF57B894, GRAY = 0xFF9AA0A6, DIM = 0xFF6B7078;
    private static final int PANEL_W = 188, LINE_H = 10;

    private record Line(String text, int color) {}

    private static Settings settings;
    private static Engine engine;

    @Override
    public void onInitializeClient() {
        settings = Config.load();
        engine = new Engine(() -> settings);
        engine.start();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyflipper.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_U, category));
        KeyMapping modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyflipper.mode", InputConstants.Type.KEYSYM, InputConstants.KEY_I, category));
        KeyMapping reloadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyflipper.reload", InputConstants.Type.KEYSYM, InputConstants.KEY_O, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            engine.setActive(client.player != null);
            String alert;
            while ((alert = engine.pollAlert()) != null) {
                if (client.player != null) client.player.sendSystemMessage(Component.literal(alert));
            }
            while (toggleKey.consumeClick()) {
                settings.hudVisible = !settings.hudVisible;
                Config.save(settings);
            }
            while (modeKey.consumeClick()) {
                settings.mode = switch (settings.mode) { case "BOTH" -> "BAZAAR"; case "BAZAAR" -> "AUCTION"; default -> "BOTH"; };
                Config.save(settings);
            }
            while (reloadKey.consumeClick()) {
                settings = Config.load();
                engine.publish();
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"), FlipperClient::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker tracker) {
        if (settings == null || engine == null || !settings.hudVisible) return;
        Minecraft mc = Minecraft.getInstance();
        List<Line> lines = buildLines();
        int x = settings.hudX, y = settings.hudY;
        int h = lines.size() * LINE_H + 5;
        g.fill(x, y, x + PANEL_W, y + h, 0x99000000);
        int ty = y + 3;
        for (Line l : lines) {
            g.text(mc.font, l.text(), x + 3, ty, l.color(), false);
            ty += LINE_H;
        }
    }

    private static List<Line> buildLines() {
        Engine.Snapshot sn = engine.snapshot();
        String mode = settings.mode;
        List<Line> out = new ArrayList<>();
        out.add(new Line("Flips - " + switch (mode) { case "BAZAAR" -> "Bazaar"; case "AUCTION" -> "Auction"; default -> "Bazaar + AH"; }, GOLD));

        if (!mode.equals("AUCTION")) {
            out.add(new Line("Bazaar (buy>sell order, profit/hr)", GRAY));
            if (sn.bz().isEmpty()) out.add(new Line("  warming up...", DIM));
            for (BzRow r : sn.bz()) {
                out.add(new Line(Fmt.trunc(r.name(), 11) + " " + Fmt.price(r.buyAt()) + ">" + Fmt.price(r.sellAt())
                        + " +" + Fmt.coins(r.profitPerHour()) + "/h", WHITE));
            }
        }
        if (!mode.equals("BAZAAR")) {
            out.add(new Line("Auction BIN (buy +profit, sells in)", GRAY));
            if (sn.ah().isEmpty()) out.add(new Line("  nothing worth flipping yet", DIM));
            for (AhDeal d : sn.ah()) {
                String name = Fmt.trunc(d.name(), 13) + (d.count() > 1 ? " x" + d.count() : "");
                String txt = name + " " + Fmt.coins(d.buy()) + " +" + (d.verified() ? "" : "~") + Fmt.coins(d.profit()) + " " + Fmt.hours(d.hours());
                out.add(new Line(txt, d.isNew() ? GREEN : WHITE));
            }
        }
        out.add(new Line(Fmt.trunc(sn.status(), 36), DIM));
        return out;
    }
}

package dev.flipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.flipper.Models.AhAuction;
import dev.flipper.Models.AhSale;
import dev.flipper.Models.BzProduct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Public Hypixel SkyBlock endpoints (no API key needed). Read-only. */
public final class Api {
    private static final String BASE = "https://api.hypixel.net/v2/skyblock/";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record AuctionPage(int totalPages, List<AhAuction> auctions) {}

    private JsonObject get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SkyFlipperHUD/1.0")
                .GET().build();
        HttpResponse<InputStream> r = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = r.body()) {
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
            try (Reader rd = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(rd).getAsJsonObject();
                if (!o.has("success") || !o.get("success").getAsBoolean()) throw new IOException("API returned success=false");
                return o;
            }
        }
    }

    private static double d(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0; }
    private static String s(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }

    private static String bytes(JsonObject o) {
        if (!o.has("item_bytes")) return null;
        JsonElement e = o.get("item_bytes");
        if (e.isJsonPrimitive()) return e.getAsString();
        if (e.isJsonObject() && e.getAsJsonObject().has("data")) return e.getAsJsonObject().get("data").getAsString();
        return null;
    }

    private static double topPrice(JsonObject product, String key) {
        if (!product.has(key) || !product.get(key).isJsonArray()) return 0;
        JsonArray arr = product.getAsJsonArray(key);
        if (arr.size() == 0) return 0;
        JsonObject first = arr.get(0).getAsJsonObject();
        return d(first, "pricePerUnit");
    }

    public List<BzProduct> bazaar() throws IOException, InterruptedException {
        JsonObject products = get("bazaar").getAsJsonObject("products");
        List<BzProduct> out = new ArrayList<>();
        for (String id : products.keySet()) {
            JsonObject p = products.getAsJsonObject(id);
            if (!p.has("quick_status")) continue;
            JsonObject q = p.getAsJsonObject("quick_status");
            // top of each side of the book; naming of the two summaries is easy to mix up, so sort by value instead
            double t1 = topPrice(p, "buy_summary"), t2 = topPrice(p, "sell_summary");
            double ask = Math.max(t1, t2), bid = (t1 > 0 && t2 > 0) ? Math.min(t1, t2) : 0;
            double qBuy = d(q, "buyPrice"), qSell = d(q, "sellPrice");
            out.add(new BzProduct(id, ask, bid, Math.max(qBuy, qSell), Math.min(qBuy, qSell),
                    (int) d(q, "buyOrders"), (int) d(q, "sellOrders"), d(q, "buyMovingWeek"), d(q, "sellMovingWeek")));
        }
        return out;
    }

    /** One page of active auctions, BIN and unclaimed only. */
    public AuctionPage auctionPage(int page) throws IOException, InterruptedException {
        JsonObject o = get("auctions?page=" + page);
        JsonArray arr = o.getAsJsonArray("auctions");
        List<AhAuction> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
            if (a.has("claimed") && a.get("claimed").getAsBoolean()) continue;
            out.add(new AhAuction(s(a, "uuid"), s(a, "item_name"), (long) d(a, "starting_bid"), (long) d(a, "end"), bytes(a)));
        }
        return new AuctionPage(o.has("totalPages") ? o.get("totalPages").getAsInt() : 1, out);
    }

    /** BIN auctions that sold in roughly the last minute. */
    public List<AhSale> endedSales() throws IOException, InterruptedException {
        JsonArray arr = get("auctions_ended").getAsJsonArray("auctions");
        List<AhSale> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
            out.add(new AhSale(s(a, "auction_id"), (long) d(a, "price"), bytes(a)));
        }
        return out;
    }
}

package dev.flipper;

public final class Models {
    private Models() {}

    /** ask/bid = best sell offer / best buy order. quickAsk/quickBid = the API's weighted averages (harder to fake). */
    public record BzProduct(String id, double ask, double bid, double quickAsk, double quickBid,
                            int buyOrders, int sellOrders, double buyWeek, double sellWeek) {}

    /** an active BIN listing as returned by the API */
    public record AhAuction(String uuid, String name, long price, long end, String b64) {}

    /** a recently ended BIN sale */
    public record AhSale(String id, long price, String b64) {}

    /** buyAt = price to put a buy order at, sellAt = price to put the sell offer at */
    public record BzRow(String name, double buyAt, double sellAt, double marginPct, double profitPerHour) {}

    public record AhDeal(String uuid, String name, int count, double buy, double resell, double profit,
                         double pct, double hours, boolean verified, boolean isNew) {}
}

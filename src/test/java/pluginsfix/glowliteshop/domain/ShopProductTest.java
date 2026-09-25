package pluginsfix.glowliteshop.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopProductTest {

    @Test
    void testPurchaseModeParsing() {
        assertThat(PurchaseMode.fromString("DIRECT")).isEqualTo(PurchaseMode.DIRECT);
        assertThat(PurchaseMode.fromString("auction")).isEqualTo(PurchaseMode.AUCTION);
        assertThat(PurchaseMode.fromString("BOTH")).isEqualTo(PurchaseMode.BOTH);
        assertThat(PurchaseMode.fromString("unknown")).isEqualTo(PurchaseMode.DIRECT);
        assertThat(PurchaseMode.fromString(null)).isEqualTo(PurchaseMode.DIRECT);
    }

    @Test
    void testActiveShopItemBidCalculation() {
        ShopProduct product = new ShopProduct(
                "test",
                null,
                PurchaseMode.AUCTION,
                "coins",
                0.0,
                1000.0,
                200.0
        );

        ActiveShopItem item = new ActiveShopItem(10, product, System.currentTimeMillis() + 60000);
        assertThat(item.currentBid()).isEqualTo(1000.0);
        assertThat(item.getNextMinimumBid()).isEqualTo(1000.0);

        java.util.UUID bidder = java.util.UUID.randomUUID();
        item.setBid(bidder, "Player1", 1000.0);
        assertThat(item.highestBidder()).isEqualTo(bidder);
        assertThat(item.getNextMinimumBid()).isEqualTo(1200.0);
    }
}

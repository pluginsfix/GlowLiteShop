package pluginsfix.glowliteshop.domain;

import org.bukkit.inventory.ItemStack;

public record ShopProduct(
        String id,
        ItemStack itemStack,
        PurchaseMode purchaseMode,
        String currencyKey,
        double directPrice,
        double auctionMinBid,
        double auctionBidStep
) {
}

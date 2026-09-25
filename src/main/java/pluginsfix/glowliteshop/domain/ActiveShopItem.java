package pluginsfix.glowliteshop.domain;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ActiveShopItem {
    private final int slot;
    private final ShopProduct product;
    private final long expiresAt;
    private @Nullable UUID highestBidder;
    private @Nullable String highestBidderName;
    private double currentBid;
    private boolean isSold;

    public ActiveShopItem(int slot, ShopProduct product, long expiresAt) {
        this.slot = slot;
        this.product = product;
        this.expiresAt = expiresAt;
        this.currentBid = product.auctionMinBid();
        this.isSold = false;
    }

    public ActiveShopItem(int slot, ShopProduct product, long expiresAt, @Nullable UUID highestBidder, @Nullable String highestBidderName, double currentBid, boolean isSold) {
        this.slot = slot;
        this.product = product;
        this.expiresAt = expiresAt;
        this.highestBidder = highestBidder;
        this.highestBidderName = highestBidderName;
        this.currentBid = currentBid;
        this.isSold = isSold;
    }

    public int slot() {
        return slot;
    }

    public ShopProduct product() {
        return product;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public @Nullable UUID highestBidder() {
        return highestBidder;
    }

    public @Nullable String highestBidderName() {
        return highestBidderName;
    }

    public double currentBid() {
        return currentBid;
    }

    public boolean isSold() {
        return isSold;
    }

    public void setBid(UUID bidder, String bidderName, double bidAmount) {
        this.highestBidder = bidder;
        this.highestBidderName = bidderName;
        this.currentBid = bidAmount;
    }

    public void markSold() {
        this.isSold = true;
    }

    public double getNextMinimumBid() {
        if (highestBidder == null) {
            return product.auctionMinBid();
        }
        return currentBid + product.auctionBidStep();
    }
}

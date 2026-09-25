package pluginsfix.glowliteshop.domain;

public enum PurchaseMode {
    DIRECT,
    AUCTION,
    BOTH;

    public static PurchaseMode fromString(String raw) {
        if (raw == null) {
            return DIRECT;
        }
        try {
            return PurchaseMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DIRECT;
        }
    }
}

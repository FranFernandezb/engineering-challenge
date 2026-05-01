package yuno.challenge.analytics.service;


import java.util.Map;

/**
 * Static lookup for common MCC descriptions.
 * Extend as needed — in a real deployment this would be loaded from a reference table.
 */
public final class MccDescriptions {

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("5411", "Grocery Stores / Supermarkets"),
            Map.entry("5651", "Family Clothing Stores"),
            Map.entry("5732", "Electronics Stores"),
            Map.entry("5812", "Eating Places / Restaurants"),
            Map.entry("5816", "Digital Goods / Games"),
            Map.entry("5912", "Drug Stores / Pharmacies"),
            Map.entry("5968", "Direct Marketing – Subscription"),
            Map.entry("5122", "Drugs, Drug Proprietaries and Druggist Sundries"),
            Map.entry("4511", "Airlines / Air Carriers"),
            Map.entry("7372", "Prepackaged Software / SaaS"),
            Map.entry("7841", "Video Tape Rental Stores / Streaming"),
            Map.entry("8299", "Schools / Educational Services"),
            Map.entry("5310", "Discount Stores"),
            Map.entry("5945", "Hobby, Toy and Game Shops"),
            Map.entry("5999", "Miscellaneous and Specialty Retail Stores")
    );

    private MccDescriptions() {}

    public static String describe(String mcc) {
        return DESCRIPTIONS.getOrDefault(mcc, "Unknown (" + mcc + ")");
    }
}

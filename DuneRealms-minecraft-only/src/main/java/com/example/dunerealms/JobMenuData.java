package com.example.dunerealms;

import java.util.*;

/** Validates the existing read-only exchange snapshot before it becomes a GUI action. */
final class JobMenuData {
    private JobMenuData() {}
    record Listing(UUID owner, String name, int remaining, boolean available) {}
    static List<Listing> listings(Map<String, Object> snapshot) {
        Object raw = snapshot.get("offers");
        if (!(raw instanceof List<?> offers)) return List.of();
        Map<UUID, Listing> unique = new LinkedHashMap<>();
        for (Object value : offers) {
            if (!(value instanceof Map<?, ?> offer) || !"contract".equals(offer.get("type"))) continue;
            try {
                UUID owner = UUID.fromString(String.valueOf(offer.get("id")));
                if(!owner.toString().equalsIgnoreCase(String.valueOf(offer.get("id"))))continue;
                Object quantity = offer.get("quantity");
                if (!(quantity instanceof Number number) || number.doubleValue() != number.intValue()
                        || number.intValue() < 1 || number.intValue() > 100000) continue;
                String status = String.valueOf(offer.get("status"));
                if (!Set.of("open", "claimed").contains(status)) continue;
                String name = String.valueOf(offer.get("owner"));
                if (name.isBlank() || name.equals("null")) name = owner.toString();
                name = name.replaceAll("[\\p{Cntrl}]", "");
                unique.put(owner, new Listing(owner, name, number.intValue(), status.equals("open")));
            } catch (IllegalArgumentException ignored) { }
        }
        return unique.values().stream().sorted(Comparator.comparing(Listing::available).reversed()
                .thenComparing(Listing::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Listing::owner)).toList();
    }
    static int page(int requested, int count) { return Math.max(0, Math.min(requested, Math.max(0, (count - 1) / 21))); }
    static int cardSlot(int index) { return 10 + (index / 7) * 9 + index % 7; }
}

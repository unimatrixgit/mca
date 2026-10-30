package com.example.duneess;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.configuration.ConfigurationSection;

/** Only commissioned routes belong in the Navigator, including the Guild return. */
final class GuildRoutes {
    static final List<String> ACTIVE = List.of("arrakis", "kaitain", "giedi_prime", "spacing_guild");
    static String canonical(String planet) {
        return switch (planet.toLowerCase(java.util.Locale.ROOT)) {
            case "guild_lobby", "spacing-guild", "lobby" -> "spacing_guild";
            default -> planet.toLowerCase(java.util.Locale.ROOT);
        };
    }
    static String server(ConfigurationSection own, ConfigurationSection core, String planet) {
        String value = own.getString("network.servers." + planet, "");
        return value.isBlank() ? core.getString("network.servers." + planet, "") : value;
    }
    static List<String> available(boolean network, String local, ConfigurationSection own,
                                  ConfigurationSection core, Predicate<String> loaded) {
        String current = canonical(local);
        return ACTIVE.stream().filter(p -> !p.equals(current))
            .filter(p -> network ? !server(own, core, p).isBlank() : loaded.test(p)).toList();
    }
}

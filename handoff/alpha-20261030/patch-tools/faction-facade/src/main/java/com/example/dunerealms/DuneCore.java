package com.example.dunerealms;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
/** Compilation donor only. Root merges just the public facade and its own generated lambda. */
public final class DuneCore {
 private final Map<UUID,String> selected=new HashMap<>();
 public void transactNetworkInventory(Player target,java.util.function.Consumer<java.sql.Connection> operation)throws Exception{throw new UnsupportedOperationException("Donor checkpoint stub must never be merged");}
    public void adminAssignFaction(Player actor, Player target, String faction,
            java.util.function.Consumer<java.sql.Connection> lockedCaseAuthority) throws Exception {
        Objects.requireNonNull(actor, "Staff actor required");
        Objects.requireNonNull(target, "Online target required");
        Objects.requireNonNull(lockedCaseAuthority, "Locked case authority required");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Faction administration requires the server thread");
        if (!actor.isOnline() || !target.isOnline() || !actor.hasPermission("dune.admin"))
            throw new SecurityException("Current staff permission and online identities are required");
        if (!Set.of("Fremen", "Atreides", "Harkonnen", "Corrino", "Ordos").contains(faction))
            throw new IllegalArgumentException("Exact canonical faction required");
        UUID targetId = target.getUniqueId();
        boolean hadPrevious = selected.containsKey(targetId);
        String previous = selected.get(targetId);
        try {
            transactNetworkInventory(target, connection -> {
                lockedCaseAuthority.accept(connection);
                if (!Bukkit.isPrimaryThread() || !actor.isOnline() || !target.isOnline()
                        || !actor.hasPermission("dune.admin"))
                    throw new SecurityException("Staff authority changed before the faction update");
                selected.put(targetId, faction);
            });
        } catch (Exception failure) {
            // Never persist or reconnect here: Core may have fenced an unknown commit result.
            if (hadPrevious) selected.put(targetId, previous);
            else selected.remove(targetId);
            throw failure;
        }
    }
}

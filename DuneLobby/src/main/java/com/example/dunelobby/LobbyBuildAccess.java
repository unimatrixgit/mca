package com.example.dunelobby;

import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerInteractEvent;

/** Leaves other protection plugins' cancellations intact. */
final class LobbyBuildAccess {
    static boolean allows(PlayerInteractEvent event) {
        var player = event.getPlayer();
        return player.hasPermission("dunelobby.build") && player.getGameMode() == GameMode.CREATIVE
                && !player.isSneaking();
    }
}


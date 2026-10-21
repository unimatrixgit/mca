package com.example.duneess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EssentialsBasicsTest {
    @Test void normalizesNamesAndHasAStableDefault() {
        assertEquals("home",EssentialsBasics.homeName(new String[]{}));
        assertEquals("sietch_2",EssentialsBasics.homeName(new String[]{"Sietch_2"}));
    }
    @Test void rejectsYamlPathsAndInvalidArguments() {
        for(String name:new String[]{"../other","player.home","","a b","x".repeat(25)})
            assertThrows(IllegalArgumentException.class,()->EssentialsBasics.homeName(new String[]{name}));
        assertThrows(IllegalArgumentException.class,()->EssentialsBasics.homeName(new String[]{"one","two"}));
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void teleportRequestsRequireConsentAndCannotBeAcceptedTwice() {
        DuneEss plugin=org.mockito.Mockito.mock(DuneEss.class);
        org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(directory.toFile());
        EssentialsBasics basics=new EssentialsBasics(plugin);
        var source=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var target=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        var border=org.mockito.Mockito.mock(org.bukkit.WorldBorder.class);
        var request=org.mockito.Mockito.mock(org.bukkit.command.Command.class);
        var accept=org.mockito.Mockito.mock(org.bukkit.command.Command.class);
        java.util.UUID senderId=java.util.UUID.randomUUID(), targetId=java.util.UUID.randomUUID();
        org.mockito.Mockito.when(source.getUniqueId()).thenReturn(senderId);
        org.mockito.Mockito.when(target.getUniqueId()).thenReturn(targetId);
        org.mockito.Mockito.when(source.getName()).thenReturn("Sender");
        org.mockito.Mockito.when(source.hasPermission("duneess.basics")).thenReturn(true);
        org.mockito.Mockito.when(target.hasPermission("duneess.basics")).thenReturn(true);
        org.mockito.Mockito.when(source.canSee(target)).thenReturn(true);
        org.mockito.Mockito.when(source.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(target.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(source.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(world.getWorldBorder()).thenReturn(border);
        org.mockito.Mockito.when(border.isInside(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(world.getMinHeight()).thenReturn(-64);
        org.mockito.Mockito.when(world.getMaxHeight()).thenReturn(320);
        var destination=new org.bukkit.Location(world,10,80,10);
        org.mockito.Mockito.when(target.getLocation()).thenReturn(destination);
        org.mockito.Mockito.when(source.teleportAsync(destination)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        org.mockito.Mockito.when(request.getName()).thenReturn("tpa");
        org.mockito.Mockito.when(accept.getName()).thenReturn("tpaccept");
        try(var bukkit=org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(()->org.bukkit.Bukkit.getPlayerExact("Target")).thenReturn(target);
            bukkit.when(()->org.bukkit.Bukkit.getPlayer(senderId)).thenReturn(source);
            basics.onCommand(source,request,"tpa",new String[]{"Target"});
            org.mockito.Mockito.verify(source,org.mockito.Mockito.never()).teleportAsync(org.mockito.ArgumentMatchers.any(org.bukkit.Location.class));
            basics.onCommand(target,accept,"tpaccept",new String[]{});
            basics.onCommand(target,accept,"tpaccept",new String[]{});
            org.mockito.Mockito.verify(source,org.mockito.Mockito.times(1)).teleportAsync(destination);
        }
    }
}

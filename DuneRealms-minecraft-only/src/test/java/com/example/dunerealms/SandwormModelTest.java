package com.example.dunerealms;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SandwormModelTest {
    private PersistentDataContainer tags() {
        PersistentDataContainer tags = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Object> values = new HashMap<>();
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; }).when(tags).set(any(), any(), any());
        when(tags.get(any(), any())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(tags.has(any(), any())).thenAnswer(call -> values.containsKey(call.getArgument(0)));
        return tags;
    }

    private record Rig(DuneCore plugin, World world, Location location, Husk brain, Sniffer body, BlockDisplay display) {}

    private Rig rig(String model) {
        DuneCore plugin = mock(DuneCore.class);
        when(plugin.getName()).thenReturn("DuneCore");
        YamlConfiguration config = new YamlConfiguration();
        if (model != null) config.set("sandworms.model", model);
        when(plugin.getConfig()).thenReturn(config);
        World world = mock(World.class); when(world.getName()).thenReturn("arrakis");
        Location location = new Location(world, 12, 75, 34);
        Husk brain = mock(Husk.class); Sniffer body = mock(Sniffer.class); BlockDisplay display = mock(BlockDisplay.class);
        when(brain.getUniqueId()).thenReturn(UUID.randomUUID()); when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(brain.getWorld()).thenReturn(world); when(brain.getLocation()).thenReturn(location);
        when(body.isValid()).thenReturn(true); when(brain.isValid()).thenReturn(true);
        PersistentDataContainer brainTags = tags(), bodyTags = tags(), displayTags = tags();
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(brain.getPersistentDataContainer()).thenReturn(brainTags); when(body.getPersistentDataContainer()).thenReturn(bodyTags);
        when(display.getPersistentDataContainer()).thenReturn(displayTags);
        when(brain.getEquipment()).thenReturn(equipment);
        AttributeInstance attribute = mock(AttributeInstance.class); when(attribute.getValue()).thenReturn(500.0);
        when(brain.getAttribute(any(Attribute.class))).thenReturn(attribute);
        when(body.getAttribute(any(Attribute.class))).thenReturn(attribute);
        when(world.spawn(any(Location.class), eq(Husk.class), any(Consumer.class))).thenReturn(brain);
        when(world.spawn(any(Location.class), eq(Sniffer.class), any(Consumer.class))).thenReturn(body);
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenReturn(display);
        return new Rig(plugin, world, location, brain, body, display);
    }

    @Test void displayModelHidesSnifferFollowsBrainAndSinksAfterDeath() {
        Rig r = rig(null); // default is display
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager plugins = mock(PluginManager.class);
        Player target = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(() -> Bukkit.getEntity(r.body().getUniqueId())).thenReturn(r.body());
            bukkit.when(() -> Bukkit.getEntity(r.brain().getUniqueId())).thenReturn(r.brain());
            SandwormMobs service = new SandwormMobs(r.plugin(), new NamespacedKey("dunerealms", "sandworm"));
            service.spawn(r.location(), target);
            verify(r.body()).setInvisible(true);
            // Skull, mouth, throat, ten teeth and fourteen body rings.
            verify(r.world(), times(27)).spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(r.plugin()), tick.capture(), eq(1L), eq(1L));
            tick.getValue().run();
            verify(r.display(), atLeastOnce()).teleport(any(Location.class));
            verify(r.display(), never()).remove();

            EntityDeathEvent death = mock(EntityDeathEvent.class); when(death.getEntity()).thenReturn(r.body());
            when(death.getDrops()).thenReturn(new ArrayList<>());
            service.onDeath(death);
            for (int i = 0; i < 40; i++) tick.getValue().run();
            verify(r.display(), atLeastOnce()).remove();
        }
    }

    @Test void snifferModelKeepsTheBodyVisibleAndSpawnsNoDisplays() {
        Rig r = rig("sniffer");
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            SandwormMobs service = new SandwormMobs(r.plugin(), new NamespacedKey("dunerealms", "sandworm"));
            service.spawn(r.location(), null);
            verify(r.body()).setInvisible(false);
            verify(r.world(), never()).spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class));
        }
    }

    @Test void verticalOnlyMovementHasBoundedHistoryAndDoesNotLoadTerrain() throws Exception {
        Rig r=rig("display");
        try(MockedStatic<Bukkit> ignored=mockStatic(Bukkit.class)) {
            SandwormModel model=new SandwormModel(r.location(),r.location().clone().add(1,0,0),new NamespacedKey("dunecore","sandworm_model"));
            for(int i=0;i<600;i++) model.tick(r.location().clone().add(0,i%2,0));
            var field=SandwormModel.class.getDeclaredField("path"); field.setAccessible(true);
            org.junit.jupiter.api.Assertions.assertTrue(((Deque<?>)field.get(model)).size()<=256);
            verify(r.world(),never()).getHighestBlockYAt(anyInt(),anyInt());
            model.removeNow();
        }
    }
    @Test void stoppingServiceRemovesModelsAndRestoresVisibleHitbox() {
        Rig r=rig("display");
        try(MockedStatic<Bukkit> b=mockStatic(Bukkit.class)) {
            b.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            b.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            b.when(()->Bukkit.getEntity(r.body().getUniqueId())).thenReturn(r.body());
            SandwormMobs service=new SandwormMobs(r.plugin(),new NamespacedKey("dunerealms","sandworm"));
            service.spawn(r.location(),null); service.close();
            verify(r.display(),times(27)).remove(); verify(r.body()).setInvisible(false);
        }
    }
    @Test void partialDisplayFailureRemovesPartsAndKeepsSnifferVisible() {
        Rig r=rig("display");
        when(r.world().spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenReturn(r.display()).thenThrow(new IllegalStateException("spawn unavailable"));
        try(MockedStatic<Bukkit> b=mockStatic(Bukkit.class)) {
            b.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            b.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            SandwormMobs service=new SandwormMobs(r.plugin(),new NamespacedKey("dunerealms","sandworm"));
            service.spawn(r.location(),null);
            verify(r.display()).remove(); verify(r.body()).setInvisible(false);
        }
    }
}

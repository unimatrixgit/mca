package com.example.dunerealms;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/**
 * The visible sandworm when {@code sandworms.model: display}: a chain of block displays that follows the
 * Husk brain, so no resource pack is needed. The invisible Sniffer body keeps the hitbox, health and rewards.
 * Head: terracotta skull, black mouth, glowing spice throat, ring of bone teeth. Body: fourteen rings that
 * trace the brain's actual path and thin toward the tail. Rises out of the sand on arrival, sinks on death.
 */
final class SandwormModel {
    private static final int SEGMENTS = 14, RISE_TICKS = 25, SINK_TICKS = 30;
    private static final double GAP = 1.45, HEAD_LIFT = 2.0, HEAD = 3.4;

    private final World world;
    private final NamespacedKey partKey;
    private final List<BlockDisplay> head = new ArrayList<>(), body = new ArrayList<>();
    private final Deque<Location> path = new ArrayDeque<>(); // newest first
    private Location last;
    private float yaw;
    private int age, sinking = -1, flash;

    /** Throws IllegalStateException when the displays cannot be spawned; the caller keeps the Sniffer visible. */
    SandwormModel(Location start, Location awayFrom, NamespacedKey partKey) {
        this.world = start.getWorld();
        this.partKey = partKey;
        this.last = start.clone();
        double dx = start.getX() - awayFrom.getX(), dz = start.getZ() - awayFrom.getZ(), len = Math.hypot(dx, dz);
        if (len < 0.01) { dx = 1; dz = 0; len = 1; }
        dx /= len; dz /= len;
        yaw = yawOf(-dx, -dz);
        // Lay the body out behind the head across the dunes, so the worm is whole from the first tick.
        for (double d = 0; d <= SEGMENTS * GAP + 4; d += 0.5) {
            int x = (int) Math.floor(start.getX() + dx * d), z = (int) Math.floor(start.getZ() + dz * d);
            path.addLast(new Location(world, start.getX() + dx * d, world.isChunkLoaded(x >> 4, z >> 4) ? world.getHighestBlockYAt(x, z) + 1 : start.getY(), start.getZ() + dz * d));
        }
        Location hidden = start.clone().subtract(0, 8, 0);
        try {
            float h = (float) (HEAD / 2);
            head.add(part(hidden, Material.TERRACOTTA, new Vector3f(-h, -h, -h), new Vector3f((float) HEAD, (float) HEAD, (float) HEAD)));
            head.add(part(hidden, Material.BLACK_CONCRETE, new Vector3f(-1.35f, -1.35f, h), new Vector3f(2.7f, 2.7f, 0.12f)));
            head.add(part(hidden, Material.SHROOMLIGHT, new Vector3f(-0.45f, -0.45f, h + 0.13f), new Vector3f(0.9f, 0.9f, 0.08f)));
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI * 2 / 10;
                head.add(part(hidden, Material.BONE_BLOCK, new Vector3f((float) Math.cos(a) - 0.13f, (float) Math.sin(a) - 0.13f, h + 0.05f),
                    new Vector3f(0.26f, 0.26f, 0.55f)));
            }
            for (int i = 0; i < SEGMENTS; i++) {
                float s = (float) (3.0 - 2.1 * i / (SEGMENTS - 1));
                body.add(part(hidden, i % 2 == 0 ? Material.TERRACOTTA : Material.BROWN_TERRACOTTA,
                    new Vector3f(-s / 2, -s * 0.45f, -s / 2), new Vector3f(s, s, s)));
            }
        } catch (RuntimeException ex) { removeNow(); throw ex; }
    }

    private BlockDisplay part(Location at, Material material, Vector3f translation, Vector3f scale) {
        BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setTransformation(new Transformation(translation, new Quaternionf(), scale, new Quaternionf()));
            d.setPersistent(false); // never saved: a fresh model is attached when the pair is restored
            d.setTeleportDuration(3);
            d.setViewRange(2.0f);
            if (material == Material.SHROOMLIGHT) d.setBrightness(new Display.Brightness(15, 15));
            d.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (display == null) throw new IllegalStateException("block display could not be spawned");
        return display;
    }

    void flash() { flash = 6; }

    /** Starts the dive back under the sand; tick(...) returns false once it is gone. */
    void sink() { if (sinking < 0) sinking = 0; }

    void removeNow() {
        head.forEach(Entity::remove); body.forEach(Entity::remove);
        head.clear(); body.clear();
    }

    /** Advances one tick following the brain's location; pass null once the worm is gone. False when removed. */
    boolean tick(Location brain) {
        age++;
        if (brain == null) sink();
        else if (sinking < 0) {
            Location newest = path.peekFirst();
            if (newest == null || newest.distanceSquared(brain) > 0.04) {
                if (newest != null) {
                    double mx = brain.getX() - newest.getX(), mz = brain.getZ() - newest.getZ();
                    if (mx * mx + mz * mz > 0.0004) yaw = yawOf(mx, mz);
                }
                path.addFirst(brain.clone());
                trim();
            }
            last = brain.clone();
        }
        if (sinking >= 0 && ++sinking > SINK_TICKS) { removeNow(); return false; }
        double sink = sinking >= 0 ? 8.0 * sinking / SINK_TICKS : Math.max(0, 8.0 * (1 - (double) age / RISE_TICKS));

        Location headAt = last.clone().add(0, HEAD_LIFT - sink, 0);
        headAt.setYaw(yaw); headAt.setPitch(-20f);
        boolean glow = flash > 0;
        if (flash > 0) flash--;
        for (BlockDisplay d : head) {
            d.teleport(headAt);
            if (d.isGlowing() != glow) { d.setGlowing(glow); d.setGlowColorOverride(glow ? Color.RED : null); }
        }
        // Each ring sits at an even distance along the path the head travelled.
        Iterator<Location> it = path.iterator();
        Location prev = it.hasNext() ? it.next() : last, ahead = last;
        double walked = 0;
        for (int seg = 0; seg < body.size(); seg++) {
            double want = HEAD / 2 + 0.6 + seg * GAP;
            while (walked < want && it.hasNext()) {
                Location next = it.next();
                walked += Math.hypot(next.getX() - prev.getX(), next.getZ() - prev.getZ());
                prev = next;
            }
            Location at = prev.clone().subtract(0, sink, 0);
            at.setYaw(yawOf(ahead.getX() - prev.getX(), ahead.getZ() - prev.getZ())); at.setPitch(0f);
            body.get(seg).teleport(at);
            ahead = prev;
        }
        if (age % 4 == 0 && sinking < 0 && sink < 1)
            world.spawnParticle(Particle.BLOCK, last, 25, 1.6, 0.2, 1.6, Material.SAND.createBlockData());
        if (sinking >= 0 && sinking % 3 == 0)
            world.spawnParticle(Particle.BLOCK, last, 60, 2.2, 0.5, 2.2, Material.SAND.createBlockData());
        return true;
    }

    private void trim() {
        double need = SEGMENTS * GAP + HEAD + 6, total = 0;
        Location prev = null;
        int keep = 0;
        for (Location l : path) {
            if (prev != null) total += Math.hypot(l.getX() - prev.getX(), l.getZ() - prev.getZ());
            prev = l; keep++;
            if (total > need) break;
        }
        // Bound vertical-only movement and tiny horizontal jitter as well.
        keep = Math.min(keep, 254);
        while (path.size() > keep + 2) path.removeLast();
    }

    /** Minecraft yaw for a horizontal direction (0 = +Z/south, 90 = -X/west). */
    private static float yawOf(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }
}

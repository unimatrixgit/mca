package com.example.duneterrain;

/** Seeded continuous ridge contours, shared across every intersecting chunk. */
final class RockPlateaus {
    static int count(long seed, int configuredMaximum) {
        return 1 + (int)Math.floorMod(seed, Math.max(1, Math.min(3, configuredMaximum)));
    }
    static int interval(int configuredInterval, int maximumHeight, int count) {
        return Math.max(1, Math.min(5, Math.min(configuredInterval, Math.max(1, maximumHeight / count))));
    }
    static int level(double depth, int count) {
        if (depth < 0.10) return 0;
        return Math.min(count, 1 + (int)Math.floor((depth - 0.10) / (0.78 / count)));
    }
    static double terrain(double x, double z, double scale) {
        return Math.sin(x * scale) * Math.cos(z * scale * 1.13)
            + Math.sin(x * scale * 2.7 + z * scale * 1.45) * 0.35;
    }
    static Shape shape(long seed, long centerX, long centerZ, double radius, double scale) {
        // Follow the local dune contour instead of cutting a circular island across it.
        double gx = terrain(centerX + 8, centerZ, scale) - terrain(centerX - 8, centerZ, scale);
        double gz = terrain(centerX, centerZ + 8, scale) - terrain(centerX, centerZ - 8, scale);
        double angle = Math.atan2(gz, gx) + Math.PI / 2;
        return new Shape(seed, centerX, centerZ, radius, scale, Math.cos(angle), Math.sin(angle));
    }
    record Shape(long seed, long centerX, long centerZ, double radius, double scale, double cosine, double sine) {
        double depth(long x, long z) {
            double dx = (x - centerX) / radius, dz = (z - centerZ) / radius;
            double u = dx * cosine + dz * sine, v = -dx * sine + dz * cosine;
            // Low-frequency domain warping produces connected promontories, bays and uneven terraces.
            double warpedU = u + 0.14 * noise(u * 2.0 + 17, v * 2.0 - 9, seed);
            double spine = 0.24 * noise(u * 2.2 - 31, 7, seed ^ 0x51ed2705L)
                + 0.12 * (terrain(x, z, scale) - terrain(centerX, centerZ, scale));
            double width = 0.57 + 0.14 * noise(u * 2.5, 13, seed ^ 0x6eed0e9dL);
            double cross = (v - spine + 0.10 * noise(u * 3, v * 3, ~seed)) / width;
            double envelope = Math.pow(Math.pow(Math.abs(warpedU), 2.6) + Math.pow(Math.abs(cross), 2.6), 1.0 / 2.6);
            return 1.0 - envelope + 0.17 * noise(u * 3.1 + 4, v * 3.1 - 5, seed ^ 0x9e3779b9L);
        }
    }
    static int lowerTerrace(double depth) {
        if(depth < .025 || depth >= .25)return 0;
        return Math.min(3,1+(int)Math.floor((depth-.025)/.075));
    }
    static int spireRise(long seed, long x, long z, double depth, int height) {
        // Thin erosion ridges on the outer mountain shoulder; terrace surfaces remain untouched.
        if (depth <= 0.25 || depth >= 0.46 || height <= 0) return 0;
        double shoulder = Math.sin(Math.PI * (depth - 0.25) / 0.21);
        double ridge = 1.0 - Math.abs(noise(x / 16.0, z / 16.0, seed ^ 0x45524f53494f4eL));
        double broken = 0.65 + 0.35 * (noise(x / 4.5, z / 4.5, ~seed) + 1) / 2;
        return (int)Math.floor(Math.min(64,height) * shoulder * Math.pow(ridge,3) * broken);
    }
    static int highestGround(Shape shape, int baseHeight, double variation) {
        int extent = (int)Math.ceil(shape.radius() * 2.0) + 3;
        int highest = Integer.MIN_VALUE;
        for (int x = (int)shape.centerX() - extent; x <= shape.centerX() + extent; x++)
            for (int z = (int)shape.centerZ() - extent; z <= shape.centerZ() + extent; z++) {
                if (shape.depth(x,z) >= 0)
                    highest = Math.max(highest, baseHeight + (int)Math.round(terrain(x,z,shape.scale()) * variation));
            }
        return highest == Integer.MIN_VALUE ? baseHeight : highest;
    }
    static long formationSeed(long cellX, long cellZ) {
        return mix(cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL ^ 0x4441524B49534C41L);
    }
    // Shared axis positions keep north/south and east/west neighbours aligned.
    // Independent bounded jitter makes each gap lie within the requested limits.
    static long axisCenter(long cell, int minimumGap, int maximumGap, long salt) {
        int spacing = (minimumGap + maximumGap) / 2;
        int jitter = Math.min(spacing - minimumGap, maximumGap - spacing) / 2;
        long offset = Math.floorMod(mix(cell ^ salt), jitter * 2L + 1) - jitter;
        return cell * spacing + spacing / 2 + offset;
    }
    static long centerX(long cell, int minimumGap, int maximumGap) {
        return axisCenter(cell,minimumGap,maximumGap,0x165667B19E3779F9L);
    }
    static long centerZ(long cell, int minimumGap, int maximumGap) {
        return axisCenter(cell,minimumGap,maximumGap,0x85EBCA77C2B2AE63L);
    }
    static int radius(long seed, int minimum, int maximum) {
        return minimum + (int)Math.floorMod(seed >>> 1, maximum - minimum + 1L);
    }
    static double regionDepth(int x, int z, int minimumGap, int maximumGap, int minimum, int maximum, double scale) {
        int spacing = (minimumGap + maximumGap) / 2;
        int reach = (int)Math.ceil(maximum * 2.0) + 3;
        double depth = -1;
        for (int cx = Math.floorDiv(x - reach, spacing); cx <= Math.floorDiv(x + reach, spacing); cx++)
            for (int cz = Math.floorDiv(z - reach, spacing); cz <= Math.floorDiv(z + reach, spacing); cz++) {
                long seed = formationSeed(cx, cz);
                depth = Math.max(depth, shape(seed, centerX(cx,minimumGap,maximumGap), centerZ(cz,minimumGap,maximumGap),
                    radius(seed,minimum,maximum), scale).depth(x,z));
            }
        return depth;
    }
    private static long mix(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
    static double noise(double x, double z, long seed) {
        long ix = (long)Math.floor(x), iz = (long)Math.floor(z);
        double tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz);
        double a = sample(ix, iz, seed), b = sample(ix + 1, iz, seed);
        double c = sample(ix, iz + 1, seed), d = sample(ix + 1, iz + 1, seed);
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
    }
    private static double sample(long x, long z, long seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ seed;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) >>> 11) * 0x1.0p-52 - 1;
    }
}

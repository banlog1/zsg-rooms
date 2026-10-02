package zsgrooms.modid.seedbank;

import net.minecraft.util.math.BlockPos;
import zsgrooms.modid.ZsgSeedBridge;

public final class SeedStructureTarget {
    private SeedStructureTarget() { }

    public static boolean hasRequiredTarget(String specification) {
        String filter = ZsgSeedBridge.resolveStructure(specification);
        if (SeedBankProfile.find(filter) == null) return true;
        try {
            return parse(specification, Long.parseLong(ZsgSeedBridge.extractMinecraftSeed(specification)), filter) != null;
        } catch (NumberFormatException error) { return false; }
    }

    public static BlockPos parse(String specification, long worldSeed, String filter) {
        if (specification == null || SeedBankProfile.find(filter) == null
                || !filter.equals(ZsgSeedBridge.resolveStructure(specification))) return null;
        try {
            if (Long.parseLong(ZsgSeedBridge.extractMinecraftSeed(specification)) != worldSeed) return null;
            BlockPos result = null;
            for (String field : specification.split("\\|")) {
                if (!field.startsWith("target:")) continue;
                if (result != null) return null;
                String[] coordinates = field.substring(7).split(",", -1);
                if (coordinates.length != 2) return null;
                int x = Integer.parseInt(coordinates[0]);
                int z = Integer.parseInt(coordinates[1]);
                if (Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L) return null;
                result = new BlockPos(x, 0, z);
            }
            return result;
        } catch (NumberFormatException error) { return null; }
    }

    public static String preserve(String previous, String replacement) {
        try {
            BlockPos target = parse(previous, Long.parseLong(ZsgSeedBridge.extractMinecraftSeed(replacement)),
                    ZsgSeedBridge.resolveStructure(replacement));
            return target == null ? replacement : replacement + "|target:" + target.getX() + "," + target.getZ();
        } catch (NumberFormatException error) { return replacement; }
    }
}

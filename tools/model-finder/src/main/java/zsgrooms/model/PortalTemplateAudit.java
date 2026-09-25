package zsgrooms.model;

import com.seedfinding.mccore.nbt.NBTIO;
import com.seedfinding.mccore.nbt.tag.NBTCompound;
import com.seedfinding.mccore.nbt.tag.NBTTag;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/** Offline asset audit only: reads a supplied 1.16.1 jar, never loads its classes. */
public final class PortalTemplateAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected vanilla 1.16.1 jar");
        try (ZipFile zip = new ZipFile(args[0])) {
            for (int t = 1; t <= 13; t++) {
                String name = t <= 10 ? "portal_" + t : "giant_portal_" + (t - 10);
                NBTCompound root;
                try (var input = zip.getInputStream(zip.getEntry("data/minecraft/structures/ruined_portal/" + name + ".nbt"))) {
                    root = NBTIO.read(input.readAllBytes());
                }
                List<?> palette = (List<?>) root.getTag("palette").getValue();
                List<?> blocks = (List<?>) root.getTag("blocks").getValue();
                List<Integer> coords = new ArrayList<>();
                Set<BPos> obsidian = new HashSet<>();
                for (Object obj : blocks) {
                    NBTCompound block = (NBTCompound) obj;
                    NBTCompound state = (NBTCompound) palette.get(block.getInt("state"));
                    if (state.getString("Name").equals("minecraft:obsidian")) {
                        List<?> position = (List<?>) block.getTag("pos").getValue();
                        obsidian.add(new BPos((Integer) ((NBTTag<?>) position.get(0)).getValue(),
                                (Integer) ((NBTTag<?>) position.get(1)).getValue(), (Integer) ((NBTTag<?>) position.get(2)).getValue()));
                    }
                    if (!state.getString("Name").equals("minecraft:lava")) continue;
                    if (!((NBTCompound) state.getTag("Properties")).getString("level").equals("0")) continue;
                    for (Object n : (List<?>) block.getTag("pos").getValue()) coords.add((Integer) ((NBTTag<?>) n).getValue());
                }
                if (!Arrays.equals(PortalLavaTemplates.COORDINATES[t - 1], coords.stream().mapToInt(Integer::intValue).toArray())) {
                    throw new IllegalStateException("Lava coordinates differ: " + name);
                }
                Set<BPos> modeled = new HashSet<>();
                RuinedPortalGenerator.STRUCTURE_TO_BLOCKS.get(name).values().forEach(modeled::addAll);
                if (!modeled.equals(obsidian)) throw new IllegalStateException("SeedFinding obsidian coordinates differ: " + name);
            }
        }
        System.out.println("PASS: all 13 portal lava source and obsidian tables match vanilla assets; no worlds generated.");
    }
}

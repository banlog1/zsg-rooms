package zsgrooms.modid;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.mixin.EntityRandomAccessor;

import java.util.Random;
import java.util.Arrays;

/** Diagnostic observations of current behavior, not approval of those policies. Never shipped. */
final class GameRuleAuditSmoke {
    static void check(ServerWorld world) {
        ServerPlayerEntity player = world.getPlayers().get(0);
        boolean roomCheats = ZsgRooms.getGame(ZsgRooms.getActiveRoomName()).areCheatsAllowed();
        require(!roomCheats && world.getServer().getSaveProperties().areCommandsAllowed(),
                "Cheat audit requires a cheats-off room and cheats-on world creation");
        ZsgRooms.LOGGER.info("[GameRuleAudit] cheats: room={}, manager={}, savedWorld={}, permission={}",
                roomCheats, world.getServer().getPlayerManager().areCheatsAllowed(),
                world.getServer().getSaveProperties().areCommandsAllowed(),
                world.getServer().getPermissionLevel(player.getGameProfile()));

        checkAnimalEligibility(world);

        ServerWorld nether = world.getServer().getWorld(World.NETHER);
        require(nether != null, "Nether fixture missing");
        BlockPos located = nether.locateStructure(StructureFeature.BASTION_REMNANT, BlockPos.ORIGIN, 100, false);
        require(located != null, "Bastion fixture missing");
        nether.getChunk(located);
        StructureStart<?> bastion = nether.getStructureAccessor().getStructureStart(
                net.minecraft.util.math.ChunkSectionPos.from(located), StructureFeature.BASTION_REMNANT,
                nether.getChunk(located));
        require(bastion != null && bastion.hasChildren(), "Bastion start missing");
        BlockPos gap = findGap(bastion);
        require(gap != null, "Fixture has no space between bastion pieces");
        nether.getChunk(gap);
        ZombifiedPiglinEntity zombie = EntityType.ZOMBIFIED_PIGLIN.create(nether);
        require(zombie != null, "Zombified piglin fixture missing");
        zombie.refreshPositionAndAngles(gap.getX() + 0.5, gap.getY(), gap.getZ() + 0.5, 0, 0);
        BastionZombifiedPiglinControl.configure(true);
        try {
            ZsgRooms.LOGGER.info("[GameRuleAudit] bastionGap: pos={} insidePiece=false rejected={} explicitSpawnAccepted={}",
                    gap, BastionZombifiedPiglinControl.shouldReject(nether, zombie), nether.spawnEntity(zombie));
        } finally {
            zombie.remove();
            BastionZombifiedPiglinControl.configure(false);
        }

        long riderSeed = 0;
        while (new Random(riderSeed).nextInt(30) != 0) riderSeed++;
        for (boolean enabled : new boolean[]{false, true}) {
            StriderEntity strider = EntityType.STRIDER.create(nether);
            require(strider != null, "Strider fixture missing");
            strider.refreshPositionAndAngles(0.5, 150, 0.5, 0, 0);
            ((EntityRandomAccessor) strider).zsgRooms$getRandom().setSeed(riderSeed);
            StriderJockeyControl.configure(enabled);
            try {
                strider.initialize(nether, nether.getLocalDifficulty(strider.getBlockPos()),
                        SpawnReason.SPAWN_EGG, null, null);
                ZsgRooms.LOGGER.info("[GameRuleAudit] strider: rule={} reason=SPAWN_EGG riders={}",
                        enabled, strider.getPassengerList().size());
            } finally {
                strider.getPassengerList().forEach(entity -> entity.remove());
                strider.remove();
                StriderJockeyControl.configure(false);
            }
        }
        ZsgRooms.LOGGER.info("[GameRuleAudit] COMPLETE");
    }

    private static void checkAnimalEligibility(ServerWorld world) {
        for (EntityType<? extends AnimalEntity> type : Arrays.<EntityType<? extends AnimalEntity>>asList(
                EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT)) {
            AnimalEntity animal = type.create(world);
            require(animal != null, "Animal fixture missing: " + type);
            animal.setBreedingAge(0);
            require(StructureAnimalGuarantee.isEligible(animal), "Adult excluded: " + type);
            animal.setBreedingAge(-24000);
            require(!StructureAnimalGuarantee.isEligible(animal), "Baby counted: " + type);
            require(animal.getBreedingAge() == -24000, "Eligibility check changed baby age: " + type);
            animal.setBreedingAge(6000);
            require(StructureAnimalGuarantee.isEligible(animal), "Adult on breeding cooldown excluded: " + type);
        }
        require(!StructureAnimalGuarantee.isEligible(EntityType.HORSE.create(world)), "Horse counted as food");
        require(!StructureAnimalGuarantee.isEligible(EntityType.ZOMBIE.create(world)), "Non-animal counted");
        require(!StructureAnimalGuarantee.isEligible(null), "Null counted");
        int counted = 0;
        for (int age : new int[]{0, -24000, -12000}) {
            CowEntity cow = EntityType.COW.create(world);
            require(cow != null, "Cow fixture missing");
            cow.setBreedingAge(age);
            if (StructureAnimalGuarantee.isEligible(cow)) counted++;
            require(cow.getBreedingAge() == age, "Counting altered an existing animal");
        }
        require(counted == 1 && StructureAnimalGuarantee.missingAnimals(counted) == 2,
                "One adult and two babies should require two additional adults");
        ZsgRooms.LOGGER.info("[GameRuleAudit] adult animal guarantee PASS: five species, baby preservation, "
                + "breeding cooldown, exclusions, mixed-age count");
    }

    private static BlockPos findGap(StructureStart<?> start) {
        BlockBox box = start.getBoundingBox();
        for (int x = box.minX; x <= box.maxX; x += 3) {
            for (int z = box.minZ; z <= box.maxZ; z += 3) {
                for (int y = box.minY; y <= box.maxY; y += 3) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (start.getChildren().stream().noneMatch(piece -> piece.getBoundingBox().contains(pos))) return pos;
                }
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

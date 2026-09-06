package zsgrooms.modid.rng;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.SeedDebugLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FortressSpawnProtection {
    private static final ThreadLocal<Pass> ACTIVE = new ThreadLocal<Pass>();

    private FortressSpawnProtection() {
    }

    public static boolean beginPass(ServerWorld world, WorldChunk chunk, boolean vanillaAllowed) {
        FortressSpawnProtectionState state = FortressSpawnProtectionState.get(world);
        if (state.isFinished()) {
            clear();
            return vanillaAllowed;
        }
        return beginPass(state, vanillaAllowed,
                chunk.getStructureReferences(StructureFeature.FORTRESS));
    }

    static boolean beginPass(FortressSpawnProtectionState state, boolean vanillaAllowed, Iterable<Long> references) {
        clear();
        if (state.isFinished()) {
            return vanillaAllowed;
        }
        Pass pass = vanillaAllowed ? new Pass(state, false) : null;
        // References identify generated fortress starts without a locate call or chunk generation.
        for (long fortress : references) {
            FortressSpawnProtectionState.Evaluation evaluation = state.beginCycle(fortress);
            if (evaluation != null) {
                if (pass == null) {
                    pass = new Pass(state, true);
                }
                pass.addEvaluation(fortress, evaluation);
            }
        }
        if (pass == null) {
            return false;
        }
        ACTIVE.set(pass);
        return true;
    }

    public static void beginPack() {
        Pass pass = ACTIVE.get();
        if (pass != null) {
            pass.selectedFortress = null;
            pass.candidateFortress = null;
            pass.protectedFortress = null;
            pass.packSelected = false;
        }
    }

    public static void beginSelection() {
        Pass pass = ACTIVE.get();
        if (pass != null) {
            pass.selecting = true;
            pass.selectedFortress = null;
        }
    }

    public static void observeTable(ServerWorld world, BlockPos pos, List<Biome.SpawnEntry> table) {
        Pass pass = ACTIVE.get();
        if (pass == null) {
            return;
        }
        Long fortress = null;
        if (table == StructureFeature.FORTRESS.getMonsterSpawns()) {
            StructureStart<?> start = fortressAt(world, pos);
            if (start.hasChildren()) {
                fortress = ChunkPos.toLong(start.getChunkX(), start.getChunkZ());
            }
        }
        observeFortress(fortress);
    }

    static void observeFortress(Long fortress) {
        Pass pass = ACTIVE.get();
        if (pass == null) {
            return;
        }
        pass.candidateFortress = fortress;
        if (pass.selecting) {
            pass.selectedFortress = fortress;
        }
    }

    public static void finishSelection(NaturalSpawnCycle cycle, BlockPos pos, Biome.SpawnEntry selected) {
        int opportunity = finishSelection();
        Pass pass = ACTIVE.get();
        if (opportunity > 0 && cycle != null && SeedDebugLog.isEnabled()) {
            long fortress = pass.protectedFortress;
            SeedDebugLog.info("[ZSG-Rooms/FortressProtection] opportunity fortress={},{} "
                            + "used={}/{} capBypassed={} chunk={},{} cycle={} pack={} pos={} entity={} "
                            + "exhausted={}",
                    new ChunkPos(fortress).x, new ChunkPos(fortress).z,
                    opportunity, FortressSpawnProtectionState.INITIAL_PACK_OPPORTUNITIES,
                    pass.capBypassed, cycle.getSection().getChunkX(), cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(), cycle.getPackIndex(), pos,
                    selected == null ? "none" : net.minecraft.util.registry.Registry.ENTITY_TYPE.getId(selected.type),
                    opportunity == FortressSpawnProtectionState.INITIAL_PACK_OPPORTUNITIES);
        }
    }

    static int finishSelection() {
        Pass pass = ACTIVE.get();
        if (pass == null) {
            return -1;
        }
        pass.selecting = false;
        if (pass.packSelected) {
            return -1;
        }
        pass.packSelected = true;
        Long fortress = pass.selectedFortress;
        if (fortress == null) {
            return -1;
        }
        FortressSpawnProtectionState.Evaluation evaluation = pass.evaluations == null
                ? null : pass.evaluations.get(fortress);
        if (evaluation == null && !pass.capBypassed) {
            // A vanilla pack can walk into a fortress from a neighbouring origin chunk.
            evaluation = pass.state.beginCycle(fortress);
            if (evaluation != null) {
                pass.addEvaluation(fortress, evaluation);
            }
        }
        int opportunity = evaluation == null ? -1 : evaluation.consumeOpportunity();
        if (opportunity > 0) {
            pass.protectedFortress = fortress;
        }
        return opportunity;
    }

    public static boolean allowsPack() {
        Pass pass = ACTIVE.get();
        return pass == null || !pass.capBypassed || pass.protectedFortress != null;
    }

    public static boolean allowsCandidate() {
        Pass pass = ACTIVE.get();
        return pass == null || !pass.capBypassed
                || (pass.protectedFortress != null && pass.protectedFortress.equals(pass.candidateFortress));
    }

    public static StructureStart<?> fortressAt(ServerWorld world, BlockPos pos) {
        if (world.getBlockState(pos.down()).isOf(Blocks.NETHER_BRICKS)) {
            StructureStart<?> start = world.getStructureAccessor().method_28388(pos, false, StructureFeature.FORTRESS);
            if (start.hasChildren()) {
                return start;
            }
        }
        return world.getStructureAccessor().method_28388(pos, true, StructureFeature.FORTRESS);
    }

    public static void clear() {
        ACTIVE.remove();
    }

    private static final class Pass {
        private final FortressSpawnProtectionState state;
        private final boolean capBypassed;
        private Map<Long, FortressSpawnProtectionState.Evaluation> evaluations;
        private Long selectedFortress;
        private Long candidateFortress;
        private Long protectedFortress;
        private boolean selecting;
        private boolean packSelected;

        private Pass(FortressSpawnProtectionState state, boolean capBypassed) {
            this.state = state;
            this.capBypassed = capBypassed;
        }

        private void addEvaluation(long fortress, FortressSpawnProtectionState.Evaluation evaluation) {
            if (this.evaluations == null) {
                this.evaluations = new HashMap<Long, FortressSpawnProtectionState.Evaluation>();
            }
            this.evaluations.put(fortress, evaluation);
        }
    }
}

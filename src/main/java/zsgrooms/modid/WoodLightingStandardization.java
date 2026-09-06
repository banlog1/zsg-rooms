package zsgrooms.modid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import zsgrooms.modid.mixin.PortalAreaAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public final class WoodLightingStandardization {
    private static final int MARGIN = 3;
    private static final int MAX_SETUPS = 4;
    private static final Map<ServerWorld, WorldState> WORLDS = Collections.synchronizedMap(
            new IdentityHashMap<ServerWorld, WorldState>());
    private static final ThreadLocal<Event> EVENT = new ThreadLocal<Event>();
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    private WoodLightingStandardization() {
    }

    private static boolean enabled(World world) {
        return RngStandardization.isEnabled() && world instanceof ServerWorld
                && World.OVERWORLD.equals(world.getRegistryKey()) && world.getServer().isOnThread();
    }

    public static void onBlockChanged(ServerWorld world, BlockPos pos, BlockState oldState, BlockState state) {
        if (!enabled(world)) {
            return;
        }
        WorldState tracked = WORLDS.get(world);
        if (tracked == null) {
            tracked = new WorldState(world);
            WORLDS.put(world, tracked);
        }
        if (tracked.generation != RngStandardization.getConfigurationGeneration()) {
            return;
        }
        Session owner = tracked.owner(pos);
        if (owner != null) {
            owner.changed(pos, oldState, state);
            return;
        }
        // Discovery only follows nearby construction/block changes, never a per-tick terrain scan.
        if (EVENT.get() == null && tracked.sessions.size() < MAX_SETUPS
                && (state.isAir() || relevant(state) || relevant(oldState)) && nearPlayer(world, pos, 16)) {
            tracked.discover(pos);
        }
    }

    private static boolean relevant(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.getMaterial().isBurnable()
                || state.getFluidState().isIn(FluidTags.LAVA);
    }

    public static void tick(ServerWorld world) {
        WorldState tracked = WORLDS.get(world);
        if (tracked == null) {
            return;
        }
        if (!enabled(world) || tracked.generation != RngStandardization.getConfigurationGeneration()) {
            tracked.release();
            WORLDS.remove(world);
            return;
        }
        Iterator<Session> iterator = tracked.sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (!session.loaded()) {
                continue;
            }
            if (session.frameDirty) {
                session.frameDirty = false;
                if (!session.matchesFrame()) {
                    session.release();
                    iterator.remove();
                    continue;
                }
            }
            session.activated |= session.fuel > 0 && session.lavaCount > 0;
            if (session.activated && world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK)
                    && world.getChunkManager().shouldTickBlock(session.anchor)
                    && nearPlayer(world, session.anchor, 128)) {
                session.tick();
            }
        }
    }

    public static void stop(MinecraftServer server) {
        synchronized (WORLDS) {
            WORLDS.keySet().removeIf(world -> world.getServer() == server);
        }
        EVENT.remove();
    }

    private static Session activeOwner(World world, BlockPos pos) {
        if (!enabled(world)) {
            return null;
        }
        WorldState tracked = WORLDS.get(world);
        if (tracked == null || tracked.generation != RngStandardization.getConfigurationGeneration()) {
            return null;
        }
        Session session = tracked.owner(pos);
        return session != null && session.activated ? session : null;
    }

    public static boolean suppressNaturalLava(World world, BlockPos source) {
        Event event = EVENT.get();
        return activeOwner(world, source) != null && (event == null || !event.matches(world, source));
    }

    public static boolean suppressScheduledFire(World world, BlockPos source) {
        Event event = EVENT.get();
        return activeOwner(world, source) != null && (event == null || !event.matches(world, source));
    }

    public static int fireDelay(World world, BlockPos pos, Random vanilla) {
        int original = 30 + vanilla.nextInt(10);
        Session session = activeOwner(world, pos);
        if (session == null) {
            return original;
        }
        return session.source(pos, false).sequence.remainingFireTicks();
    }

    public static int initialFireDelay(World world, BlockPos pos, Random vanilla) {
        Session session = activeOwner(world, pos);
        if (session != null) {
            // A newly placed fire gets a full delay even if an earlier fire here was extinguished.
            session.source(pos, false).sequence.restartFireDelay();
        }
        return fireDelay(world, pos, vanilla);
    }

    public static Random burnRandom(Random original, BlockPos target) {
        Event event = EVENT.get();
        return event == null || event.source.lava ? original
                : event.source.sequence.targetRandom("fire_burn", event.session.localKey(target), event.index);
    }

    public static void airTarget(BlockPos target) {
        Event event = EVENT.get();
        if (event != null && !event.source.lava) {
            event.airTarget = target.toImmutable();
            event.airRandom = null;
        }
    }

    public static int fireInt(Random original, int bound) {
        Event event = EVENT.get();
        if (event == null || event.airTarget == null) {
            return original.nextInt(bound);
        }
        if (event.airRandom == null) {
            event.airRandom = event.source.sequence.targetRandom("fire_air",
                    event.session.localKey(event.airTarget), event.index);
        }
        return event.airRandom.nextInt(bound);
    }

    private static boolean nearPlayer(ServerWorld world, BlockPos pos, int distance) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.squaredDistanceTo(pos.getX() + 0.5D,
                    pos.getY() + 0.5D, pos.getZ() + 0.5D) <= distance * distance) {
                return true;
            }
        }
        return false;
    }

    private static final class WorldState {
        private final ServerWorld world;
        private final long generation = RngStandardization.getConfigurationGeneration();
        private final List<Session> sessions = new ArrayList<Session>();

        private WorldState(ServerWorld world) {
            this.world = world;
        }

        private Session owner(BlockPos pos) {
            for (Session session : sessions) {
                if (session.contains(pos)) {
                    return session;
                }
            }
            return null;
        }

        private void discover(BlockPos changed) {
            // AreaHelper can inspect up to 21 blocks from its input. Never load terrain for discovery.
            if (!world.isRegionLoaded(changed.getX() - 26, 0, changed.getZ() - 26,
                    changed.getX() + 26, 255, changed.getZ() + 26)) {
                return;
            }
            for (BlockPos pos : BlockPos.iterate(changed.add(-4, -4, -4), changed.add(4, 4, 4))) {
                if (pos.getY() < 1 || pos.getY() > 254 || !world.getBlockState(pos).isAir()) {
                    continue;
                }
                boolean edge = world.getBlockState(pos.down()).isOf(Blocks.OBSIDIAN);
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    edge |= world.getBlockState(pos.offset(direction)).isOf(Blocks.OBSIDIAN);
                }
                if (!edge) {
                    continue;
                }
                NetherPortalBlock.AreaHelper area = NetherPortalBlock.createAreaHelper(world, pos);
                if (area == null) {
                    continue;
                }
                Session session = new Session(world, area);
                if (owner(session.anchor) != null || !session.loaded()) {
                    continue;
                }
                boolean overlap = false;
                for (Session existing : sessions) {
                    overlap |= session.overlaps(existing);
                }
                if (!overlap) {
                    sessions.add(session);
                    session.capture();
                    if (SeedDebugLog.isEnabled()) {
                        SeedDebugLog.info("[ZSG-Rooms/WoodLight] Tracking unlit frame at {}; lava={}, fuel={}",
                                session.anchor, session.lavaCount, session.fuel);
                    }
                }
                return;
            }
        }

        private void release() {
            for (Session session : sessions) {
                session.release();
            }
        }
    }

    private static final class Session {
        private final ServerWorld world;
        private final BlockPos anchor;
        private final BlockPos min;
        private final BlockPos max;
        private final String frameKey;
        private final Map<BlockPos, Source> lava = new TreeMap<BlockPos, Source>(POSITION_ORDER);
        private final Map<BlockPos, Source> fire = new TreeMap<BlockPos, Source>(POSITION_ORDER);
        private final List<Source> due = new ArrayList<Source>();
        private int fuel;
        private int lavaCount;
        private boolean activated;
        private boolean frameDirty;
        private long elapsedTicks;

        private Session(ServerWorld world, NetherPortalBlock.AreaHelper area) {
            this.world = world;
            PortalAreaAccessor accessor = (PortalAreaAccessor) area;
            BlockPos corner = accessor.zsgRooms$getLowerCorner();
            BlockPos other = corner.offset(accessor.zsgRooms$getNegativeDir(), area.getWidth() - 1);
            this.anchor = new BlockPos(Math.min(corner.getX(), other.getX()), corner.getY(), Math.min(corner.getZ(), other.getZ()));
            this.min = anchor.add(-MARGIN, -MARGIN, -MARGIN);
            this.max = new BlockPos(Math.max(corner.getX(), other.getX()) + MARGIN,
                    corner.getY() + area.getHeight() - 1 + MARGIN, Math.max(corner.getZ(), other.getZ()) + MARGIN);
            this.frameKey = accessor.zsgRooms$getAxis() + "|" + area.getWidth() + "x" + area.getHeight();
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        private boolean overlaps(Session other) {
            return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                    && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                    && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
        }

        private boolean loaded() {
            // Fire placement itself runs vanilla's portal search, which may read 21 blocks away.
            return world.isRegionLoaded(min.getX() - 22, 0, min.getZ() - 22,
                    max.getX() + 22, 255, max.getZ() + 22);
        }

        private boolean matchesFrame() {
            NetherPortalBlock.AreaHelper area = NetherPortalBlock.createAreaHelper(world, anchor);
            if (area == null) {
                return false;
            }
            PortalAreaAccessor accessor = (PortalAreaAccessor) area;
            BlockPos corner = accessor.zsgRooms$getLowerCorner();
            BlockPos other = corner.offset(accessor.zsgRooms$getNegativeDir(), area.getWidth() - 1);
            return frameKey.equals(accessor.zsgRooms$getAxis() + "|" + area.getWidth() + "x" + area.getHeight())
                    && anchor.getX() == Math.min(corner.getX(), other.getX())
                    && anchor.getY() == corner.getY()
                    && anchor.getZ() == Math.min(corner.getZ(), other.getZ());
        }

        private String localKey(BlockPos pos) {
            return frameKey + "|" + (pos.getX() - anchor.getX()) + ","
                    + (pos.getY() - anchor.getY()) + "," + (pos.getZ() - anchor.getZ());
        }

        private Source source(BlockPos pos, boolean isLava) {
            Map<BlockPos, Source> sources = isLava ? lava : fire;
            Source source = sources.get(pos);
            if (source == null) {
                BlockPos immutable = pos.toImmutable();
                source = new Source(immutable, isLava, new WoodLightingSequence(world.getSeed(), localKey(pos)));
                sources.put(immutable, source);
            }
            return source;
        }

        private void capture() {
            for (BlockPos pos : BlockPos.iterate(min, max)) {
                if (pos.getY() >= 0 && pos.getY() <= 255) {
                    changed(pos, Blocks.AIR.getDefaultState(), world.getBlockState(pos));
                }
            }
            activated = fuel > 0 && lavaCount > 0;
            frameDirty = false;
        }

        private void changed(BlockPos pos, BlockState oldState, BlockState state) {
            fuel += (state.getMaterial().isBurnable() ? 1 : 0) - (oldState.getMaterial().isBurnable() ? 1 : 0);
            updateSource(pos, state.getFluidState().isIn(FluidTags.LAVA), true);
            updateSource(pos, state.isOf(Blocks.FIRE), false);
            if (oldState.getBlock() != state.getBlock()) {
                frameDirty = true;
            }
            activated |= fuel > 0 && lavaCount > 0;
        }

        private void updateSource(BlockPos pos, boolean present, boolean isLava) {
            Source source = present ? source(pos, isLava) : (isLava ? lava : fire).get(pos);
            if (source != null && source.present != present) {
                if (isLava) {
                    lavaCount += present ? 1 : -1;
                }
                source.present = present;
            }
        }

        private void tick() {
            if (elapsedTicks++ == 0 && SeedDebugLog.isEnabled()) {
                SeedDebugLog.info("[ZSG-Rooms/WoodLight] Activated local ignition clock at {}", anchor);
            }
            due.clear();
            int speed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
            for (Source source : lava.values()) {
                if (source.present) {
                    source.opportunities = source.sequence.advanceLava(speed);
                    if (source.opportunities > 0) {
                        due.add(source);
                    }
                }
            }
            for (Source source : fire.values()) {
                if (source.present && source.sequence.advanceFire()) {
                    due.add(source);
                }
            }
            for (Source source : due) {
                if (!source.present) {
                    continue;
                }
                Event previous = EVENT.get();
                long index = source.lava ? -1L : source.sequence.beginFireEvent();
                EVENT.set(new Event(this, source, index));
                try {
                    if (source.lava) {
                        for (int attempt = 0; attempt < source.opportunities; attempt++) {
                            FluidState state = world.getFluidState(source.pos);
                            if (state.isIn(FluidTags.LAVA)) {
                                state.onRandomTick(world, source.pos, source.sequence.nextLavaRandom());
                            }
                        }
                    } else {
                        BlockState state = world.getBlockState(source.pos);
                        if (state.isOf(Blocks.FIRE)) {
                            state.scheduledTick(world, source.pos, source.sequence.fireRandom(index));
                        }
                    }
                } finally {
                    if (previous == null) {
                        EVENT.remove();
                    } else {
                        EVENT.set(previous);
                    }
                }
            }
        }

        private void release() {
            for (Source source : fire.values()) {
                if (source.present && (!world.isChunkLoaded(source.pos) || world.getBlockState(source.pos).isOf(Blocks.FIRE))) {
                    world.getBlockTickScheduler().schedule(source.pos, Blocks.FIRE, source.sequence.remainingFireTicks());
                }
            }
            if (SeedDebugLog.isEnabled()) {
                SeedDebugLog.info("[ZSG-Rooms/WoodLight] Released frame at {} after {} active ticks", anchor, elapsedTicks);
            }
        }
    }

    private static final class Source {
        private final BlockPos pos;
        private final boolean lava;
        private final WoodLightingSequence sequence;
        private boolean present;
        private int opportunities;

        private Source(BlockPos pos, boolean lava, WoodLightingSequence sequence) {
            this.pos = pos;
            this.lava = lava;
            this.sequence = sequence;
        }
    }

    private static final class Event {
        private final Session session;
        private final Source source;
        private final long index;
        private BlockPos airTarget;
        private Random airRandom;

        private Event(Session session, Source source, long index) {
            this.session = session;
            this.source = source;
            this.index = index;
        }

        private boolean matches(World world, BlockPos pos) {
            return session.world == world && source.pos.equals(pos);
        }
    }
}

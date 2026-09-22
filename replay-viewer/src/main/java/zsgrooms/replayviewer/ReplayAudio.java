// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.ReplaySender;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.fluid.FluidState;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/** Playback only. No capture hooks, background scans or changes to replay packets. */
public final class ReplayAudio {
    private static final SeekAudioGate gate = new SeekAudioGate();
    private static final PlayerSoundCadence cadence = new PlayerSoundCadence();
    private static final MiningSoundTracker mining = new MiningSoundTracker();
    private static final FallSoundTracker falls = new FallSoundTracker();
    private static ReplayHandler current;
    private static PlayerEntity tracked;
    private static boolean enabled;
    private static int lastPlacement = -1;
    private static int lastBucketTime = -1;
    private static BucketSoundRules.Action lastBucket = BucketSoundRules.Action.NONE;
    private static final Item[] hands = new Item[2], previousHands = new Item[2];
    private static final int[] handChanged = new int[2];

    private ReplayAudio() {}

    private static ReplayHandler handler() {
        ReplayHandler value = ReplayModReplay.instance == null ? null : ReplayModReplay.instance.getReplayHandler();
        if (value != current) {
            current = value;
            enabled = false;
            reset();
            if (value != null) gate.begin();
        }
        return value;
    }

    private static boolean busy(ReplayHandler handler) {
        ReplaySender sender = handler.getReplaySender();
        return !sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying();
    }

    public static boolean muted() {
        ReplayHandler handler = handler();
        if (handler == null) return false;
        if (busy(handler)) gate.begin();
        return gate.muted();
    }

    public static void beginSeek() {
        if (handler() == null) return;
        gate.begin();
        reset();
        MinecraftClient.getInstance().getSoundManager().stopAll();
    }

    static void frame() {
        ReplayHandler handler = handler();
        if (handler == null) return;
        if (gate.frame(busy(handler))) {
            // Drop delayed sounds from before the seek, not just incoming sound packets.
            MinecraftClient.getInstance().getSoundManager().stopAll();
            reset();
        }
    }

    static boolean enabled() { handler(); return enabled; }
    static void setEnabled(boolean value) { handler(); enabled = value; reset(); }

    private static void reset() {
        tracked = null;
        cadence.reset();
        mining.reset();
        falls.reset();
        lastPlacement = lastBucketTime = -1;
        lastBucket = BucketSoundRules.Action.NONE;
        for (int i = 0; i < 2; i++) { hands[i] = previousHands[i] = null; handChanged[i] = -1; }
    }

    private static boolean active() {
        ReplayHandler handler = handler();
        return handler != null && enabled && !muted() && !handler.getReplaySender().paused();
    }

    private static boolean recorded(Entity entity) {
        return entity instanceof PlayerEntity && entity == FarFollowController.recordedPlayer(MinecraftClient.getInstance());
    }

    public static void tick(PlayerEntity player) {
        if (!active() || !recorded(player)) { if (player == tracked) reset(); return; }
        int time = current.getReplaySender().currentTimeStamp();
        updateHands(player, time);
        boolean walking = player.isOnGround() && !player.isSneaking() && !player.hasVehicle()
                && !player.isTouchingWater() && !player.isInLava() && !player.abilities.flying && player.isAlive();
        if (cadence.step(player.getX(), player.getY(), player.getZ(), walking)) {
            BlockPos pos = new BlockPos(player.getX(), player.getY() - 0.2, player.getZ());
            BlockState block = player.world.getBlockState(pos);
            BlockState above = player.world.getBlockState(pos.up());
            if (above.isOf(Blocks.SNOW)) block = above;
            if (!block.isAir() && !block.getMaterial().isLiquid()) {
                BlockSoundGroup group = block.getSoundGroup();
                play(player, group.getStepSound(), group.getVolume() * 0.15F, group.getPitch());
            }
        }
        // Replay players may not have a swing hand until their first arm-swing packet.
        if (player.handSwinging && player.preferredHand != null && !player.isUsingItem() && player.isAlive()
                && !(player.getStackInHand(player.preferredHand).getItem() instanceof BucketItem)
                && !ReplayViewer.recordedMenuOpen(time) && (lastPlacement < 0 || time - lastPlacement >= 250)
                && (lastBucketTime < 0 || time - lastBucketTime >= 250)) {
            HitResult hit = player.rayTrace(4.5, 1, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                BlockState block = player.world.getBlockState(pos);
                if (!block.isAir() && block.getHardness(player.world, pos) > 0) {
                    if (mining.sample(time, pos.asLong(), Block.getRawIdFromState(block), player.handSwingTicks)) {
                        BlockSoundGroup group = block.getSoundGroup();
                        playBlock(player, pos, group.getHitSound(), (group.getVolume() + 1) / 8, group.getPitch() * 0.5F);
                    }
                    return;
                }
            }
        }
        mining.reset();
    }

    public static void playerSound(Entity entity, SoundEvent sound, float volume, float pitch) {
        if (!active() || !recorded(entity) || entity.isSilent() || entity == MinecraftClient.getInstance().player) return;
        PlayerEntity player = (PlayerEntity) entity;
        if (sound == SoundEvents.ENTITY_PLAYER_HURT || sound == SoundEvents.ENTITY_PLAYER_HURT_ON_FIRE
                || sound == SoundEvents.ENTITY_PLAYER_HURT_DROWN || sound == SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH
                || sound == SoundEvents.ENTITY_PLAYER_DEATH || sound == SoundEvents.ENCHANT_THORNS_HIT
                || sound == SoundEvents.ITEM_SHIELD_BLOCK || sound == SoundEvents.ITEM_SHIELD_BREAK) {
            track(player);
            play(player, sound, volume, pitch);
            if (sound == SoundEvents.ENTITY_PLAYER_HURT) {
                fallImpact(player, falls.hurt(current.getReplaySender().currentTimeStamp()));
            }
            return;
        }
        ItemStack stack = player.getActiveItem();
        if (player.isUsingItem() && !stack.isEmpty()
                && (sound == stack.getEatSound() || sound == stack.getDrinkSound())) play(player, sound, volume, pitch);
    }

    public static boolean enabledForMovement() { return active(); }

    public static void movement(Entity entity, double x, double y, double z, boolean onGround) {
        if (!active() || !recorded(entity)) return;
        PlayerEntity player = (PlayerEntity) entity;
        track(player);
        BlockPos pos = new BlockPos(x, y, z);
        boolean eligible = !player.abilities.allowFlying && !player.hasVehicle() && !player.isFallFlying()
                && !player.isClimbing() && !player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                && !player.hasStatusEffect(StatusEffects.LEVITATION)
                && !player.isTouchingWater() && !player.isInLava() && player.world.getFluidState(pos).isEmpty();
        fallImpact(player, falls.move(current.getReplaySender().currentTimeStamp(), x, y, z, onGround, eligible));
    }

    private static void fallImpact(PlayerEntity player, double distance) {
        if (distance <= 0 || player.isSilent()) return;
        BlockPos pos = new BlockPos(falls.landedX, falls.landedY - 0.2, falls.landedZ);
        BlockState block = player.world.getBlockState(pos);
        // Exceptional landing blocks cannot be inferred reliably from a generic hurt status.
        if (block.isAir() || !block.getFluidState().isEmpty() || block.isOf(Blocks.SLIME_BLOCK)) return;
        StatusEffectInstance jump = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        int damage = FallSoundTracker.damage((float) distance, jump == null ? 0 : jump.getAmplifier() + 1,
                block.getBlock() instanceof net.minecraft.block.BedBlock,
                block.isOf(Blocks.HAY_BLOCK) || block.isOf(Blocks.HONEY_BLOCK));
        if (damage <= 0) return;
        ClientWorld world = (ClientWorld) player.world;
        world.playSound(falls.landedX, falls.landedY, falls.landedZ,
                damage > 4 ? SoundEvents.ENTITY_PLAYER_BIG_FALL : SoundEvents.ENTITY_PLAYER_SMALL_FALL,
                SoundCategory.PLAYERS, 1, 1, false);
        BlockSoundGroup group = block.getSoundGroup();
        world.playSound(falls.landedX, falls.landedY, falls.landedZ, group.getFallSound(), SoundCategory.PLAYERS,
                group.getVolume() * 0.5F, group.getPitch() * 0.75F, false);
    }

    public static void blockUpdate(ClientWorld world, BlockPos pos, BlockState state) {
        if (!active() || world != MinecraftClient.getInstance().world) return;
        PlayerEntity player = FarFollowController.recordedPlayer(MinecraftClient.getInstance());
        if (player == null || player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36) return;
        int time = current.getReplaySender().currentTimeStamp();
        updateHands(player, time);
        if (ReplayViewer.recordedMenuOpen(time)) return;
        BlockState before = world.getBlockState(pos);
        if (bucketUpdate(player, pos, before, state, time)) return;
        if (state.isAir() || before.getBlock() == state.getBlock() || !before.getMaterial().isReplaceable()) return;
        boolean held = false;
        for (int i = 0; i < 2; i++) {
            if (places(hands[i], state) || recent(i, time) && places(previousHands[i], state)) held = true;
        }
        if (!held || lastPlacement >= 0 && time - lastPlacement < 50) return;
        lastPlacement = time;
        mining.reset();
        BlockSoundGroup group = state.getSoundGroup();
        playBlock(player, pos, group.getPlaceSound(), (group.getVolume() + 1) / 2, group.getPitch() * 0.8F);
    }

    private static boolean places(Item item, BlockState block) {
        return item instanceof BlockItem && ((BlockItem) item).getBlock() == block.getBlock();
    }

    private static void updateHands(PlayerEntity player, int time) {
        track(player);
        for (int i = 0; i < 2; i++) {
            Item item = (i == 0 ? player.getMainHandStack() : player.getOffHandStack()).getItem();
            if (hands[i] != item) {
                mining.reset();
                previousHands[i] = hands[i];
                hands[i] = item;
                handChanged[i] = time;
            }
        }
    }

    private static void track(PlayerEntity player) { if (tracked != player) { reset(); tracked = player; } }

    private static boolean recent(int hand, int time) { return handChanged[hand] >= 0 && time >= handChanged[hand] && time - handChanged[hand] <= 250; }

    private static int bucket(Item item) {
        if (item == Items.BUCKET) return BucketSoundRules.EMPTY_BUCKET;
        if (item == Items.WATER_BUCKET) return BucketSoundRules.WATER_BUCKET;
        if (item == Items.LAVA_BUCKET) return BucketSoundRules.LAVA_BUCKET;
        return 0;
    }

    private static int source(FluidState fluid) {
        if (!fluid.isStill()) return BucketSoundRules.NONE;
        if (fluid.isIn(FluidTags.WATER)) return BucketSoundRules.WATER;
        if (fluid.isIn(FluidTags.LAVA)) return BucketSoundRules.LAVA;
        return BucketSoundRules.NONE;
    }

    private static boolean bucketUpdate(PlayerEntity player, BlockPos pos, BlockState before, BlockState after, int time) {
        int held = 0;
        for (int i = 0; i < 2; i++) held |= bucket(hands[i]) | (recent(i, time) ? bucket(previousHands[i]) : 0);
        if (held == 0) return false;
        FluidState oldFluid = before.getFluidState(), newFluid = after.getFluidState();
        BucketSoundRules.Action action = BucketSoundRules.action(source(oldFluid), source(newFluid),
                newFluid.isEmpty(), held);
        if (action == BucketSoundRules.Action.NONE) return false;
        boolean fill = action == BucketSoundRules.Action.FILL_WATER || action == BucketSoundRules.Action.FILL_LAVA;
        HitResult hit = player.rayTrace(5, 1, fill);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockHitResult blockHit = (BlockHitResult) hit;
        if (!pos.equals(blockHit.getBlockPos()) && (fill || !pos.equals(blockHit.getBlockPos().offset(blockHit.getSide())))) return false;
        if (lastBucket == action && lastBucketTime >= 0 && time - lastBucketTime < 50) return true;
        lastBucket = action;
        lastBucketTime = time;
        mining.reset();
        SoundEvent sound;
        switch (action) {
            case FILL_WATER: sound = SoundEvents.ITEM_BUCKET_FILL; break;
            case FILL_LAVA: sound = SoundEvents.ITEM_BUCKET_FILL_LAVA; break;
            case EMPTY_WATER: sound = SoundEvents.ITEM_BUCKET_EMPTY; break;
            case EMPTY_LAVA: sound = SoundEvents.ITEM_BUCKET_EMPTY_LAVA; break;
            default: return false;
        }
        if (fill) play(player, sound, 1, 1);
        else playBlock(player, pos, sound, 1, 1);
        return true;
    }

    private static void playBlock(PlayerEntity player, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        if (player.isSilent()) return;
        ((ClientWorld) player.world).playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundCategory.BLOCKS, volume, pitch, false);
    }

    private static void play(PlayerEntity player, SoundEvent sound, float volume, float pitch) {
        if (player.isSilent()) return;
        ((ClientWorld) player.world).playSound(player.getX(), player.getY(), player.getZ(), sound,
                SoundCategory.PLAYERS, volume, pitch, false);
    }
}

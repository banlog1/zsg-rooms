package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Uses Minecraft's actual sound dispatch, without requiring audible speakers. */
final class AudioSmokeChecks {
    private static final List<String> heard = new ArrayList<>();
    private static final SoundInstanceListener listener = (sound, set) -> heard.add(sound.getId().toString());

    static void seek(ReplayHandler handler, int time) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().registerListener(listener);
        heard.clear();
        try {
            handler.doJump(time, true);
            require(heard.isEmpty(), "Sounds played while reconstructing a " + (handler.isQuickMode() ? "Quick" : "normal") + " seek: " + heard);
            require((Boolean) call("muted"), "Seek did not leave an audio settlement guard");
            probe(client);
            require(heard.isEmpty(), "Sound escaped immediately after a seek");
        } finally { client.getSoundManager().unregisterListener(listener); }
    }

    static void run(ReplayHandler handler, MinecraftClient client) throws Exception {
        require(!(Boolean) call("muted"), "Audio never resumed after seeking");
        require((Boolean) call("enabled"), "Reconstructed sounds disabled by default");
        handler.getOverlay().setMouseVisible(true);
        ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
        CheckboxWidget option = null;
        for (net.minecraft.client.gui.Element child : client.currentScreen.children()) {
            if (child instanceof CheckboxWidget && ((CheckboxWidget) child).getMessage().getString().equals("Player sounds (experimental)")) option = (CheckboxWidget) child;
        }
        require(option != null && option.isChecked(), "Default-on audio setting not checked");
        option.onPress();
        require(!(Boolean) call("enabled"), "Audio checkbox did not disable sounds");
        option.onPress();
        require((Boolean) call("enabled"), "Audio checkbox not wired");
        client.currentScreen.init(client, 320, 240);
        for (net.minecraft.client.gui.Element child : client.currentScreen.children()) {
            if (child instanceof net.minecraft.client.gui.widget.AbstractButtonWidget) {
                net.minecraft.client.gui.widget.AbstractButtonWidget button = (net.minecraft.client.gui.widget.AbstractButtonWidget) child;
                require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= 320
                        && button.y + button.getHeight() <= 240, "Audio settings overflow minimum GUI size");
            }
        }
        client.currentScreen.onClose();
        client.getSoundManager().registerListener(listener);
        try {
            heard.clear();
            probe(client);
            require(!heard.isEmpty(), "Control sound did not reach Minecraft's sound engine");
            // Both already-queued and newly requested delayed audio must be discarded.
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1), 10);
            call("beginSeek");
            heard.clear();
            probe(client);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1), 10);
            require(heard.isEmpty(), "Seek mute leaked immediate audio");
            call("frame"); call("frame");
            for (int i = 0; i < 20; i++) client.getSoundManager().tick(false);
            require(heard.isEmpty(), "Delayed seek sounds replayed afterward");
            probe(client);
            require(!heard.isEmpty(), "Audio remained muted after settlement");

            Method recorded = Class.forName("zsgrooms.replayviewer.FarFollowController").getDeclaredMethod("recordedPlayer", MinecraftClient.class);
            recorded.setAccessible(true);
            PlayerEntity player = (PlayerEntity) recorded.invoke(null, client);
            require(player != null, "No recorded player for audio test");
            handler.getReplaySender().setReplaySpeed(1);
            player.preferredHand = null;
            player.handSwinging = false;
            double x = player.getX(), y = player.getY(), z = player.getZ();
            player.abilities.flying = false;
            player.setOnGround(true);
            player.setSneaking(false);
            BlockPos floor = new BlockPos(x, y - 0.2, z);
            for (int dx = -1; dx <= 4; dx++) client.world.setBlockState(floor.add(dx, 0, 0), Blocks.STONE.getDefaultState(), 19);
            heard.clear();
            tick(player);
            player.updatePosition(x + 1, y, z); tick(player);
            player.updatePosition(x + 2, y, z); tick(player);
            require(heard.contains("minecraft:block.stone.step"), "Footsteps missing: " + heard);

            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.APPLE));
            player.setCurrentHand(Hand.MAIN_HAND);
            // On a client the server's tracked flag, not setCurrentHand, starts consumption.
            using(player, true);
            require(player.isUsingItem(), "Test did not establish recorded use-item state");
            heard.clear();
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1, 1);
            require(heard.contains("minecraft:entity.generic.eat"), "Native consumption restoration missing: " + heard);
            using(player, false);
            player.clearActiveItem();
            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.POTION));
            using(player, true);
            heard.clear();
            player.playSound(SoundEvents.ENTITY_GENERIC_DRINK, 1, 1);
            require(heard.contains("minecraft:entity.generic.drink"), "Native drinking restoration missing: " + heard);
            using(player, false);
            player.clearActiveItem();
            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
            BlockPos placement = player.getBlockPos().add(1, 0, 0);
            client.world.setBlockState(placement, Blocks.AIR.getDefaultState(), 19);
            heard.clear();
            client.world.setBlockStateWithoutNeighborUpdates(placement, Blocks.STONE.getDefaultState());
            require(heard.contains("minecraft:block.stone.place"), "Placement restoration missing: " + heard);
            heard.clear();
            client.world.setBlockStateWithoutNeighborUpdates(placement, Blocks.STONE.getDefaultState());
            require(heard.isEmpty(), "Repeated state emitted another placement");

            player.updatePosition(floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 0.5);
            for (int dy = 1; dy <= 3; dy++) client.world.setBlockState(floor.add(2, dy, 0), Blocks.AIR.getDefaultState(), 19);
            client.world.setBlockState(floor.add(2, 0, 0), Blocks.STONE.getDefaultState(), 19);
            player.pitch = 90;
            player.handSwinging = true;
            player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.WATER_BUCKET));
            call("setEnabled", new Class<?>[]{boolean.class}, true);
            tick(player);
            primeMining(handler, floor.add(2, 0, 0));
            player.handSwingTicks = 0;
            heard.clear();
            tick(player);
            require(heard.isEmpty(), "Unknown swing hand emitted mining sounds");
            player.preferredHand = Hand.MAIN_HAND;
            tick(player);
            require(heard.isEmpty(), "Unknown swing hand retained stale mining evidence");
            heard.clear();
            for (int phase = 0; phase < 6; phase++) {
                player.handSwingTicks = phase;
                tick(player);
            }
            require(heard.isEmpty(), "Single interaction swing sounded like mining: " + heard);
            primeMining(handler, floor.add(2, 0, 0));
            player.handSwingTicks = 0;
            tick(player);
            require(heard.contains("minecraft:block.stone.hit"), "Mining restoration missing: " + heard);
            player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            buckets(client, handler, player, floor.add(2, 1, 0));
            damageSounds(client, player, floor.add(2, 1, 0));
            call("setEnabled", new Class<?>[]{boolean.class}, false);
            heard.clear();
            tick(player); tick(player);
            require(heard.isEmpty(), "Opt-out still reconstructed mining sounds");
            probe(client);
            require(!heard.isEmpty(), "Opt-out muted existing replay sounds");
            handler.getReplaySender().setReplaySpeed(0);
            ZsgRooms.LOGGER.info("[ReplayAudioSmoke] PASS: default-on UI, null swing hand, footsteps, eating/drinking, placements, repeated mining only, water/lava fill/empty, damage statuses, small/big falls, quiet flow, packet ordering, opt-out, silent normal/Quick seeks and delayed-queue discard");
        } finally { client.getSoundManager().unregisterListener(listener); }
    }

    private static void primeMining(ReplayHandler handler, BlockPos pos) throws Exception {
        java.lang.reflect.Field field = Class.forName("zsgrooms.replayviewer.ReplayAudio").getDeclaredField("mining");
        field.setAccessible(true);
        Object tracker = field.get(null);
        Method sample = tracker.getClass().getDeclaredMethod("sample", int.class, long.class, int.class, int.class);
        sample.setAccessible(true);
        int time = handler.getReplaySender().currentTimeStamp();
        for (int i = 0; i < 3; i++) sample.invoke(tracker, time - 150 + i * 50, pos.asLong(),
                net.minecraft.block.Block.getRawIdFromState(Blocks.STONE.getDefaultState()), i);
    }

    private static void damageSounds(MinecraftClient client, PlayerEntity player, BlockPos pos) throws Exception {
        call("setEnabled", new Class<?>[]{boolean.class}, true);
        player.abilities.allowFlying = false;
        player.handSwinging = false;
        for (int dy = 0; dy <= 12; dy++) client.world.setBlockState(pos.up(dy), Blocks.AIR.getDefaultState(), 19);
        client.world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 19);
        int[] statuses = {2, 36, 37, 44, 3, 29, 30};
        String[] sounds = {"entity.player.hurt", "entity.player.hurt_drown", "entity.player.hurt_on_fire",
                "entity.player.hurt_sweet_berry_bush", "entity.player.death", "item.shield.block", "item.shield.break"};
        for (int i = 0; i < statuses.length; i++) {
            heard.clear();
            status(client, player, statuses[i]);
            require(heard.size() == 1 && heard.contains("minecraft:" + sounds[i]), "Damage status missing/duplicated: " + statuses[i] + ": " + heard);
        }
        heard.clear();
        status(client, player, 33);
        require(heard.size() == 2 && heard.contains("minecraft:enchant.thorns.hit")
                && heard.contains("minecraft:entity.player.hurt"), "Thorns status sounds incorrect: " + heard);
        PlayerEntity other = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Other"));
        heard.clear();
        other.handleStatus((byte) 2);
        require(heard.isEmpty(), "Reconstructed another player's damage");

        for (boolean big : new boolean[]{false, true}) {
            call("setEnabled", new Class<?>[]{boolean.class}, true);
            heard.clear();
            fallDescent(client, player, pos, big);
            if (!big) status(client, player, 2);
            move(client, player, pos, 0, true);
            if (big) status(client, player, 2);
            String impact = big ? "minecraft:entity.player.big_fall" : "minecraft:entity.player.small_fall";
            require(heard.size() == 3 && heard.contains(impact) && heard.contains("minecraft:block.stone.fall")
                    && heard.contains("minecraft:entity.player.hurt"), "Fall impact missing/duplicated: " + heard);
            heard.clear();
            move(client, player, pos, 0, true);
            require(heard.isEmpty(), "Landing replayed its impact");
        }
        call("setEnabled", new Class<?>[]{boolean.class}, true);
        heard.clear();
        fallDescent(client, player, pos, false);
        move(client, player, pos, 0, true);
        require(heard.isEmpty(), "Landing without damage emitted an impact");

        call("setEnabled", new Class<?>[]{boolean.class}, true);
        fallDescent(client, player, pos, true);
        client.world.setBlockState(pos, Blocks.WATER.getDefaultState(), 19);
        heard.clear();
        status(client, player, 2);
        move(client, player, pos, 0, true);
        require(heard.size() == 1 && heard.contains("minecraft:entity.player.hurt"), "Water landing emitted a fall: " + heard);
        client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 19);

        call("setEnabled", new Class<?>[]{boolean.class}, true);
        fallDescent(client, player, pos, true);
        call("beginSeek");
        heard.clear();
        status(client, player, 2);
        move(client, player, pos, 0, true);
        require(heard.isEmpty(), "Damage or impact leaked during seek");
        call("frame"); call("frame");
        status(client, player, 2);
        require(heard.size() == 1, "Fall evidence leaked across seek: " + heard);
        call("setEnabled", new Class<?>[]{boolean.class}, false);
        heard.clear();
        status(client, player, 2);
        status(client, player, 3);
        require(heard.isEmpty(), "Disabled option reconstructed damage/death");
    }

    private static void fallDescent(MinecraftClient client, PlayerEntity player, BlockPos pos, boolean big) {
        int height = big ? 10 : 5;
        move(client, player, pos, height, true);
        for (int dy = height - 2; dy > 0; dy -= 2) move(client, player, pos, dy, false);
    }

    private static void move(MinecraftClient client, PlayerEntity player, BlockPos pos, int dy, boolean onGround) {
        player.updatePosition(pos.getX() + 0.5, pos.getY() + dy, pos.getZ() + 0.5);
        player.setOnGround(onGround);
        client.getNetworkHandler().onEntityPosition(new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(player));
    }

    private static void status(MinecraftClient client, PlayerEntity player, int status) {
        client.getNetworkHandler().onEntityStatus(new net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket(player, (byte) status));
    }

    private static void buckets(MinecraftClient client, ReplayHandler handler, PlayerEntity player, BlockPos pos) throws Exception {
        player.updatePosition(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5);
        player.setOnGround(false);
        player.pitch = 90;
        for (int dy = 0; dy <= 4; dy++) client.world.setBlockState(pos.up(dy), Blocks.AIR.getDefaultState(), 19);
        client.world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 19);
        for (boolean lava : new boolean[]{false, true}) {
            call("setEnabled", new Class<?>[]{boolean.class}, true);
            net.minecraft.block.BlockState fluid = (lava ? Blocks.LAVA : Blocks.WATER).getDefaultState();
            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(lava ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
            tick(player);
            // Equipment can arrive before the corresponding block packet.
            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
            heard.clear();
            client.world.setBlockStateWithoutNeighborUpdates(pos, fluid);
            require(heard.size() == 1 && heard.contains(lava ? "minecraft:item.bucket.empty_lava" : "minecraft:item.bucket.empty"), "Bucket pour missing/duplicated: " + heard);
            heard.clear();
            client.world.setBlockStateWithoutNeighborUpdates(pos, fluid);
            require(heard.isEmpty(), "Duplicate fluid update played another bucket sound");
            player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(lava ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
            client.world.setBlockStateWithoutNeighborUpdates(pos, Blocks.AIR.getDefaultState());
            require(heard.size() == 1 && heard.contains(lava ? "minecraft:item.bucket.fill_lava" : "minecraft:item.bucket.fill"), "Bucket pickup missing/duplicated: " + heard);
            heard.clear();
            primeMining(handler, pos.down());
            player.handSwingTicks = 0;
            tick(player);
            require(heard.isEmpty(), "Bucket use emitted mining sounds");
        }
        call("setEnabled", new Class<?>[]{boolean.class}, true);
        player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
        heard.clear();
        client.world.setBlockStateWithoutNeighborUpdates(pos, Blocks.WATER.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, 1));
        require(heard.isEmpty(), "Ordinary flowing water emitted bucket sound");
        client.world.setBlockState(pos.east(), Blocks.AIR.getDefaultState(), 19);
        client.world.setBlockStateWithoutNeighborUpdates(pos.east(), Blocks.WATER.getDefaultState());
        require(heard.isEmpty(), "Unrelated source outside player's aim emitted bucket sound");
        call("beginSeek");
        client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 19);
        client.world.setBlockStateWithoutNeighborUpdates(pos, Blocks.WATER.getDefaultState());
        require(heard.isEmpty(), "Bucket sound leaked during seek");
        call("frame"); call("frame");
    }

    private static void tick(PlayerEntity player) throws Exception { call("tick", new Class<?>[]{PlayerEntity.class}, player); }
    private static void using(PlayerEntity player, boolean using) throws Exception {
        Method method = LivingEntity.class.getDeclaredMethod("setLivingFlag", int.class, boolean.class);
        method.setAccessible(true);
        method.invoke(player, 1, using);
    }
    static void outsideReplay() throws Exception {
        require(!(Boolean) call("muted") && !(Boolean) call("enabled"), "Audio state leaked outside replay playback");
    }
    private static void probe(MinecraftClient client) { client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1)); }
    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private static Object call(String name, Class<?>[] types, Object... values) throws Exception {
        Method method = Class.forName("zsgrooms.replayviewer.ReplayAudio").getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, values);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}

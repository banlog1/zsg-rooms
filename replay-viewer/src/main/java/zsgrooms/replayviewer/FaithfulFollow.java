// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

/** Applies recorded presentation, never recorded game actions, to the isolated replay client. */
final class FaithfulFollow extends DrawableHelper {
    private static final Identifier ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final DetailedFollowHud hud = new DetailedFollowHud();
    private final RecordedScreenView screenView = new RecordedScreenView();
    private DetailedTrack.Frame frame;
    private DetailedState state;
    private boolean active, ownsOptions;
    private int savedDistance, savedPerspective, appliedDistance, appliedPerspective;
    private int currentTime;
    private String unavailable = "Detailed capture unavailable";

    void update(RecordingIndex index, int time, boolean enabled) {
        active = enabled;
        currentTime = time;
        if (!enabled) { frame = null; state = null; releaseOptions(); return; }
        RaceRecording recording = index.recording;
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
        PlayerEntity target = FarFollowController.recordedPlayer(client);
        DetailedTrack.Frame next = interval == null || target == null || index.detail == null ? null
                : index.detail.at(time, interval.world, target.getEntityId(), interval.start);
        if (next != frame) { frame = next; state = next == null ? null : DetailedState.decode(next.payload); }
        unavailable = index.detailStatus.isEmpty() ? "Detailed capture unavailable at this time" : index.detailStatus;
        if (state == null) { releaseOptions(); return; }
        applyOptions(state.distance, state.perspective);
    }

    private void applyOptions(int distance, int perspective) {
        if (!ownsOptions) {
            savedDistance = client.options.viewDistance;
            savedPerspective = client.options.perspective;
            ownsOptions = true;
        } else {
            if (client.options.viewDistance != appliedDistance) savedDistance = client.options.viewDistance;
            if (client.options.perspective != appliedPerspective) savedPerspective = client.options.perspective;
        }
        client.options.viewDistance = appliedDistance = distance;
        client.options.perspective = appliedPerspective = perspective;
    }

    void releaseOptions() {
        if (!ownsOptions) return;
        if (client.options.viewDistance == appliedDistance) client.options.viewDistance = savedDistance;
        if (client.options.perspective == appliedPerspective) client.options.perspective = savedPerspective;
        ownsOptions = false;
    }

    boolean hidden() { return active && state != null && state.hidden; }
    boolean active() { return active; }
    boolean screenOpen() { return active && state != null && state.screen != 0; }
    int reservedHeight() { return hidden() ? 0 : hud.reservedHeight(); }

    ItemStack heldItem(PlayerEntity player, net.minecraft.entity.EquipmentSlot slot, int time) {
        if (!active || state == null || time != currentTime || player.world != client.world
                || player != client.player && player != FarFollowController.recordedPlayer(client)) return null;
        return slot == net.minecraft.entity.EquipmentSlot.MAINHAND ? state.hud.items[state.hud.selected]
                : slot == net.minecraft.entity.EquipmentSlot.OFFHAND ? state.hud.items[40] : null;
    }

    void inspect() {
        if (active && state != null) client.openScreen(new Inspection());
    }

    void render(MatrixStack matrices, int screenInset, int effectsTop) {
        if (!active) return;
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        if (state == null) {
            drawCenteredString(matrices, client.textRenderer, unavailable, width / 2, height - 48, 0xCCCCCC);
            return;
        }
        PlayerEntity player = FarFollowController.recordedPlayer(client);
        if (!state.hidden) {
            hud.renderCaptured(matrices, state.hud, player, effectsTop);
            if (state.screen == 0 && !state.paused) crosshair(matrices, width, height, player);
        }
        if (state.screen >= 1 && state.screen <= 3) drawScreen(matrices, width, height, -1, -1, false, screenInset);
        else if (state.screen == 4) drawCenteredString(matrices, client.textRenderer,
                "Recorded screen unavailable", width / 2, height / 2, 0xCCCCCC);
        if (state.paused) {
            fill(matrices, 0, 0, width, height, 0x88606060);
            drawCenteredString(matrices, client.textRenderer, "Paused", width / 2, height / 2 - 4, 0xFFFFFF);
        }
    }

    private void crosshair(MatrixStack matrices, int width, int height, PlayerEntity player) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1, 1, 1, 1);
        client.getTextureManager().bindTexture(ICONS);
        float fraction = state.paused || frame == null ? 0 : Math.max(0, Math.min(1, (currentTime - frame.time) / 50F));
        float cooldown = state.cooldown0 + (state.cooldown1 - state.cooldown0) * fraction;
        if (state.perspective == 0) {
            RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
                    com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
                    com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);
            drawTexture(matrices, (width - 15) / 2, (height - 15) / 2, 0, 0, 15, 15);
            if (state.indicator == 1) {
                int x = width / 2 - 8, y = height / 2 + 9;
                if (state.ready) drawTexture(matrices, x, y, 68, 94, 16, 16);
                else if (cooldown < 1) {
                    drawTexture(matrices, x, y, 36, 94, 16, 4);
                    drawTexture(matrices, x, y, 52, 94, (int) (cooldown * 17), 4);
                }
            }
            RenderSystem.defaultBlendFunc();
        }
        if (state.indicator == 2 && cooldown < 1) {
            int x = width / 2 + (player != null && player.getMainArm() == Arm.LEFT ? -113 : 97), y = height - 20;
            int fill = (int) (cooldown * 19);
            drawTexture(matrices, x, y, 0, 94, 18, 18);
            drawTexture(matrices, x, y + 18 - fill, 18, 112 - fill, 18, fill);
        }
    }

    private ItemStack drawScreen(MatrixStack matrices, int width, int height, int mouseX, int mouseY, boolean manual, int bottomInset) {
        boolean recorded = state.screen >= 1 && state.screen <= 3;
        ItemStack[] slots = recorded ? state.slots : state.inventory();
        ItemStack hovered = screenView.draw(matrices, recorded ? state.screen : 1, recorded ? state.title : "Inventory",
                slots, recorded ? state.cursor : state.hud.items[41], width, height, bottomInset,
                FarFollowController.recordedPlayer(client), mouseX, mouseY, recorded);
        if (manual && !recorded) drawCenteredString(matrices, client.textRenderer,
                client.textRenderer.trimToWidth("Crafting slots unavailable while inventory was closed", width - 16),
                width / 2, height - 12, 0xCCCCCC);
        return hovered;
    }

    private final class Inspection extends Screen {
        Inspection() { super(new LiteralText("Inspect recorded screen")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (state == null) {
                renderBackground(matrices);
                drawCenteredString(matrices, textRenderer, unavailable, width / 2, height / 2, 0xCCCCCC);
            } else {
                ItemStack hovered = drawScreen(matrices, width, height, mouseX, mouseY, true, 0);
                if (!hovered.isEmpty()) renderTooltip(matrices, hovered, mouseX, mouseY);
            }
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (client.options.keyInventory.matchesKey(key, scan)) { onClose(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void tick() { if (!active || client.world == null) onClose(); }
        @Override public void onClose() { client.openScreen(null); }
    }
}

package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.EnumMap;

final class SeedTypeIcon {
    private static final EnumMap<SeedVisualType, ItemStack> STACKS = new EnumMap<>(SeedVisualType.class);

    static Item item(SeedVisualType type) {
        switch (type) {
            case TREASURE: return Items.CHEST;
            case TEMPLE: return Items.CHISELED_SANDSTONE;
            case VILLAGE: return Items.BELL;
            case SHIPWRECK: return Items.OAK_BOAT;
            case PORTAL: return Items.CRYING_OBSIDIAN;
            case JUNGLE: return Items.MOSSY_COBBLESTONE;
            case MANUAL: return Items.WRITABLE_BOOK;
            case ROOM: return Items.NAME_TAG;
            default: return Items.COMPASS;
        }
    }

    static void draw(MatrixStack matrices, MinecraftClient client, SeedVisualType type, int x, int y) {
        ItemStack stack = STACKS.computeIfAbsent(type, value -> new ItemStack(item(value)));
        // The 1.16 GUI item renderer uses the global model-view matrix, not MatrixStack.
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(matrices.peek().getModel());
        try {
            client.getItemRenderer().renderInGui(stack, x, y);
        } finally {
            RenderSystem.popMatrix();
            RenderSystem.disableDepthTest();
        }
    }

    private SeedTypeIcon() {}
}
